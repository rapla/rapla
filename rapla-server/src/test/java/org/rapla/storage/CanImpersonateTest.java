package org.rapla.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.Category;
import org.rapla.entities.CategoryAnnotations;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.test.util.FacadeTestSupport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 131 D8/D11 — tier 2 for {@link PermissionController#canImpersonate}: global/group admins by
 * {@code canAdminUser}, everyone else by the {@code impersonated_by} annotation on the target's DIRECT groups
 * naming a group the actor is a DIRECT member of.
 *
 * <p>Fixture: testdefault.xml (homer global admin, monty group admin of powerplant) plus
 * {@code function-accounts} (annotated {@code impersonated_by = exporters}) with child {@code child},
 * {@code exporters} with child {@code sub}; users alice and bob in exporters, export in function-accounts.
 */
class CanImpersonateTest extends FacadeTestSupport
{
    private String exportersId;

    @BeforeEach
    void fixture() throws Exception
    {
        newGroup("function-accounts");
        newGroup("exporters");
        newGroup("sub", "exporters");
        newGroup("child", "function-accounts");
        exportersId = find("exporters").getId();
        annotate(find("function-accounts"), exportersId);
        newUser("alice", find("exporters"));
        newUser("bob", find("exporters"));
        newUser("export", find("function-accounts"));
    }

    @Test
    void directMemberMaySwitchToDirectMemberOfTheAnnotatedGroup() throws Exception
    {
        assertFalse(PermissionController.canAdminUser(user("alice"), user("export")), "precondition: not an admin right");
        assertTrue(PermissionController.canImpersonate(user("alice"), user("export")));
        assertTrue(PermissionController.canImpersonate(user("bob"), user("export")));
    }

    @Test
    void nonMemberIsRefused() throws Exception
    {
        User carol = newUser("carol", find("my-group"));
        assertFalse(PermissionController.canImpersonate(carol, user("export")));
    }

    @Test
    void transitiveActorIsRefused() throws Exception
    {
        User dave = newUser("dave", find("exporters", "sub"));
        assertFalse(PermissionController.canImpersonate(dave, user("export")), "member of a CHILD of exporters is not a direct member");
    }

    @Test
    void transitiveTargetIsRefused() throws Exception
    {
        User deep = newUser("deep", find("function-accounts", "child"));
        assertFalse(PermissionController.canImpersonate(user("alice"), deep), "member of a CHILD of function-accounts is not a direct member");
    }

    @Test
    void rightIsOneWayAndNotBetweenPeers() throws Exception
    {
        assertFalse(PermissionController.canImpersonate(user("export"), user("alice")));
        assertFalse(PermissionController.canImpersonate(user("alice"), user("bob")));
        assertFalse(PermissionController.canImpersonate(user("bob"), user("alice")));
    }

    @Test
    void adminTargetIsRefused() throws Exception
    {
        User edit = facade.edit(user("export"));
        edit.setAdmin(true);
        facade.store(edit);
        assertFalse(PermissionController.canImpersonate(user("alice"), user("export")));
    }

    @Test
    void groupAdminTargetIsRefused() throws Exception
    {
        User edit = facade.edit(user("export"));
        edit.addGroup(find("powerplant", "powerplant-admins"));
        facade.store(edit);
        assertFalse(PermissionController.getAdminGroups(user("export")).isEmpty(), "precondition: target is a group admin");
        assertFalse(PermissionController.canImpersonate(user("alice"), user("export")));
    }

    @Test
    void selfIsRefusedViaTheAnnotation() throws Exception
    {
        annotate(find("exporters"), exportersId);
        assertFalse(PermissionController.canImpersonate(user("alice"), user("alice")));
        assertTrue(PermissionController.canImpersonate(user("alice"), user("bob")), "peers of a self-naming group may still switch");
    }

    @Test
    void renamedGroupStillMatches() throws Exception
    {
        Category exporters = facade.edit(find("exporters"));
        exporters.setKey("exporters-renamed");
        exporters.getName().setName("en", "Renamed exporters");
        facade.store(exporters);
        Category functionAccounts = facade.edit(find("function-accounts"));
        functionAccounts.setKey("function-accounts-renamed");
        facade.store(functionAccounts);
        assertTrue(PermissionController.canImpersonate(user("alice"), user("export")));
    }

    @Test
    void unknownIdGrantsNothingAndIdsAreCommaSeparated() throws Exception
    {
        annotate(find("function-accounts"), "does-not-exist");
        assertFalse(PermissionController.canImpersonate(user("alice"), user("export")));
        annotate(find("function-accounts"), "does-not-exist , " + exportersId);
        assertTrue(PermissionController.canImpersonate(user("alice"), user("export")));
    }

    @Test
    void deletedGroupGrantsNothing() throws Exception
    {
        newGroup("ghosts");
        annotate(find("function-accounts"), find("ghosts").getId());
        User carol = newUser("carol", find("ghosts"));
        assertTrue(PermissionController.canImpersonate(carol, user("export")), "precondition: the group still exists");

        User edit = facade.edit(carol);
        edit.removeGroup(find("ghosts"));
        edit.addGroup(find("my-group"));
        facade.store(edit);
        facade.remove(find("ghosts"));
        assertFalse(PermissionController.canImpersonate(user("carol"), user("export")));
    }

    @Test
    void adminPathIsUnchanged() throws Exception
    {
        User homer = user("homer");
        User monty = user("monty");
        assertTrue(PermissionController.canImpersonate(homer, monty));
        assertTrue(PermissionController.canImpersonate(homer, homer), "canAdminUser path keeps self for admins");
        assertFalse(PermissionController.canImpersonate(monty, homer), "group admin never reaches the global admin");
        User lenny = newUser("lenny", find("powerplant", "powerplant-staff"));
        assertTrue(PermissionController.canImpersonate(monty, lenny), "group admin scope");
        assertFalse(PermissionController.canImpersonate(user("alice"), homer));
    }

    private User user(String username) throws Exception
    {
        return operator.getUser(username);
    }

    private Category find(String... path) throws Exception
    {
        Category category = operator.getSuperCategory().getCategory("user-groups");
        for (String key : path)
        {
            category = category.getCategory(key);
        }
        return category;
    }

    private void newGroup(String key, String... parentPath) throws Exception
    {
        Category created = facade.newCategory();
        created.setKey(key);
        Category parentEdit = facade.edit(find(parentPath));
        parentEdit.addCategory(created);
        facade.storeObjects(new Entity[] { parentEdit, created });
    }

    private void annotate(Category group, String ids) throws Exception
    {
        Category edit = facade.edit(group);
        edit.setAnnotation(CategoryAnnotations.IMPERSONATED_BY, ids);
        facade.store(edit);
    }

    private User newUser(String username, Category group) throws Exception
    {
        User u = facade.newUser();
        u.setUsername(username);
        u.setName(username);
        for (Category inherited : u.getGroupList().toArray(new Category[0]))
        {
            u.removeGroup(inherited);
        }
        u.addGroup(group);
        facade.store(u);
        return user(username);
    }
}
