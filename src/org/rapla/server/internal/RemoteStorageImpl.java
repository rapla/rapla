/*--------------------------------------------------------------------------*
 | Copyright (C) 2013 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.server.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.rapla.RaplaMainContainer;
import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Category;
import org.rapla.entities.DependencyException;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.Mementable;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.SimpleIdentifier;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.UpdateModule;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.mail.MailException;
import org.rapla.plugin.mail.MailPlugin;
import org.rapla.plugin.mail.server.MailInterface;
import org.rapla.server.AuthenticationStore;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.LocalCache;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageUpdateListener;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.UpdateResult.Change;
import org.rapla.storage.dbrm.EntityList;
import org.rapla.storage.dbrm.RemoteStorage;
import org.rapla.storage.impl.EntityStore;

/** Provides an adapter for each client-session to their shared storage operator
 * Handles security and synchronizing aspects.
 */
public class RemoteStorageImpl implements RemoteMethodFactory<RemoteStorage>, StorageUpdateListener {
    CachableStorageOperator operator;
    
    protected SecurityManager security;
    
   // RemoteServer server;
    RaplaContext context;
    
    long repositoryVersion = 0;
    long cleanupPointVersion = 0;
        
    protected AuthenticationStore authenticationStore;
    Logger logger;
    ClientFacade facade;
    RaplaLocale raplaLocale;
    CommandScheduler commandQueue;
    
    
    public RemoteStorageImpl(RaplaContext context) throws RaplaException {
        this.context = context;
        this.logger = context.lookup( Logger.class);
        commandQueue = context.lookup( CommandScheduler.class);
        facade = context.lookup( ClientFacade.class);
        raplaLocale = context.lookup( RaplaLocale.class);
        operator = (CachableStorageOperator)facade.getOperator();
        operator.addStorageUpdateListener( this);
        security = context.lookup( SecurityManager.class);
        if ( context.has( AuthenticationStore.class ) )
        {
            try 
            {
                authenticationStore = context.lookup( AuthenticationStore.class );
                getLogger().info( "Using AuthenticationStore " + authenticationStore.getName() );
            } 
            catch ( RaplaException ex)
            {
                getLogger().error( "Can't initialize configured authentication store. Using default authentication." , ex);
            }
        }
        initEventCleanup();
    }
    
    public Logger getLogger() {
        return logger;
    }

    
    public I18nBundle getI18n() throws RaplaException {
    	return context.lookup(RaplaComponent.RAPLA_RESOURCES);
    }


    private static EntityList makeTransactionSafe(Collection<? extends RefEntity<?>> objectList, long repositoryVersion) {
        Collection<? extends RefEntity<?>> emptyList = Collections.emptyList();
        EntityList saveList = new EntityList(emptyList, repositoryVersion);
        Iterator<? extends RefEntity<?>> it = objectList.iterator();
        while (it.hasNext()) {
            @SuppressWarnings("unchecked")
            Mementable<RefEntity<?>> mementable = (Mementable<RefEntity<?>>)it.next();
            saveList.add((mementable.deepClone()));
        }
        return saveList;
    }
    
    static UpdateEvent createTransactionSafeUpdateEvent( UpdateResult updateResult )
    {
        User user = updateResult.getUser();
        UpdateEvent saveEvent = new UpdateEvent();
        if ( user != null )
        {
            saveEvent.setUserId( ( (RefEntity<?>) updateResult.getUser() ).getId() );
        }
        {
            Iterator<UpdateResult.Add> it = updateResult.getOperations( UpdateResult.Add.class );
            while ( it.hasNext() )
            {
                RefEntity<?> newEntity = (RefEntity<?>) (  it.next() ).getNew();
				saveEvent.putStore( newEntity );
            }
        }
        {
            Iterator<UpdateResult.Change> it = updateResult.getOperations( UpdateResult.Change.class );
            while ( it.hasNext() )
            {
                RefEntity<?> newEntity = (RefEntity<?>) ( it.next() ).getNew();
				saveEvent.putStore( newEntity );
            }
        }
        {
            Iterator<UpdateResult.Remove> it = updateResult.getOperations( UpdateResult.Remove.class );
            while ( it.hasNext() )
            {
                RefEntity<?> removeEntity = (RefEntity<?>) (it.next() ).getCurrent();
				saveEvent.putRemove( removeEntity );
            }
        }
        return saveEvent;
    }
    
    private Map<RefEntity<?>,Long> updateMap = new HashMap<RefEntity<?>,Long>();
    private Map<User,Long> needConflictRefresh = new HashMap<User,Long>();
    private Map<User,Long> needResourceRefresh = new HashMap<User,Long>();
    private Map<RefEntity<?>,Long> removeMap = new HashMap<RefEntity<?>,Long>();
    private SortedMap<Long, TimeInterval> invalidateMap = new TreeMap<Long,TimeInterval>();

    
 // Implementation of StorageUpdateListener
    synchronized public void objectsUpdated( UpdateResult evt )
    {
        // notify the client for changes
        repositoryVersion++;
        TimeInterval invalidateInterval = evt.calulateInvalidateInterval();
        invalidateMap.put(repositoryVersion, invalidateInterval);
    	
        UpdateEvent safeResultEvent = createTransactionSafeUpdateEvent( evt );
        if ( getLogger().isDebugEnabled() )
            getLogger().debug( "Storage was modified. Calling notify." );
        for ( Iterator<RefEntity<?>> it = safeResultEvent.getStoreObjects().iterator(); it.hasNext(); )
        {
            RefEntity<?> obj = it.next();
        	RaplaType<?> raplaType = obj.getRaplaType();
        	if ((raplaType == Appointment.TYPE || raplaType == Reservation.TYPE) && !RaplaComponent.isTemplate(obj))
        	{
        		continue;
        	}
            removeMap.remove( obj );
            updateMap.remove( obj );
            updateMap.put( obj, new Long( repositoryVersion ) );
        }
        for ( Iterator<RefEntity<?>> it = safeResultEvent.getRemoveObjects().iterator(); it.hasNext(); )
        {
            RefEntity<?> obj =  it.next();
        	RaplaType<?> raplaType = obj.getRaplaType();
        	if ((raplaType == Appointment.TYPE || raplaType == Reservation.TYPE) && !RaplaComponent.isTemplate(obj))
        	{
        		continue;
        	}
            updateMap.remove( obj );
            removeMap.remove( obj );
            removeMap.put( obj, new Long( repositoryVersion ) );
        }
        
        // now we check if a the resources have changed in a way that a user needs to refresh all resources. That is the case, when 
        // someone changes the permissions on one or more resource and that affects  the visibility of that resource to a user, 
        // so its either pushed to the client or removed from it.
        //
        // We also check if a permission on a reservation has changed, so that it is no longer or new in the conflict list of a certain user.
        // If that is the case we trigger an invalidate of the conflicts for a user
        Set<User> usersResourceRefresh = new HashSet<User>();
        Category superCategory = operator.getSuperCategory();
		Set<Category> groupsConflictRefresh = new HashSet<Category>();
		Set<User> usersConflictRefresh = new HashSet<User>();
		Iterator<Change> operations = evt.getOperations(UpdateResult.Change.class);
        Set<Permission> invalidatePermissions = new HashSet<Permission>();
        while ( operations.hasNext())
		{
			Change operation = operations.next();
			RaplaObject newObject = operation.getNew();
			if ( newObject.getRaplaType().is( Allocatable.TYPE))
			{
				Allocatable newAlloc = (Allocatable) newObject;
				Allocatable current = (Allocatable) operation.getOld();
				Permission[] oldPermissions = current.getPermissions();
				Permission[] newPermissions = newAlloc.getPermissions();
				// we leave this condition for a faster equals check
				if  (oldPermissions.length == newPermissions.length)
				{
					for (int i=0;i<oldPermissions.length;i++)
					{
						Permission oldPermission = oldPermissions[i];
						Permission newPermission = newPermissions[i];
						if (!oldPermission.equals(newPermission))
						{
							invalidatePermissions.add( oldPermission);
							invalidatePermissions.add( newPermission);
						}
					}
				}
				else
				{
					HashSet<Permission> newSet = new HashSet<Permission>(Arrays.asList(newPermissions));
					HashSet<Permission> oldSet = new HashSet<Permission>(Arrays.asList(oldPermissions));
					{
						HashSet<Permission> changed = new HashSet<Permission>( newSet);
						changed.removeAll( oldSet);
						invalidatePermissions.addAll(changed);
					}
					{
						HashSet<Permission> changed = new HashSet<Permission>(oldSet);
						changed.removeAll( newSet);
						invalidatePermissions.addAll(changed);
					}
				}
			}
			if ( newObject.getRaplaType().is( User.TYPE))
			{
				User newUser = (User) newObject;
				User oldUser = (User) operation.getOld();
				HashSet<Category> newGroups = new HashSet<Category>(Arrays.asList(newUser.getGroups()));
				HashSet<Category> oldGroups = new HashSet<Category>(Arrays.asList(oldUser.getGroups()));
				if ( !newGroups.equals( oldGroups) || newUser.isAdmin() != oldUser.isAdmin())
				{
					usersResourceRefresh.add( newUser);
				}
				
			}
			if ( newObject.getRaplaType().is( Reservation.TYPE))
			{
				Reservation newEvent = (Reservation) newObject;
				Reservation oldEvent = (Reservation) operation.getOld();
				User newOwner = newEvent.getOwner();
				User oldOwner = oldEvent.getOwner();
				if ( newOwner != null && oldOwner != null && (newOwner.equals( oldOwner)) )
				{
					usersConflictRefresh.add( newOwner);
					usersConflictRefresh.add( oldOwner);
				}
				Collection<Category> newGroup = RaplaComponent.getPermissionGroups( newEvent, superCategory);
				Collection<Category> oldGroup = RaplaComponent.getPermissionGroups( oldEvent, superCategory);
				if (newGroup != null && (oldGroup == null || !oldGroup.equals(newGroup)))
				{
					groupsConflictRefresh.addAll( newGroup);
				}
				if (oldGroup != null && (newGroup == null || !oldGroup.equals(newGroup)))
				{
					groupsConflictRefresh.addAll( oldGroup);
				}
			}
		}
        boolean addAllUsersToConflictRefresh = groupsConflictRefresh.contains( superCategory);
        Set<Category> groupsResourceRefrsesh = new HashSet<Category>();
        try
        {
        	if ( !invalidatePermissions.isEmpty() || ! addAllUsersToConflictRefresh || !! groupsConflictRefresh.isEmpty())
        	{
	        	Collection<User> allUsers = operator.getObjects( User.class);
		        for ( Permission permission:invalidatePermissions)
		        {
		        	User user = permission.getUser();
		        	if ( user != null)
		        	{
		        		usersResourceRefresh.add( user);
		        	}
		        	Category group = permission.getGroup();
		        	if ( group != null)
		        	{
		        		groupsResourceRefrsesh.add( group);
		        	}
		        	if ( user == null && group == null)
		        	{
		        		usersResourceRefresh.addAll( allUsers);
		        		break;
		        	}
		        }
		        for ( User user:allUsers)
		        {
		        	if ( usersResourceRefresh.contains( user))
		        	{
		        		continue;
		        	}
		        	for (Category group:user.getGroups())
		        	{
		        		if ( groupsResourceRefrsesh.contains( group))
		        		{
		        			usersResourceRefresh.add( user);
		        			break;
		        		}
		        		if ( addAllUsersToConflictRefresh || groupsConflictRefresh.contains( group))
		        		{
		        			usersConflictRefresh.add( user);
		        			break;
		        		}
		        	}
		        }
        	}
        } 
        catch ( RaplaException ex) 
        {
        	getLogger().error(ex.getMessage(), ex);
        }
        for ( User user:usersResourceRefresh)
        {
        	if ( !user.isAdmin())
        	{
        		needResourceRefresh.put( user, repositoryVersion);
        		needConflictRefresh.put( user, repositoryVersion);
        	}
        }
        for ( User user:usersConflictRefresh)
        {
        	if ( !user.isAdmin())
        	{
        		needConflictRefresh.put( user, repositoryVersion);
        	}
        }
    }

	/** regulary removes all old update messages that are older than the updateInterval ( factor 10) and at least 1 hour old */
    private final void initEventCleanup()
    {
    	
        Command cleanupTask = new Command()
        {
            public void execute()
            {
                initEventCleanup();
            }
        };
        {
            int delay = 10000;
            {
                RefEntity<?>[] keys = updateMap.keySet().toArray(new RefEntity[] {});
                for ( int i=0;i<keys.length;i++)
                {
                    RefEntity<?> key = keys[i];
                    Long lastVersion =  updateMap.get( key );
                    if ( lastVersion.longValue() <= cleanupPointVersion )
                    {
                        updateMap.remove( key );
                    }
                }
            }
            {
                RefEntity<?>[] keys = removeMap.keySet().toArray(new RefEntity[] {});
                for ( int i=0;i<keys.length;i++)
                {
                    RefEntity<?> key = keys[i];
                    Long lastVersion = removeMap.get( key );
                    if ( lastVersion.longValue() <= cleanupPointVersion )
                    {
                        removeMap.remove( key );
                    }
                }
            }
            ArrayList<Long> removeList = new ArrayList<Long>(invalidateMap.headMap(cleanupPointVersion).keySet());
			for ( Long toRemove:removeList)
			{
				invalidateMap.remove( toRemove);
			}
            
            
            cleanupPointVersion = repositoryVersion;
            if ( operator.isConnected() )
            {
                try
                {
                    delay = operator.getPreferences( null ).getEntryAsInteger( UpdateModule.REFRESH_INTERVAL_ENTRY,  delay );
                }
                catch ( RaplaException e )
                {
                    getLogger().error( "Error during cleanup.", e );
                }
            }
            long scheduleDelay = Math.max( DateTools.MILLISECONDS_PER_HOUR, delay * 10 );
            //scheduleDelay = 30000;
            commandQueue.schedule( cleanupTask,  scheduleDelay);
        }
    }

    
    public void updateError(RaplaException ex) {
    }

    public void storageDisconnected(String disconnectionMessage) {
    }


    @Override
    public RemoteStorage createService(final RemoteSession session) {
        return new RemoteStorage() {
            public EntityList getResources() throws RaplaException
            {
                checkAuthentified();
                User user = getSessionUser();
                getLogger().debug ("A RemoteServer wants to get all resource-objects.");
                {
                    Collection<RefEntity<?>> visibleEntities = operator.getVisibleEntities(user);
                    EntityList resources = makeTransactionSafe(visibleEntities, repositoryVersion);
                    return resources;
                }
            }

            public EntityList getEntityRecursive(SimpleIdentifier... ids) throws RaplaException {
                checkAuthentified();
                User sessionUser = getSessionUser();
                //synchronized (operator.getLock()) 
                {
                    ArrayList<RefEntity<?>> completeList = new ArrayList<RefEntity<?>>();
	                for ( SimpleIdentifier id:ids)
                	{
	                    RefEntity<?> entity = operator.resolveId(id);
	                    security.checkRead(sessionUser, entity);
	                    completeList.add( entity );
	                    getLogger().debug("Get entity " + entity);
	                    Iterator<RefEntity<?>> it =  entity.getSubEntities();
	                    // FIXME check if the client has the permission to read the entity
	                    while (it.hasNext()) {
	                        completeList.add( it.next() );
	                    }
                	}
                    EntityList list = makeTransactionSafe( completeList, repositoryVersion);
                    return list;
                }
            }
			
            public EntityList getReservations(SimpleIdentifier[] allocatableIds,Date start,Date end) throws RaplaException
            {
                checkAuthentified();
                User sessionUser = getSessionUser();
                User user = null;
                getLogger().debug ("A RemoteServer wants to reservations from ." + start + " to " + end);
                {
                	boolean canReadFromOthers = facade.canReadReservationsFromOthers(sessionUser);
                	// Reservations and appointments
                    ArrayList<RefEntity<?>> completeList = new ArrayList<RefEntity<?>>();
                    List<Allocatable> allocatables = new ArrayList<Allocatable>();
                    for ( SimpleIdentifier id:allocatableIds)
                    {
                    	RefEntity<?> entity = operator.resolveId(id);
                    	Allocatable allocatable = (Allocatable) entity;
	                    security.checkRead(sessionUser, entity);
						allocatables.add( allocatable);
                    }

                    List<Reservation> reservations = operator.getReservations(user,allocatables, start, end );
                    for (Reservation res:reservations)
                    {
                    	if (isVisible(sessionUser, res))
                		{
                        	completeList.add( (RefEntity<?>) res);
                		}
                    	
                    }
                    Iterator<Reservation> it = reservations.iterator();
                    while (it.hasNext()) {
                        Iterator<RefEntity<?>> it2 = ((RefEntity<?>)it.next()).getSubEntities();
                        while (it2.hasNext()) {
                            completeList.add( it2.next() );
                        }
                    }
                    EntityList list = makeTransactionSafe( completeList, repositoryVersion );
                    for ( RefEntity<?> entity:list)
                    {
                    	if ( entity.getRaplaType() == Reservation.TYPE)
                    	{
                    		ReservationImpl reservation =(ReservationImpl) entity;
                    		User owner = reservation.getOwner();
                    		// check if the user is allowed to read the reservation info 
							if ( !canReadFromOthers && (owner != null && !owner.equals( sessionUser)))
                    		{
								// we can safely change the reservation info here because we cloned it in transaction safe before
								reservation.setReadOnly( false);
                    			DynamicType anonymousReservationType = operator.getCache().getAnonymousReservationType();
								reservation.setClassification( anonymousReservationType.newClassification());
                    		}
                    	}
                    }
                    getLogger().debug("Get reservations " + start + " " + end + ": "
                                       + reservations.size() + "," + list.size());
                    return list;
                }
            }
			protected boolean isVisible(User sessionUser, Reservation res) {
				User owner = res.getOwner();
				if (sessionUser.isAdmin() || owner == null ||  owner.equals(sessionUser) )
				{
					return true;
				}
				for (Allocatable allocatable: res.getAllocatables()) {
					if (allocatable.canRead(sessionUser)) {
						return true;
					}
				}
				return true;
			}

           public long getRepositoryVersion() 
            {
                return repositoryVersion;
            }

            public void restartServer() throws RaplaException {
                checkAuthentified();
                if (!getSessionUser().isAdmin())
                    throw new RaplaSecurityException("Only admins can restart the server");

                context.lookup(ShutdownService.class).shutdown( true);
            }


            public String getServerTime() throws RaplaException {
            	Date raplaTime = operator.getCurrentTimestamp();
            	SerializableDateTimeFormat serializableFormat = raplaLocale.getSerializableFormat();
            	return serializableFormat.formatTimestamp( raplaTime);
            }


            public UpdateEvent dispatch(UpdateEvent event) throws RaplaException
            {
             //   LocalCache cache = operator.getCache();
             //   UpdateEvent event = createUpdateEvent( context,xml, cache );
            	User sessionUser = getSessionUser();
				getLogger().info("Dispatching change for user " + sessionUser);
            	dispatch_( event);
                getLogger().info("Change for user " + sessionUser + " dispatched.");
                UpdateEvent result = createUpdateEvent(Long.valueOf( event.getRepositoryVersion()).longValue());
                return result;
            }
            
            public boolean canChangePassword() throws RaplaException {
                checkAuthentified();
                return authenticationStore == null && operator.canChangePassword();
            }

            public void changePassword(String username
                                       ,String oldPassword
                                       ,String newPassword
                                       ) throws RaplaException
            {
            	checkAuthentified();
            	User sessionUser = getSessionUser();
                
                if (!sessionUser.isAdmin()) {
                    if ( authenticationStore != null ) {
                        throw new RaplaSecurityException("Rapla can't change your password. Authentication handled by ldap plugin." );
                    }
                    operator.authenticate(username,new String(oldPassword));
                }
                @SuppressWarnings("unchecked")
                RefEntity<User> user = (RefEntity<User>)operator.getUser(username);
                operator.changePassword(user,oldPassword.toCharArray(),newPassword.toCharArray());
            }
            
            public void changeName(String username,String newTitle,
                    String newSurename, String newLastname) throws RaplaException 
            {
                @SuppressWarnings("unchecked")
                RefEntity<User> changingUser = (RefEntity<User>)getSessionUser();
                @SuppressWarnings("unchecked")
                RefEntity<User> user = (RefEntity<User>)operator.getUser(username);
                if ( changingUser.cast().isAdmin() || user.equals( changingUser) )
                {
                    operator.changeName(user,newTitle,newSurename,newLastname);
                }
                else
                {
                    throw new RaplaSecurityException("Not allowed to change email from other users");
                }
            }


            public void changeEmail(String username,String newEmail)
                    throws RaplaException 
            {
                @SuppressWarnings("unchecked")
                RefEntity<User> changingUser = (RefEntity<User>)getSessionUser();
                @SuppressWarnings("unchecked")
                RefEntity<User> user = (RefEntity<User>)operator.getUser(username);
                if ( changingUser.cast().isAdmin() || user.equals( changingUser) )
                {
                    operator.changeEmail(user,newEmail);
                }
                else
                {
                    throw new RaplaSecurityException("Not allowed to change email from other users");
                }
            }

            public void confirmEmail(String username,String newEmail)
                    throws RaplaException
            {
            	
            	@SuppressWarnings("unchecked")
                RefEntity<User> changingUser = (RefEntity<User>)getSessionUser();
                @SuppressWarnings("unchecked")
                RefEntity<User> user = (RefEntity<User>)operator.getUser(username);
                if ( changingUser.cast().isAdmin() || user.equals( changingUser) )
                {
                	String subject =  getString("security_code");
    			    Preferences prefs = operator.getPreferences( null );
    				String mailbody = "" + getString("send_code_mail_body_1") + user.cast().getUsername() + ",\n\n" 
    	        		+ getString("send_code_mail_body_2") + "\n\n" + getString("security_code") + Math.abs(user.cast().getEmail().hashCode()) 
    	        		+ "\n\n" + getString("send_code_mail_body_3") + "\n\n" + "-----------------------------------------------------------------------------------"
    	        		+ "\n\n" + getString("send_code_mail_body_4") + prefs.getEntryAsString(RaplaMainContainer.TITLE, getString("rapla.title")) + " "
    	        		+ getString("send_code_mail_body_5");
    	
    			
    	    		final MailInterface mail = context.lookup(MailInterface.class);
    	            final String defaultSender = prefs.getEntryAsString( MailPlugin.DEFAULT_SENDER_ENTRY, "");
    	            
    	            try {
    					mail.sendMail( defaultSender, newEmail,subject, "" + mailbody);
    				} catch (MailException e) {
    					throw new RaplaException( e.getMessage(), e);
    				}
                }
                else
                {
                    throw new RaplaSecurityException("Not allowed to change email from other users");
                }
            }
            
            
            private String getString(String key) throws RaplaException{
				return getI18n().getString( key);
			}
            
			public SimpleIdentifier[] createIdentifier(RaplaType raplaType, int count) throws RaplaException {
                checkAuthentified();
                //User user =
                getSessionUser(); //check if authenified
                SimpleIdentifier[] simpleIdentifier =(SimpleIdentifier[]) operator.createIdentifier(raplaType, count);
                return simpleIdentifier;
            }

            public void authenticate(String username, String password) throws RaplaException
            {
                getSessionUser(); //check if authenified
                Logger logger = getLogger().getChildLogger("passwordcheck");
				if ( authenticationStore != null  )
                {
                	logger.info("Checking external authentifiction for user " + username);
                	if (authenticationStore.authenticate( username, password ))
                	{
                		return;
                	}
                	logger.info("Now trying to authenticate with local store" + username);
                    operator.authenticate( username, password );
                    // do nothing
                } // if the authenticationStore can't authenticate the user is checked against the local database
                else
                {
                	logger.info("Check password for " + username);
                    operator.authenticate( username, password );
                }
            }
            
            public UpdateEvent refresh(String time) throws RaplaException
            {
                checkAuthentified();
                UpdateEvent event = createUpdateEvent(Long.valueOf( time).longValue());
                return event;
            }

            
            public Logger getLogger()
            {
                return session.getLogger();
            }
           

            private void checkAuthentified() throws RaplaSecurityException {
                if (!session.isAuthentified()) {
                    
                    throw new RaplaSecurityException(RemoteStorage.USER_WAS_NOT_AUTHENTIFIED);
                }
            }

            private User getSessionUser() throws RaplaException {
                return session.getUser();
            }
            
            private void dispatch_(UpdateEvent evt) throws RaplaException {
                checkAuthentified();
                try {
                    User user;
                    if ( evt.getUserId() != null)
                    {
                        user = (User) operator.resolveId(evt.getUserId());
                    }
                    else
                    {
                        user = session.getUser();
                    }
                    Collection<RefEntity<?>> storeObjects = evt.getStoreObjects();
                    LocalCache cache = operator.getCache();
                    EntityStore resolver = new EntityStore(cache, cache.getSuperCategory());
            		resolver.addAll(storeObjects);
                    for (RefEntity<?> entity:storeObjects) {
                        if (getLogger().isDebugEnabled())
                            getLogger().debug("Contextualizing " + entity);
                        entity.resolveEntities( resolver);
                    }

                    Collection<RefEntity<?>> removeObjects = evt.getRemoveObjects();
                    resolver.addAll( removeObjects );
					for ( RefEntity<?> entity:removeObjects)
                    {
                        entity.resolveEntities( resolver);
                    }

                    for (RefEntity<?> entity:storeObjects) 
                    {
                        security.checkWritePermissions(user,entity);
                    }
                    for ( RefEntity<?> entity:removeObjects)
                    {
                    	security.checkWritePermissions(user,entity);
                    }

                    if (this.getLogger().isDebugEnabled())
                        this.getLogger().debug("Dispatching changes to " + operator.getClass());

                    operator.dispatch(evt);
                    if (this.getLogger().isDebugEnabled())
                        this.getLogger().debug("Changes dispatched returning result.");
                } catch (DependencyException ex) {
                    throw ex;
                } catch (RaplaNewVersionException ex) {
                	throw ex;
                } catch (RaplaSecurityException ex) {
                    this.getLogger().warn(ex.getMessage());
                    throw ex;
                } catch (RaplaException ex) {
                    this.getLogger().error(ex.getMessage(),ex);
                    throw ex;
                } catch (Exception ex) {
                    this.getLogger().error(ex.getMessage(),ex);
                    throw new RaplaException(ex);
                } catch (Error ex) {
                    this.getLogger().error(ex.getMessage(),ex);
                    throw ex;
                }
            }
            
            synchronized private UpdateEvent createUpdateEvent( long clientRepositoryVersion ) throws RaplaException
            {
                User user = getSessionUser();
                long currentVersion = repositoryVersion;
                UpdateEvent safeResultEvent = new UpdateEvent();
                safeResultEvent.setRepositoryVersion( currentVersion );
                if ( clientRepositoryVersion < currentVersion )
                {
                    for ( Iterator<RefEntity<?>> it = updateMap.keySet().iterator(); it.hasNext(); )
                    {
                        RefEntity<?> obj = it.next();
                        Long lastVersion = updateMap.get( obj );
                        if ( lastVersion.longValue() > clientRepositoryVersion )
                        {
                        	processClientReadable( user, safeResultEvent, obj, false);
                        }
                    }
                    for ( Iterator<RefEntity<?>> it = removeMap.keySet().iterator(); it.hasNext(); )
                    {
                        RefEntity<?> obj =  it.next();
                        Long lastVersion = removeMap.get( obj );
                        if ( lastVersion.longValue() > clientRepositoryVersion )
                        {
                        	processClientReadable( user, safeResultEvent, obj, true);
                        }
                    }
                    TimeInterval invalidateInterval;
                    {
	                    Long lastVersion = needConflictRefresh.get( user);
	                    if ( lastVersion != null && lastVersion > clientRepositoryVersion)
	                    {
	                    	invalidateInterval = new TimeInterval( null, null);
	                    }
	                    else
	                    {
	                    	invalidateInterval = getInvalidateInterval( clientRepositoryVersion, currentVersion);
	                    }
                    }
                    boolean resourceRefresh;
                    {
	                    Long lastVersion = needResourceRefresh.get( user);
	                    resourceRefresh = ( lastVersion != null && lastVersion > clientRepositoryVersion);
                    }
                    safeResultEvent.setNeedResourcesRefresh( resourceRefresh);
                    safeResultEvent.setInvalidateInterval( invalidateInterval);
                }
                return safeResultEvent;
            }
            
			protected void processClientReadable(User user,
					UpdateEvent safeResultEvent, RefEntity<?> obj, boolean remove) {
				boolean clientStore = true;
				if (user != null )
				{
                    // we don't transmit preferences for other users
				    if ( obj instanceof Preferences)
				    {
				        User owner = ((Preferences) obj).getOwner();
				        if  ( owner != null && !owner.equals( user))
				        {
				        	clientStore = false;
				        }
				    }
				    else if ( obj instanceof Allocatable)
				    {
				    	Allocatable alloc = (Allocatable) obj;
				    	if ( !alloc.canReadOnlyInformation(user))
				    	{
				    		clientStore = false;
				    	}
				    }
				    else if ( obj instanceof Conflict)
				    {
				    	Conflict conflict = (Conflict) obj;
				    	if ( conflict.canModify( user) )
				    	{
				    		// TODO We ignore references from deleted conflicts. Because they can cause weird problems e.g. when an appointment in a reservation container is deleted resulting in a conflict remove leading to the appointment now without a reservation not correctly transfered to the client side  
				    		if (!remove)
				    		{
				    			
				    			Set<RefEntity<?>> toAdd = new HashSet<RefEntity<?>>();
								Appointment appointment1 = conflict.getAppointment1();
								toAdd.addAll( getDependentObjects(appointment1));
								Appointment appointment2 = conflict.getAppointment2();
								toAdd.addAll( getDependentObjects(appointment2));
								
								for (RefEntity<?> entity:toAdd)
								{
									safeResultEvent.putReference( entity);
								}
				    		}
				    	}
				    	else
				    	{
				    		clientStore = false;
				    	}
				    }
				}
				if ( clientStore)
				{
					if ( remove)
					{
						safeResultEvent.putRemove( obj );
					}
					else
					{
						safeResultEvent.putStore( obj );
					}
				}
			}
			
			protected List<RefEntity<?>> getDependentObjects(
					Appointment appointment) {
				List<RefEntity<?>> toAdd = new ArrayList<RefEntity<?>>();
				toAdd.add( (RefEntity<?>)appointment);
				@SuppressWarnings("unchecked")
				RefEntity<Reservation> reservation = (RefEntity<Reservation>)appointment.getReservation();
				{
					toAdd.add(reservation);
					Comparable id = reservation.getId();
					RefEntity<?> inCache = operator.getCache().get( id);
					if ( inCache != null && inCache.getVersion() > reservation.getVersion())
					{
						getLogger().error("Try to send an older version of the reservation to the client " + reservation.cast().getName( raplaLocale.getLocale()));
					}
					Iterator<RefEntity<?>> it = reservation.getSubEntities();
					while ( it.hasNext())
					{
						toAdd.add(it.next());
					}
				}
				if (!toAdd.contains(appointment))
				{
					getLogger().error(appointment.toString() + " at " + raplaLocale.formatDate(appointment.getStart()) + " does refer to reservation " + reservation.cast().getName( raplaLocale.getLocale()) + " but the reservation does not refer back.");
				}
				return toAdd;
			}
			

			private TimeInterval getInvalidateInterval(
				 long clientRepositoryVersion, long currentVersion) 
			{
				TimeInterval interval = null;
				for ( long version = clientRepositoryVersion;version<=currentVersion;version++)
				{
					TimeInterval current = invalidateMap.get( version);
					
					if ( current != null)
					{
						interval = current.union( interval);
					}
				}
				return interval;
			
			}
			
			public EntityList getConflicts() throws RaplaException 
			{
            	Set<RefEntity<?>> completeList = new HashSet<RefEntity<?>>();
            	User sessionUser = getSessionUser();
				Collection<Conflict> conflicts = operator.getConflicts( sessionUser);
				for ( Conflict conflict:conflicts)
				{
					RefEntity<?> conflictRef = (RefEntity<?>)conflict;
					completeList.add(conflictRef);
 					completeList.addAll( getDependentObjects(conflict.getAppointment1()));
 					completeList.addAll( getDependentObjects(conflict.getAppointment2()));
				}
				EntityList list = makeTransactionSafe( completeList, repositoryVersion );
                return list;

            }
			public Integer[][] getFirstAllocatableBindings(SimpleIdentifier[] allocatableIds, Appointment[] appointments,SimpleIdentifier[] ignoreList) throws RaplaException
			{
                checkAuthentified();
                Integer[][] result = new Integer[allocatableIds.length][];
        		List<Allocatable> allocatables = resolveAllocatables(allocatableIds);
        		Collection<Reservation> ignoreConflictsWith = resolveReservations(ignoreList);
                Map<Allocatable, Collection<Appointment>> bindings = operator.getFirstAllocatableBindings(allocatables, Arrays.asList(appointments), ignoreConflictsWith);
                for ( int i=0;i<result.length;i++)
                {
                	Allocatable alloc = allocatables.get( i);
                	Collection<Appointment> apps = bindings.get(alloc);
                	if ( apps == null)
                	{
                		apps = Collections.emptyList();
                	}
                	Integer[] indexArray = new Integer[apps.size()];
                	int index = 0;
                	for ( Appointment app: apps)
                	{
                    	for ( int j=0;j<appointments.length;j++)
                    	{
                    		if (appointments[j].equals(app ))
                    		{
                    			indexArray[index++] = j;
                    		}
                    	}
                	}
                	result[i] = indexArray;
                }
            	return result;
			}
			
			public EntityList getAllAllocatableBindings(SimpleIdentifier[] allocatableIds, Appointment[] appointments, SimpleIdentifier[] ignoreList) throws RaplaException
			{
                checkAuthentified();
                Set<RefEntity<?>> completeList = new HashSet<RefEntity<?>>();
        		Collection<Allocatable> allocatables = resolveAllocatables(allocatableIds);
                Collection<Reservation> ignoreConflictsWith = resolveReservations(ignoreList);
                Map<Allocatable, Map<Appointment, Collection<Appointment>>> bindings = operator.getAllAllocatableBindings(allocatables, Arrays.asList(appointments), ignoreConflictsWith);
				for ( Map<Appointment,Collection<Appointment>> appointmentBindings:bindings.values())
				{
                    for ( Collection<Appointment> bound: appointmentBindings.values())
    				{
    					for ( Appointment appointment: bound)
    					{
    						@SuppressWarnings("unchecked")
    						RefEntity<Reservation> reservation = (RefEntity<Reservation>)appointment.getReservation();
    						completeList.add(reservation);
    						Iterator<RefEntity<?>> it = reservation.getSubEntities();
    						while ( it.hasNext())
    						{
    							completeList.add(it.next());
    						}
    					}
    				}
				}
				EntityList list = makeTransactionSafe( completeList, repositoryVersion );
                return list;
			}
			private List<Allocatable> resolveAllocatables(
					SimpleIdentifier[] allocatableIds) throws RaplaException,
					EntityNotFoundException, RaplaSecurityException {
				List<Allocatable> allocatables = new ArrayList<Allocatable>();
				User sessionUser = getSessionUser();
				for ( SimpleIdentifier id:allocatableIds)
				{
					RefEntity<?> entity = operator.resolveId(id);
					allocatables.add( (Allocatable) entity);
					security.checkRead(sessionUser, entity);
				}
				return allocatables;
			}
			private Collection<Reservation> resolveReservations(
					SimpleIdentifier[] ignoreList) {
				Set<Reservation> ignoreConflictsWith = new HashSet<Reservation>();
				for (SimpleIdentifier reservationId: ignoreList)
				{
					try
					{
						RefEntity<?> entity = operator.resolveId(reservationId);
						ignoreConflictsWith.add( (Reservation) entity);
					}
					catch (EntityNotFoundException ex)
					{
						// Do nothing reservation not found and assumed new
					}
				}
				return ignoreConflictsWith;
			}
			
			public void logEntityNotFound(String logMessage,SimpleIdentifier... referencedIds)
			{
				StringBuilder buf = new StringBuilder();
				buf.append("{");
				for  (SimpleIdentifier id: referencedIds)
				{
					buf.append("{ id=");
					buf.append(id.toString());
					buf.append(": ");
					RefEntity<?> refEntity = operator.getCache().get(id);
					if ( refEntity != null )
					{
						buf.append( refEntity.toString());
					}
					else
					{
						buf.append("NOT FOUND");
					}
					buf.append("},  ");
				}
				buf.append("}");
				getLogger().error("EntityNotFoundFoundExceptionOnClient "+ logMessage + " " + buf.toString());
			}
			

        };
        
        
    }

}

