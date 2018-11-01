/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Praktikum Gruppe2?, Christopher Kohlhaas              |
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

import org.rapla.RaplaResources;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.Ownable;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.PermissionController;
import org.rapla.storage.PreferencePatch;
import org.rapla.storage.RaplaSecurityException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

/** checks if the client can store or delete an entity */
@Singleton public class SecurityManager
{
    final RaplaResources i18n;
    final AppointmentFormater appointmentFormater;
    final CachableStorageOperator operator;
    final Logger logger;
    private final PermissionController permissionController;

    @Inject public SecurityManager(Logger logger, RaplaResources i18n, AppointmentFormater appointmentFormater, CachableStorageOperator operator)
    {
        this.logger = logger;
        this.i18n = i18n;
        this.appointmentFormater = appointmentFormater;
        this.operator = operator;
        permissionController = operator.getPermissionController();
    }

    public void checkDeletePermissions(User user, Entity entity) throws RaplaSecurityException
    {
        checkModifyPermissions(user, entity, true);
    }

    public void checkWritePermissions(User user, Entity entity) throws RaplaSecurityException
    {
        checkModifyPermissions(user, entity, false);
    }

    private void checkModifyPermissions(User user, Entity entity, boolean needsAdminPermission) throws RaplaSecurityException
    {
        if (user.isAdmin())
            return;

        Object id = entity.getId();
        if (id == null)
            throw new RaplaSecurityException("No id set");

        boolean permitted = false;
        Entity original = operator.tryResolve(entity.getReference());
        // flag indicates if a user only exchanges allocatables  (needs to have admin-access on the allocatable)
        boolean canExchange = false;

        boolean ownable = entity instanceof Ownable;
        if (ownable || entity instanceof Appointment)
        {
            ReferenceInfo<User> entityOwnerReference;
            if (ownable)
            {
                entityOwnerReference = ((Ownable) entity).getOwnerRef();
            }
            else
            {
                entityOwnerReference = ((Appointment) entity).getOwnerRef();
            }
            if (original == null)
            {
                permitted = entityOwnerReference != null && user.getReference().equals(entityOwnerReference);
                if (getLogger().isDebugEnabled())
                {
                    getLogger().debug("Permissions for new object " + entity + "\nUser check: " + user + " = " + operator.tryResolve(entityOwnerReference));
                }
            }
            else
            {
                ReferenceInfo<User> originalOwnerReference;
                if (ownable)
                {
                    originalOwnerReference = ((Ownable) original).getOwnerRef();
                }
                else
                {
                    originalOwnerReference = ((Appointment) original).getOwnerRef();
                }

                if (getLogger().isDebugEnabled())
                {
                    final User entityOwner = operator.tryResolve(entityOwnerReference);
                    final User originalOwner = operator.tryResolve(originalOwnerReference);
                    getLogger().debug("Permissions for existing object " + entity + "\nUser check: " + user + " = " + entityOwner + " = " + originalOwner);
                }
                permitted = (originalOwnerReference != null) && originalOwnerReference.equals(user.getReference()) && originalOwnerReference
                        .equals(entityOwnerReference);
                if (!permitted && !needsAdminPermission)
                {
                    canExchange = canExchange(user, entity, original);
                    permitted = canExchange;
                }
            }
        }
        if (permitted && entity instanceof Classifiable)
        {
            if (original == null)
            {
                permitted = permissionController.canCreate((Classifiable) entity, user);
            }
        }
        if (!permitted && original != null && original instanceof PermissionContainer)
        {
            if (needsAdminPermission)
            {
                permitted = permissionController.canAdmin(original, user);
            }
            else
            {
                permitted = permissionController.canModify(original, user);
            }

        }
        if (!permitted && entity instanceof Appointment)
        {
            final Reservation reservation = ((Appointment) entity).getReservation();
            Reservation originalReservation = operator.tryResolve(reservation.getReference());
            if (originalReservation != null)
            {
                permitted = permissionController.canModify(originalReservation, user);
            }
        }

        if (!permitted && entity instanceof Conflict)
        {
            Conflict conflict = (Conflict) entity;
            if (permissionController.canModify(conflict, user))
            {
                permitted = true;
            }
        }
        if (!permitted && entity instanceof Category)
        {
            Category category = (Category) entity;
            if (permissionController.canModify(category, user))
            {
                permitted = true;
            }
        }
        if (!permitted && entity instanceof User)
        {
            if (permissionController.canModify(entity, user) && (original == null || permissionController.canModify(original, user)))
            {
                final User userToModify = (User) entity;
                Collection<Category> newCompleteUserGroups = new ArrayList<>(userToModify.getGroupList());
                Collection<Category> newUserGroups = new ArrayList<>(newCompleteUserGroups);
                Collection<Category> removedUserGroups = new ArrayList<>();
                if (original != null)
                {
                    final Collection<Category> originalList = ((User) original).getGroupList();
                    removedUserGroups.addAll(originalList);
                    removedUserGroups.removeAll(newUserGroups);
                    newUserGroups.removeAll(originalList);
                }
                final Collection<Category> groupsToAdmin = PermissionController.getGroupsToAdmin(user);
                checkCanAdminGroups(newUserGroups, groupsToAdmin, user);
                checkCanAdminGroups(removedUserGroups, groupsToAdmin, user);

                // check if new group list has still one group to admin
                if (newCompleteUserGroups.isEmpty() || Collections.disjoint(newCompleteUserGroups,groupsToAdmin))
                {
                    String errorText = i18n.format("error.modify_not_allowed", user.toString(), userToModify);
                    throw new RaplaSecurityException(errorText);
                }

                permitted = true;
            }
        }

        if (!permitted)
        {
            String errorText;
            if (needsAdminPermission)
            {
                errorText = i18n.format("error.admin_not_allowed", user.toString(), entity.toString());
            }
            else
            {
                errorText = i18n.format("error.modify_not_allowed", user.toString(), entity.toString());
            }
            throw new RaplaSecurityException(errorText);

        }

        // Check if the user can change the reservation
        if (Reservation.class == entity.getTypeClass())
        {
            Reservation reservation = (Reservation) entity;
            Reservation originalReservation = (Reservation) original;
            Allocatable[] all = reservation.getAllocatables();
            if (originalReservation != null && canExchange)
            {
                List<Allocatable> newAllocatabes = new ArrayList<>(Arrays.asList(reservation.getAllocatables()));
                newAllocatabes.removeAll(Arrays.asList(originalReservation.getAllocatables()));
                all = newAllocatabes.toArray(Allocatable.ALLOCATABLE_ARRAY);
            }
            if (originalReservation == null)
            {
                boolean canCreate = permissionController.canCreate(reservation, user);
                //Category group = getUserGroupsCategory().getCategory( Permission.GROUP_CAN_CREATE_EVENTS);
                if (!canCreate)
                {
                    throw new RaplaSecurityException(i18n.format("error.create_not_allowed", new Object[] { user.toString(), entity.toString() }));
                }
            }
            checkPermissions(user, reservation, originalReservation, all);
        }

        // FIXME check if permissions are changed and user has admin priviliges 

    }

    private void checkCanAdminGroups(Collection<Category> groups, final Collection<Category> groupsToAdmin, User user) throws RaplaSecurityException
    {
        for ( Category group: groups)
        {
            if (!PermissionController.canAdminGroup( groupsToAdmin, group))
            {
                String errorText = i18n.format("error.modify_not_allowed", user.toString(), group.getName( ));
                throw new RaplaSecurityException(errorText);
            }
        }
    }

    private Logger getLogger()
    {
        return logger;
    }

    //    protected boolean isRegisterer(User user) throws RaplaSecurityException {
    //        try {
    //            Category registererGroup = getUserGroupsCategory().getCategory(Permission.GROUP_REGISTERER_KEY);
    //            return user.belongsTo(registererGroup);
    //        } catch (RaplaException ex) {
    //            throw new RaplaSecurityException(ex );
    //        }
    //    }

    public Category getUserGroupsCategory() throws RaplaSecurityException
    {
        Category userGroups = operator.getSuperCategory().getCategory(Permission.GROUP_CATEGORY_KEY);
        if (userGroups == null)
        {
            throw new RaplaSecurityException("No category '" + Permission.GROUP_CATEGORY_KEY + "' available");
        }
        return userGroups;
    }

    /** checks if the user just exchanges one allocatable or removes one. The user needs admin-access on the
     * removed allocatable and the newly inserted allocatable */
    private boolean canExchange(User user, Entity entity, Entity original)
    {
        final Class<? extends Entity> typeClass = entity.getTypeClass();
        if (Appointment.class == typeClass)
        {
            return ((Appointment) entity).matches((Appointment) original);
        }
        if (Reservation.class == typeClass)
        {
            Reservation newReservation = (Reservation) entity;
            Reservation oldReservation = (Reservation) original;
            // We only need to check the length because we compare the appointments above.
            if (newReservation.getAppointments().length != oldReservation.getAppointments().length)
            {
                return false;
            }

            List<Allocatable> oldAllocatables = Arrays.asList(oldReservation.getAllocatables());
            List<Allocatable> newAllocatables = Arrays.asList(newReservation.getAllocatables());
            List<Allocatable> inserted = new ArrayList<>(newAllocatables);
            List<Allocatable> removed = new ArrayList<>(oldAllocatables);
            List<Allocatable> overlap = new ArrayList<>(oldAllocatables);
            inserted.removeAll(oldAllocatables);
            removed.removeAll(newAllocatables);
            overlap.retainAll(inserted);
            if (inserted.size() == 0 && removed.size() == 0)
            {
                return false;
            }
            //  he must have admin rights on all inserted resources
            Iterator<Allocatable> it = inserted.iterator();
            while (it.hasNext())
            {
                if (!canAllocateForOthers(it.next(), user))
                {
                    return false;
                }
            }

            //   and  he must have admin rights on all the removed resources
            it = removed.iterator();
            while (it.hasNext())
            {
                if (!canAllocateForOthers(it.next(), user))
                {
                    return false;
                }
            }

            // He can't change appointments, only exchange allocatables he has admin-priviliges  for
            it = overlap.iterator();
            while (it.hasNext())
            {
                Allocatable all = it.next();
                Appointment[] r1 = newReservation.getRestriction(all);
                Appointment[] r2 = oldReservation.getRestriction(all);
                boolean changed = false;
                if (r1.length != r2.length)
                {
                    changed = true;
                }
                else
                {
                    for (int i = 0; i < r1.length; i++)
                    {
                        if (!r1[i].matches(r2[i]))
                        {
                            changed = true;
                        }
                    }
                }
                if (changed && !canAllocateForOthers(all, user))
                {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /** for Thierry, we can make this configurable in the next version */
    private boolean canAllocateForOthers(Allocatable allocatable, User user)
    {
        // only admins, current behaviour
        return permissionController.canModify(allocatable, user);
        // everyone who can allocate the resource anytime
        //return allocatable.canAllocate( user, null, null, operator.today());
        // everyone
        //return true;
    }

    private void checkConflictsAllowed(User user, Allocatable allocatable, Collection<Conflict> conflictsBefore, Collection<Conflict> conflictsAfter)
            throws RaplaSecurityException
    {
        int nConflictsBefore = 0;
        int nConflictsAfter = 0;
        if (permissionController.canCreateConflicts(allocatable, user))
        {
            return;
        }
        for (Conflict conflict : conflictsBefore)
        {
            if (conflict.getAllocatable().equals(allocatable))
            {
                nConflictsBefore++;
            }
        }
        for (Conflict conflict : conflictsAfter)
        {
            if (conflict.getAllocatable().equals(allocatable))
            {
                nConflictsAfter++;
            }
        }
        if (nConflictsAfter > nConflictsBefore)
        {
            String all = allocatable.getName(i18n.getLocale());
            throw new RaplaSecurityException(i18n.format("warning.no_conflict_permission", all));
        }
    }

    // check if conflict creation is allowed and the user has the right to allocate  the resources
    private void checkPermissions(User user, Reservation r, Reservation original, Allocatable[] allocatables) throws RaplaSecurityException
    {
        final Collection<Conflict> conflictsBefore;
        final Collection<Conflict> conflictsAfter ;
        try
        {
            if (original != null)
            {
                conflictsBefore = new ArrayList<>();
                conflictsAfter = new ArrayList<>();
                try
                {
                    operator.waitForWithRaplaException(operator.getConflicts(original).thenAcceptBoth(operator.getConflicts(r), (beforeConfl, afterConf) ->
                    {
                        conflictsBefore.addAll(beforeConfl);
                        conflictsAfter.addAll(afterConf);
                    }), 10000);
                }
                catch (RaplaException ex)
                {
                    throw new RaplaSecurityException(" Can't check permissions due to:" + ex.getMessage(), ex);
                }
            }
            else
            {
                conflictsAfter = operator.waitForWithRaplaException(operator.getConflicts(r), 10000);
                conflictsBefore = new ArrayList<>();
            }
        }
        catch (RaplaException ex)
        {
            throw new RaplaSecurityException(" Can't check permissions due to:" + ex.getMessage(), ex);
        }



        Appointment[] appointments = r.getAppointments();
        // ceck if the user has the permisson to add allocations in the given time
        for (int i = 0; i < allocatables.length; i++)
        {
            Allocatable allocatable = allocatables[i];
            checkConflictsAllowed(user, allocatable, conflictsBefore, conflictsAfter);
            for (int j = 0; j < appointments.length; j++)
            {
                Appointment appointment = appointments[j];
                Date today = operator.today();
                if (r.hasAllocatedOn(allocatable, appointment) && !permissionController.hasPermissionToAllocate(user, appointment, allocatable, original, today))
                {
                    String all = allocatable.getName(i18n.getLocale());
                    String app = appointmentFormater.getSummary(appointment);
                    String error = i18n.format("warning.no_reserve_permission", all, app);
                    throw new RaplaSecurityException(error);
                }
            }
        }
        if (original == null)
            return;

        Date today = operator.today();

        // 1. calculate the deleted assignments from allocatable to appointments
        // 2. check if they were allowed to change in the specified time
        appointments = original.getAppointments();
        allocatables = original.getAllocatables();
        for (int i = 0; i < allocatables.length; i++)
        {
            Allocatable allocatable = allocatables[i];
            for (int j = 0; j < appointments.length; j++)
            {
                Appointment appointment = appointments[j];
                if (original.hasAllocatedOn(allocatable, appointment) && !r.hasAllocatedOn(allocatable, appointment))
                {
                    Date start = appointment.getStart();
                    Date end = appointment.getMaxEnd();
                    if (!permissionController.canAllocate(allocatable, user, start, end, today))
                    {
                        String all = allocatable.getName(i18n.getLocale());
                        String app = appointmentFormater.getSummary(appointment);
                        String error = i18n.format("warning.no_reserve_permission", all, app);
                        throw new RaplaSecurityException(error);
                    }
                }
            }
        }
    }

    public void checkRead(User user, Entity entity) throws RaplaException
    {
        Class<? extends Entity> raplaType = entity.getTypeClass();
        if (raplaType == Allocatable.class)
        {
            Allocatable allocatable = (Allocatable) entity;
            if (!permissionController.canReadOnlyInformation(allocatable, user))
            {
                throw new RaplaSecurityException(i18n.format("error.read_not_allowed", user, allocatable.getName(null)));
            }
        }
        if (raplaType == Preferences.class)
        {
            Ownable ownable = (Preferences) entity;
            ReferenceInfo<User> ownerReference = ownable.getOwnerRef();
            if (user != null && !user.isAdmin() && (ownerReference == null || !user.getReference().equals(ownerReference)))
            {
                throw new RaplaSecurityException(i18n.format("error.read_not_allowed", user, entity));
            }
        }

    }

    public void checkWritePermissions(User user, PreferencePatch patch) throws RaplaSecurityException
    {
        ReferenceInfo<User> ownerRef = patch.getUserRef();
        if (user != null && !user.isAdmin() && (ownerRef == null || !user.getReference().equals(ownerRef)))
        {
            throw new RaplaSecurityException("User " + user + " can't modify preferences " + ownerRef);
        }

    }

}
