package org.rapla.server.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.AppointmentFormaterImpl;
import org.rapla.RaplaResources;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.entities.Category;
import org.rapla.entities.CategoryAnnotations;
import org.rapla.entities.User;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.storage.PermissionController;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.test.util.FacadeTestSupport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Security audit F6-1 / PH1 — tier 2 for the dispatch field guard in {@link SecurityManager#checkWritePermissions}.
 *
 * <p>Fixture (testdefault.xml): monty is a group admin of {@code powerplant} (member of {@code powerplant-admins},
 * annotated {@code can_admin_parent}); homer is the global admin. Seeded: smithers directly in {@code powerplant}
 * (and {@code powerplant-staff}) with an external authentication source.
 */
class SecurityManagerDispatchFieldGuardTest extends FacadeTestSupport
{
    private SecurityManager security;
    private User homer;
    private User monty;
    private User smithers;
    private Category powerplant;
    private Category staff;

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
        powerplant = operator.getSuperCategory().getCategory("user-groups").getCategory("powerplant");
        staff = powerplant.getCategory("powerplant-staff");

        User u = facade.newUser();
        u.setUsername("smithers");
        // the user branch keeps a group admin inside his scope by DIRECT membership in the administered group
        u.addGroup(powerplant);
        u.addGroup(staff);
        u.setAuthenticationSource("ldap");
        facade.store(u);
        smithers = operator.getUser("smithers");
        assertTrue(PermissionController.canAdminUser(monty, smithers), "precondition: smithers is in monty's scope");
    }

    // === F6-1 — authenticationSource ==========================================

    @Test
    void groupAdminCannotClearAuthenticationSource() throws Exception
    {
        User edit = facade.edit(smithers);
        edit.setAuthenticationSource(null);
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit),
                "clearing the external source would let a group admin set a local password and take the account over");
    }

    @Test
    void groupAdminCannotSwitchAuthenticationSource() throws Exception
    {
        User edit = facade.edit(smithers);
        edit.setAuthenticationSource("other");
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit));
    }

    @Test
    void groupAdminMayStillEditOtherUserData() throws Exception
    {
        User edit = facade.edit(smithers);
        edit.setName("Waylon Smithers");
        assertDoesNotThrow(() -> security.checkWritePermissions(monty, edit));
    }

    @Test
    void globalAdminMayChangeAuthenticationSource() throws Exception
    {
        User edit = facade.edit(smithers);
        edit.setAuthenticationSource(null);
        assertDoesNotThrow(() -> security.checkWritePermissions(homer, edit));
    }

    // === PH1 — admin-scope category metadata =================================

    @Test
    void groupAdminCannotMarkOwnScopeRootAsAdminOfParent() throws Exception
    {
        Category edit = facade.edit(powerplant);
        edit.setAnnotation(CategoryAnnotations.CAN_ADMIN_PARENT, "true");
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit),
                "can_admin_parent on the scope root would lift monty to admin of user-groups");
    }

    @Test
    void groupAdminCannotMarkChildGroupAsAdminOfParent() throws Exception
    {
        Category edit = facade.edit(staff);
        edit.setAnnotation(CategoryAnnotations.CAN_ADMIN_PARENT, "true");
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit));
    }

    @Test
    void groupAdminCannotRemoveAdminOfParentMarker() throws Exception
    {
        Category admins = powerplant.getCategory("powerplant-admins");
        Category edit = facade.edit(admins);
        edit.setAnnotation(CategoryAnnotations.CAN_ADMIN_PARENT, null);
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit));
    }

    @Test
    void groupAdminMayStillRenameGroupInScope() throws Exception
    {
        Category edit = facade.edit(staff);
        edit.getName().setName("en", "power plant staff");
        assertDoesNotThrow(() -> security.checkWritePermissions(monty, edit));
    }

    @Test
    void globalAdminMaySetAdminOfParentMarker() throws Exception
    {
        Category edit = facade.edit(staff);
        edit.setAnnotation(CategoryAnnotations.CAN_ADMIN_PARENT, "true");
        assertDoesNotThrow(() -> security.checkWritePermissions(homer, edit));
    }
}
