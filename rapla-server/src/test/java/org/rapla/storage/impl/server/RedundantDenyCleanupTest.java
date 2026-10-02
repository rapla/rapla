package org.rapla.storage.impl.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.test.util.FacadeTestSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 090 Phase 6 — the DENIED rows the load-time normalizer drops (ADR 0003) are removed from
 * the store once, by storing the affected entities; asserted on the raw data file, because a
 * reload would normalize them away again.
 */
class RedundantDenyCleanupTest extends FacadeTestSupport
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
    }

    @Test
    void removesOnlyNonLoadBearingDeniesFromTheStoreOnce() throws Exception
    {
        Allocatable worldDeny = newRoom("WP13-WORLD-DENY");
        permission(worldDeny, null, null, AccessLevel.DENIED);
        Allocatable groupDeny = newRoom("WP13-GROUP-DENY");
        permission(groupDeny, null, powerplant, AccessLevel.DENIED);
        permission(groupDeny, null, myGroup, AccessLevel.ALLOCATE);
        Allocatable userDeny = newRoom("WP13-USER-DENY");
        permission(userDeny, null, myGroup, AccessLevel.ALLOCATE);
        permission(userDeny, monty, null, AccessLevel.DENIED);
        facade.storeObjects(new Allocatable[] { worldDeny, groupDeny, userDeny });
        assertEquals(3, deniedInFile(), "stores keep DENIED rows; only loading normalizes");

        operator.disconnect();
        operator.connect();
        LocalDateTime worldBefore = lastChanged(worldDeny);
        LocalDateTime userBefore = lastChanged(userDeny);
        assertEquals(3, deniedInFile(), "loading alone never persists");

        operator.removeRedundantDeniesIfNeeded();
        assertEquals(1, deniedInFile(), "only the load-bearing USER deny (over a GROUP grant) stays");
        assertEquals(1, deniedInFile("WP13-USER-DENY"));
        assertTrue(RedundantDenyCleanup.markerSet(operator), "marker written");
        assertTrue(lastChanged(worldDeny).isAfter(worldBefore), "a cleaned entity is really changed");
        assertEquals(userBefore, lastChanged(userDeny), "an untouched entity keeps its stamp");

        String fileAfterFirstRun = Files.readString(dataFile());
        LocalDateTime worldAfterFirstRun = lastChanged(worldDeny);
        operator.removeRedundantDeniesIfNeeded();
        assertEquals(fileAfterFirstRun, Files.readString(dataFile()), "second run is a no-op");
        assertEquals(worldAfterFirstRun, lastChanged(worldDeny));
    }

    private Path dataFile()
    {
        return tempDir.resolve("rapla-data.xml");
    }

    private long deniedInFile() throws Exception
    {
        return Files.readString(dataFile()).split("access=\"denied\"", -1).length - 1;
    }

    private long deniedInFile(String roomName) throws Exception
    {
        for (String resource : Files.readString(dataFile()).split("<rapla:resource "))
        {
            if (resource.contains(roomName))
            {
                return resource.split("access=\"denied\"", -1).length - 1;
            }
        }
        throw new IllegalStateException(roomName + " not in data file");
    }

    private LocalDateTime lastChanged(Allocatable a)
    {
        return ((Allocatable) operator.tryResolve(a.getReference())).getLastChanged();
    }

    private Allocatable newRoom(String name) throws Exception
    {
        Allocatable a = facade.newAllocatable(roomType.newClassification(), getAdmin());
        a.getClassification().setValue("name", name);
        for (Permission p : new java.util.ArrayList<>(a.getPermissionList()))
        {
            a.removePermission(p);
        }
        return a;
    }

    private static void permission(Allocatable a, User user, Category group, AccessLevel level)
    {
        Permission p = a.newPermission();
        if (user != null) p.setUser(user);
        if (group != null) p.setGroup(group);
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
