package org.rapla.storage.impl.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.test.util.FacadeTestSupport;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 090 Phase 2 — the one-shot additive-permission migration freezes exactly the
 * allocatables whose effective access rose at the flip into the worklist system
 * preference, and is idempotent (marker-guarded).
 */
class AdditivePermissionMigrationTest extends FacadeTestSupport
{
    private DynamicType roomType;
    private User monty;
    private Category myGroup;
    private Category powerplant;

    @BeforeEach
    void lookups() throws Exception
    {
        roomType = facade.getDynamicType("room");
        monty = findUser("monty");
        Category userGroups = operator.getSuperCategory().getCategory("user-groups");
        myGroup = userGroups.getCategory("my-group");
        powerplant = userGroups.getCategory("powerplant");
        assertNotNull(myGroup);
        assertNotNull(powerplant);
    }

    @Test
    void freezesExactlyTheEscalatedAllocatables() throws Exception
    {
        // (1) user-below-group: monty READ next to group ALLOCATE → escalation.
        Allocatable softDeny = newRoom("SOFT-DENY");
        clear(softDeny);
        group(softDeny, myGroup, AccessLevel.ALLOCATE);
        user(softDeny, monty, AccessLevel.READ);
        facade.store(softDeny);

        // (2) load-bearing DENIED over a world grant → escalation.
        Allocatable deniedOverWorld = newRoom("DENIED-OVER-WORLD");
        clear(deniedOverWorld);
        world(deniedOverWorld, AccessLevel.ALLOCATE);
        group(deniedOverWorld, powerplant, AccessLevel.DENIED);
        facade.store(deniedOverWorld);

        // (3) clean: group ALLOCATE only — monty gets ALLOCATE either way.
        Allocatable clean = newRoom("CLEAN");
        clear(clean);
        group(clean, myGroup, AccessLevel.ALLOCATE);
        facade.store(clean);

        // (4) OQ1: the capping user row starts far in the future → no cap today → excluded.
        Allocatable futureCap = newRoom("FUTURE-CAP");
        clear(futureCap);
        group(futureCap, myGroup, AccessLevel.ALLOCATE);
        Permission futureRead = futureCap.newPermission();
        futureRead.setUser(monty);
        futureRead.setAccessLevel(AccessLevel.READ);
        futureRead.setStart(LocalDateTime.now().plusYears(5));
        futureCap.addPermission(futureRead);
        facade.store(futureCap);

        operator.migrateAdditivePermissionsIfNeeded();

        Preferences sys = operator.getPreferences(null, false);
        assertTrue(AdditiveMigrationState.markerSet(sys), "marker written");
        Set<String> worklist = AdditiveMigrationState.readWorklist(sys);

        assertTrue(worklist.contains(softDeny.getId()), "user-below-group escalation must be frozen");
        assertTrue(worklist.contains(deniedOverWorld.getId()), "DENIED-over-world escalation must be frozen");
        assertFalse(worklist.contains(clean.getId()), "clean allocatable must NOT be in the worklist");
        assertFalse(worklist.contains(futureCap.getId()), "OQ1: future-windowed cap must be excluded");
        assertEquals(2, worklist.size(), "exactly the two real escalations");
    }

    @Test
    void isIdempotent() throws Exception
    {
        Allocatable softDeny = newRoom("SOFT-DENY");
        clear(softDeny);
        group(softDeny, myGroup, AccessLevel.ALLOCATE);
        user(softDeny, monty, AccessLevel.READ);
        facade.store(softDeny);

        operator.migrateAdditivePermissionsIfNeeded();
        Set<String> first = AdditiveMigrationState.readWorklist(operator.getPreferences(null, false));

        // a NEW post-flip soft-deny must NOT be added on a second run (frozen set).
        Allocatable postFlip = newRoom("POST-FLIP");
        clear(postFlip);
        group(postFlip, myGroup, AccessLevel.ALLOCATE);
        user(postFlip, monty, AccessLevel.READ);
        facade.store(postFlip);

        operator.migrateAdditivePermissionsIfNeeded();
        Set<String> second = AdditiveMigrationState.readWorklist(operator.getPreferences(null, false));

        assertEquals(first, second, "marker-guarded: the worklist is frozen, new soft-denies are not added");
        assertFalse(second.contains(postFlip.getId()), "post-flip soft-deny excluded");
    }

    private Allocatable newRoom(String name) throws Exception
    {
        Allocatable a = facade.newAllocatable(roomType.newClassification(), getAdmin());
        a.getClassification().setValue("name", name);
        return a;
    }

    private void user(Allocatable a, User u, AccessLevel level)
    {
        Permission p = a.newPermission();
        p.setUser(u);
        p.setAccessLevel(level);
        a.addPermission(p);
    }

    private void group(Allocatable a, Category g, AccessLevel level)
    {
        Permission p = a.newPermission();
        p.setGroup(g);
        p.setAccessLevel(level);
        a.addPermission(p);
    }

    private void world(Allocatable a, AccessLevel level)
    {
        Permission p = a.newPermission();
        p.setAccessLevel(level);
        a.addPermission(p);
    }

    private void clear(Allocatable a)
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
