package org.rapla.server.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.AppointmentFormaterImpl;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.RaplaResources;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.test.util.FacadeTestSupport;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 security regression for the {@code // FIXME check if permissions are
 * changed and user has admin priviliges} in
 * {@link SecurityManager#checkModifyPermissions}.
 *
 * <p>A non-admin with EDIT access on a resource ({@code canModify == true}) but
 * without ADMIN access ({@code canAdmin == false}) must NOT be able to rewrite
 * that resource's permission list — otherwise an EDIT grant could be escalated
 * to ADMIN. Editing the resource's data without touching permissions must still
 * be allowed. DynamicTypes are excluded (already fully admin-gated).
 */
class SecurityManagerPermissionChangeTest extends FacadeTestSupport
{
    private SecurityManager security;
    private User monty;
    private Category myGroup;

    @BeforeEach
    void setUpSecurity() throws Exception
    {
        AbstractBundleManager bundleManager = new ServerBundleManager();
        RaplaResources i18n = new RaplaResources(bundleManager);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        AppointmentFormater fmt = new AppointmentFormaterImpl(i18n, raplaLocale);
        security = new SecurityManager(i18n, fmt, operator, operator);

        monty = null;
        for (User u : facade.getUsers())
        {
            if ("monty".equals(u.getUsername())) monty = u;
        }
        assertNotNull(monty, "fixture must include non-admin user monty");
        Category userGroups = operator.getSuperCategory().getCategory("user-groups");
        myGroup = userGroups.getCategory("my-group");
        assertNotNull(myGroup, "fixture must have my-group");
    }

    /** Build an admin-owned resource on which my-group (monty's group) has EDIT. */
    private Allocatable editableByMontyViaGroupEdit() throws Exception
    {
        DynamicType roomType = facade.getDynamicType("room");
        User admin = null;
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) { admin = u; break; }
        }
        assertNotNull(admin, "fixture must include an admin user");
        Allocatable a = facade.newAllocatable(roomType.newClassification(), admin);
        a.getClassification().setValue("name", "shared-room");
        Permission p = a.newPermission();
        p.setGroup(myGroup);
        p.setAccessLevel(AccessLevel.EDIT);
        a.addPermission(p);
        facade.store(a);
        return (Allocatable) operator.tryResolve(a.getReference());
    }

    @Test
    void montyHasEditButNotAdmin() throws Exception
    {
        Allocatable stored = editableByMontyViaGroupEdit();
        assertTrue(operator.getPermissionController().canModify(stored, monty),
                "monty must be able to modify (EDIT via my-group)");
        assertFalse(operator.getPermissionController().canAdmin(stored, monty),
                "monty must NOT have admin (EDIT < ADMIN, not owner)");
    }

    @Test
    void nonAdminCannotChangePermissionListOfResourceTheyOnlyEdit() throws Exception
    {
        Allocatable stored = editableByMontyViaGroupEdit();

        // monty escalates: grant my-group ADMIN on the resource.
        Allocatable edit = facade.edit(stored);
        Permission escalate = edit.newPermission();
        escalate.setGroup(myGroup);
        escalate.setAccessLevel(AccessLevel.ADMIN);
        edit.addPermission(escalate);

        assertThrows(RaplaSecurityException.class,
                () -> security.checkWritePermissions(monty, edit),
                "a non-admin must not be able to change a resource's permission list");
    }

    @Test
    void nonAdminCanStillEditResourceDataWithoutTouchingPermissions() throws Exception
    {
        Allocatable stored = editableByMontyViaGroupEdit();

        // monty edits data only — no permission change.
        Allocatable edit = facade.edit(stored);
        edit.getClassification().setValue("name", "renamed-room");

        assertDoesNotThrow(() -> security.checkWritePermissions(monty, edit),
                "editing data (no permission change) must remain allowed for an EDIT user");
    }

    /** Build a reservation owned by {@code owner} on which my-group (monty's group) has EDIT. */
    private Reservation reservationEditableByMontyViaGroupEdit(User owner) throws Exception
    {
        DynamicType eventType = facade.getDynamicTypes(
                DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        Reservation r = facade.newReservation(eventType.newClassification(), owner);
        Appointment app = facade.newAppointmentWithUser(
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 11, 0), owner);
        r.addAppointment(app);
        Permission p = r.newPermission();
        p.setGroup(myGroup);
        p.setAccessLevel(AccessLevel.EDIT);
        r.addPermission(p);
        facade.store(r);
        return (Reservation) operator.tryResolve(r.getReference());
    }

    private User admin() throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) return u;
        }
        throw new AssertionError("fixture must include an admin user");
    }

    @Test
    void nonAdminCannotChangePermissionListOfReservationTheyOnlyEdit() throws Exception
    {
        Reservation stored = reservationEditableByMontyViaGroupEdit(admin());
        assertTrue(operator.getPermissionController().canModify(stored, monty),
                "monty must be able to modify (EDIT via my-group)");
        assertFalse(operator.getPermissionController().canAdmin(stored, monty),
                "monty must NOT have admin (EDIT < ADMIN, not owner)");

        Reservation edit = facade.edit(stored);
        Permission escalate = edit.newPermission();
        escalate.setGroup(myGroup);
        escalate.setAccessLevel(AccessLevel.ADMIN);
        edit.addPermission(escalate);

        assertThrows(RaplaSecurityException.class,
                () -> security.checkWritePermissions(monty, edit),
                "a non-admin must not be able to change a reservation's permission list");
    }

    @Test
    void nonAdminCanStillEditReservationDataWithoutTouchingPermissions() throws Exception
    {
        Reservation stored = reservationEditableByMontyViaGroupEdit(admin());

        Reservation edit = facade.edit(stored);
        edit.getClassification().setValue("name", "renamed-event");

        assertDoesNotThrow(() -> security.checkWritePermissions(monty, edit),
                "editing data (no permission change) must remain allowed for an EDIT user");
    }

    @Test
    void ownerCanChangePermissionListOfOwnReservation() throws Exception
    {
        Reservation stored = reservationEditableByMontyViaGroupEdit(monty);

        Reservation edit = facade.edit(stored);
        Permission grant = edit.newPermission();
        grant.setGroup(myGroup);
        grant.setAccessLevel(AccessLevel.ADMIN);
        edit.addPermission(grant);

        assertDoesNotThrow(() -> security.checkWritePermissions(monty, edit),
                "the owner must still be able to change their reservation's permission list");
    }
}
