package org.rapla.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.test.util.FacadeTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the load-time redundant-DENY normalizer
 * ({@link PermissionContainer.Util#normalizeRedundantDenies}) against
 * [[0003-permissions-are-grant-only]]: a DENIED row is load-bearing only when it
 * sits at strictly higher precedence than some grant on the SAME container
 * (USER &gt; GROUP &gt; WORLD). Non-load-bearing DENIED rows change nothing about
 * effective access and are dropped on hydration.
 */
class RedundantDenyNormalizationTest extends FacadeTestSupport
{
    private DynamicType roomType;
    private User monty;
    private Category myGroup;
    private Category powerplant;

    @BeforeEach
    void lookups() throws Exception
    {
        roomType = facade.getDynamicType("room");
        assertNotNull(roomType, "fixture must define a 'room' type");
        monty = findUser("monty");
        Category userGroups = operator.getSuperCategory().getCategory("user-groups");
        myGroup = userGroups.getCategory("my-group");
        powerplant = userGroups.getCategory("powerplant");
        assertNotNull(myGroup);
        assertNotNull(powerplant);
    }

    private Allocatable newRoom(String name) throws Exception
    {
        Allocatable a = facade.newAllocatable(roomType.newClassification(), getAdmin());
        a.getClassification().setValue("name", name);
        clearPermissions(a);
        return a;
    }

    private static long deniedCount(Allocatable a)
    {
        return a.getPermissionList().stream()
                .filter(p -> p.getAccessLevel() == Permission.DENIED)
                .count();
    }

    @Test
    void worldDenyAlwaysRemoved() throws Exception
    {
        Allocatable a = newRoom("WORLD-DENY");
        addAllUsersPermission(a, AccessLevel.DENIED);

        int removed = PermissionContainer.Util.normalizeRedundantDenies(a);
        assertEquals(1, removed, "lone all-users DENIED is never load-bearing");
        assertEquals(0, deniedCount(a), "DENIED row must be gone");
    }

    @Test
    void groupDenyRemovedWithoutWorldGrant() throws Exception
    {
        Allocatable a = newRoom("GROUP-DENY-NO-WORLD");
        addGroupPermission(a, powerplant, AccessLevel.DENIED);
        addGroupPermission(a, myGroup, AccessLevel.ALLOCATE);

        int removed = PermissionContainer.Util.normalizeRedundantDenies(a);
        assertEquals(1, removed, "group DENIED with no WORLD grant is redundant");
        assertEquals(0, deniedCount(a), "DENIED row must be gone");
        assertTrue(a.getPermissionList().stream()
                        .anyMatch(p -> p.getAccessLevel() == AccessLevel.ALLOCATE),
                "the ALLOCATE grant must remain");
    }

    @Test
    void groupDenyKeptWithWorldGrant() throws Exception
    {
        Allocatable a = newRoom("GROUP-DENY-WITH-WORLD");
        addAllUsersPermission(a, AccessLevel.ALLOCATE);
        addGroupPermission(a, powerplant, AccessLevel.DENIED);

        int removed = PermissionContainer.Util.normalizeRedundantDenies(a);
        assertEquals(0, removed, "group DENIED is load-bearing over a WORLD grant");
        assertEquals(1, deniedCount(a), "DENIED row must still be present");
    }

    @Test
    void userDenyRemovedWithoutLowerGrant() throws Exception
    {
        Allocatable a = newRoom("USER-DENY-ALONE");
        addUserPermission(a, monty, AccessLevel.DENIED);

        int removed = PermissionContainer.Util.normalizeRedundantDenies(a);
        assertEquals(1, removed, "lone user DENIED with no lower grant is redundant");
        assertEquals(0, deniedCount(a), "DENIED row must be gone");
    }

    @Test
    void userDenyKeptWithGroupGrant() throws Exception
    {
        Allocatable a = newRoom("USER-DENY-WITH-GROUP");
        addGroupPermission(a, myGroup, AccessLevel.ALLOCATE);
        addUserPermission(a, monty, AccessLevel.DENIED);

        int removed = PermissionContainer.Util.normalizeRedundantDenies(a);
        assertEquals(0, removed, "user DENIED is load-bearing over a GROUP grant");
        assertEquals(1, deniedCount(a), "DENIED row must still be present");
    }

    @Test
    void grantsAreNeverRemoved() throws Exception
    {
        Allocatable a = newRoom("GRANTS-ONLY");
        addAllUsersPermission(a, AccessLevel.READ);
        addGroupPermission(a, myGroup, AccessLevel.ALLOCATE);

        int sizeBefore = a.getPermissionList().size();
        int removed = PermissionContainer.Util.normalizeRedundantDenies(a);
        assertEquals(0, removed, "no DENIED rows means nothing to remove");
        assertEquals(sizeBefore, a.getPermissionList().size(), "all grants remain");
        assertEquals(0, deniedCount(a));
    }

    private void addUserPermission(Allocatable a, User user, AccessLevel level)
    {
        Permission p = a.newPermission();
        p.setUser(user);
        p.setAccessLevel(level);
        a.addPermission(p);
    }

    private void addGroupPermission(Allocatable a, Category group, AccessLevel level)
    {
        Permission p = a.newPermission();
        p.setGroup(group);
        p.setAccessLevel(level);
        a.addPermission(p);
    }

    private void addAllUsersPermission(Allocatable a, AccessLevel level)
    {
        Permission p = a.newPermission(); // no user, no group → all-users (WORLD)
        p.setAccessLevel(level);
        a.addPermission(p);
    }

    private void clearPermissions(Allocatable a)
    {
        for (Permission p : new java.util.ArrayList<>(a.getPermissionList()))
        {
            a.removePermission(p);
        }
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
