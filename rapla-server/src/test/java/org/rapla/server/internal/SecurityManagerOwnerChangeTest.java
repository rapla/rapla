package org.rapla.server.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.AppointmentFormaterImpl;
import org.rapla.RaplaResources;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.storage.PermissionController;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.test.util.FacadeTestSupport;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP O1 — tier 2 for owner changes by group admins in {@link SecurityManager#checkWritePermissions}.
 *
 * <p>Fixture (testdefault.xml): monty is a group admin of {@code powerplant} (member of {@code powerplant-admins},
 * annotated {@code can_admin_parent}); homer is the global admin. Seeded here: smithers and carl in
 * {@code powerplant-staff} (inside monty's scope; carl holds no admin group), lenny in no group (outside it).
 */
class SecurityManagerOwnerChangeTest extends FacadeTestSupport
{
    private SecurityManager security;
    private User homer;
    private User monty;
    private User smithers;
    private User carl;
    private User lenny;
    private Category myGroup;

    @BeforeEach
    void setUpSecurity() throws Exception
    {
        AbstractBundleManager bundleManager = new ServerBundleManager();
        RaplaResources i18n = new RaplaResources(bundleManager);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        AppointmentFormater fmt = new AppointmentFormaterImpl(i18n, raplaLocale);
        security = new SecurityManager(i18n, fmt, operator, operator);

        homer = operator.getUser("homer");
        monty = operator.getUser("monty");
        Category userGroups = operator.getSuperCategory().getCategory("user-groups");
        myGroup = userGroups.getCategory("my-group");
        Category staff = userGroups.getCategory("powerplant").getCategory("powerplant-staff");
        smithers = user("smithers", staff);
        carl = user("carl", staff);
        lenny = user("lenny", null);

        PermissionController pc = operator.getPermissionController();
        assertTrue(PermissionController.canAdminUser(monty, smithers), "precondition: smithers is in monty's scope");
        assertTrue(PermissionController.canAdminUser(monty, carl), "precondition: carl is in monty's scope");
        assertFalse(PermissionController.canAdminUser(monty, lenny), "precondition: lenny is outside monty's scope");
        assertFalse(PermissionController.canAdminUsers(carl), "precondition: carl administers no group");
        assertTrue(pc != null);
    }

    private User user(String username, Category group) throws Exception
    {
        User u = facade.newUser();
        u.setUsername(username);
        if (group != null) u.addGroup(group);
        facade.store(u);
        return operator.getUser(username);
    }

    /** A room owned by {@code owner}; {@code readableByMonty} adds a my-group READ row. */
    private Allocatable room(User owner, boolean readableByMonty) throws Exception
    {
        return room(owner, readableByMonty ? AccessLevel.READ : null);
    }

    private Allocatable room(User owner, AccessLevel myGroupLevel) throws Exception
    {
        DynamicType roomType = facade.getDynamicType("room");
        Allocatable a = facade.newAllocatable(roomType.newClassification(), owner);
        a.getClassification().setValue("name", "o1-room");
        for (Permission p : a.getPermissionList().toArray(new Permission[0]))
        {
            a.removePermission(p);
        }
        if (myGroupLevel != null)
        {
            Permission read = a.newPermission();
            read.setGroup(myGroup);
            read.setAccessLevel(myGroupLevel);
            a.addPermission(read);
        }
        facade.store(a);
        return (Allocatable) operator.tryResolve(a.getReference());
    }

    private Allocatable ownerChangedTo(Allocatable stored, User newOwner) throws Exception
    {
        Allocatable edit = facade.edit(stored);
        edit.setOwner(newOwner);
        return edit;
    }

    // (a)
    @Test
    void groupAdminChangesOwnerWithinScope() throws Exception
    {
        Allocatable edit = ownerChangedTo(room(smithers, true), carl);
        assertDoesNotThrow(() -> security.checkWritePermissions(monty, edit),
                "a group admin may hand an entity from one user of his scope to another");
    }

    // (b)
    @Test
    void newOwnerOutsideScopeDenied() throws Exception
    {
        Allocatable edit = ownerChangedTo(room(smithers, true), lenny);
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit));
    }

    // (c)
    @Test
    void oldOwnerOutsideScopeDenied() throws Exception
    {
        Allocatable edit = ownerChangedTo(room(lenny, true), carl);
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit),
                "a group admin must not take over an entity of a user outside his scope");
    }

    // (d)
    @Test
    void userWithoutAdminGroupDeniedAsToday() throws Exception
    {
        Allocatable stored = room(smithers, true);
        Allocatable edit = ownerChangedTo(stored, carl);
        RaplaSecurityException today = assertThrows(RaplaSecurityException.class,
                () -> security.checkWritePermissions(carl, edit));
        Allocatable plainEdit = facade.edit(stored);
        plainEdit.getClassification().setValue("name", "renamed");
        RaplaSecurityException plain = assertThrows(RaplaSecurityException.class,
                () -> security.checkWritePermissions(carl, plainEdit));
        assertEquals(plain.getMessage().replace("renamed", "o1-room"), today.getMessage(),
                "same error as any other unpermitted write — no hint about the owner rule");
    }

    // (e)
    @Test
    void globalAdminUnchanged() throws Exception
    {
        Allocatable edit = ownerChangedTo(room(lenny, false), smithers);
        assertDoesNotThrow(() -> security.checkWritePermissions(homer, edit));
    }

    // (f)
    @Test
    void unreadableEntityDeniedEvenWithinScope() throws Exception
    {
        Allocatable edit = ownerChangedTo(room(smithers, false), carl);
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit),
                "canRead is part of the rule — an entity the group admin cannot see stays untouchable");
    }

    // (A) the pre-existing hole: an EDIT grant re-permitted an owner change through canModify
    @Test
    void editOnlyUserCannotChangeOwner() throws Exception
    {
        Allocatable edit = ownerChangedTo(room(lenny, AccessLevel.EDIT), monty);
        assertTrue(operator.getPermissionController().canModify(edit, monty), "precondition: monty may edit the room");
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit),
                "EDIT on an entity is not a licence to hand it to someone else");
    }

    // (B) owner-only: owner + data in one dispatch falls back to the normal path
    @Test
    void ownerAndDataChangeInOneDispatchDenied() throws Exception
    {
        Allocatable edit = ownerChangedTo(room(smithers, true), carl);
        edit.getClassification().setValue("name", "taken-over");
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit),
                "the group-admin owner rule covers the owner only, not data changes riding along");
    }

    // (C) a pure owner change skips the allocation checks: the booking itself is unchanged
    @Test
    void pureOwnerChangeNeedsNoAllocateRightOnBookedResources() throws Exception
    {
        // no row on the room affects monty at all — the allocation check has nothing to go on
        Allocatable bookedRoom = room(homer, false);
        DynamicType eventType = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        Reservation r = facade.newReservation(eventType.newClassification(), smithers);
        Appointment app = facade.newAppointmentWithUser(LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 11, 0), smithers);
        r.addAppointment(app);
        r.addAllocatable(bookedRoom);
        for (Permission p : r.getPermissionList().toArray(new Permission[0])) r.removePermission(p);
        Permission read = r.newPermission();
        read.setGroup(myGroup);
        read.setAccessLevel(AccessLevel.READ);
        r.addPermission(read);
        facade.store(r);
        Reservation stored = (Reservation) operator.tryResolve(r.getReference());
        assertFalse(operator.getPermissionController().canAllocate(bookedRoom, monty, java.time.LocalDate.of(2026, 6, 1)),
                "precondition: monty cannot allocate the booked room");

        Reservation edit = facade.edit(stored);
        edit.setOwner(carl);
        assertDoesNotThrow(() -> security.checkWritePermissions(monty, edit));
    }

    // (i) a global admin is never inside a group admin's scope
    @Test
    void globalAdminAsNewOwnerDenied() throws Exception
    {
        Allocatable edit = ownerChangedTo(room(smithers, true), homer);
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit));
    }
}
