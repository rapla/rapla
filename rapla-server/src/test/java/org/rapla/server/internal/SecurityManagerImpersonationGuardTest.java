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
 * PRD 131 D8 — tier 2 for the {@code impersonated_by} guard in {@link SecurityManager#checkWritePermissions}:
 * only a global admin changes the annotation, or the membership in a group that carries or is named by it.
 *
 * <p>Fixture (testdefault.xml): monty is a group admin of {@code powerplant}; homer is the global admin.
 * {@code powerplant-staff} lies in monty's scope, {@code my-group} outside it.
 */
class SecurityManagerImpersonationGuardTest extends FacadeTestSupport
{
    private SecurityManager security;
    private User homer;
    private User monty;
    private Category powerplant;
    private Category staff;
    private Category myGroup;
    private User smithers;

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
        powerplant = userGroups.getCategory("powerplant");
        staff = powerplant.getCategory("powerplant-staff");
        myGroup = userGroups.getCategory("my-group");

        User u = facade.newUser();
        u.setUsername("smithers");
        for (Category group : u.getGroupList().toArray(new Category[0]))
        {
            u.removeGroup(group);
        }
        u.addGroup(powerplant);
        facade.store(u);
        smithers = operator.getUser("smithers");
        assertTrue(PermissionController.canAdminUser(monty, smithers), "precondition: smithers is in monty's scope");
    }

    private Category group(String... path) throws Exception
    {
        Category category = operator.getSuperCategory().getCategory("user-groups");
        for (String key : path)
        {
            category = category.getCategory(key);
        }
        return category;
    }

    private void annotate(Category group, String ids) throws Exception
    {
        Category edit = facade.edit(group);
        edit.setAnnotation(CategoryAnnotations.IMPERSONATED_BY, ids);
        facade.store(edit);
    }

    // === the annotation itself ================================================

    @Test
    void groupAdminCannotSetImpersonatedBy() throws Exception
    {
        Category edit = facade.edit(staff);
        edit.setAnnotation(CategoryAnnotations.IMPERSONATED_BY, myGroup.getId());
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit),
                "impersonated_by decides who may switch to the group's members");
    }

    @Test
    void groupAdminCannotChangeImpersonatedBy() throws Exception
    {
        annotate(staff, myGroup.getId());
        Category edit = facade.edit(group("powerplant", "powerplant-staff"));
        edit.setAnnotation(CategoryAnnotations.IMPERSONATED_BY, powerplant.getId());
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit));
    }

    @Test
    void groupAdminCannotRemoveImpersonatedBy() throws Exception
    {
        annotate(staff, myGroup.getId());
        Category edit = facade.edit(group("powerplant", "powerplant-staff"));
        edit.setAnnotation(CategoryAnnotations.IMPERSONATED_BY, null);
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit));
    }

    @Test
    void groupAdminMayStillRenameACarryingGroup() throws Exception
    {
        annotate(staff, myGroup.getId());
        Category edit = facade.edit(group("powerplant", "powerplant-staff"));
        edit.getName().setName("en", "power plant staff");
        assertDoesNotThrow(() -> security.checkWritePermissions(monty, edit));
    }

    @Test
    void globalAdminMaySetImpersonatedBy() throws Exception
    {
        Category edit = facade.edit(staff);
        edit.setAnnotation(CategoryAnnotations.IMPERSONATED_BY, myGroup.getId());
        assertDoesNotThrow(() -> security.checkWritePermissions(homer, edit));
    }

    // === membership in a carrying group =======================================

    @Test
    void groupAdminCannotAddUserToCarryingGroup() throws Exception
    {
        User control = facade.edit(smithers);
        control.addGroup(staff);
        assertDoesNotThrow(() -> security.checkWritePermissions(monty, control), "control: staff is an ordinary group without the annotation");

        annotate(staff, myGroup.getId());
        User edit = facade.edit(smithers);
        edit.addGroup(group("powerplant", "powerplant-staff"));
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit),
                "members of a carrying group are switch targets");
    }

    @Test
    void groupAdminCannotRemoveUserFromCarryingGroup() throws Exception
    {
        User member = facade.edit(smithers);
        member.addGroup(staff);
        facade.store(member);
        annotate(staff, myGroup.getId());

        User edit = facade.edit(operator.getUser("smithers"));
        edit.removeGroup(group("powerplant", "powerplant-staff"));
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit));
    }

    // === membership in a named group ==========================================

    @Test
    void groupAdminCannotAddUserToNamedGroup() throws Exception
    {
        annotate(myGroup, staff.getId());
        User edit = facade.edit(smithers);
        edit.addGroup(group("powerplant", "powerplant-staff"));
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit),
                "members of a named group are the actors that may switch");
    }

    @Test
    void groupAdminCannotRemoveUserFromNamedGroup() throws Exception
    {
        User member = facade.edit(smithers);
        member.addGroup(staff);
        facade.store(member);
        annotate(myGroup, staff.getId() + ", other-id");

        User edit = facade.edit(operator.getUser("smithers"));
        edit.removeGroup(group("powerplant", "powerplant-staff"));
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit));
    }

    @Test
    void groupAdminCannotCreateUserInNamedGroup() throws Exception
    {
        annotate(myGroup, staff.getId());
        User created = facade.newUser();
        created.setUsername("lenny");
        for (Category group : created.getGroupList().toArray(new Category[0]))
        {
            created.removeGroup(group);
        }
        created.addGroup(powerplant);
        created.addGroup(group("powerplant", "powerplant-staff"));
        assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, created));
    }

    @Test
    void groupAdminMayStillChangeMembershipOfUnrelatedGroups() throws Exception
    {
        annotate(myGroup, powerplant.getId());
        User edit = facade.edit(smithers);
        edit.addGroup(staff);
        assertDoesNotThrow(() -> security.checkWritePermissions(monty, edit), "staff neither carries nor is named by an annotation");
    }

    @Test
    void globalAdminMayChangeMembershipOfNamedAndCarryingGroups() throws Exception
    {
        annotate(myGroup, staff.getId());
        User edit = facade.edit(smithers);
        edit.addGroup(group("powerplant", "powerplant-staff"));
        edit.addGroup(group("my-group"));
        assertDoesNotThrow(() -> security.checkWritePermissions(homer, edit));
    }
}
