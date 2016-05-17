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

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.Ownable;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.PermissionContainer.Util;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.ImportExportEntity;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.PermissionController;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateOperation;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.UpdateResult.Change;
import org.rapla.storage.UpdateResult.Remove;

/** Provides an adapter for each client-session to their shared storage operator
 * Handles security and synchronizing aspects.
 */
@DefaultImplementation(of=UpdateDataManager.class, context = InjectionContext.server)
@Singleton
public class UpdateDataManagerImpl implements  UpdateDataManager
{
    private CachableStorageOperator operator;

    private SecurityManager security;

    private Logger logger;

    private final PermissionController permissionController;


    @Inject public UpdateDataManagerImpl(Logger logger, CachableStorageOperator operator, SecurityManager securityManager)
    {
        this.logger = logger;
        this.operator = operator;
        this.permissionController = operator.getPermissionController();
        this.security = securityManager;
    }

    protected Logger getLogger()
    {
        return logger;
    }

    static Preferences removeServerOnlyPreferences(Preferences preferences)
    {
        Preferences clone = preferences.clone();
        {
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

    private TimeInterval expandInterval(RaplaObject obj,
            TimeInterval currentInterval)
    {
        if ( obj.getTypeClass() == Reservation.class)
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
        safeResultEvent.setLastValidated(updateResult.getUntil());
        if(updateResult.getSince() == null)
        {
            safeResultEvent.setInvalidateInterval(null);
            safeResultEvent.setNeedResourcesRefresh(true);
            return safeResultEvent;
        }
        boolean resourceRefresh = lastSynced.before( historyValidStart);
        boolean conflictRefresh = lastSynced.before( conflictValidStart);
        for (Remove op : updateResult.getOperations(Remove.class))
        {
            if ( op.getType() == DynamicType.class)
            {
                resourceRefresh = true;
                conflictRefresh = true;
            }
        }
        if ( !conflictRefresh)
        {
            for (UpdateOperation op : updateResult.getOperations(Change.class))
            {
                if (op.getType() == DynamicType.class)
                {
                    conflictRefresh = true;
                }
            }
            for (UpdateOperation op : updateResult.getOperations(UpdateResult.Add.class))
            {
                if (op.getType() == DynamicType.class)
                {
                    conflictRefresh = true;
                }
            }
        }

        Set<Permission> invalidatePermissions = new HashSet<Permission>();
        Set<Permission> invalidateEventPermissions = new HashSet<Permission>();

        for (Change operation : updateResult.getOperations(UpdateResult.Change.class))
        {
            final ReferenceInfo currentId = operation.getReference();
            final Class<? extends Entity> typeClass = currentId.getType();
            Entity newObject = updateResult.getLastKnown(currentId);
            if ( newObject == null)
            {
                getLogger().error("Object with id " + currentId + " not found in history. Ignoring. ");
            }
            // we get all the permissions that have changed on an allocatable
            if (typeClass == Allocatable.class && isTransferedToClient(newObject))
            {
                PermissionContainer current = (PermissionContainer) updateResult.getLastEntryBeforeUpdate(currentId);
                if ( current == null)
                {
                    resourceRefresh = true;
                }
                else
                {
                    PermissionContainer newObj = (PermissionContainer) newObject;
                    Util.addDifferences(invalidatePermissions, current, newObj);
                }
            }
            // We trigger a resource refresh if the groups of the user have changed

            if (typeClass == User.class && newObject.equals( user))
            {
                UserImpl newUser = (UserImpl) newObject;
                HashSet<String> newGroups = new HashSet<String>(newUser.getGroupIdList());
                UserImpl oldUser = (UserImpl) updateResult.getLastEntryBeforeUpdate(currentId);
                if ( oldUser == null)
                {
                    resourceRefresh = true;
                }
                else
                {
                    HashSet<String> oldGroups = new HashSet<String>(oldUser.getGroupIdList());
                    if (!newGroups.equals(oldGroups) || newUser.isAdmin() != oldUser.isAdmin())
                    {
                        resourceRefresh = true;
                    }
                }

            }
            // We also check if a permission on a reservation has changed, so that it is no longer or new in the conflict list of a certain user.
            // If that is the case we trigger an invalidate of the conflicts for a user
            if (newObject instanceof Ownable)
            {
                Ownable newOwnable = (Ownable) newObject;
                Ownable oldOwnable = (Ownable) updateResult.getLastEntryBeforeUpdate(currentId);
                if ( oldOwnable == null)
                {
                    resourceRefresh = true;
                    continue;
                }
                ReferenceInfo<User> newOwnerId = newOwnable.getOwnerRef();
                ReferenceInfo<User> oldOwnerId = oldOwnable.getOwnerRef();
                if (newOwnerId != null && oldOwnerId != null && (!newOwnerId.equals(oldOwnerId)))
                {
                    if ( user.getReference().isSame(newOwnerId) || user.getReference().isSame(oldOwnerId))
                    {
                        if (typeClass != Reservation.class)
                        {
                            resourceRefresh = true;
                        }
                        conflictRefresh = true;
                    }
                }
            }
            if (typeClass == Reservation.class)
            {
                PermissionContainer current = (PermissionContainer) updateResult.getLastEntryBeforeUpdate(currentId);
                if ( current == null)
                {
                    resourceRefresh = true;
                }
                else
                {
                    PermissionContainer newObj = (PermissionContainer) newObject;
                    Util.addDifferences(invalidateEventPermissions, current, newObj);
                }
            }
        }
        if (!invalidatePermissions.isEmpty() || !invalidateEventPermissions.isEmpty())
        {
            Set<String> groupsResourceRefresh = new HashSet<String>();
            Set<String> groupsConflictRefresh = new HashSet<String>();
            for (Permission permission : invalidatePermissions)
            {
                String permissionUser = ((PermissionImpl)permission).getUserId();
                if (permissionUser != null && permissionUser.equals(user.getId()))
                {
                    resourceRefresh = true;
                    break;
                }
                String group = ((PermissionImpl)permission).getGroupId();
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
                    String permissionUser = ((PermissionImpl)permission).getUserId();
                    if (permissionUser != null && permissionUser.equals(user.getId()))
                    {
                        conflictRefresh = true;
                        break;
                    }
                    String group = ((PermissionImpl)permission).getGroupId();
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
                for (String group : ((UserImpl)user).getGroupIdList())
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

            for (ReferenceInfo id : updateResult.getAddedAndChangedIds())
            {
                final Entity obj = updateResult.getLastKnown(id);
                final Class<? extends  Entity> raplaType = obj.getTypeClass();
                if ( raplaType == Reservation.class)
                {
                    timeInterval = expandInterval(obj, timeInterval);
                    final Entity entity  = updateResult.getLastEntryBeforeUpdate(id);
                    if ( entity != null)
                    {
                        timeInterval = expandInterval(entity, timeInterval);
                    }
                    else
                    {
                        timeInterval = new TimeInterval( null, null);
                    }
                }
                    // Add entity to result
                processClientReadable(user, safeResultEvent, obj, false);
            }
            Collection<Remove> removedEntities = updateResult.getOperations(UpdateResult.Remove.class);
            for (Remove remove : removedEntities)
            {
                ReferenceInfo ref = remove.getReference();
                Class<? extends Entity> type = ref.getType();
                if (type == Allocatable.class || type == Conflict.class || type == DynamicType.class || type == User.class || type == Category.class)
                {
                    safeResultEvent.putRemoveId(ref);
                }
                if ( type == Reservation.class)
                {
                    final Entity entity = updateResult.getLastEntryBeforeUpdate(ref);
                    if ( entity != null)
                    {
                        timeInterval = expandInterval(entity, timeInterval);
                    }
                    else
                    {
                        timeInterval = new TimeInterval( null, null);
                    }
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
                ReferenceInfo<User> ownerId = preferences.getOwnerRef();
                if (ownerId != null && !ownerId.isSame(user.getReference()))
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
            if ( obj instanceof User)
            {
                final Collection<Category> adminGroups = PermissionController.getGroupsToAdmin(user);
                if ( adminGroups.size() > 0)
                {
                    clientStore = permissionController.canAdmin( (User) obj, user);
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
        Class<? extends RaplaObject> raplaType = obj.getTypeClass();
        if (raplaType == Appointment.class || raplaType == Reservation.class || raplaType == ImportExportEntity.class)
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


}

