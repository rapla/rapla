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

import org.jetbrains.annotations.NotNull;
import org.rapla.ConnectInfo;
import org.rapla.RaplaResources;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.*;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.ReferenceHandler;
import org.rapla.facade.Conflict;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.ModificationEventImpl;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.storage.PreferencePatch;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.StorageUpdateListener;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.dbrm.RemoteStorage.AllocatableBindingsRequest;
import org.rapla.storage.dbrm.RemoteStorage.BindingMap;
import org.rapla.storage.dbrm.RemoteStorage.MergeRequest;
import org.rapla.storage.dbrm.RemoteStorage.NextAllocatableDateRequest;
import org.rapla.storage.dbrm.RemoteStorage.PasswordPost;
import org.rapla.storage.dbrm.RemoteStorage.QueryAppointments;
import org.rapla.storage.impl.AbstractCachableOperator;
import org.rapla.storage.impl.EntityStore;
import org.rapla.storage.impl.RaplaLock;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This operator can be used to modify and access data over the
 * network.  It needs an server-process providing the StorageService
 * (usually this is the default rapla-server).
 */
@DefaultImplementation(of = StorageOperator.class, context = InjectionContext.client)
@DefaultImplementation(of = RestartServer.class, context = InjectionContext.client)
@Singleton
public class RemoteOperator
        extends AbstractCachableOperator implements RestartServer, Disposable {
    final List<StorageUpdateListener> storageUpdateListeners = new Vector<>();

    private boolean bSessionActive = false;
    String userId;
    RemoteAuthentificationService remoteAuthentificationService;
    RemoteStorage remoteStorage;
    protected CommandScheduler commandQueue;

    Date lastSyncedTimeLocal;
    Date lastValidatedTimeServer;
    int timezoneOffset;
    RemoteConnectionInfo connectionInfo;

    @Inject
    public RemoteOperator(Logger logger, RaplaResources i18n, RaplaLocale locale, CommandScheduler scheduler,
                          Map<String, FunctionFactory> functionFactoryMap, RemoteAuthentificationService remoteAuthentificationService, RemoteStorage remoteStorage,
                          RemoteConnectionInfo connectionInfo, Set<PermissionExtension> permissionExtensions, RaplaLock lockManager) {
        super(logger.getChildLogger("remote"), i18n, locale, functionFactoryMap, permissionExtensions, lockManager);
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

    public RemoteConnectionInfo getRemoteConnectionInfo() {
        return connectionInfo;
    }

    User user;
    int intervalLength;

    public CommandScheduler getScheduler() {
        return commandQueue;
    }

    synchronized public User connect(ConnectInfo connectInfo) throws RaplaException {

        if (isConnected())
            throw new RaplaException("Already connected");
        getLogger().info("Connecting to server and starting login..");
        if (connectInfo != null) {
            try {
                String connectAs = connectInfo.getConnectAs();
                String password = new String(connectInfo.getPassword());
                String username = connectInfo.getUsername();
                RemoteAuthentificationService serv1 = getRemoteAuthentificationService();
                LoginTokens loginToken = serv1.login(username, password, connectAs);
                String accessToken = loginToken.getAccessToken();
                if (accessToken != null) {
                    connectionInfo.setAccessToken(accessToken);
                } else {
                    throw new RaplaSecurityException("Invalid Access token");
                }
            } catch (RaplaException ex) {
                disconnect();
                throw ex;
            } catch (Exception ex) {
                disconnect();
                throw new RaplaException(ex);
            }
            getLogger().info("login successfull");
            connectionInfo.setReconnectInfo(connectInfo);
        }
        user = loadData();
        return user;
    }

    public User getUser() {
        return user;
    }

    public Promise<User> connectAsync() {
        RemoteStorage serv = getRemoteStorage();
        Promise<User> userPromise = serv.getResources().thenApply((evt) -> {
            RaplaLock.WriteLock writeLock = lockManager.writeLock(getClass() ,"connectAsync", 10);
            try {
                user = loadData(evt);
                return user;
            } finally {
                lockManager.unlock(writeLock);
            }
        });
        return userPromise;
    }

    public Date getCurrentTimestamp() {
        if (lastValidatedTimeServer == null) {
            return new Date(System.currentTimeMillis());
        }
        // no matter what the client clock says we always sync to the server clock
        long passedMillis = System.currentTimeMillis() - lastSyncedTimeLocal.getTime();
        if (passedMillis < 0) {
            passedMillis = 0;
        }
        long correctTime = this.lastValidatedTimeServer.getTime() + passedMillis;
        Date date = new Date(correctTime);
        return date;
    }

    public Date today() {
        long time = getCurrentTimestamp().getTime();
        Date raplaTime = new Date(time + timezoneOffset);
        return DateTools.cutDate(raplaTime);
    }



    public void dispose() {
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
    public boolean isLoaded() {
        return isConnected();
    }

    @Override
    public boolean supportsActiveMonitoring() {
        return true;
    }

    @Override
    synchronized public void refresh() throws RaplaException {
        String clientRepoVersion = getLastValidatedTimeServer();
        RemoteStorage serv = getRemoteStorage();
        try {
            UpdateEvent evt = serv.refreshSync(clientRepoVersion);
            refresh(evt);
        } catch (EntityNotFoundException ex) {
            getLogger().error("Refreshing all resources due to " + ex.getMessage(), ex);
            refreshAll();
        } catch (RaplaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RaplaException(ex);
        }
    }

    boolean refreshInProgress;

    public void triggerRefresh()
    {
        if (refreshInProgress) {
            return;
        }
        // if not we skip until the next update cycle
        if (lockManager.isWriteLocked() && !isConnected()) {
            return;
        }
        refreshAsync();
    }
    @Override
     public Promise<Void> refreshAsync() {

        String clientRepoVersion = getLastValidatedTimeServer();
        RemoteStorage serv = getRemoteStorage();
        refreshInProgress = true;
        final Promise<UpdateEvent> updateEventPromise = serv.refresh(clientRepoVersion);
        final Promise<Void> returnPromise = updateEventPromise.thenAccept((evt) -> {
            try {
                refresh(evt);
            } catch (EntityNotFoundException ex) {
                getLogger().error("Refreshing all resources due to " + ex.getMessage(), ex);
                refreshAll();
            }
        }).finally_(() -> refreshInProgress = false);
        return returnPromise;
    }

    private String getLastValidatedTimeServer() {
        return SerializableDateTimeFormat.INSTANCE.formatTimestamp(lastValidatedTimeServer);
    }

    /**
     * returns if the Facade is connected through a server (false if it has a local store)
     */
    synchronized public boolean isRestartPossible() {
        final boolean isStandalone = connectionInfo.getServerURL().startsWith("file://");
        return !isStandalone;
    }

    synchronized public Promise<Void> restartServer()  {
        getLogger().info("Restart in progress ...");
        String message = i18n.getString("restart_server");
        return getRemoteStorage().restartServer().thenRun(()->fireStorageDisconnected(message));
    }

    @Override
    synchronized public void disconnect() throws RaplaException {
        connectionInfo.setAccessToken(null);
        connectionInfo.setReconnectInfo(null);
        disconnect("Disconnection from Server initiated");
    }

    /**
     * disconnect from the server
     */
    synchronized public void disconnect(String message) throws RaplaException {
        boolean wasConnected = bSessionActive;
        getLogger().info("Disconnecting from server");
        try {
            bSessionActive = false;
            cache.clearAll();
        } catch (Exception e) {
            throw new RaplaException("Could not disconnect", e);
        }
        if (wasConnected) {
            RemoteAuthentificationService serv1 = getRemoteAuthentificationService();
            try {
                serv1.logout();
            } catch (RaplaConnectException ex) {
                getLogger().warn(ex.getMessage());
            } catch (RaplaException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new RaplaException(ex);
            }
            fireStorageDisconnected(message);
        }
    }

    // problem of resolving the bootstrap loading of unreadable resources before resource type is referencable
    protected void testResolveInitial(Collection<? extends Entity> entities) throws EntityNotFoundException {
        EntityStore store = new EntityStore(this);
//        {
//            protected <T extends Entity> T tryResolveParent(String id, Class<T> entityClass) {
//                T tryResolve = super.tryResolveParent(id, entityClass);
//                if (tryResolve != null) {
//                    return tryResolve;
//                } else {
//                    return tryResolveMissingAllocatable(this, id, entityClass);
//                }
//            }
//        };
        store.addAll(entities);
        for (Entity entity : entities) {
            if (entity instanceof DynamicType) {
                ((DynamicTypeImpl) entity).setOperator(this);
            }
            if (entity instanceof EntityReferencer) {
                ((EntityReferencer) entity).setResolver(store);
            }

        }
        for (Entity entity : entities) {
            if (entity instanceof EntityReferencer) {
                testResolve(store, (EntityReferencer) entity);
            }
        }
    }

    @Override
    protected boolean isEntityNotFoundWarningFor(ReferenceInfo reference) {
        Class<? extends Entity> class1 = reference.getType();
        String id = reference.getId();
        // We ignore user not found expecptions if its not the current useradmin
        return class1 != User.class || (userId == null || userId.equals(id));
    }


    private User loadData() throws RaplaException {
        RemoteStorage serv = getRemoteStorage();
        try {
            getLogger().debug("Loading Data from server");
            UpdateEvent evt = serv.getResourcesSync();
            getLogger().debug("Data loaded");
            return loadData(evt);
        } catch (RaplaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RaplaException(ex);
        }
    }

    private User loadData(UpdateEvent evt) throws RaplaException {
        Date lastUpdated = evt.getLastValidated();
        setLastRefreshed(lastUpdated);
        {
            this.userId = evt.getUserId();
            if (userId != null) {
                cache.setClientUserId(userId);
            }
            if (userId == null) {
                throw new EntityNotFoundException("Userid not passed in result");
            }
            updateTimestamps(evt);
            Collection<Entity> storeObjects = evt.getStoreObjects();
            cache.clearAll();
            for (Entity entity: storeObjects) {
                if ( entity.getTypeClass() == DynamicType.class) {
                    if (EntityResolver.isInternalType((DynamicType) entity)) {
                        ((RefEntity) entity).setReadOnly();
                    }
                }
            }
            testResolveInitial(storeObjects);
            setResolver(storeObjects);
            for (Entity entity : storeObjects) {
                cache.put(entity);
            }
            getLogger().debug("Data flushed");
            bSessionActive = true;
            User user = cache.resolve(userId, User.class);
            intervalLength = getPreferences(null,true).getEntryAsInteger(ClientFacade.REFRESH_INTERVAL_ENTRY, ClientFacade.REFRESH_INTERVAL_DEFAULT);
            return user;
        }

    }

    public void updateTimestamps(UpdateEvent evt) throws RaplaException {
        if (evt.getLastValidated() == null) {
            throw new RaplaException("Server sync time is missing");
        }
        lastSyncedTimeLocal = new Date(System.currentTimeMillis());
        lastValidatedTimeServer = evt.getLastValidated();
        timezoneOffset = evt.getTimezoneOffset();
        //long offset = TimeZoneConverterImpl.getOffset( DateTools.getTimeZone(), systemTimeZone, time);

    }

    protected void checkConnected() throws RaplaException {
        if (!bSessionActive) {
            throw new RaplaException("Not logged in or connection closed!");
        }
    }


    protected <T> Promise<T> checkConnect(Supplier<Promise<T>> function)  {
        if (!bSessionActive) {
            return new ResolvedPromise<>(new RaplaException("Not logged in or connection closed!"));
        }
        return function.get();
    }

    public Collection<Allocatable> getAllocatables(ClassificationFilter[] filters) throws RaplaException {
        return getAllocatables(filters, -1);
    }

    @Override
    public <T extends Entity, S extends Entity> Promise<Void> storeAndRemoveAsync(Collection<T> storeObjects, Collection<ReferenceInfo<S>> removeObjects, User user, boolean forceRessourceDelete) {
        final UpdateEvent evt;
        try {
            evt = createUpdateEvent(storeObjects, removeObjects, user);
            evt.setForceAllocatableDeletesIgnoreDependencies( forceRessourceDelete );
            logEvent(evt);
        } catch (RaplaException ex)
        {
            return new ResolvedPromise<>(ex);
        }
        evt.setLastValidated(lastValidatedTimeServer);

        RemoteStorage serv = getRemoteStorage();
        return serv.dispatch(evt).thenAccept((serverEvent)->refresh(serverEvent));
    }

    @Override
    public void dispatch(UpdateEvent evt) throws RaplaException {
        checkConnected();
        logEvent(evt);
        RemoteStorage serv = getRemoteStorage();
        evt.setLastValidated(lastValidatedTimeServer);
        try {
            UpdateEvent serverClosure = serv.store(evt);
            refresh(serverClosure);
        } catch (RaplaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RaplaException(ex);
        }
    }

    private void logEvent(UpdateEvent evt) throws RaplaException {
        // Store on server
        if (getLogger().isDebugEnabled()) {
            for (Entity entity : evt.getStoreObjects()) {
                getLogger().debug("dispatching store for: " + entity);
            }
            for (ReferenceInfo id : evt.getRemoveIds()) {
                getLogger().debug("dispatching remove for: " + id);
            }
            //            Iterator<Entity> it =evt.getRemoveObjects().iterator();
            //            while (it.hasNext()) {
            //                Entity entity = it.next();
            //                getLogger().debug("dispatching remove for: " + entity);
            //            }
        }
    }

    public <T extends Entity> List<ReferenceInfo<T>> createIdentifier(final Class<T> raplaType, int count) throws RaplaException {
        try {
            String localname = RaplaType.getLocalName(raplaType);
            List<String> ids = getRemoteStorage().createIdentifierSync(localname, count);
            return createReferenceInfos(raplaType, ids);
        } catch (RaplaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RaplaException(ex);
        }
    }

    @Override
    public <T extends Entity> Promise<List<ReferenceInfo<T>>> createIdentifierAsync(Class<T> raplaType, int count)
    {
        String localname = RaplaType.getLocalName(raplaType);
        return getRemoteStorage().createIdentifier(localname, count).thenApply( (ids)->createReferenceInfos(raplaType, ids));
    }

    private RemoteStorage getRemoteStorage() {
        return remoteStorage;
    }

    private RemoteAuthentificationService getRemoteAuthentificationService() {
        return remoteAuthentificationService;
    }

    public boolean canChangePassword() throws RaplaException {
        RemoteStorage remoteMethod = getRemoteStorage();
        try {
            boolean canChangePassword = remoteMethod.canChangePassword();
            return canChangePassword;
        } catch (RaplaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RaplaException(ex);
        }
    }

    @Override
    public String getUsername(ReferenceInfo<User> userId) throws RaplaException {
        User user = tryResolve(userId);
        if (user == null) {
            RemoteStorage remoteMethod = getRemoteStorage();
            try {
                final String username = remoteMethod.getUsername(userId.getId());
                return username;
            } catch (Exception e) {
                throw new RaplaException(e.getMessage(), e);
            }

        }
        Locale locale = raplaLocale.getLocale();
        String name = user.getName(locale);
        return name;
    }

    @Override
    public void changePassword(User user, char[] oldPassword, char[] newPassword) throws RaplaException {
        try {
            RemoteStorage remoteMethod = getRemoteStorage();
            String username = user.getUsername();
            remoteMethod.changePassword(new PasswordPost(username, new String(oldPassword), new String(newPassword)));
            refresh();
        } catch (RaplaSecurityException ex) {
            if ( ex.getMessage().contains("authentication store plugin")) {
                throw  ex;
            }
            throw new RaplaSecurityException(i18n.getString("error.wrong_password"));
        } catch (RaplaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RaplaException(ex);
        }
    }

    @Override
    public void changeEmail(User user, String newEmail) throws RaplaException {
        try {
            RemoteStorage remoteMethod = getRemoteStorage();
            String username = user.getUsername();
            remoteMethod.changeEmail(username, newEmail);
            refresh();
        } catch (RaplaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RaplaException(ex);
        }
    }

    @Override
    public void confirmEmail(User user, String newEmail) throws RaplaException {
        try {
            RemoteStorage remoteMethod = getRemoteStorage();
            String username = user.getUsername();
            remoteMethod.confirmEmail(username, newEmail);
        } catch (RaplaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RaplaException(ex);
        }
    }

    @Override
    public void changeName(User user, String newTitle, String newFirstname, String newSurname) throws RaplaException {
        try {
            RemoteStorage remoteMethod = getRemoteStorage();
            String username = user.getUsername();
            remoteMethod.changeName(username, newTitle, newFirstname, newSurname);
            refresh();
        } catch (RaplaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RaplaException(ex);
        }

    }

    @Override
    public <T extends Entity> Promise<Map<ReferenceInfo<T>, T>> getFromIdAsync(Collection<ReferenceInfo<T>> idSet,final boolean throwEntityNotFound) {
        if ( idSet.isEmpty())
        {
            return new ResolvedPromise<>(Collections.emptyMap());
        }
        UpdateEvent.SerializableReferenceInfo[] array = createReferenceInfos(idSet);
        return getRemoteStorage().getEntityDependencies(throwEntityNotFound,array).thenApply(entityList -> resolveLocal(entityList, idSet));
    }

    @Override
    public <T extends Entity> Map<ReferenceInfo<T>, T> getFromId(Collection<ReferenceInfo<T>> idSet, boolean throwEntityNotFound)
            throws RaplaException {
        if ( idSet.isEmpty())
        {
            return Collections.emptyMap();
        }
        UpdateEvent.SerializableReferenceInfo[] array = createReferenceInfos(idSet);
        UpdateEvent entityList;
        try {
            RemoteStorage serv = getRemoteStorage();
            entityList = serv.getEntityRecursive(throwEntityNotFound,array);
            return resolveLocal(entityList, idSet);
        } catch (RaplaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RaplaException(ex);
        }
    }

    @NotNull
    private <T extends Entity> UpdateEvent.SerializableReferenceInfo[] createReferenceInfos(Collection<ReferenceInfo<T>> idSet) {
        UpdateEvent.SerializableReferenceInfo[] array = new UpdateEvent.SerializableReferenceInfo[idSet.size()];
        int i = 0;
        for (ReferenceInfo<T> ref : idSet) {
            final UpdateEvent.SerializableReferenceInfo serializableReferenceInfo = new UpdateEvent.SerializableReferenceInfo(ref);
            array[i++] = serializableReferenceInfo;
        }
        return array;
    }

    @NotNull
    private <T extends Entity> List<ReferenceInfo<T>> createReferenceInfos(Class<T> raplaType, List<String> ids)
    {
        return ids.stream().map(( id)->new ReferenceInfo<T>(id, raplaType)).collect(Collectors.toList());
    }


    @NotNull
    private <T extends Entity> Map<ReferenceInfo<T>, T> resolveLocal(UpdateEvent entityList, Collection<ReferenceInfo<T>> idSet) throws RaplaException {
        Map<ReferenceInfo<T>, T> result = new HashMap();
        Collection<Entity> list = entityList.getStoreObjects();
        testResolve(list);
        setResolver(list);
        for (Entity entity : list) {
            ReferenceInfo<T> ref = entity.getReference();
            if (idSet.contains(ref)) {
                result.put(ref, (T) entity);
            }
        }
        return result;
    }

    @Override
    public Promise<AppointmentMapping> queryAppointments(User user, Collection<Allocatable> allocatables, Collection<User> owners, Date start, Date end,
                                                             final ClassificationFilter[] filters, Map<String, String> annotationQuery) {
        final RemoteStorage serv = getRemoteStorage();
        Promise<AppointmentMapping> result = refreshIfIdle().thenCompose((refreshed) -> {String[] allocatableId = getIdList(allocatables);
            String[] ownerIds = getIdList( owners);
            return serv.queryAppointments(new QueryAppointments(ownerIds,allocatableId, start, end, annotationQuery)).thenApply(list -> {
                AppointmentMapping filtered;
                {
                    long time = System.currentTimeMillis();
                    logger.debug("event server call took  " + (System.currentTimeMillis() - time) + " ms");
                }
                {
                    long time = System.currentTimeMillis();
                    filtered = processReservationResult(list, filters);
                    logger.debug("event post processing took  " + (System.currentTimeMillis() - time) + " ms");
                }

                return filtered;
            });
        });
        return result;
    }

    protected Promise<Promise<Boolean>> refreshIfIdle() {
        return getScheduler().supply(() -> {
            // if a refresh is due, we assume the system went to sleep so we refresh before we continue
            if (intervalLength > 0 && lastValidatedTimeServer != null && (lastValidatedTimeServer.getTime() + intervalLength * 2L) < getCurrentTimestamp().getTime()) {
                getLogger().info("cache not uptodate. Refreshing first.");
                return refreshAsync().thenApply((dummy)->true);
            } else {
                return new ResolvedPromise<>(false);
            }
        });
    }

    private AppointmentMapping processReservationResult(AppointmentMap appointmentMap, ClassificationFilter[] filters) {
        final RemoteOperator resolver = this;
        appointmentMap.init(resolver);
        return appointmentMap.getResult(filters);
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
        List<String> idList = new ArrayList<>();
        if (entities != null) {
            for (Entity entity : entities) {
                if (entity != null)
                    idList.add(entity.getId());
            }
        }
        String[] ids = idList.toArray(new String[]{});
        return ids;
    }

    synchronized private void refresh(UpdateEvent evt) throws RaplaException {

        updateTimestamps(evt);
        if (evt.isNeedResourcesRefresh()) {
            refreshAll();
            return;
        }
        UpdateResult result = null;

        // we don't test the references of the removed objects
        //setResolver(evt.getRemoveObjects());
        Date since = getLastRefreshed();
        Date until = evt.getLastValidated();
        if (bSessionActive && !evt.isEmpty()) {
            getLogger().debug("Objects updated!");
            // TODO User informieren, dass sich daten evtl geaendert haben
            final Collection<Entity> storeObjects = evt.getStoreObjects();
            Collection<ReferenceInfo> removedIds = evt.getRemoveIds();
            if (!(storeObjects.isEmpty() && removedIds.isEmpty())) {
                testResolve(storeObjects);
                setResolver(storeObjects);
                RaplaLock.WriteLock writeLock = writeLockIfLoaded("refresh " + evt.getInfoString());
                try {
                    Collection<PreferencePatch> preferencePatches = evt.getPreferencePatches();
                    result = update(since, until, storeObjects, preferencePatches, removedIds);
                } finally {
                    lockManager.unlock(writeLock);
                }
            } else {
                result = createUpdateResult(Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), since, until);
            }
        }
        if (result != null) {
            fireStorageUpdated(result, evt.getInvalidateInterval());
        }
    }

    protected void refreshAll() throws RaplaException {
        UpdateResult result;
        Collection<Entity> oldEntities;
        RaplaLock.ReadLock readLock = lockManager.readLock(getClass(),"refreshAll");
        try {
            User user = cache.resolve(userId, User.class);
            oldEntities = new HashSet(cache.getVisibleEntities(user));
        } finally {
            lockManager.unlock(readLock);
        }
        RemoteStorage serv = getRemoteStorage();
        UpdateEvent evt;
        try {
            getLogger().info("Reloading all Data from Server triggered");
            evt = serv.getResourcesSync();
            getLogger().debug("Data loaded");
        } catch (RaplaException ex)
        {

        }
        RaplaLock.WriteLock writeLock = writeLockIfLoaded("Refresh all");
        try {
            loadData();
        } finally {
            lockManager.unlock(writeLock);
        }

        Collection<Entity> newEntities;
        readLock = lockManager.readLock(getClass(),"refreshAll");
        try {
            User user = cache.resolve(userId, User.class);
            newEntities = new HashSet<>(cache.getVisibleEntities(user));
        } finally {
            lockManager.unlock(readLock);
        }
        HashSet<Entity> updated = new HashSet<>(newEntities);
        Set<Entity> toRemove = new HashSet<>(oldEntities);
        Set<Entity> toUpdate = new HashSet<>(oldEntities);
        toRemove.removeAll(newEntities);
        updated.removeAll(toRemove);
        toUpdate.retainAll(newEntities);

        HashMap<ReferenceInfo, Entity> oldEntityMap = new HashMap<>();
        for (Entity oldEntity : toUpdate) {
            @SuppressWarnings("unchecked") Class<? extends Entity> typeClass = oldEntity.getTypeClass();
            Entity newEntity = cache.tryResolve(oldEntity.getId(), typeClass);
            if (newEntity != null) {
                oldEntityMap.put(newEntity.getReference(), oldEntity);
            }
        }
        Collection<ReferenceInfo> removeInfo = new ArrayList<>();
        for (Entity entity : toRemove) {
            removeInfo.add(new ReferenceInfo(entity.getId(), entity.getTypeClass()));
        }
        Date since = null;
        Date until = getLastRefreshed();
        result = createUpdateResult(oldEntityMap, updated, removeInfo, since, until);
        TimeInterval invalidateInterval = new TimeInterval(null, null);
        fireStorageUpdated(result, invalidateInterval);
    }

    public synchronized void addStorageUpdateListener(StorageUpdateListener listener) {
        storageUpdateListeners.add(listener);
    }

    public synchronized void removeStorageUpdateListener(StorageUpdateListener listener) {
        storageUpdateListeners.remove(listener);
    }

    public synchronized StorageUpdateListener[] getStorageUpdateListeners() {
        return storageUpdateListeners.toArray(new StorageUpdateListener[]{});
    }

    protected void fireStorageUpdated(final UpdateResult updateResult, TimeInterval timeInterval) {
        StorageUpdateListener[] listeners = getStorageUpdateListeners();
        final ModificationEventImpl evt = new ModificationEventImpl(updateResult, timeInterval);
        if (evt.isEmpty()) {
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
    public Promise<Map<ReferenceInfo<Allocatable>, Collection<Appointment>>> getFirstAllocatableBindings(final Collection<Allocatable> allocatables,
                                                                                          Collection<Appointment> appointments, Collection<Reservation> ignoreList) {
        final RemoteStorage serv = getRemoteStorage();
        final String[] allocatableIds = getIdList(allocatables);
        //AppointmentImpl[] appointmentArray = appointments.toArray( new AppointmentImpl[appointments.size()]);
        final String[] reservationIds = getIdList(ignoreList);
        final List<AppointmentImpl> appointmentList = new ArrayList<>();
        final Map<String, Appointment> appointmentMap = new HashMap<>();
        for (Appointment app : appointments) {
            appointmentList.add((AppointmentImpl) app);
            appointmentMap.put(app.getId(), app);
        }
        final Promise<BindingMap> bindingMapPromise = serv.getFirstAllocatableBindings(new AllocatableBindingsRequest(allocatableIds, appointmentList, reservationIds));

        Promise<Map<ReferenceInfo<Allocatable>, Collection<Appointment>>> resultPromise = bindingMapPromise.thenApply((bindingMap) -> {
            Map<String, List<String>> resultMap = bindingMap.get();
            HashMap<ReferenceInfo<Allocatable>, Collection<Appointment>> result = new HashMap<>();
            for (Allocatable alloc : allocatables) {
                List<String> list = resultMap.get(alloc.getId());
                if (list != null) {
                    Collection<Appointment> appointmentBinding = new ArrayList<>();
                    for (String id : list) {
                        Appointment e = appointmentMap.get(id);
                        if (e != null) {
                            appointmentBinding.add(e);
                        }
                    }
                    result.put(alloc.getReference(), appointmentBinding);
                }
            }
            return result;
        });
        return resultPromise;
    }

    @Override
    public Promise<Map<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>>> getAllAllocatableBindings(final Collection<Allocatable> allocatables,
                                                                                                          final Collection<Appointment> appointments, final Collection<Reservation> ignoreList) {
        final RemoteStorage serv = getRemoteStorage();
        final String[] allocatableIds = getIdList(allocatables);
        final List<AppointmentImpl> appointmentArray = Arrays.asList(appointments.toArray(new AppointmentImpl[]{}));
        final String[] reservationIds = getIdList(ignoreList);
        final Promise<List<ReservationImpl>> listPromise = serv.getAllAllocatableBindings(new AllocatableBindingsRequest(allocatableIds, appointmentArray, reservationIds));
        return listPromise.thenApply((serverResult) -> getMap(allocatables, appointments, ignoreList, serverResult));
    }

    private Map<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> getMap(Collection<Allocatable> allocatables, Collection<Appointment> appointments,
                                                                               Collection<Reservation> ignoreList, List<ReservationImpl> serverResult) throws RaplaException {
        testResolve(serverResult);
        setResolver(serverResult);
        SortedSet<Appointment> allAppointments = new TreeSet<>(new AppointmentStartComparator());
        for (ReservationImpl reservation : serverResult) {
            allAppointments.addAll(reservation.getAppointmentList());
        }

        Map<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> result = new HashMap<>();
        for (Allocatable alloc : allocatables) {
            final Set<ReferenceInfo<Allocatable>> dependent = cache.getDependent(Collections.singleton(alloc));
            Map<Appointment, Collection<Appointment>> appointmentBinding = new HashMap<>();
            for (Appointment appointment : appointments) {
                final boolean onlyFirstConflictingAppointment = false;
                Set<Appointment> allConflictingAppointments = new LinkedHashSet<>();
                for (ReferenceInfo<Allocatable> dependentAlloc : dependent) {
                    SortedSet<Appointment> appointmentSet = getAppointments(dependentAlloc, allAppointments);
                    Set<Appointment> conflictingAppointments = AppointmentImpl
                            .getConflictingAppointments(appointmentSet, appointment, ignoreList, onlyFirstConflictingAppointment);
                    allConflictingAppointments.addAll(conflictingAppointments);

                }
                appointmentBinding.put(appointment, allConflictingAppointments);
            }
            result.put(alloc.getReference(), appointmentBinding);
        }
        return result;
    }

    @Override
    public Promise<Date> getNextAllocatableDate(Collection<Allocatable> allocatables, Appointment appointment, Collection<Reservation> ignoreList,
                                                Integer worktimeStartMinutes, Integer worktimeEndMinutes, Integer[] excludedDays, Integer rowsPerHour) {
        RemoteStorage serv = getRemoteStorage();
        String[] allocatableIds = getIdList(allocatables);
        String[] reservationIds = getIdList(ignoreList);
        Promise<Date> nextAllocatableDate = serv.getNextAllocatableDate(
                new NextAllocatableDateRequest(allocatableIds, (AppointmentImpl) appointment, reservationIds, worktimeStartMinutes, worktimeEndMinutes,
                        excludedDays, rowsPerHour));
        return nextAllocatableDate;
    }

    static private SortedSet<Appointment> getAppointments(ReferenceInfo<Allocatable> allocRef, SortedSet<Appointment> allAppointments) {
        SortedSet<Appointment> result = new TreeSet<>(new AppointmentStartComparator());
        for (Appointment appointment : allAppointments) {
            ReservationImpl reservation = (ReservationImpl)appointment.getReservation();
            if (reservation.hasAllocatedOnRef(allocRef, appointment)) {
                result.add(appointment);
            }
        }
        return result;
    }

    @Override
    public Promise<Collection<Conflict>> getConflicts(User user) {
        RemoteStorage serv = getRemoteStorage();
        return serv.getConflicts().thenApply( list->
        {
            testResolve(list);
            setResolver(list);
            List<Conflict> result = new ArrayList<>();
            Iterator it = list.iterator();
            while (it.hasNext()) {
                Object object = it.next();
                if (object instanceof Conflict) {
                    Conflict next = (Conflict) object;
                    result.add(next);
                }
            }
            return result;
        });
    }

    @Override
    public Promise<Allocatable> doMerge(Allocatable selectedObject, Set<ReferenceInfo<Allocatable>> allocatableIds, User user) {
        String lastSyncedTime = getLastValidatedTimeServer();
        List<String> allocIds = new ArrayList<>(allocatableIds.size());
        for (ReferenceInfo<Allocatable> allocId : allocatableIds) {
            allocIds.add(allocId.getId());
        }
        RemoteStorage serv = getRemoteStorage();
        final MergeRequest job = new MergeRequest((AllocatableImpl) selectedObject, allocIds.toArray(new String[allocatableIds.size()]));
        return serv.doMerge(job, lastSyncedTime).thenApply( (updateEvent)->
                {
                    refresh(updateEvent);
                    return resolve(selectedObject.getReference());
                }
        );
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

