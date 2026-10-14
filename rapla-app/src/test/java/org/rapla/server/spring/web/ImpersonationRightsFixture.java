package org.rapla.server.spring.web;

import org.rapla.entities.Category;
import org.rapla.entities.CategoryAnnotations;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.storage.StorageOperator;

/**
 * PRD 131 D8 fixture on top of testdefault.xml: group {@code function-accounts} carries
 * {@code impersonated_by = exporters}; alice (exporters) may switch to export (function-accounts), nobody else.
 * adminfa is a global admin inside function-accounts, outsider is in neither group, staffer lies in monty's
 * group-admin scope. Every user has the password {@code pw}.
 */
final class ImpersonationRightsFixture
{
    static final String PASSWORD = "pw";

    private ImpersonationRightsFixture() {}

    static void seed(RaplaFacade facade, StorageOperator operator) throws Exception
    {
        if (operator.getUser("alice") != null)
        {
            return;
        }
        Category functionAccounts = group(facade, operator, "function-accounts");
        Category exporters = group(facade, operator, "exporters");
        Category edit = facade.edit(userGroups(operator).getCategory("function-accounts"));
        edit.setAnnotation(CategoryAnnotations.IMPERSONATED_BY, exporters.getId());
        facade.store(edit);

        user(facade, operator, "alice", false, exporters);
        user(facade, operator, "export", false, functionAccounts);
        user(facade, operator, "adminfa", true, functionAccounts);
        user(facade, operator, "outsider", false, userGroups(operator).getCategory("my-group"));
        user(facade, operator, "staffer", false, userGroups(operator).getCategory("powerplant").getCategory("powerplant-staff"));
    }

    private static Category userGroups(StorageOperator operator) throws Exception
    {
        return operator.getSuperCategory().getCategory("user-groups");
    }

    private static Category group(RaplaFacade facade, StorageOperator operator, String key) throws Exception
    {
        Category created = facade.newCategory();
        created.setKey(key);
        Category parent = facade.edit(userGroups(operator));
        parent.addCategory(created);
        facade.storeObjects(new Entity[] { parent, created });
        return created;
    }

    private static void user(RaplaFacade facade, StorageOperator operator, String username, boolean admin, Category group) throws Exception
    {
        User u = facade.newUser();
        u.setUsername(username);
        u.setName(username);
        u.setAdmin(admin);
        for (Category inherited : u.getGroupList().toArray(new Category[0]))
        {
            u.removeGroup(inherited);
        }
        u.addGroup(group);
        facade.store(u);
        operator.changePassword(operator.getUser(username), new char[0], PASSWORD.toCharArray());
    }
}
