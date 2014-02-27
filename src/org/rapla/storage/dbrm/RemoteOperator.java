/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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

package org.rapla.storage.dbrm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;

import org.rapla.ConnectInfo;
import org.rapla.components.util.Cancelable;
import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentStartComparator;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.RefEntity;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.UpdateModule;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.Configuration;
import org.rapla.framework.Disposable;
import org.rapla.framework.Provider;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.LocalCache;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.dbrm.StatusUpdater.Status;
import org.rapla.storage.impl.AbstractCachableOperator;
import org.rapla.storage.impl.EntityStore;

/** This operator can be used to modify and access data over the
 * network.  It needs an server-process providing the StorageService
 * (usually this is the default rapla-server).
 * <p>Sample configuration:
  <pre>
   &lt;remote-storage id="web">
   &lt;/remote-storate>
  </pre>
*/
public class RemoteOperator
    extends
        AbstractCachableOperator
    implements
    RestartServer,Disposable, Provider<EntityStore>, RemoteMethodCaller
{
    private boolean bSessionActive = false;
//    private boolean isRestarting;
    Connector connector;
    RemoteMethodSerialization remoteMethodSerialization;
    String userId;
	RemoteServer remoteServer;
	RemoteStorage remoteStorage;
	protected CommandScheduler commandQueue;
	
    public RemoteOperator(RaplaContext context, Logger logger,Configuration config, RemoteServer remoteServer, RemoteStorage remoteStorage) throws RaplaException {
        super( context, logger );
        this.remoteServer = remoteServer;
        this.remoteStorage = remoteStorage;
        ConnectorFactory connectorFatory = context.lookup(ConnectorFactory.class);
        connector = connectorFatory.create( config);
        remoteMethodSerialization = new RemoteMethodSerialization(context,  this);
    	commandQueue = context.lookup( CommandScheduler.class);
    }
    
    synchronized public void connect(ConnectInfo connectInfo) throws RaplaException {
        if ( connectInfo == null)
        {
            throw new RaplaException("RemoteOperator doesn't support anonymous connect");
        }
        if (isConnected())
            return;
        getLogger().info("Connecting to server and starting login..");
    	Lock writeLock = writeLock();
    	try
    	{
    		loginAndLoadData(connectInfo);
    		initRefresh();
    	}
    	finally
    	{
    		unlock(writeLock);
    	}
    }

	long lastSyncedTimeLocal;
	long lastSyncedTime;

   private void loginAndLoadData(ConnectInfo connectInfo) throws RaplaException {
		doConnect();
        String connectAs = connectInfo.getConnectAs();
        String password = new String( connectInfo.getPassword());
        String username = connectInfo.getUsername();
		try {
            RemoteServer serv1 = getRemoteServer();
            serv1.login(username,password, connectAs);
            bSessionActive = true;
    		// today should be the date of the server
    		lastSyncedTimeLocal = System.currentTimeMillis();
    		lastSyncedTime = getServerTime();
            getLogger().info("login successfull");
        } catch (RaplaException ex){
            disconnect();
            throw ex;
        }
        loadData(username);
	}
   
	public Date getCurrentTimestamp() {
		long passedMillis =  System.currentTimeMillis()- lastSyncedTimeLocal;
//		if (passedMillis >= DateTools.MILLISECONDS_PER_HOUR * 2	|| passedMillis <= 0) {
//			updateToday();
//			passedMillis = lastSyncedTimeLocal - System.currentTimeMillis();
//		}
		long correctTime = this.lastSyncedTime + passedMillis;
		Date date = new Date(correctTime);
		return date;
	}

   
   Cancelable timerTask;
   private final void initRefresh() 
	{
		Command refreshTask = new Command() {
			public void execute() {
			    try {
		            if (isConnected()) {
		                refresh();
		            }
			    } catch (RaplaConnectException e) {
                    getLogger().error("Error connecting " + e.getMessage());
			    } catch (RaplaException e) {
			        getLogger().error("Error refreshing.", e);
			    }
			}
		};
		int intervalLength = UpdateModule.REFRESH_INTERVAL_DEFAULT;
		if (isConnected()) {
			try {
				intervalLength = getPreferences(null, true).getEntryAsInteger(UpdateModule.REFRESH_INTERVAL_ENTRY, UpdateModule.REFRESH_INTERVAL_DEFAULT);
			} catch (RaplaException e) {
				getLogger().error("Error refreshing.", e);
			}
		}
		if ( timerTask != null)
		{
			timerTask.cancel();
		}
		timerTask = commandQueue.schedule(refreshTask, 0, intervalLength);
	}

	public void dispose() 
	{
		if ( timerTask != null)
		{
			timerTask.cancel();
		}
	}
	
    public void saveData(LocalCache cache) throws RaplaException {
        throw new RaplaException("RemoteOperator doesn't support storing complete cache, yet!");
    }

//    /** implementation specific. Should be private */
//    public void serverHangup() {
//        getLogger().warn("Server hangup");
//        final String message;
//        if (!isRestarting) {
//            message = getI18n().format("error.connection_closed",getConnectionName());
//            getLogger().error(message);
//        }
//        else
//        {
//            message = getI18n().getString("restart_server");
//        }
//        isRestarting = false;
//        commandQueue.schedule(new Command()
//        {
//
//			public void execute() throws Exception {
//                fireStorageDisconnected(message);
//			}
//        	
//        }
//        , 0);
//    }
    
//    public void serverDisconnected()  {
//        bSessionActive = false;
//    }


    public String getConnectionName() {
    	if ( connector != null)
    	{
    		return connector.getInfo();
    	}
    	else
    	{
    		return "standalone";
    	}
    }

    private void doConnect() throws RaplaException {
        boolean bFailed = true;
        try {
            bFailed = false;
        } catch (Exception e) {
            throw new RaplaException(i18n.format("error.connect",getConnectionName()),e);
        } finally {
            if (bFailed)
                disconnect();
        }
    }

    public boolean isConnected() {
        return bSessionActive;
    }

    public boolean supportsActiveMonitoring() {
        return true;
    }

    synchronized public void refresh() throws RaplaException {
        String clientRepoVersion = String.valueOf(clientRepositoryVersion);
        RemoteStorage serv = getRemoteStorage();
    	try
        {
	        UpdateEvent evt = serv.refresh( clientRepoVersion);
	        refresh( evt);
        }
        catch (EntityNotFoundException ex)
        {
        	getLogger().error("Refreshing all resources due to " + ex.getMessage(), ex);
        	refreshAll();
        }
    }
 
    synchronized public void restartServer() throws RaplaException {
    	getLogger().info("Restart in progress ...");
    	String message = i18n.getString("restart_server");
  //      isRestarting = true;
        RemoteStorage serv = getRemoteStorage();
        serv.restartServer();
        fireStorageDisconnected(message);
    }
   
    synchronized public void disconnect() throws RaplaException {
        disconnect("Disconnection from Server initiated");
    }
    
    /** disconnect from the server */
    synchronized public void disconnect(String message) throws RaplaException {
        boolean wasConnected = bSessionActive;
    	getLogger().info("Disconnecting from server");
        try {
            bSessionActive = false;
            cache.clearAll();
        } catch (Exception e) {
            throw new RaplaException("Could not disconnect", e);
        }
        if ( wasConnected)
        {
            RemoteServer serv1 = getRemoteServer();
            try 
            {
            	serv1.logout();
            }
            catch (RaplaConnectException ex)
            {
            	getLogger().warn( ex.getMessage());
            }
        	fireStorageDisconnected(message);
        }
    }

    private List<Entity>addToCache(EntityList list, boolean useCache) throws RaplaException {
        List<Entity>result = new ArrayList<Entity>();
    	EntityResolver entityResolver = createEntityStore( list, useCache ? cache : null );
        synchronized (cache) {
        	resolveEntities( list, entityResolver );
            for( Iterator<Entity>it = list.iterator();it.hasNext();) {
                Entity entity =  it.next();
				if ( isStorableInCache(entity))
				{
					cache.put(entity);
				}
				result.add( entity);
            }
        }
        return result;
    }

    private void loadData(String username) throws RaplaException {
    	checkConnected();
        cache.clearAll();
        getLogger().debug("Getting Data..");
        // recontextualize Entities
        RemoteStorage serv = getRemoteStorage();
        EntityList resources = serv.getResources();
        clientRepositoryVersion = resources.getRepositoryVersion();
        addToCache(resources, false );
        if ( username != null)
        {
        	UserImpl user = cache.getUser( username);
        	userId = user.getId();
        }
        getLogger().debug("Data flushed");
        
    }

    protected void checkConnected() throws RaplaException {
        if ( !bSessionActive ) {
            throw new RaplaException("Not logged in or connection closed!");
        }
    }
   
    public void dispatch(UpdateEvent evt) throws RaplaException {
        checkConnected();
        // Create closure
        UpdateEvent closure = createClosure(evt );
        check( closure );
        // Store on server
        if (getLogger().isDebugEnabled()) {
            Iterator<Entity>it =closure.getStoreObjects().iterator();
            while (it.hasNext()) {
                Entity entity = it.next();
                getLogger().debug("dispatching store for: " + entity);
            }
            it =closure.getRemoveObjects().iterator();
            while (it.hasNext()) {
                Entity entity = it.next();
                getLogger().debug("dispatching remove for: " + entity);
            }
        }
        RemoteStorage serv = getRemoteStorage();
        closure.setRepositoryVersion( clientRepositoryVersion);
        UpdateEvent serverClosure =serv.dispatch( closure );
        refresh(serverClosure);
    }
    
	/**
	 * Create a closure for all objects that should be updated. The closure
	 * contains all objects that are sub-entities of the entities and all
	 * objects and all other objects that are affected by the update: e.g.
	 * Classifiables when the DynamicType changes. The method will recursivly
	 * proceed with all discovered objects.
	 */
	protected UpdateEvent createClosure(final UpdateEvent evt) throws RaplaException {
		UpdateEvent closure = evt.clone();
		for (Entity object:evt.getStoreObjects())
		{
			addStoreOperationsToClosure(closure, object);
		}
		return closure;
	}

	protected void check(final UpdateEvent evt) throws RaplaException {
		Set<Entity> storeObjects = new HashSet<Entity>(evt.getStoreObjects());
		checkConsistency(storeObjects);
	}
    
	
	protected void addStoreOperationsToClosure(UpdateEvent evt, Entity entity) throws RaplaException {
		if (getLogger().isDebugEnabled() && !evt.getStoreObjects().contains(entity)) {
			getLogger().debug("Adding " + entity + " to store closure");
		}
		evt.putStore(entity);
//		Iterable<Entity>subEntities = entity.getSubEntities();
//		for (Entity subEntity:subEntities)
//		{
//			addStoreOperationsToClosure(evt, subEntity);
//		}

	}


    public String[] createIdentifier(RaplaType raplaType, int count) throws RaplaException {
    	RemoteStorage serv = getRemoteStorage();
    	String[] id = serv.createIdentifier(raplaType, count);
    	return id;
    }

    /** we must override this method because we can't store the passwords on the client*/
    public void authenticate(String username,String password) throws RaplaException {
    	RemoteStorage remoteMethod = getRemoteStorage();
    	remoteMethod.authenticate(username, password);
    }


    public long getServerTime() throws RaplaException {
    	RemoteStorage remoteMethod = getRemoteStorage();
        String serverTimeString = remoteMethod.getServerTime();
		Date serverTime;
		try {
			serverTime = raplaLocale.getSerializableFormat().parseTimestamp( serverTimeString);
			return serverTime.getTime();
		} catch (ParseDateException e) {
			throw new RaplaException(e.getMessage(),e);
		}
    }

	private RemoteStorage getRemoteStorage() {
		return remoteStorage;
	}
	
	private RemoteServer getRemoteServer()  {
		return remoteServer;
	}


    public boolean canChangePassword() throws RaplaException  {
        RemoteStorage remoteMethod = getRemoteStorage();
        boolean result = remoteMethod.canChangePassword();
        return result;
    }

    @Override
    public void changePassword(User user,char[] oldPassword,char[] newPassword) throws RaplaException {
        try {
        	RemoteStorage remoteMethod = getRemoteStorage();
	        String username = user.getUsername();
			remoteMethod.changePassword(username, new String(oldPassword),new String(newPassword));
		    refresh();
        } catch (RaplaSecurityException ex) {
            throw new RaplaSecurityException(i18n.getString("error.wrong_password"));
        }
    }
    
    @Override
    public void changeEmail(User user, String newEmail) throws RaplaException 
    		
    {
    	RemoteStorage remoteMethod = getRemoteStorage();
        String username = user.getUsername();
		remoteMethod.changeEmail(username,newEmail);
        refresh();
    }
    
    @Override
	public void confirmEmail(User user, String newEmail)	throws RaplaException 
	{
    	RemoteStorage remoteMethod = getRemoteStorage();
        String username = user.getUsername();
		remoteMethod.confirmEmail(username,newEmail);
	}

    
    @Override
    public void changeName(User user, String newTitle, String newFirstname, String newSurname) throws RaplaException 
    {
    	RemoteStorage remoteMethod = getRemoteStorage();
        String username = user.getUsername();
		remoteMethod.changeName(username,newTitle, newFirstname, newSurname);
        refresh();
    }
    
    public Map<Entity,Entity> getPersistant(Collection<? extends Entity> list) throws RaplaException 
	{
    	Map<Entity,Entity> superResult = super.getPersistant(list);
     	Map<Entity,Entity> result = new LinkedHashMap<Entity,Entity>();
    	Map<String,Entity> idMap = new LinkedHashMap<String,Entity>();
        
     	for ( Entity key: list)
    	{
			Entity resolved = superResult.get(key);
			if ( resolved != null)
			{
				result.put( key, resolved);
			}
			else
			{
				String id =  key.getId().toString();
				idMap.put( id, key);
			}
    	}
    	RemoteStorage serv = getRemoteStorage();
		Set<String> keySet = idMap.keySet();
		try
		{
			String[] array = keySet.toArray(new String[] {});
			EntityList entityList = serv.getEntityRecursive( array);
	    	List<Entity>resolvedList = addToCache(entityList, true );
	    	for (Entity entity:resolvedList)
	    	{
	    		String id = entity.getId().toString();
				Entity key = idMap.get( id);
				if ( key != null )
				{
					result.put( key, entity);
				}
	    	}
		} 
		catch (EntityNotFoundException ex)
		{
		}
    	return result;
	}
    
    public Entity resolve(String id) throws EntityNotFoundException {
        try {
            return super.resolve(id);
        } catch (EntityNotFoundException ex) {
            try {
            	String castedId = id.toString();
            	//String idS = castedId.getTypeName() + "_" + String.valueOf(castedId.getKey());
            	RemoteStorage serv = getRemoteStorage();
            	EntityList resolved = serv.getEntityRecursive( new String[] { castedId });
            	addToCache(resolved, true );
            	for ( Entity entity: resolved)
            	{
            		if ( id.equals(entity.getId()))
            		{
            			return entity;
            		}
            	}
            } catch (RaplaException rex) {
            	throw new EntityNotFoundException("Object for id " + id.toString() + " not found due to " + ex.getMessage());
            }
            return super.resolve(id);
        }
    }
    
    public List<Reservation> getReservations(User user,Collection<Allocatable> allocatables,Date start,Date end) throws RaplaException {
        checkConnected();
    	RemoteStorage serv = getRemoteStorage();
    	String[] allocatableId = getIdList(allocatables);
		EntityList list =serv.getReservations(allocatableId,start, end);
        EntityResolver entityResolver = createEntityStore( list,  cache  );
        synchronized (cache) {
        	resolveEntities( list, entityResolver );
        }
        List<Reservation> result = new ArrayList<Reservation>();
        Iterator it = list.iterator();
        while ( it.hasNext())
        {
        	Object object = it.next();
        	if ( object instanceof Reservation)
        	{
        		Reservation next = (Reservation)object;
        		result.add( next);
        	}
        }
        return result;
    }

    public EntityStore get()
    {
    	Collection<Entity>emptyList = Collections.emptyList();
		EntityStore store = createEntityStore(emptyList, cache);
		return store;
    }
    /**
	 * Entities will be resolved against resolveableEntities. If not found the
	 * ParentResolver will be used.
	 */
	private EntityStore createEntityStore(Collection<Entity>resolveableEntities, LocalCache parentCache) {
		EntityStore resolver = new EntityStore(parentCache, cache.getSuperCategory())
		{
			@Override
			public Entity resolve(String id) {
				Entity refEntity = super.tryResolve(id);
				if ( refEntity == null)
				{
					{
						if ( id.startsWith(Allocatable.TYPE.getLocalName()))
						{
							AllocatableImpl unresolved = new AllocatableImpl(null, null);
							unresolved.setId( id);
							unresolved.setClassification( getUnresolvedAllocatableType().newClassification());
							return unresolved;
						}
						// if the type is not found we test if its an anonymous type (key = 0)
						if ( id.startsWith(DynamicType.TYPE.getLocalName() + "_0"))
						{
							DynamicType unresolvedReservation = getAnonymousReservationType();
							return (Entity) unresolvedReservation;
						}
					}
				}
				return refEntity;
			}
			
			public DynamicType getDynamicType(String key) {
				DynamicType unresolvedReservation = getAnonymousReservationType();
				if ( key.equals(unresolvedReservation.getElementKey()))
				{
					return unresolvedReservation;
				}
				return super.getDynamicType(key);
			}
		};
		resolver.addAll(resolveableEntities);
		return resolver;
	}

    
	protected String[] getIdList(Collection<? extends Entity> entities) {
		List<String> idList = new ArrayList<String>();
    	if ( entities != null )
    	{
    		for ( Entity entity:entities)
    		{
                if (entity != null)
    			    idList.add( ((Entity)entity).getId().toString());
    		}
    	}
    	String[] ids = idList.toArray(new String[] {});
		return ids;
	}
   
    long clientRepositoryVersion = 0;

    synchronized private void refresh(UpdateEvent evt) throws RaplaException
    {
    	if ( evt.isNeedResourcesRefresh())
    	{
    		refreshAll();
    		return;
    	}
		Lock writeLock = writeLock();
		try
        {
            Collection<Entity> storeObjects = evt.getStoreObjects();
            Collection<Entity> removeObjects = evt.getRemoveObjects();
            Collection<Entity> referenceObjects = evt.getReferenceObjects();

            for (Iterator<Entity>it = storeObjects.iterator();it.hasNext();)
            {
                Entity entity =  it.next();
                if ( isStorableInCache(entity))
                {
	                RefEntity cachedVersion = (RefEntity) cache.tryResolve(entity.getId());
	                // Ignore object if its not newer than the one in cache.
	                if (cachedVersion != null && cachedVersion.getVersion() >= ((RefEntity)entity).getVersion()) {
	                    //getLogger().debug("already on client " + entity + " version " + cachedVersion.getVersion());
	                    it.remove();
	                    continue;
	                }
                }
            }
            
    	

            Collection<Entity>allObject = evt.getAllObjects();
			RemoteOperator.super.resolveEntities
                (
                 storeObjects
                 ,createEntityStore(allObject,cache)
                 );

			RemoteOperator.super.resolveEntities
            (
            		referenceObjects
            		,createEntityStore(allObject,cache)
             );
			// TODO We ignore references from deleted conflicts. Because they can cause weird problems e.g. when an appointment in a reservation container is deleted resulting in a conflict remove leading to the appointment now without a reservation not correctly transfered to the client side  
            List<Entity> removedObjectsWithoutConflicts = new LinkedList<Entity>(removeObjects);
            for ( Iterator<Entity> it = removedObjectsWithoutConflicts.iterator();it.hasNext();)
            {
            	Entity obj = it.next();
            	if ( obj instanceof Conflict) 
            	{
            		it.remove();
            	}
            }
			RemoteOperator.super.resolveEntities
                (
                	removedObjectsWithoutConflicts
                	,createEntityStore(allObject,cache)
                 );

            clientRepositoryVersion = evt.getRepositoryVersion();
            TimeInterval invalidateInterval = evt.getInvalidateInterval();
			if ( bSessionActive  &&
                  ( removeObjects.size() > 0
                 || storeObjects.size() > 0  || invalidateInterval != null)  ) {
                getLogger().debug("Objects updated!");
                UpdateResult result = update(evt);
                // now we can set the cache as updated
                fireStorageUpdated(result);
            }
        }
		finally
		{
			unlock(writeLock);
		}
    }

	protected void refreshAll() throws RaplaException,
			EntityNotFoundException {
		UpdateResult result;
		Lock writeLock = writeLock();
		try
		{
			Set<Entity> oldEntities = cache.getAllEntities();
			loadData(null);
			Set<Entity> newEntities = cache.getAllEntities();
			HashSet<Entity> updated = new HashSet<Entity>(newEntities);
			Set<Entity> toRemove = new HashSet<Entity>(oldEntities);
			Set<Entity> toUpdate = new HashSet<Entity>(oldEntities);
			toRemove.removeAll(newEntities);
			updated.removeAll( toRemove);
			toUpdate.retainAll(newEntities);
			
			HashMap<Entity,Entity> oldEntityMap = new HashMap<Entity,Entity>();
			for ( Entity update: toUpdate)
			{
				Entity newEntity = cache.tryResolve( update.getId());
				if ( newEntity != null)
				{
					oldEntityMap.put( newEntity, update);
				}
			}
			TimeInterval invalidateInterval = new TimeInterval( null,null);
			result  = createUpdateResult(oldEntityMap, updated, toRemove, invalidateInterval, userId);
		}
		finally
		{
			unlock(writeLock);
		}
		fireStorageUpdated(result);
	}
    
    @Override
    protected void increaseVersion(Entity e) {
    	// To nothing here versions are increased on the server
    }
    
    /**
	 * @param entity  
	 */
	protected boolean isAddedToUpdateResult(Entity entity) {
		RaplaType raplaType = entity.getRaplaType();
		if ((raplaType ==  Appointment.TYPE  || raplaType == Reservation.TYPE) && !RaplaComponent.isTemplate(entity))
		{
			return false;
		}
		return true;
	}

	/**
	 * @param entity  
	 */
	protected boolean isStorableInCache(Entity entity) {
		RaplaType raplaType = entity.getRaplaType();
		if  (raplaType == Conflict.TYPE)
		{
			return false;
		}
		else if ((raplaType ==  Appointment.TYPE  || raplaType == Reservation.TYPE) && !RaplaComponent.isTemplate(entity))
		{
			return false;
		}
		return true;
	}

    StatusUpdater updater;
    public void setStatusUpdater(StatusUpdater statusUpdater) {
    	this.updater = statusUpdater;
    }

    public Object call( Class<?> service, String methodName,Class<?>[] parameterTypes, Class<?> returnType ,Object[] args) throws RaplaException {
        try {
        	if ( updater != null)
        	{
        		updater.setStatus( Status.BUSY );
        	}
            Object result =connector.call( service, methodName, parameterTypes, returnType , args,remoteMethodSerialization);
            return result;
        } catch (IOException ex) {
            throw new RaplaException(ex);
        }  catch ( RaplaException ex) {
            if ( ex.getMessage() != null && ex.getMessage().equals( RemoteStorage.USER_WAS_NOT_AUTHENTIFIED))
            {
                getLogger().warn(ex.getMessage() + ". Disconnecting from server.");
                String message = getI18n().format("error.connection_closed", getConnectionName());
                disconnect(message);
                throw new RaplaRestartingException();
            }
            else
            {
                throw ex;
            }
        }
        finally
        {
        	if ( updater != null)
        	{
        		updater.setStatus( Status.READY );
        	}
        }
    }

	@Override
	public Map<Allocatable, Collection<Appointment>> getFirstAllocatableBindings( Collection<Allocatable> allocatables,	Collection<Appointment> appointments, Collection<Reservation> ignoreList) throws RaplaException {
		checkConnected();
    	RemoteStorage serv = getRemoteStorage();
    	String[] allocatableIds = getIdList(allocatables);
		Appointment[] appointmentArray = appointments.toArray( Appointment.EMPTY_ARRAY);
		String[] reservationIds = getIdList(ignoreList);
		Integer[][] bindings = serv.getFirstAllocatableBindings(allocatableIds, appointmentArray, reservationIds);
		HashMap<Allocatable, Collection<Appointment>> result = new HashMap<Allocatable, Collection<Appointment>>();
		int allocNumber = 0;
		for ( Allocatable alloc:allocatables)
		{
			Integer[] bindingsAlloc = bindings[allocNumber++];
			Collection<Appointment> appointmentBinding = new ArrayList<Appointment>();
			for ( Integer binding:bindingsAlloc)
			{
				appointmentBinding.add( appointmentArray[binding]);
			}
			result.put( alloc, appointmentBinding);
		}
		return result;
	}

	@Override
	public Map<Allocatable, Map<Appointment, Collection<Appointment>>> getAllAllocatableBindings( Collection<Allocatable> allocatables,	Collection<Appointment> appointments, Collection<Reservation> ignoreList) throws RaplaException {
		checkConnected();
    	RemoteStorage serv = getRemoteStorage();
    	String[] allocatableIds = getIdList(allocatables);
		Appointment[] appointmentArray = appointments.toArray( Appointment.EMPTY_ARRAY);
		String[] reservationIds = getIdList(ignoreList);
		EntityList list = serv.getAllAllocatableBindings(allocatableIds, appointmentArray, reservationIds);
	    EntityResolver entityResolver = createEntityStore( list,  cache  );
	    Lock readLock = readLock();
	    try
	    {
        	resolveEntities( list, entityResolver );
        }
	    finally
	    {
	    	unlock( readLock );
	    }
        SortedSet<Appointment> allAppointments = new TreeSet<Appointment>(new AppointmentStartComparator());
        Iterator<Entity>it = list.iterator();
        while ( it.hasNext())
        {
        	Entity entity = it.next();
        	if ( entity.getRaplaType() == Appointment.TYPE)
        	{
        		allAppointments.add( (Appointment) entity);
        	}
        }
		Map<Allocatable, Map<Appointment,Collection<Appointment>>> result = new HashMap<Allocatable, Map<Appointment,Collection<Appointment>>>();
		for ( Allocatable alloc:allocatables)
		{
			Map<Appointment,Collection<Appointment>> appointmentBinding = new HashMap<Appointment, Collection<Appointment>>();
			for (Appointment appointment: appointments)
			{
				SortedSet<Appointment> appointmentSet = getAppointments(alloc, allAppointments );
				boolean onlyFirstConflictingAppointment = false;
				Set<Appointment> conflictingAppointments = AppointmentImpl.getConflictingAppointments(appointmentSet, appointment, ignoreList, onlyFirstConflictingAppointment);
				appointmentBinding.put( appointment, conflictingAppointments);
			}
			result.put( alloc, appointmentBinding);
		}
		return result;
	}
	
	@Override
	public Date getNextAllocatableDate(Collection<Allocatable> allocatables,Appointment appointment, Collection<Reservation> ignoreList, Integer worktimeStartMinutes,Integer worktimeEndMinutes, Integer[] excludedDays, Integer rowsPerHour) throws RaplaException
	{
		checkConnected();
    	RemoteStorage serv = getRemoteStorage();
    	String[] allocatableIds = getIdList(allocatables);
		String[] reservationIds = getIdList(ignoreList);
		Date result = serv.getNextAllocatableDate(allocatableIds, appointment, reservationIds, worktimeStartMinutes, worktimeEndMinutes, excludedDays, rowsPerHour);
		return result;
	}


	static private SortedSet<Appointment> getAppointments(Allocatable alloc, SortedSet<Appointment> allAppointments) 
	{
		SortedSet<Appointment> result = new TreeSet<Appointment>(new AppointmentStartComparator());
        for ( Appointment appointment:allAppointments)
        {
        	Reservation reservation = appointment.getReservation();
        	if ( reservation.hasAllocated( alloc, appointment))
        	{
        		result.add( appointment);
        	}
        }
		return result;
	}

	@Override
	public Collection<Conflict> getConflicts(User user) throws RaplaException {
        checkConnected();
    	RemoteStorage serv = getRemoteStorage();
    	EntityList list =serv.getConflicts();
        EntityResolver entityResolver = createEntityStore( list,  cache  );
        Lock readLock = readLock();
	    try
	    {
        	resolveEntities( list, entityResolver );
        }
	    finally
	    {
	    	unlock( readLock );
	    }
        List<Conflict> result = new ArrayList<Conflict>();
        Iterator it = list.iterator();
        while ( it.hasNext())
        {
        	Object object = it.next();
        	if ( object instanceof Conflict)
        	{
        		Conflict next = (Conflict)object;
        		result.add( next);
        	}
        }
        return result;
	}

	@Override
	protected void logEntityNotFound(Entity obj, EntityNotFoundException ex) {
		RemoteStorage serv = getRemoteStorage();
		Comparable id = ex.getId();
		try {
			if ( obj instanceof ConflictImpl)
			{
				Collection<String> referencedIds = ((ConflictImpl)obj).getReferenceHandler().getReferencedIds();
				List<String> ids = new ArrayList<String>();
				for (String refId:referencedIds)
				{
					ids.add(  refId);
				}
				serv.logEntityNotFound( id  + " not found in conflict :",ids.toArray(new  String[0]));
			}
			else if ( id != null )
			{
				serv.logEntityNotFound("Not found", id.toString() );
			}
		} catch (Exception e) {
			getLogger().error("Can't call server logging for " + ex.getMessage() + " due to " + e.getMessage(), e);
		}
	}
}

