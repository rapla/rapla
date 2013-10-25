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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.rapla.ConnectInfo;
import org.rapla.components.util.Cancelable;
import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.iterator.FilterIterator;
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
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.ReferenceHandler;
import org.rapla.entities.storage.internal.SimpleIdentifier;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.UpdateModule;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.Configuration;
import org.rapla.framework.Container;
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
 * The messaging-client value contains the id of a
 * messaging-client-component which handles the
 * communication with the server.
 * The RemoteOperator provides also the Service {@link RemoteServiceCaller}
 *   @see org.rapla.components.rpc.MessagingClient
*/
public class RemoteOperator
    extends
        AbstractCachableOperator
    implements
    RemoteServiceCaller,RestartServer,Disposable, Provider<EntityStore>
{
    private boolean bSessionActive = false;
    Connector connector;
    private boolean isRestarting;
    RemoteMethodSerialization remoteMethodSerialization;
    RemoteMethodStub server;
	Comparable userId;
	
	protected CommandScheduler commandQueue;
		
    public RemoteOperator(RaplaContext context, Logger logger,Configuration config) throws RaplaException {
        super( context, logger );
        Container container = context.lookup( Container.class); 
        String stub = config.getChild("stub").getValue(null);
		if (stub != null)
        {
        	server =  container.lookup(RemoteMethodStub.class,stub);
    	}
        else
        {
        	connector = new HTTPConnector(context,config);
        	remoteMethodSerialization = new RemoteMethodSerialization(context,  this, cache);
        }
    	commandQueue = context.lookup( CommandScheduler.class);
    }
    
    public void connect(ConnectInfo connectInfo) throws RaplaException {
        if ( connectInfo == null)
        {
            throw new RaplaException("RemoteOperator doesn't support anonymous connect");
        }
        if (isConnected())
            return;
        getLogger().info("Connecting to server and starting login..");
        loginAndLoadData(connectInfo);
        initRefresh();
    }
    
	long lastSyncedTimeLocal;
	long lastSyncedTime;

   private void loginAndLoadData(ConnectInfo connectInfo) throws RaplaException {
		doConnect();
        String connectAs = connectInfo.getConnectAs();
        String password = new String( connectInfo.getPassword());
        String username = connectInfo.getUsername();
		try {
            String clientVersion= i18n.getString("rapla.version") ;
            RemoteServer serv1 = getRemoteMethod( RemoteServer.class);
            serv1.checkServerVersion( clientVersion);
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
// 		if (passedMillis >= DateTools.MILLISECONDS_PER_HOUR * 2	|| passedMillis <= 0) {
// 			updateToday();
// 			passedMillis = lastSyncedTimeLocal - System.currentTimeMillis();
// 		}
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
 				intervalLength = getPreferences(null).getEntryAsInteger(UpdateModule.REFRESH_INTERVAL_ENTRY, UpdateModule.REFRESH_INTERVAL_DEFAULT);
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

    /** implementation specific. Should be private */
    public void serverHangup() {
        getLogger().warn("Server hangup");
        final String message;
        if (!isRestarting) {
            message = getI18n().format("error.connection_closed",getConnectionName());
            getLogger().error(message);
        }
        else
        {
            message = getI18n().getString("restart_server");
        }
        isRestarting = false;
        new Thread() {
            public void run() {
                fireStorageDisconnected(message);
            }
        }.start();
    }

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
        return server == null;
    }

    public void refresh() throws RaplaException {
        String clientRepoVersion = String.valueOf(clientRepositoryVersion);
        RemoteStorage serv = getRemoteMethod( RemoteStorage.class);
    	
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
 
    public void restartServer() throws RaplaException {
    	getLogger().info("Restart in progress ...");
    	String message = i18n.getString("restart_server");
        isRestarting = true;
        RemoteStorage serv = getRemoteMethod( RemoteStorage.class);
        serv.restartServer();
        fireStorageDisconnected(message);
    }
   
    public void disconnect() throws RaplaException {
        disconnect("Disconnection from Server initiated");
    }
    
    /** disconnect from the server */
    public void disconnect(String message) throws RaplaException {
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
            RemoteServer serv1 = getRemoteMethod( RemoteServer.class);
            serv1.logout();
        	fireStorageDisconnected(message);
        }
    }

    private List<RefEntity<?>> addToCache(EntityList list, boolean useCache) throws RaplaException {
        List<RefEntity<?>> result = new ArrayList<RefEntity<?>>();
    	EntityResolver entityResolver = createEntityStore( list, useCache ? cache : null );
        synchronized (cache) {
        	resolveEntities( list.iterator(), entityResolver );
            for( Iterator<RefEntity<?>> it = list.iterator();it.hasNext();) {
                RefEntity<?> entity =  it.next();
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
        RemoteStorage serv = getRemoteMethod( RemoteStorage.class);
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
            Iterator<RefEntity<?>> it =closure.getStoreObjects().iterator();
            while (it.hasNext()) {
                RefEntity<?> entity = it.next();
                getLogger().debug("dispatching store for: " + entity);
            }
            it =closure.getRemoveObjects().iterator();
            while (it.hasNext()) {
                RefEntity<?> entity = it.next();
                getLogger().debug("dispatching remove for: " + entity);
            }
        }
        RemoteStorage serv = getRemoteMethod( RemoteStorage.class);
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
		for (RefEntity<?> object:evt.getStoreObjects())
		{
			addStoreOperationsToClosure(closure, object);
		}
		return closure;
	}

	protected void check(final UpdateEvent evt) throws RaplaException {
		Set<RefEntity<?>> storeObjects = new HashSet<RefEntity<?>>(evt.getStoreObjects());
		checkConsistency(storeObjects);
	}
    
	
	protected void addStoreOperationsToClosure(UpdateEvent evt, RefEntity<?> entity) throws RaplaException {
		if (getLogger().isDebugEnabled() && !evt.getStoreObjects().contains(entity)) {
			getLogger().debug("Adding " + entity + " to store closure");
		}
		evt.putStore(entity);
		
		Iterator<RefEntity<?>> it = entity.getSubEntities();
		while (it.hasNext()) {
			RefEntity<?> subEntity = it.next();
			addStoreOperationsToClosure(evt, subEntity);
		}

	}


    public Comparable[] createIdentifier(RaplaType raplaType, int count) throws RaplaException {
    	RemoteStorage serv = getRemoteMethod( RemoteStorage.class);
    	Comparable[] id = serv.createIdentifier(raplaType, count);
    	return id;
    }

    /** we must override this method because we can't store the passwords on the client*/
    public void authenticate(String username,String password) throws RaplaException {
    	RemoteStorage remoteMethod = getRemoteMethod(RemoteStorage.class);
    	remoteMethod.authenticate(username, password);
    }


    public long getServerTime() throws RaplaException {
    	RemoteStorage remoteMethod = getRemoteMethod(RemoteStorage.class);
        String serverTimeString = remoteMethod.getServerTime();
		Date serverTime;
		try {
			serverTime = raplaLocale.getSerializableFormat().parseTimestamp( serverTimeString);
			return serverTime.getTime();
		} catch (ParseException e) {
			throw new RaplaException(e.getMessage(),e);
		}
    }

    public boolean canChangePassword() throws RaplaException  {
        RemoteStorage remoteMethod = getRemoteMethod(RemoteStorage.class);
        boolean result = remoteMethod.canChangePassword();
        return result;
    }

    @Override
    public void changePassword(RefEntity<User> user,char[] oldPassword,char[] newPassword) throws RaplaException {
        try {
        	RemoteStorage remoteMethod = getRemoteMethod(RemoteStorage.class);
	        String username = user.cast().getUsername();
			remoteMethod.changePassword(username, new String(oldPassword),new String(newPassword));
		    refresh();
        } catch (RaplaSecurityException ex) {
            throw new RaplaSecurityException(i18n.getString("error.wrong_password"));
        }
    }
    
    @Override
    public void changeEmail(RefEntity<User> user, String newEmail) throws RaplaException 
    		
    {
    	RemoteStorage remoteMethod = getRemoteMethod(RemoteStorage.class);
        String username = user.cast().getUsername();
		remoteMethod.changeEmail(username,newEmail);
        refresh();
    }
    
    @Override
	public void confirmEmail(RefEntity<User> user, String newEmail)	throws RaplaException 
	{
    	RemoteStorage remoteMethod = getRemoteMethod(RemoteStorage.class);
        String username = user.cast().getUsername();
		remoteMethod.confirmEmail(username,newEmail);
	}

    
    @Override
    public void changeName(RefEntity<User> user, String newTitle, String newFirstname, String newSurname) throws RaplaException 
    {
    	RemoteStorage remoteMethod = getRemoteMethod(RemoteStorage.class);
        String username = user.cast().getUsername();
		remoteMethod.changeName(username,newTitle, newFirstname, newSurname);
        refresh();
    }
    
    public <T> Map<RefEntity<T>, T> getPersistant(Collection<RefEntity<T>> list) throws RaplaException 
	{
    	Map<RefEntity<T>,T> superResult = super.getPersistant(list);
     	Map<RefEntity<T>,T> result = new LinkedHashMap<RefEntity<T>, T>();
    	Map<SimpleIdentifier,RefEntity<T>> idMap = new LinkedHashMap<SimpleIdentifier, RefEntity<T>>();
        
     	for ( RefEntity<T> key: list)
    	{
			T resolved = superResult.get(key);
			if ( resolved != null)
			{
				result.put( key, resolved);
			}
			else
			{
				SimpleIdentifier id = (SimpleIdentifier) key.getId();
				idMap.put( id, key);
			}
    	}
    	RemoteStorage serv = getRemoteMethod( RemoteStorage.class);
		Set<SimpleIdentifier> keySet = idMap.keySet();
		try
		{
			SimpleIdentifier[] array = keySet.toArray(new SimpleIdentifier[] {});
			EntityList entityList = serv.getEntityRecursive( array);
	    	List<RefEntity<?>> resolvedList = addToCache(entityList, true );
	    	for (RefEntity<?> entity:resolvedList)
	    	{
	    		SimpleIdentifier id = (SimpleIdentifier) entity.getId();
				RefEntity<T> key = idMap.get( id);
				if ( key != null )
				{
					@SuppressWarnings("unchecked")
					T cast = (T) entity.cast();
					result.put( key, cast);
				}
	    	}
		} 
		catch (EntityNotFoundException ex)
		{
		}
    	return result;
	}
    
    public RefEntity<?> resolveId(Object id) throws EntityNotFoundException {
        try {
            return super.resolveId(id);
        } catch (EntityNotFoundException ex) {
            try {
            	SimpleIdentifier castedId = (SimpleIdentifier) id;
            	//String idS = castedId.getTypeName() + "_" + String.valueOf(castedId.getKey());
            	RemoteStorage serv = getRemoteMethod( RemoteStorage.class);
            	EntityList resolved = serv.getEntityRecursive( new SimpleIdentifier[] { castedId });
            	addToCache(resolved, true );
            	for ( RefEntity<?> entity: resolved)
            	{
            		if ( id.equals(entity.getId()))
            		{
            			return entity;
            		}
            	}
            } catch (RaplaException rex) {
            	throw new EntityNotFoundException("Object for id " + id.toString() + " not found due to " + ex.getMessage(), (Comparable)id);
            }
            return super.resolveId(id);
        }
    }
    
    public List<Reservation> getReservations(User user,Collection<Allocatable> allocatables,Date start,Date end) throws RaplaException {
        checkConnected();
    	RemoteStorage serv = getRemoteMethod( RemoteStorage.class);
    	SimpleIdentifier[] allocatableId = getIdList(allocatables);
		EntityList list =serv.getReservations(allocatableId,start, end);
        EntityResolver entityResolver = createEntityStore( list,  cache  );
        synchronized (cache) {
        	resolveEntities( list.iterator(), entityResolver );
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
    	Collection<RefEntity<?>> emptyList = Collections.emptyList();
		EntityStore store = createEntityStore(emptyList, cache);
		return store;
    }
    /**
	 * Entities will be resolved against resolveableEntities. If not found the
	 * ParentResolver will be used.
	 */
	private EntityStore createEntityStore(Collection<RefEntity<?>> resolveableEntities, LocalCache parent) {
		EntityStore resolver = new EntityStore(parent, cache.getSuperCategory())
		{
			@Override
			public RefEntity<?> get(Object id) {
				RefEntity<?> refEntity = super.get(id);
				if ( refEntity == null)
				{
					if ( id instanceof SimpleIdentifier)
					{
						SimpleIdentifier castedId = (SimpleIdentifier)id;
						String typeName = castedId.getTypeName();
						if ( typeName.equals(Allocatable.TYPE.toString()))
						{
							AllocatableImpl unresolved = new AllocatableImpl(null, null);
							unresolved.setId( castedId);
							unresolved.setClassification( cache.getUnresolvedAllocatableType().newClassification());
							return unresolved;
						}
						if ( typeName.equals(DynamicType.TYPE.toString()))
						{
							if ( ((SimpleIdentifier) id).getKey() == 0)
							{
								DynamicTypeImpl unresolvedReservation = cache.getAnonymousReservationType();
								return unresolvedReservation;
							}
						}
					}
				}
				return refEntity;
			}
			
			public DynamicType getDynamicType(String key) {
				DynamicType unresolvedReservation = cache.getAnonymousReservationType();
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

    
	protected SimpleIdentifier[] getIdList(Collection<? extends Entity> entities) {
		List<SimpleIdentifier> idList = new ArrayList<SimpleIdentifier>();
    	if ( entities != null )
    	{
    		for ( Entity entity:entities)
    		{
                if (entity != null)
    			    idList.add((SimpleIdentifier) ((RefEntity<?>)entity).getId());
    		}
    	}
    	SimpleIdentifier[] ids = idList.toArray(new SimpleIdentifier[] {});
		return ids;
	}
   
    long clientRepositoryVersion = 0;

    private void refresh(UpdateEvent evt) throws RaplaException
    {
    	if ( evt.isNeedResourcesRefresh())
    	{
    		refreshAll();
    		return;
    	}
        synchronized (getLock()) 
        {
            Collection<RefEntity<?>> storeObjects = evt.getStoreObjects();
            Collection<RefEntity<?>> removeObjects = evt.getRemoveObjects();
            Collection<RefEntity<?>> referenceObjects = evt.getReferenceObjects();

            Iterator<RefEntity<?>> it = storeObjects.iterator();
            while (it.hasNext()) {
                RefEntity<?> entity =  it.next();
                if ( isStorableInCache(entity))
                {
	                RefEntity<?> cachedVersion = cache.get(entity.getId());
	                // Ignore object if its not newer than the one in cache.
	                if (cachedVersion != null && cachedVersion.getVersion() >= entity.getVersion()) {
	                    //getLogger().debug("already on client " + entity + " version " + cachedVersion.getVersion());
	                    it.remove();
	                    continue;
	                }
                }
            }

            Collection<RefEntity<?>> allObject = evt.getAllObjects();
			RemoteOperator.super.resolveEntities
                (
                 storeObjects.iterator()
                 ,createEntityStore(allObject,cache)
                 );

			RemoteOperator.super.resolveEntities
            (
            		referenceObjects.iterator()
            		,createEntityStore(allObject,cache)
             );
			RemoteOperator.super.resolveEntities
                (
		    		// TODO We ignore references from deleted conflicts. Because they can cause weird problems e.g. when an appointment in a reservation container is deleted resulting in a conflict remove leading to the appointment now without a reservation not correctly transfered to the client side  
                	new FilterIterator<RefEntity<?>>(removeObjects.iterator()) {

					protected boolean isInIterator(Object obj) {
						return  !( obj instanceof Conflict);
					}
                 }
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
    }

	protected void refreshAll() throws RaplaException,
			EntityNotFoundException {
		Set<RefEntity<?>> oldEntities = new HashSet<RefEntity<?>>();
		{
			Iterator<RefEntity<?>> it = cache.getAllEntities();
			while ( it.hasNext())
			{
				oldEntities.add(it.next());
			}
		}
		loadData(null);
		Set<RefEntity<?>> newEntities = new HashSet<RefEntity<?>>();
		{
			Iterator<RefEntity<?>> it = cache.getAllEntities();
			while ( it.hasNext())
			{
				newEntities.add(it.next());
			}
		}
		HashSet<RefEntity<?>> updated = new HashSet<RefEntity<?>>(newEntities);
		Set<RefEntity<?>> toRemove = new HashSet<RefEntity<?>>(oldEntities);
		Set<RefEntity<?>> toUpdate = new HashSet<RefEntity<?>>(oldEntities);
		toRemove.removeAll(newEntities);
		updated.removeAll( toRemove);
		toUpdate.retainAll(newEntities);
		
		HashMap<RefEntity<?>, RefEntity<?>> oldEntityMap = new HashMap<RefEntity<?>, RefEntity<?>>();
		for ( RefEntity<?> update: toUpdate)
		{
			RefEntity<?> newEntity = cache.get( update.getId());
			if ( newEntity != null)
			{
				oldEntityMap.put( newEntity, update);
			}
		}
		TimeInterval invalidateInterval = new TimeInterval( null,null);
		UpdateResult result  = createUpdateResult(oldEntityMap, updated, toRemove, invalidateInterval, userId);
		fireStorageUpdated(result);
	}
    
    @Override
    protected void increaseVersion(RefEntity<?> e) {
    	// To nothing here versions are increased on the server
    }
    
    /**
	 * @param entity  
	 */
	protected boolean isAddedToUpdateResult(RefEntity<?> entity) {
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
	protected boolean isStorableInCache(RefEntity<?> entity) {
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


    public void serverDisconnected()  {
        bSessionActive = false;
    }

    StatusUpdater updater;
    public void setStatusUpdater(StatusUpdater statusUpdater) {
    	this.updater = statusUpdater;
    }

    @SuppressWarnings("unchecked")
    public <T> T getRemoteMethod(final Class<T> a) throws RaplaException 
    {
        if ( server != null)
        {
	    	return server.getWebserviceLocalStub(a);
        }
    	InvocationHandler proxy = new InvocationHandler() {
            
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable 
            {
                Object result = call(a, method, args);
                return result;
            }
            
            Object call( Class<?> service, Method method,Object[] args) throws RaplaException {
                try {
                	if ( updater != null)
                	{
                		updater.setStatus( Status.BUSY );
                	}
                    Object result =connector.call( service, method , args,remoteMethodSerialization);
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
        };
        ClassLoader classLoader = getClass().getClassLoader();
        Class<T>[] interfaces = new Class[] {a};
        Object proxyInstance = Proxy.newProxyInstance(classLoader, interfaces, proxy);
        return (T) proxyInstance;
    }

	@Override
	public Map<Allocatable, Collection<Appointment>> getFirstAllocatableBindings( Collection<Allocatable> allocatables,	Collection<Appointment> appointments, Collection<Reservation> ignoreList) throws RaplaException {
		checkConnected();
    	RemoteStorage serv = getRemoteMethod( RemoteStorage.class);
    	SimpleIdentifier[] allocatableIds = getIdList(allocatables);
    	SimpleIdentifier[] reservationIds = getIdList(ignoreList);
		Appointment[] appointmentArray = appointments.toArray( Appointment.EMPTY_ARRAY);
		Integer[][] bindings = serv.getFirstAllocatableBindings(allocatableIds, appointmentArray , reservationIds);
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
	public Map<Allocatable, Map<Appointment, Collection<Appointment>>> getAllAllocatableBindings( Collection<Allocatable> allocatables,	Collection<Appointment> appointments,Collection<Reservation> ignoreList) throws RaplaException {
		checkConnected();
    	RemoteStorage serv = getRemoteMethod( RemoteStorage.class);
    	SimpleIdentifier[] allocatableIds = getIdList(allocatables);
    	SimpleIdentifier[] reservationIds = getIdList(ignoreList);
    	Appointment[] appointmentArray = appointments.toArray( Appointment.EMPTY_ARRAY);
		EntityList list = serv.getAllAllocatableBindings(allocatableIds, appointmentArray, reservationIds);
	    EntityResolver entityResolver = createEntityStore( list,  cache  );
        synchronized (cache) {
        	resolveEntities( list.iterator(), entityResolver );
        }
        SortedSet<Appointment> allAppointments = new TreeSet<Appointment>(new AppointmentStartComparator());
        Iterator<RefEntity<?>> it = list.iterator();
        while ( it.hasNext())
        {
        	RefEntity<?> entity = it.next();
        	if ( entity.getRaplaType().equals( Appointment.TYPE))
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

	private SortedSet<Appointment> getAppointments(Allocatable alloc, SortedSet<Appointment> allAppointments) 
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
    	RemoteStorage serv = getRemoteMethod( RemoteStorage.class);
    	EntityList list =serv.getConflicts();
//    	for ( RefEntity<?> ref:list)
//    	{
//    		if (ref instanceof ConflictImpl)
//    		{
//    			logEntityNotFound((ConflictImpl)ref, new EntityNotFoundException("Test", ref.getId()));
//    		}
//    	}
    	EntityResolver entityResolver = createEntityStore( list,  cache  );
        synchronized (cache) {
        	resolveEntities( list.iterator(), entityResolver );
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
	protected void logEntityNotFound(RefEntity<?> obj, EntityNotFoundException ex) {
		RemoteStorage serv;
		try {
			serv = getRemoteMethod( RemoteStorage.class);
		} catch (RaplaException e) {
			getLogger().error(e.getMessage(), ex);
			return;
		}
		Comparable id = ex.getId();
		try
		{
			if ( obj instanceof ConflictImpl)
			{
				
				ReferenceHandler referenceHandler = ((ConflictImpl)obj).getReferenceHandler();
				SimpleIdentifier id0 = (SimpleIdentifier) referenceHandler.getId("allocatable");
				SimpleIdentifier id1 = (SimpleIdentifier) referenceHandler.getId("appointment1");
				SimpleIdentifier id2 = (SimpleIdentifier) referenceHandler.getId("appointment2");
				List<SimpleIdentifier> ids = new ArrayList<SimpleIdentifier>();
				ids.add( id0);
				ids.add( id1);
				ids.add( id2);
	//			List<Comparable> referencedIds = referenceHandler.getReferencedIds();
	//			for (Comparable refId:referencedIds)
	//			{
	//				ids.add( (SimpleIdentifier) refId);
	//			}
				SimpleIdentifier[] idArray = ids.toArray(new  SimpleIdentifier[0]);
				serv.logEntityNotFound( id  + " not found in conflict " + ids.toString(),idArray);
			}
			else if ( id != null && id instanceof SimpleIdentifier)
			{
				serv.logEntityNotFound("Not found", (SimpleIdentifier)id );
			}
		}
		catch (Exception e)
		{
			getLogger().error("Can't call server logging for " + ex.getMessage() + " due to " + e.getMessage(), e);
		}
	}

}

