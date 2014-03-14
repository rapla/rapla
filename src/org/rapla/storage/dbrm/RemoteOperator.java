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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;

import org.rapla.ConnectInfo;
import org.rapla.components.util.Assert;
import org.rapla.components.util.Cancelable;
import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
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
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.internal.UserImpl;
import org.rapla.facade.Conflict;
import org.rapla.facade.UpdateModule;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.Configuration;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.LocalCache;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.dbrm.StatusUpdater.Status;
import org.rapla.storage.impl.AbstractCachableOperator;

import com.google.gwtjsonrpc.common.FutureResult;
import com.google.gwtjsonrpc.common.ResultImpl;

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
    RestartServer,Disposable, RemoteMethodCaller
{
    private boolean bSessionActive = false;
//    private boolean isRestarting;
    Connector connector;
    //RemoteMethodSerialization remoteMethodSerialization;
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
        //remoteMethodSerialization = new RemoteMethodSerialization(context,  this);
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
	String accessToken;
	
   private void loginAndLoadData(ConnectInfo connectInfo) throws RaplaException {
		doConnect();
        String connectAs = connectInfo.getConnectAs();
        String password = new String( connectInfo.getPassword());
        String username = connectInfo.getUsername();
		try {
            RemoteServer serv1 = getRemoteServer();
            accessToken = serv1.login(username,password, connectAs).get();
       
    		// today should be the date of the server
    		lastSyncedTimeLocal = System.currentTimeMillis();
    		lastSyncedTime = getServerTime();
            getLogger().info("login successfull");
        } catch (RaplaException ex){
            disconnect();
            throw ex;
        } catch (Exception ex){
            disconnect();
            throw new RaplaException(ex);
        }
        loadData(username);
        bSessionActive = true;
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
    
    public String authenticate(String username, String password) throws RaplaException {
        throw new RaplaException("RemoteOperator only supports login method!");
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
	        UpdateEvent evt = serv.refresh( clientRepoVersion).get();
	        refresh( evt);
        }
        catch (EntityNotFoundException ex)
        {
        	getLogger().error("Refreshing all resources due to " + ex.getMessage(), ex);
        	refreshAll();
        }
	    catch (RaplaException ex)
	    {
	    	throw ex;
	    }
	    catch (Exception ex)
	    {
	    	throw new RaplaException(ex);
	    }		
    }
 
    synchronized public void restartServer() throws RaplaException {
    	getLogger().info("Restart in progress ...");
    	String message = i18n.getString("restart_server");
  //      isRestarting = true;
    	try
    	{
	        RemoteStorage serv = getRemoteStorage();
	        serv.restartServer().get();
	        fireStorageDisconnected(message);
    	}
	    catch (RaplaException ex)
	    {
	    	throw ex;
	    }
	    catch (Exception ex)
	    {
	    	throw new RaplaException(ex);
	    }		

    }
   
    synchronized public void disconnect() throws RaplaException { 
    	accessToken = null;
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
            	serv1.logout().get();
            }
            catch (RaplaConnectException ex)
            {
            	getLogger().warn( ex.getMessage());
            }
    	    catch (RaplaException ex)
    	    {
    	    	throw ex;
    	    }
    	    catch (Exception ex)
    	    {
    	    	throw new RaplaException(ex);
    	    }		
        	fireStorageDisconnected(message);
        }
    }

    private List<Entity> addToCache(Collection<Entity> list) throws RaplaException {
        List<Entity> result = new ArrayList<Entity>();
        synchronized (cache) {
        	resolveEntities( list );
            for( Entity entity:list) {
				if ( isStorableInCache(entity))
				{
					cache.put(entity);
				}
				result.add( entity);
            }
        }
        return result;
    }
    
    @Override
    protected void resolveEntities(Collection<? extends Entity> entities) throws RaplaException {
    	if (context.has(RemoteMethodStub.class))
    	{
    		return;
    	}
    	super.resolveEntities(entities);
//		Iterable<String> referencedIds = ((RefEntity)obj).getReferencedIds();
//		for ( String id:referencedIds)
//		{
//			resolve(id);
//		}
    }

    private void loadData(String username) throws RaplaException {
        cache.clearAll();
        getLogger().debug("Getting Data..");
        // recontextualize Entities
        RemoteStorage serv = getRemoteStorage();
        try
        {
	        UpdateEvent resources = serv.getResources().get();
	        clientRepositoryVersion = resources.getRepositoryVersion();
	        Collection<Entity> storeObjects = resources.getStoreObjects();
			addToCache(storeObjects );
	        if ( username != null)
	        {
	        	UserImpl user = cache.getUser( username);
	        	userId = user.getId();
	        }
	        getLogger().debug("Data flushed");
        }
	    catch (RaplaException ex)
	    {
	    	throw ex;
	    }
	    catch (Exception ex)
	    {
	    	throw new RaplaException(ex);
	    }		
    }

    protected void checkConnected() throws RaplaException {
        if ( !bSessionActive ) {
            throw new RaplaException("Not logged in or connection closed!");
        }
    }
   
    public void dispatch(UpdateEvent evt) throws RaplaException {
        checkConnected();
        // Store on server
        if (getLogger().isDebugEnabled()) {
            Iterator<Entity>it =evt.getStoreObjects().iterator();
            while (it.hasNext()) {
                Entity entity = it.next();
                getLogger().debug("dispatching store for: " + entity);
            }
            it =evt.getRemoveObjects().iterator();
            while (it.hasNext()) {
                Entity entity = it.next();
                getLogger().debug("dispatching remove for: " + entity);
            }
        }
        RemoteStorage serv = getRemoteStorage();
        evt.setRepositoryVersion( clientRepositoryVersion);
        try
        {
        	UpdateEvent serverClosure =serv.dispatch( evt ).get();
        	refresh(serverClosure);
        } 
        catch (RaplaException ex)
        {
        	throw ex;
        }
        catch (Exception ex)
        {
        	throw new RaplaException(ex);
        }
    }
    
    public String[] createIdentifier(RaplaType raplaType, int count) throws RaplaException {
    	RemoteStorage serv = getRemoteStorage();
    	try
    	{
	    	String[] id = serv.createIdentifier(raplaType.getLocalName(), count).get();
	    	return id;
    	} 
        catch (RaplaException ex)
        {
        	throw ex;
        }
        catch (Exception ex)
        {
        	throw new RaplaException(ex);
        }
    }

    public long getServerTime() throws RaplaException {
    	RemoteStorage remoteMethod = getRemoteStorage();
    	try
    	{
    		String serverTimeString = remoteMethod.getServerTime().get();
    		Date serverTime = raplaLocale.getSerializableFormat().parseTimestamp( serverTimeString);
			return serverTime.getTime();
    	} 
        catch (RaplaException ex)
        {
        	throw ex;
        }
        catch (Exception ex)
        {
        	throw new RaplaException(ex);
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
        try
        {
	        Boolean canChangePassword = remoteMethod.canChangePassword().get();
			boolean result = canChangePassword;
	        return result;
        } 
        catch (RaplaException ex)
        {
        	throw ex;
        }
        catch (Exception ex)
        {
        	throw new RaplaException(ex);
        }
    }

    @Override
    public void changePassword(User user,char[] oldPassword,char[] newPassword) throws RaplaException {
        try {
        	RemoteStorage remoteMethod = getRemoteStorage();
	        String username = user.getUsername();
			remoteMethod.changePassword(username, new String(oldPassword),new String(newPassword)).get();
		    refresh();
        } 
        catch (RaplaSecurityException ex) 
        {
        	throw new RaplaSecurityException(i18n.getString("error.wrong_password"));
        }
        catch (RaplaException ex)
        {
        	throw ex;
        }
        catch (Exception ex)
        {
        	throw new RaplaException(ex);
        }
    }
    
    @Override
    public void changeEmail(User user, String newEmail) throws RaplaException 
    {
    	try
    	{
	    	RemoteStorage remoteMethod = getRemoteStorage();
	        String username = user.getUsername();
			remoteMethod.changeEmail(username,newEmail).get();
	        refresh();
    	}
        catch (RaplaException ex)
        {
        	throw ex;
        }
        catch (Exception ex)
        {
        	throw new RaplaException(ex);
        }		
    }
    
    @Override
	public void confirmEmail(User user, String newEmail)	throws RaplaException 
	{
    	try
    	{
	    	RemoteStorage remoteMethod = getRemoteStorage();
	        String username = user.getUsername();
			remoteMethod.confirmEmail(username,newEmail).get();
    	}
        catch (RaplaException ex)
        {
        	throw ex;
        }
        catch (Exception ex)
        {
        	throw new RaplaException(ex);
        }					
	}

    
    @Override
    public void changeName(User user, String newTitle, String newFirstname, String newSurname) throws RaplaException 
    {
    	try
    	{
	    	RemoteStorage remoteMethod = getRemoteStorage();
	        String username = user.getUsername();
			remoteMethod.changeName(username,newTitle, newFirstname, newSurname).get();
	        refresh();
    	}
        catch (RaplaException ex)
        {
        	throw ex;
        }
        catch (Exception ex)
        {
        	throw new RaplaException(ex);
        }		

    }
    
    public Map<String,Entity> getFromId(Collection<String> idSet, boolean throwEntityNotFound) throws RaplaException
    {
     	RemoteStorage serv = getRemoteStorage();
     	String[] array = idSet.toArray(new String[] {});
     	 Map<String,Entity> result = new HashMap<String,Entity>();
     	try
     	{
			UpdateEvent entityList = serv.getEntityRecursive( array).get();
	    	Collection<Entity> storeObjects = entityList.getStoreObjects();
			List<Entity>resolvedList = addToCache(storeObjects  );
			for (Entity entity:resolvedList)
			{
				result.put( entity.getId(), entity);
			}
     	} 
     	catch (EntityNotFoundException ex)
     	{
     		if ( throwEntityNotFound)
     		{
     			throw ex;
     		}
		}
	    catch (RaplaException ex)
	    {
	    	throw ex;
	    }
	    catch (Exception ex)
	    {
	    	throw new RaplaException(ex);
	    }		
		return result;
    }
   
    @Override
    public Entity tryResolve(String id) {
    	Assert.notNull( id);
    	Entity entity =  super.tryResolve(id);
    	if ( entity != null)
    	{
    		return entity;
    	}
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
		return null;
    }
    
    @Override
    public Entity resolve(String id) throws EntityNotFoundException {
    	try {
            return super.resolve(id);
        } catch (EntityNotFoundException ex) {
//            try {
//            	String castedId = id.toString();
//            	//String idS = castedId.getTypeName() + "_" + String.valueOf(castedId.getKey());
//            	RemoteStorage serv = getRemoteStorage();
//            	UpdateEvent resolved = serv.getEntityRecursive( new String[] { castedId });
//            	Collection<Entity> storeObjects = resolved.getStoreObjects();
//				addToCache(storeObjects  );
//            	for ( Entity entity: storeObjects)
//            	{
//            		if ( id.equals(entity.getId()))
//            		{
//            			return entity;
//            		}
//            	}
//            } catch (RaplaException rex) {
            	throw new EntityNotFoundException("Object for id " + id.toString() + " not found due to " + ex.getMessage());
            //}
            //return super.resolve(id);
        }
    }
    
    public List<Reservation> getReservations(User user,Collection<Allocatable> allocatables,Date start,Date end, Map<String,String> annotationQuery) throws RaplaException {
        checkConnected();
    	RemoteStorage serv = getRemoteStorage();
    	String[] allocatableId = getIdList(allocatables);
    	try
    	{
			List<ReservationImpl> list =serv.getReservations(allocatableId,start, end, annotationQuery).get().get();
	        synchronized (cache) {
	        	resolveEntities( list );
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
	    catch (RaplaException ex)
	    {
	    	throw ex;
	    }
	    catch (Exception ex)
	    {
	    	throw new RaplaException(ex);
	    }		
    }
    
    public List<String> getTemplateNames() throws RaplaException {
    	checkConnected();
    	RemoteStorage serv = getRemoteStorage();
    	try
    	{
    		List<String> result = Arrays.asList(serv.getTemplateNames().get());
    		return result;
    	}
	    catch (RaplaException ex)
	    {
	    	throw ex;
	    }
	    catch (Exception ex)
	    {
	    	throw new RaplaException(ex);
	    }		
    }

//    public EntityStore get()
//    {
//    	Collection<Entity>emptyList = Collections.emptyList();
//		EntityStore store = createEntityStore(emptyList, cache);
//		return store;
//    }
    /**
	 * Entities will be resolved against resolveableEntities. If not found the
	 * ParentResolver will be used.
	 */
//	private EntityStore createEntityStore(Collection<? extends Entity>resolveableEntities, LocalCache parentCache) {
//		EntityStore resolver = new EntityStore(parentCache, cache.getSuperCategory())
//		{
//			@Override
//			public Entity resolve(String id) {
//				Entity refEntity = super.tryResolve(id);
//				if ( refEntity == null)
//				{
//					{
//						if ( id.startsWith(Allocatable.TYPE.getLocalName()))
//						{
//							AllocatableImpl unresolved = new AllocatableImpl(null, null);
//							unresolved.setId( id);
//							unresolved.setClassification( getUnresolvedAllocatableType().newClassification());
//							return unresolved;
//						}
//						// if the type is not found we test if its an anonymous type (key = 0)
//						if ( id.startsWith(DynamicType.TYPE.getLocalName() + "_0"))
//						{
//							DynamicType unresolvedReservation = getAnonymousReservationType();
//							return (Entity) unresolvedReservation;
//						}
//					}
//				}
//				return refEntity;
//			}
//			
//			public DynamicType getDynamicType(String key) {
//				DynamicType unresolvedReservation = getAnonymousReservationType();
//				if ( key.equals(unresolvedReservation.getElementKey()))
//				{
//					return unresolvedReservation;
//				}
//				return super.getDynamicType(key);
//			}
//		};
//		resolver.addAll(resolveableEntities);
//		return resolver;
//	}

    
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
   
    int clientRepositoryVersion = 0;

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
			resolveEntities(evt.getStoreObjects());
			resolveEntities(evt.getRemoveObjects());
			clientRepositoryVersion = evt.getRepositoryVersion();
    		if ( bSessionActive  &&   !evt.isEmpty()  ) {
                getLogger().debug("Objects updated!");
                UpdateResult result = update(evt);
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
		if ((raplaType ==  Appointment.TYPE  || raplaType == Reservation.TYPE) )
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
		else if ((raplaType ==  Appointment.TYPE  || raplaType == Reservation.TYPE) )
		{
			return false;
		}
		return true;
	}

    StatusUpdater updater;
    public void setStatusUpdater(StatusUpdater statusUpdater) {
    	this.updater = statusUpdater;
    }

    public FutureResult call( Class<?> service, String methodName,Class<?>[] parameterTypes, Class<?> returnType ,Object[] args)  {
        try {
        	if ( updater != null)
        	{
        		updater.setStatus( Status.BUSY );
        	}
            FutureResult result =connector.call( accessToken,service, methodName, parameterTypes, returnType , args);
            return result;
        } catch (Exception ex) {
            return new ResultImpl(new RaplaException(ex));
        }
        //FIXME implement refresh logic on server restart
//        }  catch ( RaplaException ex) {
//            if ( ex.getMessage() != null && ex.getMessage().equals( RemoteStorage.USER_WAS_NOT_AUTHENTIFIED))
//            {
//                getLogger().warn(ex.getMessage() + ". Disconnecting from server.");
//                String message = getI18n().format("error.connection_closed", getConnectionName());
//                disconnect(message);
//                throw new RaplaRestartingException();
//            }
//            else
//            {
//                throw ex;
//            }
//        }
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
		//AppointmentImpl[] appointmentArray = appointments.toArray( new AppointmentImpl[appointments.size()]);
		String[] reservationIds = getIdList(ignoreList);
		List<AppointmentImpl> appointmentList = new ArrayList<AppointmentImpl>();
		Map<String,Appointment> appointmentMap= new HashMap<String,Appointment>();
		for ( Appointment app: appointments)
		{
			appointmentList.add( (AppointmentImpl) app);
			appointmentMap.put( app.getId(), app);
		}
		try
		{
			Map<String, List<String>> resultMap = serv.getFirstAllocatableBindings(allocatableIds, appointmentList, reservationIds).get().get();
			HashMap<Allocatable, Collection<Appointment>> result = new HashMap<Allocatable, Collection<Appointment>>();
			for ( Allocatable alloc:allocatables)
			{
				List<String> list = resultMap.get( alloc.getId());
				if ( list != null)
				{
					Collection<Appointment> appointmentBinding = new ArrayList<Appointment>();
					for ( String id:list)
					{
						Appointment e = appointmentMap.get( id);
						if ( e != null)
						{
							appointmentBinding.add( e);
						}
					}
					result.put( alloc, appointmentBinding);
				}
			}
			return result;
		}
	    catch (RaplaException ex)
	    {
	    	throw ex;
	    }
	    catch (Exception ex)
	    {
	    	throw new RaplaException(ex);
	    }				
	}

	@Override
	public Map<Allocatable, Map<Appointment, Collection<Appointment>>> getAllAllocatableBindings( Collection<Allocatable> allocatables,	Collection<Appointment> appointments, Collection<Reservation> ignoreList) throws RaplaException {
		checkConnected();
    	RemoteStorage serv = getRemoteStorage();
    	String[] allocatableIds = getIdList(allocatables);
		List<AppointmentImpl> appointmentArray = Arrays.asList(appointments.toArray( new AppointmentImpl[]{}));
		String[] reservationIds = getIdList(ignoreList);
		List<ReservationImpl> serverResult;
		try
		{
			serverResult = serv.getAllAllocatableBindings(allocatableIds, appointmentArray, reservationIds).get().get();
		}
		catch (RaplaException ex)
		{
			throw ex;
		}
		catch (Exception ex)
		{
			throw new RaplaException(ex);
		}
		Lock readLock = readLock();
	    try
	    {
        	resolveEntities( serverResult);
        }
	    finally
	    {
	    	unlock( readLock );
	    }
        SortedSet<Appointment> allAppointments = new TreeSet<Appointment>(new AppointmentStartComparator());
        for ( ReservationImpl reservation: serverResult)
        {
        	for ( Appointment app:reservation.getAppointments())
        	{
        		allAppointments.add(  app);        		
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
		try
		{
			Date result = serv.getNextAllocatableDate(allocatableIds, (AppointmentImpl)appointment, reservationIds, worktimeStartMinutes, worktimeEndMinutes, excludedDays, rowsPerHour).get();
			return result;
		}
	    catch (RaplaException ex)
	    {
	    	throw ex;
	    }
	    catch (Exception ex)
	    {
	    	throw new RaplaException(ex);
	    }		
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
    	try
    	{
	    	List<ConflictImpl> list = serv.getConflicts().get().get();
	        //EntityResolver entityResolver = createEntityStore( list,  cache  );
	        Lock readLock = readLock();
		    try
		    {
	        	resolveEntities( list );
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
    	catch (RaplaException ex)
    	{
    		throw ex;
    	}
    	catch (Exception ex)
    	{
    		throw new RaplaException(ex);
    	}		
	}

//	@Override
//	protected void logEntityNotFound(Entity obj, EntityNotFoundException ex) {
//		RemoteStorage serv = getRemoteStorage();
//		Comparable id = ex.getId();
//		try {
//			if ( obj instanceof ConflictImpl)
//			{
//				Iterable<String> referencedIds = ((ConflictImpl)obj).getReferencedIds();
//				List<String> ids = new ArrayList<String>();
//				for (String refId:referencedIds)
//				{
//					ids.add(  refId);
//				}
//				serv.logEntityNotFound( id  + " not found in conflict :",ids.toArray(new  String[0]));
//			}
//			else if ( id != null )
//			{
//				serv.logEntityNotFound("Not found", id.toString() );
//			}
//		} catch (Exception e) {
//			getLogger().error("Can't call server logging for " + ex.getMessage() + " due to " + e.getMessage(), e);
//		}
//	}
}

