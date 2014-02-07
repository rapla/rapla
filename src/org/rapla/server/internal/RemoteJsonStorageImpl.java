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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.rapla.components.util.Cancelable;
import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Category;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.storage.Mementable;
import org.rapla.entities.storage.RefEntity;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.UpdateModule;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.server.AuthenticationStore;
import org.rapla.server.RemoteJsonFactory;
import org.rapla.server.RemoteSession;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.UpdateResult.Change;
import org.rapla.storage.dbrm.EntityList;
import org.rapla.storage.dbrm.RemoteJsonStorage;

import com.google.gwtjsonrpc.common.AsyncCallback;

/** Provides an adapter for each client-session to their shared storage operator
 * Handles security and synchronizing aspects.
 */
public class RemoteJsonStorageImpl implements RemoteJsonFactory<RemoteJsonStorage>, Disposable {
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
    Cancelable scheduledCleanup;
    
    public RemoteJsonStorageImpl(RaplaContext context) throws RaplaException {
        this.context = context;
        this.logger = context.lookup( Logger.class);
        commandQueue = context.lookup( CommandScheduler.class);
        facade = context.lookup( ClientFacade.class);
        raplaLocale = context.lookup( RaplaLocale.class);
        operator = (CachableStorageOperator)facade.getOperator();
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
   
    ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
	protected Lock writeLock() throws RaplaException {
		return RaplaComponent.lock( lock.writeLock(), 60);
	}

	protected Lock readLock() throws RaplaException {
		return RaplaComponent.lock( lock.readLock(), 10);
	}
	
	protected void unlock(Lock lock) {
		RaplaComponent.unlock( lock );
	}
    
 // Implementation of StorageUpdateListener
    public void objectsUpdated( UpdateResult evt )
    {
    	Lock writeLock;
    	try
    	{
    		writeLock = writeLock();
    	}
    	catch (RaplaException ex)
    	{
    		getLogger().error(ex.getMessage(), ex);
    		return;
    	}
    	try
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
					Collection<Category> newGroup = RaplaComponent.getPermissionGroups( newEvent, superCategory, ReservationImpl.PERMISSION_MODIFY, false);
					Collection<Category> oldGroup = RaplaComponent.getPermissionGroups( oldEvent, superCategory, ReservationImpl.PERMISSION_MODIFY, false);
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
    	finally
    	{
    		unlock(writeLock);
    	}
    }

    @Override
	public void dispose() {
    	if ( scheduledCleanup != null)
    	{
    		scheduledCleanup.cancel();
    	}
	}
    
	/** regulary removes all old update messages that are older than the updateInterval ( factor 10) and at least 1 hour old */
    private final void initEventCleanup()
    {
    	Lock writeLock;
    	try
    	{
    		writeLock = writeLock();
    	}
        catch (RaplaException ex)
        {
        	getLogger().error("Can't cleanup due to ", ex);
        	return;
        }
 
    	Command cleanupTask = new Command()
        {
            public void execute()
            {
                initEventCleanup();
            }
        };
        try
        {
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
        }
        finally
        {
        	unlock( writeLock );
        }
        int delay = 10000;
        if ( operator.isConnected() )
        {
            try
            {
                delay = operator.getPreferences( null, true ).getEntryAsInteger( UpdateModule.REFRESH_INTERVAL_ENTRY,  delay );
            }
            catch ( RaplaException e )
            {
                getLogger().error( "Error during cleanup.", e );
            }
        }
        long scheduleDelay = Math.max( DateTools.MILLISECONDS_PER_HOUR, delay * 10 );
        //scheduleDelay = 30000;
        scheduledCleanup = commandQueue.schedule( cleanupTask,  scheduleDelay);
    }

    
    public void updateError(RaplaException ex) {
    }

    public void storageDisconnected(String disconnectionMessage) {
    }


    @Override
    public RemoteJsonStorage createService(final RemoteSession session) {
        return new RemoteJsonStorage() {


			@Override
			public void getUsers(String username, AsyncCallback<List<User>> callback) {
				try {
					Collection<User> users = operator.getObjects(User.class);
					ArrayList<User> result = new ArrayList<User>();
					for (User user: users)
					{
						if ( username == null || user.getUsername().equalsIgnoreCase(username))
						{
							result.add( user );
						}
					}
					callback.onSuccess(result);
				} catch (RaplaException e) {
					callback.onFailure( e );
				}
				
			}
		};
    }

}

