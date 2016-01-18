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

import org.rapla.ConnectInfo;
import org.rapla.RaplaResources;
import org.rapla.components.util.Assert;
import org.rapla.components.util.Cancelable;
import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.SerializableDateTimeFormat;
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
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.Conflict;
import org.rapla.facade.UpdateModule;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.facade.internal.ModificationEventImpl;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.DefaultImplementationRepeatable;
import org.rapla.inject.InjectionContext;
import org.rapla.jsonrpc.client.gwt.MockProxy;
import org.rapla.jsonrpc.common.AsyncCallback;
import org.rapla.jsonrpc.common.FutureResult;
import org.rapla.jsonrpc.common.ResultImpl;
import org.rapla.jsonrpc.common.VoidResult;
import org.rapla.storage.PreferencePatch;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.StorageUpdateListener;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.dbrm.RemoteStorage.BindingMap;
import org.rapla.storage.impl.AbstractCachableOperator;
import org.rapla.storage.impl.EntityStore;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.locks.Lock;


/** This operator can be used to modify and access data over the
 * network.  It needs an server-process providing the StorageService
 * (usually this is the default rapla-server).
*/
@DefaultImplementationRepeatable({
@DefaultImplementation(of = StorageOperator.class, context = InjectionContext.client),
@DefaultImplementation(of = RestartServer.class, context = InjectionContext.client),
})
@Singleton
public class RemoteOperator  extends  AbstractCachableOperator implements  RestartServer,Disposable
{
    final List<StorageUpdateListener> storageUpdateListeners = new Vector<StorageUpdateListener>();

    private boolean bSessionActive = false;
    String userId;
	RemoteAuthentificationService remoteAuthentificationService;
	RemoteStorage remoteStorage;
	protected CommandScheduler commandQueue;
	
	Date lastSyncedTimeLocal;
    Date lastSyncedTime;
    int timezoneOffset;
    ConnectInfo connectInfo;
    RemoteConnectionInfo connectionInfo;
	
    @Inject
    public RemoteOperator( Logger logger, RaplaResources i18n,RaplaLocale locale, CommandScheduler scheduler,Map<String, FunctionFactory> functionFactoryMap,RemoteAuthentificationService remoteAuthentificationService, RemoteStorage remoteStorage, RemoteConnectionInfo connectionInfo, Set<PermissionExtension> permissionExtensions) {
        super(  logger, i18n,locale, functionFactoryMap, permissionExtensions);
        this.remoteAuthentificationService = remoteAuthentificationService;
        this.remoteStorage = remoteStorage;
    	commandQueue = scheduler;
    	this.connectionInfo = connectionInfo;
//    	this.connectionInfo = new RemoteConnectionInfo();
//    	remoteStorage.setConnectInfo( connectionInfo );
//    	remoteAuthentificationService.setConnectInfo( connectionInfo );
//    	if ( config != null)
//    	{
//    	    String serverConfig  = config.getChild("server").getValue("${downloadServer}");
//    	    final String serverURL= ContextTools.resolveContext(serverConfig, context );
//    	    connectionInfo.setServerURL(serverURL);
//    	}
    }
    
    public RemoteConnectionInfo getRemoteConnectionInfo()
    {
        return connectionInfo;
    }

    User user;

    synchronized public User connect(ConnectInfo connectInfo) throws RaplaException {
       
        if (isConnected())
            throw new RaplaException("Already connected");
        getLogger().info("Connecting to server and starting login..");
	    User user;
	    if ( connectInfo != null)
	    {
    		user = loginAndLoadData(connectInfo);
    		connectionInfo.setReconnectInfo( connectInfo);
//    		connectionInfo.setReAuthenticateCommand(
//    		        
//    		        new FutureResult<String>() {
//
//    	            @Override
//    	            public String get() throws Exception {
//    	                getLogger().info("Refreshing access token.");
//    	                return loginWithoutDisconnect();
//    	            }
//
//    	            @Override
//    	            public void get(AsyncCallback<String> callback) {
//    	               try {
//    	                   String string = get();
//    	                   callback.onSuccess(string);
//    	               } catch (Exception e) {
//    	                   callback.onFailure(e);
//    	               } 
//    	            }
//    	        });
	    }
	    else
	    {
	        user = loadData();
	    }
        this.user = user;
		initRefresh();
		return user;
    }

    public User getUser()
    {
        return user;
    }
    
    public FutureResult<User> connectAsync()
    {
        return new FutureResult<User>() {

            @Override
            public User get() throws Exception {
                return connect(null);
            }

            @Override
            public void get(final AsyncCallback<User> callback) {
                RemoteStorage serv = getRemoteStorage();
                FutureResult<UpdateEvent> resources;
                try {
                    resources = serv.getResources();
                } catch (RaplaException e) {
                    callback.onFailure( e);
                    return;
                }
                resources.get( new AsyncCallback<UpdateEvent>() {

                    @Override
                    public void onFailure(Throwable caught) {
                        callback.onFailure( caught);
                        return;                        
                    }

                    @Override
                    public void onSuccess(UpdateEvent evt) {
                        
                        try {
                            User user = loadData(evt);
                            initRefresh();
                            callback.onSuccess( user );
                        } catch (RaplaException e) {
                            callback.onFailure( e);
                        }
                        
                    }
                });
            }
        };
    }
	
	private User loginAndLoadData(ConnectInfo connectInfo) throws RaplaException {
		this.connectInfo = connectInfo;
		login();
        getLogger().info("login successfull");
		User user = loadData();
        return user;
	}

	protected String login() throws RaplaException {
		try {
		    return loginWithoutDisconnect();
		} catch (RaplaException ex){
		    disconnect();
		    throw ex;
		} catch (Exception ex){
		    disconnect();
		    throw new RaplaException(ex);
		}
	}

    private String loginWithoutDisconnect() throws Exception
    {
        String connectAs = this.connectInfo.getConnectAs();
        String password = new String( this.connectInfo.getPassword());
        String username = this.connectInfo.getUsername();
        RemoteAuthentificationService serv1 = getRemoteAuthentificationService();
        LoginTokens loginToken = serv1.login(username,password, connectAs).get();
        String accessToken = loginToken.getAccessToken();
        if ( accessToken != null)
        {
            connectionInfo.setAccessToken( accessToken);
            return accessToken;
        }
        else
        {
            throw new RaplaSecurityException("Invalid Access token");
        }
    }
   
	public Date getCurrentTimestamp() {
	    if (lastSyncedTime == null)
	    {
	        return new Date(System.currentTimeMillis());
	    }
		// no matter what the client clock says we always sync to the server clock
		long passedMillis =  System.currentTimeMillis()- lastSyncedTimeLocal.getTime();
		if ( passedMillis < 0)
		{
			passedMillis = 0;
		}
		long correctTime = this.lastSyncedTime.getTime() + passedMillis;
		Date date = new Date(correctTime);
		return date;
	}
	
	public Date today() {
		long time = getCurrentTimestamp().getTime();
		Date raplaTime = new Date(time + timezoneOffset);
		return DateTools.cutDate( raplaTime);
	}

	Cancelable timerTask;
	int intervalLength;
	public final void initRefresh() 
	{
		Command refreshTask = new Command() {
			public void execute() {
			    // test if the remote operator is writable
                // if not we skip until the next update cycle
                Lock writeLock = lock.writeLock();
                boolean tryLock = writeLock.tryLock();
                if ( tryLock)
                {
                    writeLock.unlock();
                }
                if (isConnected() && tryLock) {
                    refreshAsync( new AsyncCallback<VoidResult>() {

                        @Override
                        public void onFailure(Throwable caught) {
                            getLogger().error("Error refreshing.", caught);
                            
                        }

                        @Override
                        public void onSuccess(VoidResult result) {
                            
                        }
                    });
                }
			}
		};
		intervalLength = UpdateModule.REFRESH_INTERVAL_DEFAULT;
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
	
//    public String getConnectionName() {
//    	if ( connector != null)
//    	{
//    		return connector.getInfo();
//    	}
//    	else
//    	{
//    		return "standalone";
//    	}
//    }
//
//    private void doConnect() throws RaplaException {
//        boolean bFailed = true;
//        try {
//            bFailed = false;
//        } catch (Exception e) {
//            throw new RaplaException(i18n.format("error.connect",getConnectionName()),e);
//        } finally {
//            if (bFailed)
//                disconnect();
//        }
//    }

    @Override
    public boolean isConnected() {
        return bSessionActive;
    }

    @Override
    public boolean isLoaded()
    {
        return isConnected();
    }

    @Override
    public boolean supportsActiveMonitoring() {
        return true;
    }

    @Override
    synchronized public void refresh() throws RaplaException {
        String clientRepoVersion = getLastSyncedTime();
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

    boolean refreshInProgress;

    synchronized private void refreshAsync( final AsyncCallback<VoidResult> callback)  {
        if ( refreshInProgress )
        {
            return;
        }
        String clientRepoVersion = getLastSyncedTime();
        RemoteStorage serv = getRemoteStorage();
        refreshInProgress = true;
        try
        {
            serv.refresh( clientRepoVersion).get( new AsyncCallback<UpdateEvent>() {
                
                @Override
                public void onFailure(Throwable caught) {
                    refreshInProgress = false;
                    callback.onFailure(caught);
                }
    
                @Override
                public void onSuccess(UpdateEvent evt) {
                    refreshInProgress = false;
                    try {
                        refresh( evt);
                    }
                    catch (EntityNotFoundException ex)
                    {
                        getLogger().error("Refreshing all resources due to " + ex.getMessage(), ex);
                        try {
                            refreshAll();
                        } catch (Exception e) {
                            onFailure(ex);
                            return;
                        }
                    }
                    catch (Exception ex)
                    {
                        onFailure(ex);
                        return;
                    }
                    callback.onSuccess( VoidResult.INSTANCE);
                }
            });
        } catch (Exception ex)
        {
            refreshInProgress = false;
        }
    }


    private String getLastSyncedTime() {
        return SerializableDateTimeFormat.INSTANCE.formatTimestamp(lastSyncedTime);
    }

    /** returns if the Facade is connected through a server (false if it has a local store)*/
    synchronized public boolean isRestartPossible()
    {
        final MockProxy mockProxy = connectionInfo.getMockProxy();
        return mockProxy == null;
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
    	connectionInfo.setAccessToken( null);
    	this.connectInfo = null;
    	connectionInfo.setReconnectInfo( null );
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
            RemoteAuthentificationService serv1 = getRemoteAuthentificationService();
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

    
    @Override
    protected void setResolver(Collection<? extends Entity> entities) throws RaplaException {
    	// don't resolve entities in standalone mode
    	if (isStandalone())
    	{
    		return;
    	}
    	super.setResolver(entities);
    }

    private boolean isStandalone() {
        String serverURL = connectionInfo.getServerURL();
        return serverURL != null && serverURL.contains("file:");
    }
    
    @Override
    protected void testResolve(Collection<? extends Entity> entities) throws EntityNotFoundException {
        //  don't resolve entities in standalone mode
        if (isStandalone())
        {
            return;
        }
        super.testResolve(entities);
    }
    
    // problem of resolving the bootstrap loading of unreadable resources before resource type is referencable
    protected void testResolveInitial(Collection<? extends Entity> entities) throws EntityNotFoundException {
        if (isStandalone())
        {
            return;
        }
        EntityStore store = new EntityStore( this, getSuperCategory())
        {
            protected <T extends Entity> T tryResolveParent(String id, Class<T> entityClass) {
                T tryResolve = super.tryResolveParent(id, entityClass);
                if ( tryResolve != null)
                {
                    return tryResolve;
                }
                else
                {
                    if ( entityClass != null && isAllocatableClass(entityClass))
                    {
                        AllocatableImpl unresolved = new AllocatableImpl(null, null);
                        unresolved.setId( id);
                        DynamicType dynamicType = getDynamicType(StorageOperator.UNRESOLVED_RESOURCE_TYPE);
                        ((DynamicTypeImpl)dynamicType).setReadOnly();
                        Classification newClassification = dynamicType.newClassification();
                        unresolved.setClassification( newClassification);
                        @SuppressWarnings("unchecked")
                        T casted = (T) unresolved;
                        return casted;
                    }
                    return null;
                }
            }
        };
        store.addAll( entities);
        for (Entity entity: entities) {
            if (entity instanceof  DynamicType)
            {
                ((DynamicTypeImpl) entity).setOperator( this);
            }
            if (entity instanceof EntityReferencer)
            {
                ((EntityReferencer)entity).setResolver(store);
            }

        }
        for (Entity entity: entities) {
            if (entity instanceof EntityReferencer)
            {
                testResolve(store, (EntityReferencer)entity);
            }
        }  
    }

    protected void testResolve(EntityResolver resolver, EntityReferencer obj, ReferenceInfo reference) throws EntityNotFoundException {
        Class<? extends Entity> class1 = reference.getType();
        String id = reference.getId();
        if (tryResolve(resolver,id, class1) == null)
        {
            if ( class1 != User.class || (userId == null || userId.equals(id)))
            {
                String prefix = (class1 != null) ? class1.getName() : " unkown type";
                throw new EntityNotFoundException(prefix + " with id " + id + " not found for " + obj);
            }
        }
    }

    public User loadData() throws RaplaException {
        RemoteStorage serv = getRemoteStorage();
        FutureResult<UpdateEvent> resources = serv.getResources();
        try
        {
            getLogger().debug("Getting Data..");
            UpdateEvent evt = resources.get();
	        return loadData(evt);
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

    private User loadData(UpdateEvent evt) throws RaplaException {
        Lock writeLock = writeLock();
        Date lastUpdated = evt.getLastValidated();
        setLastRefreshed(lastUpdated);
        try
        {
            this.userId = evt.getUserId();
            if ( userId != null)
            {
                cache.setClientUserId( userId);
            }
            if ( userId == null)
            {
                throw new EntityNotFoundException("Userid not passed in result");
            }
            updateTimestamps(evt);
            Collection<Entity> storeObjects = evt.getStoreObjects();
            cache.clearAll();
            testResolveInitial( storeObjects);
            setResolver( storeObjects );
            for( Entity entity:storeObjects)
            {
                cache.put(entity);
            }
            getLogger().debug("Data flushed");
            bSessionActive = true;
            User user = cache.resolve( userId, User.class);
            return user;        }
        finally
        {
            unlock(writeLock);
        }


    }

	public void updateTimestamps(UpdateEvent evt) throws RaplaException {
		if ( evt.getLastValidated() == null)
		{
			throw new RaplaException("Server sync time is missing");
		}
		lastSyncedTimeLocal = new Date(System.currentTimeMillis());
		lastSyncedTime = evt.getLastValidated();
		timezoneOffset = evt.getTimezoneOffset();
		//long offset = TimeZoneConverterImpl.getOffset( DateTools.getTimeZone(), systemTimeZone, time);

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
            for (Entity entity:evt.getStoreObjects()) {
                getLogger().debug("dispatching store for: " + entity);
            }
            for (ReferenceInfo id:evt.getRemoveIds()) {
                getLogger().debug("dispatching remove for: " + id);
            }
//            Iterator<Entity> it =evt.getRemoveObjects().iterator();
//            while (it.hasNext()) {
//                Entity entity = it.next();
//                getLogger().debug("dispatching remove for: " + entity);
//            }
        }
        RemoteStorage serv = getRemoteStorage();
        evt.setLastValidated(lastSyncedTime);
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
    
    public String[] createIdentifier(Class<? extends Entity> raplaType, int count) throws RaplaException {
    	RemoteStorage serv = getRemoteStorage();
    	try
    	{
            String localname = RaplaType.getLocalName( raplaType);
	    	List<String> id = serv.createIdentifier(localname, count).get();
	    	return id.toArray(new String[] {});
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
	
	private RemoteAuthentificationService getRemoteAuthentificationService()  {
		return remoteAuthentificationService;
	}

    public boolean canChangePassword() throws RaplaException  {
        RemoteStorage remoteMethod = getRemoteStorage();
        try
        {
	        String canChangePassword = remoteMethod.canChangePassword().get();
			boolean result = canChangePassword != null && canChangePassword.equalsIgnoreCase("true");
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
    public String getUsername(String userId) throws RaplaException
    {
        User user = tryResolve(userId, User.class);
        if ( user == null)
        {
            RemoteStorage remoteMethod = getRemoteStorage();
            final FutureResult<String> result = remoteMethod.getUsername(userId);
            try
            {
                String    username = result.get();
                return username;
            }
            catch (Exception e)
            {
                throw new RaplaException( e.getMessage(),e );
            }

        }
        Locale locale = raplaLocale.getLocale();
        String name =user.getName(locale);
        return name;
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
	    	Collection<Entity> list = entityList.getStoreObjects();
	    	Lock lock = readLock();
	    	try 
	    	{
	    	    testResolve( list);
                setResolver( list );
            } 
            finally
            {
                unlock(lock);
            }
	    	for (Entity entity:list)
			{
				String id = entity.getId();
				if ( idSet.contains( id ))
				{
					result.put( id, entity);
				}
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
    protected <T extends Entity> T tryResolve(EntityResolver resolver,String id,Class<T> entityClass)  {
    	Assert.notNull( id);
    	T entity =  super.tryResolve(resolver,id, entityClass);
    	if ( entity != null)
    	{
    		return entity;
    	}
    	if ( entityClass != null && isAllocatableClass(entityClass))
		{
			AllocatableImpl unresolved = new AllocatableImpl(null, null);
			unresolved.setId( id);
			DynamicType dynamicType = resolver.getDynamicType(UNRESOLVED_RESOURCE_TYPE);
		    if ( dynamicType == null)
            {
                //throw new IllegalStateException("Unresolved resource type not found");
                return null;
            }
            Classification newClassification = dynamicType.newClassification();
            unresolved.setClassification( newClassification);
			@SuppressWarnings("unchecked")
            T casted = (T) unresolved;
            return casted;
		}
		return null;
    }

    private <T extends Entity> boolean isAllocatableClass(Class<T> entityClass) 
    {
        return entityClass.equals(Allocatable.class) || entityClass.equals(AllocatableImpl.class);
    }
    
    public FutureResult<Collection<Reservation>> getReservations(User user,Collection<Allocatable> allocatables,Date start,Date end,final ClassificationFilter[] filters, Map<String,String> annotationQuery) {
    	RemoteStorage serv = getRemoteStorage();
    	
        // if a refresh is due, we assume the system went to sleep so we refresh before we continue
    	// TODO we should make a refresh async as well
        if ( intervalLength > 0 && lastSyncedTime != null && (lastSyncedTime.getTime() + intervalLength * 2) < getCurrentTimestamp().getTime())
        {
            getLogger().info("cache not uptodate. Refreshing first.");
            try {
                refresh();
            } catch (RaplaException e) {
                return new ResultImpl<Collection<Reservation>>(e);
            }
        }

        String[] allocatableId = getIdList(allocatables);
        final FutureResult<List<ReservationImpl>> serverQuery = serv.getReservations(allocatableId,start, end, annotationQuery);
    	return new FutureResult<Collection<Reservation>>() {

            @SuppressWarnings("unchecked")
            @Override
            public Collection<Reservation> get() throws Exception {
                List<ReservationImpl> list;
                Collection<Reservation> filtered;
                {
                    long time = System.currentTimeMillis();
                    list = serverQuery.get();
                    logger.debug("event server call took  " + (System.currentTimeMillis() - time) + " ms");
                }
                {
                    long time = System.currentTimeMillis();
                    filtered = processReservationResult(list, filters);
                    logger.debug("event post processing took  " + (System.currentTimeMillis() - time) + " ms");
                }

                return filtered;
            }

            @Override
            public void get(final AsyncCallback<Collection<Reservation>> callback) {
                serverQuery.get(new AsyncCallback<List<ReservationImpl>>() {

                    @Override
                    public void onFailure(Throwable caught) {
                        callback.onFailure( caught);
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public void onSuccess(List<ReservationImpl> list) {
                        final Collection<Reservation> filtered;
                        try {
                            filtered =processReservationResult(list, filters);
                        } catch (RaplaException e) {
                            callback.onFailure( e);
                            return;
                        }
                        callback.onSuccess(filtered);
                    }
                });
                
            }
        };
    }

    private List<Reservation> processReservationResult(List<ReservationImpl> list,ClassificationFilter[] filters) throws RaplaException {
    	try
    	{
		    Lock lock = readLock();
			try 
	        {
	        	testResolve( list);
	        	setResolver( list );
	        } 
			finally
	        {
				unlock(lock);
	        }
	        List<Reservation> result = new ArrayList<Reservation>();
	        Iterator it = list.iterator();
	        while ( it.hasNext())
	        {
	        	Object object = it.next();
	        	Reservation classifiable = (Reservation)object;
	        	if ( isInFilter(classifiable, filters))
	        	{
	        	    result.add( classifiable);
	        	}
	        }
	        return result;
    	}
	    catch (RaplaException ex)
	    {
	    	throw ex;
	    }
    }
    
//    public List<String> getTemplateNames() throws RaplaException {
//    	checkConnected();
//    	RemoteStorage serv = getRemoteStorage();
//    	try
//    	{
//    		List<String> result = serv.getTemplateNames().get();
//    		return result;
//    	}
//	    catch (RaplaException ex)
//	    {
//	    	throw ex;
//	    }
//	    catch (Exception ex)
//	    {
//	    	throw new RaplaException(ex);
//	    }		
//    }

	protected String[] getIdList(Collection<? extends Entity> entities) {
		List<String> idList = new ArrayList<String>();
    	if ( entities != null )
    	{
    		for ( Entity entity:entities)
    		{
                if (entity != null)
    			    idList.add( entity.getId().toString());
    		}
    	}
    	String[] ids = idList.toArray(new String[] {});
		return ids;
	}
   
    synchronized private void refresh(UpdateEvent evt) throws RaplaException
    {

    	updateTimestamps(evt);		
    	if ( evt.isNeedResourcesRefresh())
    	{
    		refreshAll();
    		return;
    	}
    	UpdateResult result = null;
    	testResolve(evt.getStoreObjects());
    	setResolver(evt.getStoreObjects());
    	// we don't test the references of the removed objects
    	//setResolver(evt.getRemoveObjects());
        Date since = getLastRefreshed();
        Date until = evt.getLastValidated();
    	if ( bSessionActive  &&   !evt.isEmpty()  )
    	{
    	    getLogger().debug("Objects updated!");
    	    Lock writeLock = writeLock();
    	    try
    	    {
                final Collection<Entity> storeObjects1 = evt.getStoreObjects();
                Collection<PreferencePatch> preferencePatches = evt.getPreferencePatches();
                Collection<ReferenceInfo> removedIds = evt.getRemoveIds();
    	        result = update( since, until, storeObjects1, preferencePatches, removedIds);
    	    }
    	    finally
    	    {
    	        unlock(writeLock);
    	    }
        }
	
		if ( result != null )
        {
            fireStorageUpdated(result,evt.getInvalidateInterval());
        }
    }

    
	protected void refreshAll() throws RaplaException
    {
		UpdateResult result;
		Collection<Entity> oldEntities; 
		Lock readLock = readLock();
        try
        {
            User user = cache.resolve( userId, User.class);
            oldEntities = cache.getVisibleEntities(user);
        }
        finally
        {
            unlock(readLock);
        }
        loadData();
		
        
		Collection<Entity> newEntities; 
		readLock = readLock();
		try
		{
		    User user = cache.resolve( userId, User.class);
		    newEntities = cache.getVisibleEntities(user);
		}
		finally
		{
		    unlock(readLock);
		}
		HashSet<Entity> updated = new HashSet<Entity>(newEntities);
		Set<Entity> toRemove = new HashSet<Entity>(oldEntities);
		Set<Entity> toUpdate = new HashSet<Entity>(oldEntities);
		toRemove.removeAll(newEntities);
		updated.removeAll( toRemove);
		toUpdate.retainAll(newEntities);
		
		HashMap<String,Entity> oldEntityMap = new HashMap<String,Entity>();
		for ( Entity oldEntity: toUpdate)
		{
			@SuppressWarnings("unchecked")
            Class<? extends Entity> typeClass = oldEntity.getTypeClass();
            Entity newEntity = cache.tryResolve( oldEntity.getId(), typeClass);
			if ( newEntity != null)
			{
				oldEntityMap.put( newEntity.getId(), oldEntity);
			}
		}
		TimeInterval invalidateInterval = new TimeInterval( null,null);
        Collection<ReferenceInfo> removeInfo = new ArrayList<ReferenceInfo>();
        for ( Entity entity:toRemove)
        {
            removeInfo.add( new ReferenceInfo(entity.getId(), entity.getTypeClass()));
        }
        Date since = null;
        Date until = getLastRefreshed();
		result  = createUpdateResult(oldEntityMap, updated, removeInfo, since,until);
		fireStorageUpdated(result, new TimeInterval( null, null));
	}

    public synchronized void addStorageUpdateListener(StorageUpdateListener listener) {
        storageUpdateListeners.add(listener);
    }

    public synchronized void removeStorageUpdateListener(StorageUpdateListener listener) {
        storageUpdateListeners.remove(listener);
    }

    public synchronized StorageUpdateListener[] getStorageUpdateListeners() {
        return storageUpdateListeners.toArray(new StorageUpdateListener[] {});
    }

    protected void fireStorageUpdated(final UpdateResult updateResult, TimeInterval timeInterval) {
        StorageUpdateListener[] listeners = getStorageUpdateListeners();
        final ModificationEventImpl evt = new ModificationEventImpl(updateResult, timeInterval);
        if ( evt.isEmpty())
        {
            return;
        }
        for (int i = 0; i < listeners.length; i++) {
            listeners[i].objectsUpdated(evt);
        }
    }

    protected void fireStorageDisconnected(String message) {
        if (storageUpdateListeners.size() == 0)
            return;
        StorageUpdateListener[] listeners = getStorageUpdateListeners();
        for (int i = 0; i < listeners.length; i++) {
            listeners[i].storageDisconnected(message);
        }
    }

    @Override
	public FutureResult<Map<Allocatable, Collection<Appointment>>> getFirstAllocatableBindings( final Collection<Allocatable> allocatables,	Collection<Appointment> appointments, Collection<Reservation> ignoreList){
		final RemoteStorage serv = getRemoteStorage();
    	final String[] allocatableIds = getIdList(allocatables);
		//AppointmentImpl[] appointmentArray = appointments.toArray( new AppointmentImpl[appointments.size()]);
		final String[] reservationIds = getIdList(ignoreList);
		final List<AppointmentImpl> appointmentList = new ArrayList<AppointmentImpl>();
		final Map<String,Appointment> appointmentMap= new HashMap<String,Appointment>();
		for ( Appointment app: appointments)
		{
			appointmentList.add( (AppointmentImpl) app);
			appointmentMap.put( app.getId(), app);
		}
		return new FutureResult<Map<Allocatable,Collection<Appointment>>>() {

            @Override
            public Map<Allocatable, Collection<Appointment>> get() throws Exception {
                checkConnected();
                BindingMap bindingMap = serv.getFirstAllocatableBindings(allocatableIds, appointmentList, reservationIds).get();
                Map<String, List<String>> resultMap = bindingMap.get();
                return convert(resultMap);
            }

            @Override
            public void get(final AsyncCallback<Map<Allocatable, Collection<Appointment>>> callback) {
                serv.getFirstAllocatableBindings(allocatableIds, appointmentList, reservationIds).get( new AsyncCallback<BindingMap>()
                        {

                            @Override
                            public void onFailure(Throwable caught) {
                                callback.onFailure(caught);
                            }

                            @Override
                            public void onSuccess(BindingMap result) {
                                Map<String, List<String>> resultMap = result.get();
                                HashMap<Allocatable, Collection<Appointment>> convert = convert(resultMap);
                                callback.onSuccess( convert);
                            }
                    
                        }
                );
            }
            
            private HashMap<Allocatable, Collection<Appointment>> convert(Map<String, List<String>> resultMap)
            {
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
        };
		
	}

	@Override
	public FutureResult<Map<Allocatable, Map<Appointment, Collection<Appointment>>>> getAllAllocatableBindings( final Collection<Allocatable> allocatables,	final Collection<Appointment> appointments, final Collection<Reservation> ignoreList)  {
		final RemoteStorage serv = getRemoteStorage();
    	final String[] allocatableIds = getIdList(allocatables);
		final List<AppointmentImpl> appointmentArray = Arrays.asList(appointments.toArray( new AppointmentImpl[]{}));
		final String[] reservationIds = getIdList(ignoreList);
		
		return new FutureResult<Map<Allocatable,Map<Appointment,Collection<Appointment>>>>() {

            @Override
            public Map<Allocatable, Map<Appointment, Collection<Appointment>>> get() throws Exception {
                List<ReservationImpl> serverResult = serv.getAllAllocatableBindings(allocatableIds, appointmentArray, reservationIds).get();
                return getMap(allocatables, appointments, ignoreList, serverResult);
            }

            @Override
            public void get(final AsyncCallback<Map<Allocatable, Map<Appointment, Collection<Appointment>>>> callback) {
                serv.getAllAllocatableBindings(allocatableIds, appointmentArray, reservationIds).get(new AsyncCallback<List<ReservationImpl>>() {

                    @Override
                    public void onFailure(Throwable caught) {
                        callback.onFailure(caught);
                    }

                    @Override
                    public void onSuccess(List<ReservationImpl> serverResult) {
                        Map<Allocatable, Map<Appointment, Collection<Appointment>>> map;
                        try {
                            map = getMap(allocatables, appointments, ignoreList, serverResult);
                        } catch (Exception e) {
                            callback.onFailure(e);
                            return;
                        }
                        callback.onSuccess( map);
                    }
                }
                );
                
            }
        };
		
	}

    private Map<Allocatable, Map<Appointment, Collection<Appointment>>> getMap(Collection<Allocatable> allocatables, Collection<Appointment> appointments,
            Collection<Reservation> ignoreList, List<ReservationImpl> serverResult) throws RaplaException {
        testResolve( serverResult);
	    setResolver( serverResult );
        SortedSet<Appointment> allAppointments = new TreeSet<Appointment>(new AppointmentStartComparator());
        for ( ReservationImpl reservation: serverResult)
        {
        	allAppointments.addAll(reservation.getAppointmentList());
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
	public FutureResult<Date> getNextAllocatableDate(Collection<Allocatable> allocatables,Appointment appointment, Collection<Reservation> ignoreList, Integer worktimeStartMinutes,Integer worktimeEndMinutes, Integer[] excludedDays, Integer rowsPerHour) 
	{
		RemoteStorage serv = getRemoteStorage();
    	String[] allocatableIds = getIdList(allocatables);
		String[] reservationIds = getIdList(ignoreList);
		FutureResult<Date> nextAllocatableDate = serv.getNextAllocatableDate(allocatableIds, (AppointmentImpl)appointment, reservationIds, worktimeStartMinutes, worktimeEndMinutes, excludedDays, rowsPerHour);
		return nextAllocatableDate;
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
	    	List<ConflictImpl> list = serv.getConflicts().get();
	    	testResolve( list);
	    	setResolver( list);
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

