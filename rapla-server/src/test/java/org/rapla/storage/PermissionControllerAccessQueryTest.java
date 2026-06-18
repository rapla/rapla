package org.rapla.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.test.util.FacadeTestSupport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 069 — tier-2 for the access-by-target evaluation helpers
 * {@link PermissionController#hasUserAccessAtLeast} and
 * {@link PermissionController#hasGroupAccessAtLeast}.
 *
 * <p>Fixture (testdefault.xml): users homer (admin) / monty (non-admin);
 * user-groups subtree: my-group, powerplant → {powerplant-admins,
 * powerplant-staff}, registerer.
 */
class PermissionControllerAccessQueryTest extends FacadeTestSupport
{
    private PermissionController pc;
    private DynamicType roomType;
    private User monty;
    private Category myGroup;
    private Category powerplant;
    private Category powerplantAdmins;

    @BeforeEach
    void lookups() throws Exception
    {
        pc = operator.getPermissionController();
        roomType = facade.getDynamicType("room");
        assertNotNull(roomType, "fixture must define a 'room' type");
        monty = findUser("monty");
        Category userGroups = operator.getSuperCategory().getCategory("user-groups");
        assertNotNull(userGroups, "fixture must have user-groups");
        myGroup = userGroups.getCategory("my-group");
        powerplant = userGroups.getCategory("powerplant");
        powerplantAdmins = powerplant.getCategory("powerplant-admins");
        assertNotNull(myGroup);
        assertNotNull(powerplantAdmins);
    }

    @Test
    void ownerHasReadAndEditWithoutAnyExplicitPermission() throws Exception
    {
        Allocatable owned = facade.newAllocatable(roomType.newClassification(), monty);
        owned.getClassification().setValue("name", "OWNED-BY-MONTY");

        assertTrue(pc.hasUserAccessAtLeast(owned, monty, AccessLevel.READ),
                "owner reads own resource even with no permission entry");
        assertTrue(pc.hasUserAccessAtLeast(owned, monty, AccessLevel.EDIT),
                "owner edits own resource even with no permission entry");
    }

    @Test
    void ownershipDoesNotLeakToGroupQuery() throws Exception
    {
        Allocatable owned = facade.newAllocatable(roomType.newClassification(), monty);
        owned.getClassification().setValue("name", "OWNED-BY-MONTY");

        // monty owns it, but no permission grants my-group anything — a group
        // query must not surface it (groups never own).
        assertFalse(pc.hasGroupAccessAtLeast(owned, List.of(myGroup), AccessLevel.READ),
                "group query must ignore the owner relationship");
    }

    @Test
    void groupPermissionGrantsAtLevelButNotAbove() throws Exception
    {
        Allocatable a = facade.newAllocatable(roomType.newClassification(), getAdmin());
        a.getClassification().setValue("name", "GROUP-READ");
        addGroupPermission(a, myGroup, AccessLevel.READ);

        assertTrue(pc.hasGroupAccessAtLeast(a, List.of(myGroup), AccessLevel.READ),
                "READ permission on my-group satisfies a READ query");
        assertFalse(pc.hasGroupAccessAtLeast(a, List.of(myGroup), AccessLevel.EDIT),
                "READ permission must not satisfy an EDIT query");
    }

    @Test
    void parentGroupPermissionCascadesToChildGroup() throws Exception
    {
        Allocatable a = facade.newAllocatable(roomType.newClassification(), getAdmin());
        a.getClassification().setValue("name", "POWERPLANT-READ");
        addGroupPermission(a, powerplant, AccessLevel.READ);

        // permission on the parent group "powerplant" applies to the child
        // group "powerplant-admins".
        assertTrue(pc.hasGroupAccessAtLeast(a, List.of(powerplantAdmins), AccessLevel.READ),
                "permission on parent group must cascade to a descendant group");
    }

    @Test
    void unionAcrossGroups() throws Exception
    {
        Allocatable a = facade.newAllocatable(roomType.newClassification(), getAdmin());
        a.getClassification().setValue("name", "GROUP-READ");
        addGroupPermission(a, myGroup, AccessLevel.READ);

        assertTrue(pc.hasGroupAccessAtLeast(a, List.of(powerplantAdmins, myGroup), AccessLevel.READ),
                "union: any matching group in the list grants access");
        assertFalse(pc.hasGroupAccessAtLeast(a, List.of(powerplantAdmins), AccessLevel.READ),
                "no permission for powerplant-admins on this resource");
    }

    private void addGroupPermission(Allocatable a, Category group, AccessLevel level)
    {
        Permission p = a.newPermission();
        p.setGroup(group);
        p.setAccessLevel(level);
        a.addPermission(p);
    }

    private User findUser(String username) throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (username.equals(u.getUsername())) return u;
        }
        throw new IllegalStateException("fixture must include user " + username);
    }

    private User getAdmin() throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) return u;
        }
        throw new IllegalStateException("fixture must include an admin user");
    }
}
