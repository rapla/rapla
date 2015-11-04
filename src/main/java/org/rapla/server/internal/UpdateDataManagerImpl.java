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

import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.*;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.*;
import org.rapla.entities.domain.PermissionContainer.Util;
import org.rapla.entities.domain.permission.PermissionController;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.storage.EntityReferencer.ReferenceInfo;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.*;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.StorageUpdateListener;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.UpdateResult.Change;
import org.rapla.storage.UpdateResult.Remove;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Provides an adapter for each client-session to their shared storage operator
 * Handles security and synchronizing aspects.
 */
@DefaultImplementation(of=UpdateDataManager.class, context = InjectionContext.server)
@Singleton
public class UpdateDataManagerImpl implements StorageUpdateListener, Disposable, UpdateDataManager
{
    private CachableStorageOperator operator;

    private SecurityManager security;

    private int cleanupPointVersion = 0;

    private Logger logger;
    private ClientFacade facade;

    private final PermissionController permissionController;

    @Inject public UpdateDataManagerImpl(Logger logger, ClientFacade facade, CachableStorageOperator operator, SecurityManager securityManager,
             PermissionController permissionController) throws RaplaException
    {
        this.logger = logger;
        this.facade = facade;
        this.operator = operator;
        this.permissionController = permissionController;
        this.operator.addStorageUpdateListener(this);
        this.security = securityManager;

        Long repositoryVersion = operator.getCurrentTimestamp().getTime();
        // Invalidate all clients
        for (User user : operator.getUsers())
        {
            String userId = user.getId();
            needResourceRefresh.put(userId, repositoryVersion);
            needConflictRefresh.put(userId, repositoryVersion);
        }

        synchronized (invalidateMap)
        {
            invalidateMap.put(repositoryVersion, new TimeInterval(null, null));
        }
    }

    public Logger getLogger()
    {
        return logger;
    }

    static Preferences removeServerOnlyPreferences(Preferences preferences)
    {
        Preferences clone = preferences.clone();
        {
            //removeOldPluginConfigs(preferences, clone);
            for (String role : ((PreferencesImpl) preferences).getPreferenceEntries())
            {
                if (role.contains(".server."))
                {
                    clone.removeEntry(role);
                }
            }
        }
        return clone;
    }

    static UpdateEvent createTransactionSafeUpdateEvent(UpdateResult updateResult)
    {
        User user = updateResult.getUser();
        UpdateEvent saveEvent = new UpdateEvent();
        if (user != null)
        {
            saveEvent.setUserId(user.getId());
        }
        {
            for (UpdateResult.Add add : updateResult.getOperations(UpdateResult.Add.class))
            {
                Entity newEntity = (Entity) (add).getNew();
                saveEvent.putStore(newEntity);
            }
        }
        {
            for (UpdateResult.Change change : updateResult.getOperations(UpdateResult.Change.class))
            {
                Entity newEntity = (Entity) change.getNew();
                saveEvent.putStore(newEntity);
            }
        }
        {
            for (UpdateResult.Remove remove : updateResult.getOperations(UpdateResult.Remove.class))
            {
                Entity removeEntity = (Entity) remove.getCurrent();
                saveEvent.putRemove(removeEntity);
            }
        }
        return saveEvent;
    }

    private Map<String, Long> needConflictRefresh = new ConcurrentHashMap<String, Long>();
    private Map<String, Long> needResourceRefresh = new ConcurrentHashMap<String, Long>();
    private SortedMap<Long, TimeInterval> invalidateMap = Collections.synchronizedSortedMap(new TreeMap<Long, TimeInterval>());

    // Implementation of StorageUpdateListener
    // 
    public void objectsUpdated(UpdateResult evt)
    {
        long repositoryVersion = operator.getCurrentTimestamp().getTime();
        // notify the client for changes
        TimeInterval invalidateInterval = evt.calulateInvalidateInterval();

        if (invalidateInterval != null)
        {
            long oneHourAgo = repositoryVersion - DateTools.MILLISECONDS_PER_HOUR;
            // clear the entries that are older than one hour and replace them with a clear_all
            // that is set one hour in the past, to refresh all clients that have not been connected in the past hour on the next connect
            synchronized (invalidateMap)
            {
                SortedMap<Long, TimeInterval> headMap = invalidateMap.headMap(oneHourAgo);
                if (!headMap.isEmpty())
                {
                    Set<Long> toDelete = new TreeSet<Long>(headMap.keySet());
                    for (Long key : toDelete)
                    {
                        invalidateMap.remove(key);
                    }
                    invalidateMap.put(oneHourAgo, new TimeInterval(null, null));
                }
                invalidateMap.put(repositoryVersion, invalidateInterval);
            }
        }

        UpdateEvent safeResultEvent = createTransactionSafeUpdateEvent(evt);
        if (getLogger().isDebugEnabled())
            getLogger().debug("Storage was modified. Calling notify.");
        boolean addAllUsersToConflictRefresh = false;
        for (Iterator<Entity> it = safeResultEvent.getStoreObjects().iterator(); it.hasNext(); )
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
            if (obj instanceof DynamicType)
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
        Set<Permission> invalidateEventPermissions = new HashSet<Permission>();

        boolean addAllUsersToResourceRefresh = false;
        {
            for (Remove operation : evt.getOperations(UpdateResult.Remove.class))
            {
                Entity obj = operation.getCurrent();
                if (obj instanceof User)
                {
                    String userId = obj.getId();
                    needConflictRefresh.remove(userId);
                    needResourceRefresh.remove(userId);
                    //addAllUsersToResourceRefresh = true;
                }
                if (!isTransferedToClient(obj))
                {
                    continue;
                }
                if (obj instanceof DynamicType)
                {
                    addAllUsersToResourceRefresh = true;
                    addAllUsersToConflictRefresh = true;
                }
            }
        }
        if (addAllUsersToResourceRefresh || addAllUsersToConflictRefresh)
        {
            invalidateAll(repositoryVersion, addAllUsersToResourceRefresh, addAllUsersToConflictRefresh);
        }
        else
        {
            invalidate(evt, repositoryVersion, invalidatePermissions, invalidateEventPermissions);
        }
    }

    public UpdateEvent createUpdateEvent(User user, Date lastSynced) throws RaplaException
    {
        Date currentTimestamp = operator.getCurrentTimestamp();
        if (lastSynced.after(currentTimestamp))
        {
            long diff = lastSynced.getTime() - currentTimestamp.getTime();
            getLogger().warn("Timestamp of client " + diff + " ms  after server ");
            lastSynced = currentTimestamp;
        }
        UpdateEvent safeResultEvent = new UpdateEvent();
        safeResultEvent.setLastValidated(currentTimestamp);
        TimeZone systemTimeZone = operator.getTimeZone();
        int timezoneOffset = TimeZoneConverterImpl.getOffset(DateTools.getTimeZone(), systemTimeZone, currentTimestamp.getTime());
        safeResultEvent.setTimezoneOffset(timezoneOffset);
        //if ( lastSynced.before( currentTimestamp ))
        {
            String userId = user.getId();
            TimeInterval invalidateInterval;
            {
                Long lastVersion = needConflictRefresh.get(userId);
                if (lastVersion != null && lastVersion > lastSynced.getTime())
                {
                    invalidateInterval = new TimeInterval(null, null);
                }
                else
                {
                    invalidateInterval = getInvalidateInterval(lastSynced.getTime());
                }
            }
            boolean resourceRefresh;
            {
                Long lastVersion = needResourceRefresh.get(userId);
                resourceRefresh = (lastVersion != null && lastVersion > lastSynced.getTime());
            }
            safeResultEvent.setNeedResourcesRefresh(resourceRefresh);
            safeResultEvent.setInvalidateInterval(invalidateInterval);
        }
        if (!safeResultEvent.isNeedResourcesRefresh())
        {
            Collection<Entity> updatedEntities = operator.getUpdatedEntities(user, lastSynced);
            for (Entity obj : updatedEntities)
            {
                processClientReadable(user, safeResultEvent, obj, false);
            }
            Collection<ReferenceInfo> removedEntities = operator.getDeletedEntities(user, lastSynced);
            for (ReferenceInfo ref : removedEntities)
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

    protected void processClientReadable(User user, UpdateEvent safeResultEvent, Entity obj, boolean remove)
    {
        if (!UpdateDataManagerImpl.isTransferedToClient(obj))
        {
            return;
        }
        boolean clientStore = true;
        if (user != null)
        {
            // we don't transmit preferences for other users
            if (obj instanceof Preferences)
            {
                Preferences preferences = (Preferences) obj;
                User owner = preferences.getOwner();
                if (owner != null && !owner.equals(user))
                {
                    clientStore = false;
                }
                else
                {
                    obj = removeServerOnlyPreferences(preferences);
                }
            }
            else if (obj instanceof Allocatable)
            {
                Allocatable alloc = (Allocatable) obj;
                if (!permissionController.canReadOnlyInformation(alloc, user))
                {
                    clientStore = false;
                }
            }
            else if (obj instanceof Conflict)
            {
                Conflict conflict = (Conflict) obj;
                if (!permissionController.canModify(conflict, user, operator))
                {
                    clientStore = false;
                }
                else
                {
                    operator.fillConflictDisableInformation(user, conflict);
                }

            }
        }
        if (clientStore)
        {
            if (remove)
            {
                safeResultEvent.putRemove(obj);
            }
            else
            {
                safeResultEvent.putStore(obj);
            }
        }
    }

    private void invalidateAll(long repositoryVersion, boolean resourceRefreh, boolean conflictRefresh)
    {
        Collection<String> allUserIds = new ArrayList<String>();
        try
        {
            Collection<User> allUsers = operator.getUsers();
            for (User user : allUsers)
            {
                String id = user.getId();
                allUserIds.add(id);
            }
        }
        catch (RaplaException ex)
        {
            getLogger().error(ex.getMessage(), ex);
            // we stay with the old list.
            // keySet iterator from concurrent hashmap is thread safe
            Iterator<String> iterator = needResourceRefresh.keySet().iterator();
            while (iterator.hasNext())
            {
                String id = iterator.next();
                allUserIds.add(id);
            }
        }
        for (String userId : allUserIds)
        {
            if (resourceRefreh)
            {
                needResourceRefresh.put(userId, repositoryVersion);
            }
            if (conflictRefresh)
            {
                needConflictRefresh.put(userId, repositoryVersion);
            }
        }
    }

    private void invalidate(UpdateResult evt, long repositoryVersion, Set<Permission> invalidatePermissions, Set<Permission> invalidateEventPermissions)
    {
        Collection<User> allUsers;
        try
        {
            allUsers = operator.getUsers();
        }
        catch (RaplaException e)
        {
            // we need to invalidate all on an exception
            invalidateAll(repositoryVersion, true, true);
            return;
        }
        Set<User> usersResourceRefresh = new HashSet<User>();
        Set<User> usersConflictRefresh = new HashSet<User>();

        for (Change operation : evt.getOperations(UpdateResult.Change.class))
        {
            Entity newObject = operation.getNew();
            // we get all the permissions that have changed on an allocatable
            if (newObject.getRaplaType().is(Allocatable.TYPE) && isTransferedToClient(newObject))
            {
                PermissionContainer current = (PermissionContainer) operation.getOld();
                PermissionContainer newObj = (PermissionContainer) newObject;
                Util.addDifferences(invalidatePermissions, current, newObj);
            }
            // We trigger a resource refresh if the groups of the user have changed
            if (newObject.getRaplaType().is(User.TYPE))
            {
                User newUser = (User) newObject;
                User oldUser = (User) operation.getOld();
                HashSet<Category> newGroups = new HashSet<Category>(newUser.getGroupList());
                HashSet<Category> oldGroups = new HashSet<Category>(oldUser.getGroupList());
                if (!newGroups.equals(oldGroups) || newUser.isAdmin() != oldUser.isAdmin())
                {
                    usersResourceRefresh.add(newUser);
                }

            }
            // We also check if a permission on a reservation has changed, so that it is no longer or new in the conflict list of a certain user.
            // If that is the case we trigger an invalidate of the conflicts for a user
            if (newObject instanceof Ownable)
            {
                Ownable newOwnable = (Ownable) newObject;
                Ownable oldOwnable = (Ownable) operation.getOld();
                User newOwner = newOwnable.getOwner();
                User oldOwner = oldOwnable.getOwner();
                if (newOwner != null && oldOwner != null && (!newOwner.equals(oldOwner)))
                {
                    if (!newObject.getRaplaType().is(Reservation.TYPE))
                    {
                        usersResourceRefresh.add(newOwner);
                        usersResourceRefresh.add(oldOwner);
                    }
                    usersConflictRefresh.add(newOwner);
                    usersConflictRefresh.add(oldOwner);
                }
            }
            if (newObject.getRaplaType().is(Reservation.TYPE))
            {
                PermissionContainer current = (PermissionContainer) operation.getOld();
                PermissionContainer newObj = (PermissionContainer) newObject;
                Util.addDifferences(invalidateEventPermissions, current, newObj);
            }
        }
        if (!invalidatePermissions.isEmpty() || !invalidateEventPermissions.isEmpty())
        {
            Set<Category> groupsResourceRefresh = new HashSet<Category>();
            Set<Category> groupsConflictRefresh = new HashSet<Category>();
            for (Permission permission : invalidatePermissions)
            {
                User user = permission.getUser();
                if (user != null)
                {
                    usersResourceRefresh.add(user);
                }
                Category group = permission.getGroup();
                if (group != null)
                {
                    groupsResourceRefresh.add(group);
                }
                if (user == null && group == null)
                {
                    usersResourceRefresh.addAll(allUsers);
                    break;
                }
            }
            for (Permission permission : invalidateEventPermissions)
            {
                User user = permission.getUser();
                if (user != null)
                {
                    usersConflictRefresh.add(user);
                }
                Category group = permission.getGroup();
                if (group != null)
                {
                    groupsConflictRefresh.add(group);
                }
                if (user == null && group == null)
                {
                    usersConflictRefresh.addAll(allUsers);
                    break;
                }
            }
            // we add all users belonging to group marked for refresh
            for (User user : allUsers)
            {
                if (usersResourceRefresh.contains(user))
                {
                    continue;
                }
                for (Category group : user.getGroupList())
                {
                    if (groupsResourceRefresh.contains(group))
                    {
                        usersResourceRefresh.add(user);
                        break;
                    }
                    if (groupsConflictRefresh.contains(group))
                    {
                        usersConflictRefresh.add(user);
                    }
                }
            }
        }

        for (User user : usersResourceRefresh)
        {
            String userId = user.getId();
            needResourceRefresh.put(userId, repositoryVersion);
            needConflictRefresh.put(userId, repositoryVersion);
        }
        for (User user : usersConflictRefresh)
        {
            String userId = user.getId();
            needConflictRefresh.put(userId, repositoryVersion);
        }
    }

    private TimeInterval getInvalidateInterval(long clientRepositoryVersion)
    {
        TimeInterval interval = null;
        synchronized (invalidateMap)
        {
            for (TimeInterval current : invalidateMap.tailMap(clientRepositoryVersion).values())
            {
                if (current != null)
                {
                    interval = current.union(interval);
                }
            }
            return interval;
        }

    }

    static boolean isTransferedToClient(RaplaObject obj)
    {
        RaplaType<?> raplaType = obj.getRaplaType();
        if (raplaType == Appointment.TYPE || raplaType == Reservation.TYPE)
        {
            return false;
        }
        if (obj instanceof DynamicType)
        {
            if (!DynamicTypeImpl.isTransferedToClient((DynamicType) obj))
            {
                return false;
            }
        }
        if (obj instanceof Classifiable)
        {
            if (!DynamicTypeImpl.isTransferedToClient((Classifiable) obj))
            {
                return false;
            }
        }
        return true;

    }

    @Override public void dispose()
    {
        operator.removeStorageUpdateListener(this);
    }

    public void updateError(RaplaException ex)
    {
    }

    public void storageDisconnected(String disconnectionMessage)
    {
    }

    static public void convertToNewPluginConfig(ClientFacade facade, Logger logger, String className, TypedComponentRole<RaplaConfiguration> newConfKey)
            throws RaplaContextException
    {
        try
        {
            PreferencesImpl clone = (PreferencesImpl) facade.edit(facade.getSystemPreferences());
            RaplaConfiguration entry = clone.getEntry(RaplaComponent.PLUGIN_CONFIG, null);
            if (entry == null)
            {
                return;
            }
            RaplaConfiguration newPluginConfigEntry = entry.clone();
            DefaultConfiguration pluginConfig = (DefaultConfiguration) newPluginConfigEntry.find("class", className);
            // we split the config entry in the plugin config and the new config entry;
            if (pluginConfig != null)
            {
                logger.info("Converting plugin conf " + className + " to preference entry " + newConfKey);
                newPluginConfigEntry.removeChild(pluginConfig);
                boolean enabled = pluginConfig.getAttributeAsBoolean("enabled", false);
                RaplaConfiguration newPluginConfig = new RaplaConfiguration(pluginConfig.getName());
                newPluginConfig.setAttribute("enabled", enabled);
                newPluginConfig.setAttribute("class", className);
                newPluginConfigEntry.addChild(newPluginConfig);

                RaplaConfiguration newConfigEntry = new RaplaConfiguration(pluginConfig);

                newConfigEntry.setAttribute("enabled", null);
                newConfigEntry.setAttribute("class", null);

                clone.putEntry(newConfKey, newConfigEntry);
                clone.putEntry(RaplaComponent.PLUGIN_CONFIG, newPluginConfigEntry);
                facade.store(clone);
            }
        }
        catch (RaplaException ex)
        {
            if (ex instanceof RaplaContextException)
            {
                throw (RaplaContextException) ex;
            }
            throw new RaplaContextException(ex.getMessage(), ex);
        }
    }

}

