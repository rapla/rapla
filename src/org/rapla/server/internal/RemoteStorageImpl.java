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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.rapla.RaplaMainContainer;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Category;
import org.rapla.entities.DependencyException;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.Ownable;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.PermissionContainer.Util;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityReferencer.ReferenceInfo;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.mail.MailPlugin;
import org.rapla.plugin.mail.server.MailInterface;
import org.rapla.rest.gwtjsonrpc.common.FutureResult;
import org.rapla.rest.gwtjsonrpc.common.ResultImpl;
import org.rapla.rest.gwtjsonrpc.common.VoidResult;
import org.rapla.server.AuthenticationStore;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.PreferencePatch;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.StorageUpdateListener;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.UpdateResult.Change;
import org.rapla.storage.UpdateResult.Remove;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.rapla.storage.dbrm.RemoteStorage;
import org.rapla.storage.impl.EntityStore;

/** Provides an adapter for each client-session to their shared storage operator
 * Handles security and synchronizing aspects.
 */
public class RemoteStorageImpl implements RemoteMethodFactory<RemoteStorage>, StorageUpdateListener, Disposable {
    CachableStorageOperator operator;
    
    protected SecurityManager security;
    
    RaplaContext context;
    
    int cleanupPointVersion = 0;
        
    protected AuthenticationStore authenticationStore;
    Logger logger;
    ClientFacade facade;
    RaplaLocale raplaLocale;
    
    
    public RemoteStorageImpl(RaplaContext context) throws RaplaException {
        this.context = context;
        this.logger = context.lookup( Logger.class);
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
        
        Long repositoryVersion = operator.getCurrentTimestamp().getTime();
		// Invalidate all clients
        for ( User user:operator.getUsers())
        {
        	String userId = user.getId();
			needResourceRefresh.put( userId, repositoryVersion);
        	needConflictRefresh.put( userId, repositoryVersion);
        }
    	
        synchronized (invalidateMap)
        {
        	invalidateMap.put( repositoryVersion, new TimeInterval( null, null));
        }
    }
    
    public Logger getLogger() {
        return logger;
    }

    
    public I18nBundle getI18n() throws RaplaException {
    	return context.lookup(RaplaComponent.RAPLA_RESOURCES);
    }

    static UpdateEvent createTransactionSafeUpdateEvent( UpdateResult updateResult )
    {
        User user = updateResult.getUser();
        UpdateEvent saveEvent = new UpdateEvent();
        if ( user != null )
        {
            saveEvent.setUserId( user.getId() );
        }
        {
            for (UpdateResult.Add add: updateResult.getOperations( UpdateResult.Add.class ))
            {
                Entity newEntity = (Entity) (  add ).getNew();
				saveEvent.putStore( newEntity );
            }
        }
        {
            for ( UpdateResult.Change change:updateResult.getOperations( UpdateResult.Change.class ) )
            {
                Entity newEntity = (Entity)  change.getNew();
				saveEvent.putStore( newEntity );
            }
        }
        {
            for (UpdateResult.Remove remove: updateResult.getOperations( UpdateResult.Remove.class ))
            {
                Entity removeEntity = (Entity) remove.getCurrent();
				saveEvent.putRemove( removeEntity );
            }
        }
        return saveEvent;
    }
    
    private Map<String,Long> needConflictRefresh = new ConcurrentHashMap<String,Long>();
    private Map<String,Long> needResourceRefresh = new ConcurrentHashMap<String,Long>();
    private SortedMap<Long, TimeInterval> invalidateMap = Collections.synchronizedSortedMap(new TreeMap<Long,TimeInterval>());
   
 // Implementation of StorageUpdateListener
    // 
    public void objectsUpdated( UpdateResult evt )
    {
    	long repositoryVersion = operator.getCurrentTimestamp().getTime();
        // notify the client for changes
        TimeInterval invalidateInterval = evt.calulateInvalidateInterval();
          
        if ( invalidateInterval != null)
        {
        	long oneHourAgo = repositoryVersion - DateTools.MILLISECONDS_PER_HOUR;
        	// clear the entries that are older than one hour and replace them with a clear_all
        	// that is set one hour in the past, to refresh all clients that have not been connected in the past hour on the next connect
        	synchronized ( invalidateMap) 
        	{
        		SortedMap<Long, TimeInterval> headMap = invalidateMap.headMap( oneHourAgo);
            	if ( !headMap.isEmpty())
            	{
            		Set<Long> toDelete  = new TreeSet<Long>(headMap.keySet());
            		for ( Long key:toDelete)
            		{
    	        		invalidateMap.remove(key);
            		}
            		invalidateMap.put(oneHourAgo, new TimeInterval( null, null));
            	}
            	invalidateMap.put(repositoryVersion, invalidateInterval);	
			}
        }
    	
        UpdateEvent safeResultEvent = createTransactionSafeUpdateEvent( evt );
        if ( getLogger().isDebugEnabled() )
            getLogger().debug( "Storage was modified. Calling notify." );
        boolean addAllUsersToConflictRefresh = false;
        for ( Iterator<Entity>it = safeResultEvent.getStoreObjects().iterator(); it.hasNext(); )
        {
            Entity obj = it.next();
            if (!isTransferedToClient(obj))
        	{
        		continue;
        	}
//            if (  obj instanceof Conflict)
//            {
//                addAllUsersToConflictRefresh = true;
//            }
            if (  obj instanceof DynamicType)
            {
                addAllUsersToConflictRefresh = true;
            }
//            RaplaType<?> raplaType = obj.getRaplaType();
//            if (raplaType == Conflict.TYPE)
//            {
//                String id = obj.getId();
//                updateMap.remove( id );
//                removeMap.remove( id );
//                updateMap.put( id, new Long( repositoryVersion ) );
//            }
        }
        
        // now we check if a the resources have changed in a way that a user needs to refresh all resources. That is the case, when 
        // someone changes the permissions on one or more resource and that affects  the visibility of that resource to a user, 
        // so its either pushed to the client or removed from it.
        Set<Permission> invalidatePermissions = new HashSet<Permission>();
        Set<Permission> invalidateEventPermissions =  new HashSet<Permission>(); 

        boolean addAllUsersToResourceRefresh = false;
        {
	        for ( Remove operation: evt.getOperations(UpdateResult.Remove.class) )
	        {
				Entity obj =  operation.getCurrent();
	        	if ( obj instanceof User)
	        	{
	        		String userId = obj.getId();
	        		needConflictRefresh.remove( userId);
	        		needResourceRefresh.remove( userId);
	        		//addAllUsersToResourceRefresh = true;
	        	}
	        	if (!isTransferedToClient(obj))
	        	{
	        		continue;
	        	}
	        	if (  obj instanceof DynamicType)
	        	{
	        		addAllUsersToResourceRefresh = true;
	        		addAllUsersToConflictRefresh = true;
	        	}
	        }
        }
        if (addAllUsersToResourceRefresh || addAllUsersToConflictRefresh)
        {
        	invalidateAll(repositoryVersion, addAllUsersToResourceRefresh,addAllUsersToConflictRefresh);
        }
        else
        {
        	invalidate(evt, repositoryVersion, invalidatePermissions,invalidateEventPermissions );
        }
    }

	private void invalidateAll(long repositoryVersion, boolean resourceRefreh, boolean conflictRefresh) {
		Collection<String> allUserIds = new ArrayList<String>();
        try
        {
        	Collection<User> allUsers = operator.getUsers();
        	for ( User user:allUsers)
        	{
        		String id = user.getId();
        		allUserIds.add( id);
        	}
        }
        catch (RaplaException ex)
        {
        	getLogger().error( ex.getMessage(), ex);
        	// we stay with the old list. 
        	// keySet iterator from concurrent hashmap is thread safe
        	Iterator<String> iterator = needResourceRefresh.keySet().iterator();
        	while ( iterator.hasNext())
        	{
        		String id = iterator.next();
				allUserIds.add( id);
        	}
        }
		for ( String userId :allUserIds)
		{
		    if (resourceRefreh )
		    {
		        needResourceRefresh.put( userId, repositoryVersion);
		    }
		    if ( conflictRefresh)
		    {
		        needConflictRefresh.put( userId, repositoryVersion);
		    }
		}
	}

	private void invalidate(UpdateResult evt, long repositoryVersion,	Set<Permission> invalidatePermissions, Set<Permission> invalidateEventPermissions) {
		Collection<User> allUsers;
		try {
			allUsers = operator.getUsers();
		} catch (RaplaException e) {
			// we need to invalidate all on an exception
        	invalidateAll(repositoryVersion, true, true);
        	return;
		}
		Set<User> usersResourceRefresh = new HashSet<User>();
		Set<User> usersConflictRefresh = new HashSet<User>();

		for (Change operation:evt.getOperations(UpdateResult.Change.class))
		{
			Entity newObject = operation.getNew();
			// we get all the permissions that have changed on an allocatable
			if ( newObject.getRaplaType().is( Allocatable.TYPE) && isTransferedToClient(newObject))
			{
			    PermissionContainer current = (PermissionContainer) operation.getOld();
                PermissionContainer newObj = (PermissionContainer) newObject;
				Util.addDifferences(invalidatePermissions, current, newObj);
			}
			// We trigger a resource refresh if the groups of the user have changed 
			if ( newObject.getRaplaType().is( User.TYPE))
			{
				User newUser = (User) newObject;
				User oldUser = (User) operation.getOld();
				HashSet<Category> newGroups = new HashSet<Category>(newUser.getGroupList());
				HashSet<Category> oldGroups = new HashSet<Category>(oldUser.getGroupList());
				if ( !newGroups.equals( oldGroups) || newUser.isAdmin() != oldUser.isAdmin())
				{
					usersResourceRefresh.add( newUser);
				}
				
			}
	        // We also check if a permission on a reservation has changed, so that it is no longer or new in the conflict list of a certain user.
	        // If that is the case we trigger an invalidate of the conflicts for a user
			if ( newObject instanceof Ownable)
			{
				Ownable newOwnable = (Ownable) newObject;
				Ownable oldOwnable = (Ownable) operation.getOld();
				User newOwner = newOwnable.getOwner();
				User oldOwner = oldOwnable.getOwner();
				if ( newOwner != null && oldOwner != null && (!newOwner.equals( oldOwner)) )
				{
				    if ( !newObject.getRaplaType().is( Reservation.TYPE))
				    {
				        usersResourceRefresh.add( newOwner);
				        usersResourceRefresh.add( oldOwner);
				    }
				    usersConflictRefresh.add( newOwner);
				    usersConflictRefresh.add( oldOwner);
				}
			}
			if ( newObject.getRaplaType().is( Reservation.TYPE) )
            {
                PermissionContainer current = (PermissionContainer) operation.getOld();
                PermissionContainer newObj = (PermissionContainer) newObject;
                Util.addDifferences(invalidateEventPermissions, current, newObj);
            }
		}
		if ( !invalidatePermissions.isEmpty() || !invalidateEventPermissions.isEmpty())
		{
		    Set<Category> groupsResourceRefresh = new HashSet<Category>();
            Set<Category> groupsConflictRefresh = new HashSet<Category>();
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
	        		groupsResourceRefresh.add( group);
	        	}
	        	if ( user == null && group == null)
	        	{
	        		usersResourceRefresh.addAll( allUsers);
	        		break;
	        	}
	        }
            for ( Permission permission:invalidateEventPermissions)
            {
                User user = permission.getUser();
                if ( user != null)
                {
                    usersConflictRefresh.add( user);
                }
                Category group = permission.getGroup();
                if ( group != null)
                {
                    groupsConflictRefresh.add( group);
                }
                if ( user == null && group == null)
                {
                    usersConflictRefresh.addAll( allUsers);
                    break;
                }
            }
	    	// we add all users belonging to group marked for refresh 
	        for ( User user:allUsers)
	        {
	        	if ( usersResourceRefresh.contains( user))
	        	{
	        		continue;
	        	}
	        	for (Category group:user.getGroupList())
	        	{
	        		if ( groupsResourceRefresh.contains( group))
	        		{
	        			usersResourceRefresh.add( user);
	        			break;
	        		}
	        		if ( groupsConflictRefresh.contains( group))
	        		{
	        			usersConflictRefresh.add( user);
	        		}
	        	}
	        }
		}
	  
		for ( User user:usersResourceRefresh)
		{
			String userId = user.getId(); 
			needResourceRefresh.put( userId, repositoryVersion);
			needConflictRefresh.put( userId, repositoryVersion);
		}
		for ( User user:usersConflictRefresh)
		{
			String userId = user.getId(); 
			needConflictRefresh.put( userId, repositoryVersion);
		}
	}

    private boolean isTransferedToClient(RaplaObject obj) 
    {
    	RaplaType<?> raplaType = obj.getRaplaType();
    	if (raplaType == Appointment.TYPE || raplaType == Reservation.TYPE)
    	{
    		return false;
    	}
	    if ( obj instanceof DynamicType)
	    {
	    	if (!DynamicTypeImpl.isTransferedToClient(( DynamicType) obj))
    		{
    			return false;
    		}
	    }
    	if ( obj instanceof Classifiable)
    	{
    		if (!DynamicTypeImpl.isTransferedToClient(( Classifiable) obj))
    		{
    			return false;
    		}
    	}
    	return true;
	
    }

	@Override
	public void dispose() {
	}
    
	public void updateError(RaplaException ex) {
    }

    public void storageDisconnected(String disconnectionMessage) {
    }

    public Class<RemoteStorage> getServiceClass() {
    	return RemoteStorage.class;
    }
    	
    @Override
    public RemoteStorage createService(final RemoteSession session) {
        return new RemoteStorage() {
            public FutureResult<UpdateEvent> getResources()
            {
            	try
            	{
	                checkAuthentified();
	                User user = getSessionUser();
	                getLogger().debug ("A RemoteServer wants to get all resource-objects.");
                    Date serverTime = operator.getCurrentTimestamp();
                    Collection<Entity> visibleEntities = operator.getVisibleEntities(user);
                    UpdateEvent evt = new UpdateEvent();
                    evt.setUserId( user.getId());
                    for ( Entity entity: visibleEntities)
                    {
                    	if ( isTransferedToClient(entity))
                    	{
                    		if ( entity instanceof Preferences)
                    		{
                    			Preferences preferences = (Preferences)entity;
								User owner = preferences.getOwner();
                    			if ( owner == null && !user.isAdmin())
                    			{
                    				entity = removeServerOnlyPreferences(preferences);
                    			}
                    		}
                    		evt.putStore(entity);
                    	}
                    }
					evt.setLastValidated(serverTime);
                    return new ResultImpl<UpdateEvent>( evt);
            	}
            	catch (RaplaException ex )
            	{
            		return new ResultImpl<UpdateEvent>(ex );
            	}
            }
            
        	private Preferences removeServerOnlyPreferences(Preferences preferences) 
            {
            	Preferences clone = preferences.clone();
            	{
            		//removeOldPluginConfigs(preferences, clone);
            		for (String role :((PreferencesImpl)preferences).getPreferenceEntries())
            		{
            		    if ( role.contains(".server."))
            		    {
            		        clone.removeEntry(role);
            		    }
            		}
            	}
                return clone;
			}

            public FutureResult<UpdateEvent> getEntityRecursive(String... ids) 
            {
                try
                {
                    checkAuthentified();
                    Date repositoryVersion = operator.getCurrentTimestamp();
                    User sessionUser = getSessionUser();
                    
                    ArrayList<Entity>completeList = new ArrayList<Entity>();
	                for ( String id:ids)
                	{
	                    Entity entity = operator.resolve(id);
	                    if ( entity instanceof Classifiable)
	                	{
	                		if (!DynamicTypeImpl.isTransferedToClient(( Classifiable) entity))
	                		{
	                			throw new RaplaSecurityException("Entity for id " + id + " is not transferable to the client");
	                		}
	                	}
	                    if ( entity instanceof DynamicType)
	                    {
	                    	if (!DynamicTypeImpl.isTransferedToClient(( DynamicType) entity))
	                		{
	                			throw new RaplaSecurityException("Entity for id " + id + " is not transferable to the client");
	                		}
	                    }
	                    if ( entity instanceof Reservation)
                    	{
                    		entity = checkAndMakeReservationsAnonymous(sessionUser,	entity);
                    	}
	                    if ( entity instanceof Preferences)
                        {
	                        entity = removeServerOnlyPreferences((Preferences)entity);
                        }
	                    security.checkRead(sessionUser, entity);
	                    completeList.add( entity );
	                    getLogger().debug("Get entity " + entity);
                	}
	                UpdateEvent evt = new UpdateEvent();
					evt.setLastValidated(repositoryVersion);
                    for ( Entity entity: completeList)
                    {
                    	evt.putStore(entity);
                    }
                    return new ResultImpl<UpdateEvent>( evt);
	        	}
	        	catch (RaplaException ex )
	        	{
	        		return new ResultImpl<UpdateEvent>(ex );
	        	}
            }
			
            public FutureResult<List<ReservationImpl>> getReservations(String[] allocatableIds,Date start,Date end,Map<String,String> annotationQuery) 
            {
            	getLogger().debug ("A RemoteServer wants to reservations from ." + start + " to " + end);
                try
                {
                	checkAuthentified();
                    User sessionUser = getSessionUser();
                    User user = null;
              	// Reservations and appointments
                    ArrayList<ReservationImpl> list = new ArrayList<ReservationImpl>();
                    List<Allocatable> allocatables = new ArrayList<Allocatable>();
                    if ( allocatableIds != null )
                    {
	                    for ( String id:allocatableIds)
	                    {
	                    	Allocatable allocatable = operator.resolve(id, Allocatable.class);
		                    security.checkRead(sessionUser, allocatable);
							allocatables.add( allocatable);
	                    }
                    }
					ClassificationFilter[] classificationFilters = null;
					Collection<Reservation> reservations = operator.getReservations(user,allocatables, start, end, classificationFilters,annotationQuery );
                    for (Reservation res:reservations)
                    {
                    	if (isAllocatablesVisible(sessionUser, res))
                		{
                        	ReservationImpl safeRes = checkAndMakeReservationsAnonymous(sessionUser,	 res);
							list.add( safeRes);
                		}
                    	
                    }
                    getLogger().debug("Get reservations " + start + " " + end + ": "  + reservations.size() + "," + list.size());
                    return new ResultImpl<List<ReservationImpl>>(list);
            	}
            	catch (RaplaException ex )
            	{
            		return new ResultImpl<List<ReservationImpl>>(ex );
            	}
            }
            
			private ReservationImpl checkAndMakeReservationsAnonymous(User sessionUser,Entity entity) {
				ReservationImpl reservation =(ReservationImpl) entity;
				boolean reservationVisible = RaplaComponent.canRead( reservation, sessionUser, operator);
				// check if the user is allowed to read the reservation info 
				if ( !reservationVisible )
				{
					ReservationImpl clone =  reservation.clone();
					// we can safely change the reservation info here because we cloned it in transaction safe before
					DynamicType anonymousReservationType = operator.getDynamicType( StorageOperator.ANONYMOUSEVENT_TYPE);
					clone.setClassification( anonymousReservationType.newClassification());
					clone.setReadOnly();
	            	return clone;
				}
				else
				{
					return reservation;
				}
			}
            
			
			protected boolean isAllocatablesVisible(User sessionUser, Reservation res) {
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

			public FutureResult<VoidResult> restartServer() {
            	try
            	{
	                checkAuthentified();
	                if (!getSessionUser().isAdmin())
	                    throw new RaplaSecurityException("Only admins can restart the server");
	
	                context.lookup(ShutdownService.class).shutdown( true);
	                return ResultImpl.VOID;
            	}
            	catch (RaplaException ex )
            	{
            		return new ResultImpl<VoidResult>(ex );
            	}
            }

			public FutureResult<UpdateEvent> dispatch(UpdateEvent event)
            {
            	try
            	{
            	    Date currentTimestamp = operator.getCurrentTimestamp();
            	    Date lastSynced = event.getLastValidated();
            	    if ( lastSynced == null)
                    {
                        throw new RaplaException("client sync time is missing");
                    }
            	    if ( lastSynced.after( currentTimestamp))
                    {
                        long diff  = lastSynced.getTime() - currentTimestamp.getTime();
                        getLogger().warn("Timestamp of client " +diff  +  " ms  after server ");
                        lastSynced = currentTimestamp;
                    }
	            	//   LocalCache cache = operator.getCache();
	             //   UpdateEvent event = createUpdateEvent( context,xml, cache );
	            	User sessionUser = getSessionUser();
					getLogger().info("Dispatching change for user " + sessionUser);
					if ( sessionUser != null)
					{
						event.setUserId(sessionUser.getId());
					}
	            	dispatch_( event);
	                getLogger().info("Change for user " + sessionUser + " dispatched.");
	               
					UpdateEvent result = createUpdateEvent( lastSynced );
					return new ResultImpl<UpdateEvent>(result );
	        	}
	        	catch (RaplaException ex )
	        	{
	        		return new ResultImpl<UpdateEvent>(ex );
	        	}
            }
            
			public FutureResult<String> canChangePassword() {
            	try
            	{
	            	checkAuthentified();
	                Boolean result =  operator.canChangePassword();
	                return new ResultImpl<String>( result.toString());
	        	}
	        	catch (RaplaException ex )
	        	{
	        		return new ResultImpl<String>(ex );
	        	}
            }

            public FutureResult<VoidResult> changePassword(String username
                                       ,String oldPassword
                                       ,String newPassword
                                       )
            {
            	try
            	{
	            	checkAuthentified();
	                User sessionUser = getSessionUser();
	                
	                if (!sessionUser.isAdmin()) {
	                    if ( authenticationStore != null ) {
	                        throw new RaplaSecurityException("Rapla can't change your password. Authentication handled by ldap plugin." );
	                    }
	                    operator.authenticate(username,new String(oldPassword));
	                }
	                User user = operator.getUser(username);
	                operator.changePassword(user,oldPassword.toCharArray(),newPassword.toCharArray());
	                return ResultImpl.VOID;
	        	}
	        	catch (RaplaException ex )
	        	{
	        		return new ResultImpl<VoidResult>(ex );
	        	}


            }
            
            public FutureResult<VoidResult> changeName(String username,String newTitle,
                    String newSurename, String newLastname) 
            {
            	try
            	{
	                User changingUser = getSessionUser();
	                User user = operator.getUser(username);
	                if ( changingUser.isAdmin() || user.equals( changingUser) )
	                {
	                    operator.changeName(user,newTitle,newSurename,newLastname);
	                }
	                else
	                {
	                    throw new RaplaSecurityException("Not allowed to change email from other users");
	                }
	                return ResultImpl.VOID;
	        	}
	        	catch (RaplaException ex )
	        	{
	        		return new ResultImpl<VoidResult>(ex );
	        	}
            }


            public FutureResult<VoidResult> changeEmail(String username,String newEmail)
                    
            {
            	try
            	{
	                User changingUser = getSessionUser();
	                User user = operator.getUser(username);
	                if ( changingUser.isAdmin() || user.equals( changingUser) )
	                {
	                    operator.changeEmail(user,newEmail);
	                }
	                else
	                {
	                    throw new RaplaSecurityException("Not allowed to change email from other users");
	                }
	                return ResultImpl.VOID;
	        	}
	        	catch (RaplaException ex )
	        	{
	        		return new ResultImpl<VoidResult>(ex );
	        	}
            }

            public FutureResult<VoidResult> confirmEmail(String username,String newEmail)
            {
            	try
            	{
	            	User changingUser = getSessionUser();
	                User user = operator.getUser(username);
	                if ( changingUser.isAdmin() || user.equals( changingUser) )
	                {
	                	String subject =  getString("security_code");
	    			    Preferences prefs = operator.getPreferences( null, true );
	    				String mailbody = "" + getString("send_code_mail_body_1") + user.getUsername() + ",\n\n" 
	    	        		+ getString("send_code_mail_body_2") + "\n\n" + getString("security_code") + Math.abs(user.getEmail().hashCode()) 
	    	        		+ "\n\n" + getString("send_code_mail_body_3") + "\n\n" + "-----------------------------------------------------------------------------------"
	    	        		+ "\n\n" + getString("send_code_mail_body_4") + prefs.getEntryAsString(RaplaMainContainer.TITLE, getString("rapla.title")) + " "
	    	        		+ getString("send_code_mail_body_5");
	    	
	    			
	    	    		final MailInterface mail = context.lookup(MailInterface.class);
	    	            final String defaultSender = prefs.getEntryAsString( MailPlugin.DEFAULT_SENDER_ENTRY, "");
	    	            
	    	            mail.sendMail( defaultSender, newEmail,subject, "" + mailbody);
	                }
	                else
	                {
	                    throw new RaplaSecurityException("Not allowed to change email from other users");
	                }
	                return ResultImpl.VOID;
	        	}
	        	catch (RaplaException ex )
	        	{
	        		return new ResultImpl<VoidResult>(ex );
	        	}
            }
            
            private String getString(String key) throws RaplaException {
				return getI18n().getString( key);
			}
            
            public FutureResult<List<String>> createIdentifier(String type, int count) {
				try
				{
					RaplaType raplaType = RaplaType.find( type);
					checkAuthentified();
		            //User user =
		            getSessionUser(); //check if authenified
		            String[] result =operator.createIdentifier(raplaType, count);
		            return new ResultImpl<List<String>>( Arrays.asList(result));
				}
				catch (RaplaException ex )
				{
					return new ResultImpl<List<String>>(ex );
				}
            }

            public FutureResult<UpdateEvent> refresh(String lastSyncedTime)
            {
            	try
            	{
	                checkAuthentified();
	                Date clientRepoVersion = SerializableDateTimeFormat.INSTANCE.parseTimestamp(lastSyncedTime);
	                UpdateEvent event = createUpdateEvent(clientRepoVersion);
	                return new ResultImpl<UpdateEvent>( event);
            	}
            	catch (RaplaException ex )
            	{
            		return new ResultImpl<UpdateEvent>(ex );
            	} 
            	catch (ParseDateException e) 
            	{
            		return new ResultImpl<UpdateEvent>(new RaplaException( e.getMessage()) );
				}
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
                        user = operator.resolve(evt.getUserId(), User.class);
                    }
                    else
                    {
                        user = session.getUser();
                    }
                    Collection<Entity>storeObjects = evt.getStoreObjects();
                    EntityStore store = new EntityStore(operator, operator.getSuperCategory());
            		store.addAll(storeObjects);
                    for (EntityReferencer references:evt.getEntityReferences()) 
                    {
                        references.setResolver( store);
                    }                    
                    for (Entity entity:storeObjects) 
                    {
                        security.checkWritePermissions(user,entity, false);
                    }
                    List<PreferencePatch> preferencePatches = evt.getPreferencePatches();
                    for (PreferencePatch patch:preferencePatches) 
                    {
                        security.checkWritePermissions(user,patch);
                    }
                    Collection<String>removeObjects = evt.getRemoveIds();
                    for ( String id:removeObjects)
                    {
                    	Entity entity = operator.tryResolve( id);
                    	if ( entity!=null)
                    	{
                    	    security.checkWritePermissions(user,entity, true);
                    	}
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
            
            private UpdateEvent createUpdateEvent( Date lastSynced ) throws RaplaException
            {
                Date currentTimestamp = operator.getCurrentTimestamp();
                if ( lastSynced.after( currentTimestamp))
                {
                    long diff  = lastSynced.getTime() - currentTimestamp.getTime();
                    getLogger().warn("Timestamp of client " +diff  +  " ms  after server ");
                    lastSynced = currentTimestamp;
                }
            	User user = getSessionUser();
                UpdateEvent safeResultEvent = new UpdateEvent();
                safeResultEvent.setLastValidated(  currentTimestamp);
                TimeZone systemTimeZone = operator.getTimeZone();
        		int timezoneOffset = TimeZoneConverterImpl.getOffset( DateTools.getTimeZone(), systemTimeZone, currentTimestamp.getTime());
                safeResultEvent.setTimezoneOffset( timezoneOffset );
                //if ( lastSynced.before( currentTimestamp ))
                {
                    String userId = user.getId();
                    TimeInterval invalidateInterval;
                    {
	                    Long lastVersion = needConflictRefresh.get( userId );
	                    if ( lastVersion != null && lastVersion > lastSynced.getTime())
	                    {
	                    	invalidateInterval = new TimeInterval( null, null);
	                    }
	                    else
	                    {
	                    	invalidateInterval = getInvalidateInterval( lastSynced.getTime() );
	                    }
                    }
                    boolean resourceRefresh;
                    {
                        Long lastVersion = needResourceRefresh.get( userId);
	                    resourceRefresh = ( lastVersion != null && lastVersion > lastSynced.getTime());
                    }
                    safeResultEvent.setNeedResourcesRefresh( resourceRefresh);
                    safeResultEvent.setInvalidateInterval( invalidateInterval);
                }
                if ( !safeResultEvent.isNeedResourcesRefresh())
                {
	            	Collection<Entity> updatedEntities = operator.getUpdatedEntities(user,lastSynced );
                    for ( Entity obj: updatedEntities )
                    {
                    	processClientReadable( user, safeResultEvent, obj, false);
                    }
                    Collection<ReferenceInfo> removedEntities = operator.getDeletedEntities(user,lastSynced );
                    for ( ReferenceInfo ref: removedEntities )
                    {
                        String id = ref.getId();
                        Class<? extends Entity> type = ref.getType();
                        if (type == Allocatable.class || type == Conflict.class || type == DynamicType.class || type == User.class)
                        {
                            safeResultEvent.putRemoveId(id);
                        }
                    }    
                }
                return safeResultEvent;
            }
            
			protected void processClientReadable(User user,UpdateEvent safeResultEvent, Entity obj, boolean remove) {
				if ( !isTransferedToClient(obj))
				{
					return;
				}
				boolean clientStore = true;
				if (user != null )
				{
                    // we don't transmit preferences for other users
				    if ( obj instanceof Preferences)
				    {
				        Preferences preferences = (Preferences) obj;
                        User owner = preferences.getOwner();
				        if  ( owner != null && !owner.equals( user))
				        {
				        	clientStore = false;
				        }
				        else 
				        {
				            obj = removeServerOnlyPreferences(preferences);
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
				    	if ( !RaplaComponent.canModify( conflict, user, operator) )
				    	{
				    		clientStore = false;
				    	}
				    	else
				    	{
				    	    operator.fillConflictDisableInformation( user,  conflict);
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
			
			private TimeInterval getInvalidateInterval( long clientRepositoryVersion) 
			{
				TimeInterval interval = null;
				synchronized (invalidateMap)
				{
					for ( TimeInterval current:invalidateMap.tailMap( clientRepositoryVersion).values())
					{
						if ( current != null)
						{
							interval = current.union( interval);
						}
					}
					return interval;
				}
			
			}
			
			public FutureResult<List<ConflictImpl>> getConflicts() 
			{
				try
				{
	            	Set<Entity>completeList = new HashSet<Entity> ();
	            	User sessionUser = getSessionUser();
					Collection<Conflict> conflicts = operator.getConflicts( sessionUser);
					List<ConflictImpl> result = new ArrayList<ConflictImpl>();
					for ( Conflict conflict:conflicts)
					{
						result.add( (ConflictImpl) conflict);
						Entity conflictRef = (Entity)conflict;
						completeList.add(conflictRef);
					}
				    return new ResultImpl<List<ConflictImpl>>( result);
	        	}
	        	catch (RaplaException ex )
	        	{
	        		return new ResultImpl<List<ConflictImpl>>(ex );
	        	}
            }
			@Override
			public FutureResult<Date> getNextAllocatableDate(	String[] allocatableIds, AppointmentImpl appointment,String[] reservationIds, Integer worktimestartMinutes, Integer worktimeendMinutes, Integer[] excludedDays, Integer rowsPerHour)  
			{
				try
				{
	                checkAuthentified();
	            	List<Allocatable> allocatables = resolveAllocatables(allocatableIds);
	            	Collection<Reservation> ignoreList = resolveReservations(reservationIds);
	            	Date result = operator.getNextAllocatableDate(allocatables, appointment, ignoreList,  worktimestartMinutes, worktimeendMinutes, excludedDays, rowsPerHour).get();
	                return new ResultImpl<Date>( result);
	        	}
	        	catch (Exception ex )
	        	{
	        		return new ResultImpl<Date>(ex );
	        	}
			}

            @Override
            public FutureResult<VoidResult> setConnectInfo(RemoteConnectionInfo info) {
                return ResultImpl.VOID;
            }

			
			@Override
			public FutureResult<BindingMap> getFirstAllocatableBindings(String[] allocatableIds, List<AppointmentImpl> appointments, String[] reservationIds)
			{
				try
				{
	                checkAuthentified();
	                //Integer[][] result = new Integer[allocatableIds.length][];
	        		List<Allocatable> allocatables = resolveAllocatables(allocatableIds);
	                Collection<Reservation> ignoreList = resolveReservations(reservationIds);
	                List<Appointment> asList = cast(appointments);
					Map<Allocatable, Collection<Appointment>> bindings = operator.getFirstAllocatableBindings(allocatables, asList, ignoreList).get();
					Map<String,List<String>> result = new LinkedHashMap<String,List<String>>();
	                for ( Allocatable alloc:bindings.keySet())
	                {
	                	Collection<Appointment> apps = bindings.get(alloc);
	                	if ( apps == null)
	                	{
	                		apps = Collections.emptyList();
	                	}
	                	ArrayList<String> indexArray = new ArrayList<String>(apps.size());
	                	for ( Appointment app: apps)
	                	{
	                    	for (Appointment app2:appointments)
	                    	{
	                    		if (app2.equals(app ))
	                    		{
	                    			indexArray.add ( app.getId());
	                    		}
	                    	}
	                	}
	                	result.put(alloc.getId(), indexArray);
	                }
	                return new ResultImpl<BindingMap>(new BindingMap(result));
	        	}
	        	catch (Exception ex )
	        	{
	        		return new ResultImpl<BindingMap>(ex);
	        	}
         	}
			
			private List<Appointment> cast(List<AppointmentImpl> appointments) {
				List<Appointment> result = new ArrayList<Appointment>(appointments.size());
				for (Appointment app:appointments)
				{
					result.add( app);
				}
				return result;
			}

			public FutureResult<List<ReservationImpl>> getAllAllocatableBindings(String[] allocatableIds, List<AppointmentImpl> appointments, String[] reservationIds) 
			{
				try
				{
					Set<ReservationImpl> result = new HashSet<ReservationImpl>(); 
	                checkAuthentified();
	        		List<Allocatable> allocatables = resolveAllocatables(allocatableIds);
	                Collection<Reservation> ignoreList = resolveReservations(reservationIds);
					List<Appointment> asList = cast(appointments);
					Map<Allocatable, Map<Appointment, Collection<Appointment>>> bindings = operator.getAllAllocatableBindings(allocatables, asList, ignoreList).get();
					for (Allocatable alloc:bindings.keySet())
					{
						Map<Appointment,Collection<Appointment>> appointmentBindings = bindings.get( alloc);
						for (Appointment app: appointmentBindings.keySet())
	    				{
							Collection<Appointment> bound = appointmentBindings.get( app);
							if ( bound != null)
							{
								for ( Appointment appointment: bound)
								{
									ReservationImpl reservation = (ReservationImpl) appointment.getReservation();
									if ( reservation != null)
									{
										result.add( reservation);
									}
		    					}
							}
	    				}
					}
	                return new ResultImpl<List<ReservationImpl>>(new ArrayList<ReservationImpl>(result));
				}
				catch (Exception ex)
				{
					return new ResultImpl<List<ReservationImpl>>(ex);
				}
			}
			
			private List<Allocatable> resolveAllocatables(String[] allocatableIds) throws RaplaException,EntityNotFoundException, RaplaSecurityException 
			{
				List<Allocatable> allocatables = new ArrayList<Allocatable>();
				User sessionUser = getSessionUser();
				for ( String id:allocatableIds)
				{
					Allocatable entity = operator.resolve(id, Allocatable.class);
					allocatables.add( entity);
					security.checkRead(sessionUser, entity);
				}
				return allocatables;
			}
			
			private Collection<Reservation> resolveReservations(String[] ignoreList) {
				Set<Reservation> ignoreConflictsWith = new HashSet<Reservation>();
				for (String reservationId: ignoreList)
				{
					try
					{
						Reservation entity = operator.resolve(reservationId, Reservation.class);
						ignoreConflictsWith.add( entity);
					}
					catch (EntityNotFoundException ex)
					{
						// Do nothing reservation not found and assumed new
					}
				}
				return ignoreConflictsWith;
			}
			
//			public void logEntityNotFound(String logMessage,String... referencedIds)
//			{
//				StringBuilder buf = new StringBuilder();
//				buf.append("{");
//				for  (String id: referencedIds)
//				{
//					buf.append("{ id=");
//					if ( id != null)
//					{
//						buf.append(id.toString());
//						buf.append(": ");
//						Entity refEntity = operator.tryResolve(id);
//						if ( refEntity != null )
//						{
//							buf.append( refEntity.toString());
//						}
//						else
//						{
//							buf.append("NOT FOUND");
//						}
//					}
//					else
//					{
//						buf.append( "is null");
//					}
//					
//					buf.append("},  ");
//				}
//				buf.append("}");
//				getLogger().error("EntityNotFoundFoundExceptionOnClient "+ logMessage + " " + buf.toString());
//				//return ResultImpl.VOID;
//			}
        };
    }

    static public void convertToNewPluginConfig(RaplaContext context, String className, TypedComponentRole<RaplaConfiguration> newConfKey) throws RaplaContextException {
            ClientFacade facade = context.lookup( ClientFacade.class);
            try
            {
                PreferencesImpl clone = (PreferencesImpl) facade.edit( facade.getSystemPreferences());  
                RaplaConfiguration entry = clone.getEntry(RaplaComponent.PLUGIN_CONFIG,null);
                if ( entry == null)
                {
                    return;
                }
                RaplaConfiguration newPluginConfigEntry = entry.clone();
                DefaultConfiguration pluginConfig = (DefaultConfiguration)newPluginConfigEntry.find("class", className);
                // we split the config entry in the plugin config and the new config entry;
                if ( pluginConfig != null)
                {
                    context.lookup(Logger.class).info("Converting plugin conf "  + className + " to preference entry " + newConfKey);
                    newPluginConfigEntry.removeChild( pluginConfig);
                    boolean enabled = pluginConfig.getAttributeAsBoolean("enabled", false);
                    RaplaConfiguration newPluginConfig = new RaplaConfiguration(pluginConfig.getName());
                    newPluginConfig.setAttribute("enabled", enabled);
                    newPluginConfig.setAttribute("class", className);
                    newPluginConfigEntry.addChild( newPluginConfig);
        
                    RaplaConfiguration newConfigEntry = new RaplaConfiguration( pluginConfig);
                    
                    newConfigEntry.setAttribute("enabled", null);
                    newConfigEntry.setAttribute("class", null);
                    
                    clone.putEntry(newConfKey, newConfigEntry);
                    clone.putEntry(RaplaComponent.PLUGIN_CONFIG, newPluginConfigEntry);
                    facade.store( clone);
                }
            }
            catch (RaplaException ex)
            {
                if ( ex instanceof RaplaContextException)
                {
                    throw (RaplaContextException)ex;
                }
                throw new RaplaContextException(ex.getMessage(),ex);
            }
        }

}

