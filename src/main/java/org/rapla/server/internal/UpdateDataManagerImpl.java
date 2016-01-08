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
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.storage.PermissionController;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.*;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateOperation;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.UpdateResult.Change;
import org.rapla.storage.UpdateResult.Remove;
import org.rapla.storage.xml.RaplaXMLContextException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

/** Provides an adapter for each client-session to their shared storage operator
 * Handles security and synchronizing aspects.
 */
@DefaultImplementation(of=UpdateDataManager.class, context = InjectionContext.server)
@Singleton
public class UpdateDataManagerImpl implements  Disposable, UpdateDataManager
{
    private CachableStorageOperator operator;

    private SecurityManager security;

    private Logger logger;

    private final PermissionController permissionController;

    @Inject public UpdateDataManagerImpl(Logger logger, CachableStorageOperator operator, SecurityManager securityManager) throws RaplaException
    {
        this.logger = logger;
        this.operator = operator;
        this.permissionController = operator.getPermissionController();
        this.security = securityManager;
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

    static UpdateEvent createTransactionSafeUpdateEvent(UpdateResult updateResult, User user)
    {
        UpdateEvent saveEvent = new UpdateEvent();
        if (user != null)
        {
            saveEvent.setUserId(user.getId());
        }
        {
            for (UpdateResult.Add add : updateResult.getOperations(UpdateResult.Add.class))
            {
                Entity newEntity = (Entity) updateResult.getLastKnown(add.getCurrentId());
                saveEvent.putStore(newEntity);
            }
        }
        {
            for (UpdateResult.Change change : updateResult.getOperations(UpdateResult.Change.class))
            {
                Entity newEntity = (Entity) updateResult.getLastKnown(change.getCurrentId());
                saveEvent.putStore(newEntity);
            }
        }
        {
            for (UpdateResult.Remove remove : updateResult.getOperations(UpdateResult.Remove.class))
            {
                String removeEntity =  remove.getCurrentId();
                saveEvent.putRemoveId(removeEntity);
            }
        }
        return saveEvent;
    }

//    public TimeInterval calulateInvalidateInterval(UpdateResult result) {
//        TimeInterval currentInterval = null;
//        {
//            Collection<Change> operations = result.getOperations(Change.class);
//            for (Change change:operations)
//            {
//                currentInterval = expandInterval( result.getLastKnown(change.getCurrentId()), currentInterval);
//                currentInterval = expandInterval( result.getLastEntryBeforeUpdate(change.getCurrentId()).getUnresolvedEntity(), currentInterval);
//            }
//        }
//        {
//            Collection<UpdateResult.Add> operations = result.getOperations(UpdateResult.Add.class);
//            for (UpdateResult.Add add:operations)
//            {
//                currentInterval = expandInterval( result.getLastKnown(add.getCurrentId()), currentInterval);
//            }
//        }
//
//        {
//            Collection<Remove> operations = result.getOperations(Remove.class);
//            for (Remove remove:operations)
//            {
//                currentInterval = expandInterval( result.getLastKnown(remove.getCurrentId()), currentInterval);
//            }
//        }
//        return currentInterval;
//    }

    private TimeInterval expandInterval(RaplaObject obj,
            TimeInterval currentInterval)
    {
        RaplaType type = obj.getRaplaType();
        if ( type == Reservation.TYPE)
        {
            for ( Appointment app:((ReservationImpl)obj).getAppointmentList())
            {
                Date start = app.getStart();
                Date end = app.getMaxEnd();
                currentInterval = new TimeInterval(start, end).union( currentInterval);
            }
        }
        return currentInterval;
    }

    public UpdateEvent createUpdateEvent(User user, Date lastSynced) throws RaplaException
    {
        Date currentTimestamp = operator.getCurrentTimestamp();
        Date historyValidStart = operator.getHistoryValidStart();
        Date conflictValidStart = operator.getConnectStart();
        if (lastSynced.after(currentTimestamp))
        {
            long diff = lastSynced.getTime() - currentTimestamp.getTime();
            getLogger().warn("Timestamp of client " + diff + " ms  after server ");
            lastSynced = currentTimestamp;
        }
        UpdateEvent safeResultEvent = new UpdateEvent();
        TimeZone systemTimeZone = operator.getTimeZone();
        int timezoneOffset = TimeZoneConverterImpl.getOffset(DateTools.getTimeZone(), systemTimeZone, currentTimestamp.getTime());
        safeResultEvent.setTimezoneOffset(timezoneOffset);
        TimeInterval timeInterval= null;
        final UpdateResult updateResult = operator.getUpdateResult(lastSynced, user);
        // FIMXE If lastSynced is before historyStart then set conflict und resourceRefresh true
        safeResultEvent.setLastValidated(updateResult.getUntil());
        // FIMXE If lastSynced is before serverStart then set conflictRefresh true
        boolean resourceRefresh = lastSynced.before( historyValidStart);
        boolean conflictRefresh = lastSynced.before( conflictValidStart);
        for (Remove op : updateResult.getOperations(Remove.class))
        {
            if ( op.getRaplaType() == DynamicType.TYPE)
            {
                resourceRefresh = true;
                conflictRefresh = true;
            }
        }
        if ( !conflictRefresh)
        {
            for (UpdateOperation op : updateResult.getOperations(Change.class))
            {
                if (op.getRaplaType() == DynamicType.TYPE)
                {
                    conflictRefresh = true;
                }
            }
            for (UpdateOperation op : updateResult.getOperations(UpdateResult.Add.class))
            {
                if (op.getRaplaType() == DynamicType.TYPE)
                {
                    conflictRefresh = true;
                }
            }
        }

        Set<Permission> invalidatePermissions = new HashSet<Permission>();
        Set<Permission> invalidateEventPermissions = new HashSet<Permission>();

        for (Change operation : updateResult.getOperations(UpdateResult.Change.class))
        {
            final String currentId = operation.getCurrentId();
            Entity newObject = updateResult.getLastKnown(currentId);
            // we get all the permissions that have changed on an allocatable
            if (newObject.getRaplaType().is(Allocatable.TYPE) && isTransferedToClient(newObject))
            {
                PermissionContainer current = (PermissionContainer) updateResult.getLastEntryBeforeUpdate(currentId);
                PermissionContainer newObj = (PermissionContainer) newObject;
                Util.addDifferences(invalidatePermissions, current, newObj);
            }
            // We trigger a resource refresh if the groups of the user have changed

            if (newObject.getRaplaType().is(User.TYPE) && newObject.equals( user))
            {
                User newUser = (User) newObject;
                User oldUser = (User) updateResult.getLastEntryBeforeUpdate(currentId);
                HashSet<Category> newGroups = new HashSet<Category>(newUser.getGroupList());
                HashSet<Category> oldGroups = new HashSet<Category>(oldUser.getGroupList());
                if (!newGroups.equals(oldGroups) || newUser.isAdmin() != oldUser.isAdmin())
                {
                    resourceRefresh = true;
                }

            }
            // We also check if a permission on a reservation has changed, so that it is no longer or new in the conflict list of a certain user.
            // If that is the case we trigger an invalidate of the conflicts for a user
            if (newObject instanceof Ownable)
            {
                Ownable newOwnable = (Ownable) newObject;
                Ownable oldOwnable = (Ownable) updateResult.getLastEntryBeforeUpdate(currentId);
                String newOwnerId = newOwnable.getOwnerId();
                String oldOwnerId = oldOwnable.getOwnerId();
                if (newOwnerId != null && oldOwnerId != null && (!newOwnerId.equals(oldOwnerId)))
                {
                    if ( user.getId().equals(newOwnerId) || user.getId().equals(oldOwnerId))
                    {
                        if (!newObject.getRaplaType().is(Reservation.TYPE))
                        {
                            resourceRefresh = true;
                        }
                        conflictRefresh = true;
                    }
                }
            }
            if (newObject.getRaplaType().is(Reservation.TYPE))
            {
                PermissionContainer current = (PermissionContainer) updateResult.getLastEntryBeforeUpdate(currentId);
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
                User permissionUser = permission.getUser();
                if (permissionUser != null && permissionUser.equals(user))
                {
                    resourceRefresh = true;
                    break;
                }
                Category group = permission.getGroup();
                if (group != null)
                {
                    groupsResourceRefresh.add(group);
                }
                if (permissionUser == null && group == null)
                {
                    resourceRefresh = true;
                    break;
                }
            }
            if (!resourceRefresh)
            {
                for (Permission permission : invalidateEventPermissions)
                {
                    User permissionUser = permission.getUser();
                    if (permissionUser != null && permissionUser.equals(user))
                    {
                        conflictRefresh = true;
                        break;
                    }
                    Category group = permission.getGroup();
                    if (group != null)
                    {
                        groupsConflictRefresh.add(group);
                    }
                    if (permissionUser == null && group == null)
                    {
                        conflictRefresh = true;
                        break;
                    }
                }
            }
            // we add all users belonging to group marked for refresh
            if (!resourceRefresh)
            {
                for (Category group : user.getGroupList())
                {
                    if (groupsResourceRefresh.contains(group))
                    {
                        resourceRefresh = true;
                        break;
                    }
                    if (groupsConflictRefresh.contains(group))
                    {
                        conflictRefresh = true;
                    }
                }
            }
        }


        //if ( lastSynced.before( currentTimestamp ))
        {
            String userId = user.getId();
            safeResultEvent.setNeedResourcesRefresh(resourceRefresh);
        }
        if (!resourceRefresh)
        {
            //Collection<Entity> updatedEntities = operator.getUpdatedEntities(user, lastSynced);

            for (String id : updateResult.getAddedAndChangedIds())
            {
                final Entity obj = updateResult.getLastKnown(id);
                final RaplaType raplaType = obj.getRaplaType();
                if ( raplaType == Reservation.TYPE)
                {
                    timeInterval = expandInterval(obj, timeInterval);
                    final Entity entity  = updateResult.getLastEntryBeforeUpdate(id);
                    if ( entity != null)
                    {
                        timeInterval = expandInterval(entity, timeInterval);
                    }
                }
                // Add entity to result
                processClientReadable(user, safeResultEvent, obj, false);
            }
            Collection<Remove> removedEntities = updateResult.getOperations(UpdateResult.Remove.class);
            for (Remove ref : removedEntities)
            {
                String id = ref.getCurrentId();
                final RaplaType raplaType = ref.getRaplaType();
                Class<? extends Entity> type = raplaType.getTypeClass();
                if (type == Allocatable.class || type == Conflict.class || type == DynamicType.class || type == User.class)
                {
                    safeResultEvent.putRemoveId(id);
                }
                if ( type == Reservation.class)
                {
                    final Entity entity = updateResult.getLastEntryBeforeUpdate(id);
                    timeInterval = expandInterval(entity, timeInterval);
                }
            }
        }
        {
            if (conflictRefresh || resourceRefresh)
            {
                timeInterval = new TimeInterval(null, null);
            }
            safeResultEvent.setInvalidateInterval(timeInterval);
        }
        return safeResultEvent;
    }

    // adds an object to the update event if the client can see it
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
                if (!permissionController.canModify(conflict, user))
                {
                    clientStore = false;
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

    }

    static public void convertToNewPluginConfig(ClientFacade facade, Logger logger, String className, TypedComponentRole<RaplaConfiguration> newConfKey)
            throws RaplaXMLContextException
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
            if (ex instanceof RaplaXMLContextException)
            {
                throw (RaplaXMLContextException) ex;
            }
            throw new RaplaXMLContextException(ex.getMessage(), ex);
        }
    }

}

