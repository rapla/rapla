package org.rapla.storage.impl.server.readmodel;

import org.junit.jupiter.api.Test;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.storage.PermissionController;
import org.rapla.test.util.FacadeTestSupport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 (FacadeTestSupport, real entities — AGENTS.md §10/§13, no mocks).
 *
 * <p>Companion to {@link PermissionIndexTest}, but instead of leaning on the
 * (permission-poor) {@code testdefault.xml} fixture, each test <b>builds its
 * own</b> groups / users / allocatables with a specific permission shape, stores
 * them through the facade, and then asserts two things:
 * <ol>
 *   <li>the §12 equivalence invariant — {@code index.readableAllocatables(user)}
 *       equals exactly the id set {@code PermissionController.canRead} would
 *       accept over every resident allocatable (the index never surfaces more,
 *       never less);</li>
 *   <li>the concrete expected membership of the resource the test created.</li>
 * </ol>
 *
 * <p>These tests LOCK the current, verified behaviour (no production change).
 * Springfield personas only (AGENTS.md §17): {@code homer} (admin) /
 * {@code monty} (non-admin) come from the fixture; created principals use
 * obvious dummy ids ({@code lisa}, {@code bart}, {@code ned}, …).
 */
class PermissionIndexScenarioTest extends FacadeTestSupport
{
    // ---- helpers mirroring PermissionIndexTest ----------------------------

    private PermissionController controller()
    {
        final Set<PermissionExtension> extensions = new LinkedHashSet<>();
        extensions.add(new RaplaDefaultPermissionImpl());
        return new PermissionController(extensions, operator);
    }

    private PermissionIndex index()
    {
        return new PermissionIndex(operator, controller());
    }

    private Set<String> canReadIds(PermissionController controller, User user) throws Exception
    {
        final Set<String> expected = new LinkedHashSet<>();
        for (Allocatable a : operator.getAllocatables(null))
        {
            if (controller.canRead(a, user))
            {
                expected.add(a.getId());
            }
        }
        return expected;
    }

    /** Assert the §12 equivalence: index readable set == canRead-derived set. */
    private void assertIndexEqualsCanRead(User user) throws Exception
    {
        final PermissionController controller = controller();
        final Set<String> expected = canReadIds(controller, user);
        final Set<String> actual = index().readableAllocatables(user);
        assertEquals(expected, actual,
                "index readable set must equal canRead set for user " + user.getUsername());
    }

    private User getAdmin() throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (u.isAdmin())
            {
                return u;
            }
        }
        throw new IllegalStateException("fixture must include an admin user");
    }

    private Category userGroups() throws Exception
    {
        final Category groups = operator.getSuperCategory().getCategory("user-groups");
        assertNotNull(groups, "fixture must have user-groups");
        return groups;
    }

    private DynamicType roomType() throws Exception
    {
        final DynamicType room = facade.getDynamicType("room");
        assertNotNull(room, "fixture must define a 'room' type");
        return room;
    }

    /** Create a fresh non-admin user (no groups), store it, return the resident copy. */
    private User createUser(String username) throws Exception
    {
        final User u = facade.newUser();
        u.setUsername(username);
        u.setName(username);
        facade.store(u);
        return facade.getUser(username);
    }

    private void addUserToGroup(User user, Category group) throws Exception
    {
        final User edit = facade.edit(user);
        edit.addGroup(group);
        facade.store(edit);
    }

    private void removeUserFromGroup(User user, Category group) throws Exception
    {
        final User edit = facade.edit(user);
        edit.removeGroup(group);
        facade.store(edit);
    }

    /**
     * Create a room owned by admin (no implicit owner-read for the test subject)
     * with its inherited default permissions STRIPPED. {@code newAllocatable}
     * copies the resource type's default permissions onto the instance — which
     * include a world {@code READ_TYPE} and a world {@code ALLOCATE_CONFLICTS}
     * (so an out-of-the-box room is world-readable). Strip them so each test's
     * explicit grants are the only ones in effect.
     */
    private Allocatable newRoom(String name) throws Exception
    {
        final Allocatable a = facade.newAllocatable(roomType().newClassification(), getAdmin());
        a.getClassification().setValue("name", name);
        for (Permission inherited : new ArrayList<>(a.getPermissionList()))
        {
            a.removePermission(inherited);
        }
        return a;
    }

    private Permission grant(Allocatable a, Category group, User user, AccessLevel level)
    {
        final Permission p = a.newPermission();
        if (group != null)
        {
            p.setGroup(group);
        }
        if (user != null)
        {
            p.setUser(user);
        }
        p.setAccessLevel(level);
        a.addPermission(p);
        return p;
    }

    private Allocatable resident(Allocatable created) throws Exception
    {
        return (Allocatable) operator.tryResolve(created.getReference());
    }

    // =======================================================================
    // A — permission-shape cases
    // =======================================================================

    /** A1: READ on a PARENT group; user in a (>=2-level deep) CHILD subgroup -> readable. */
    @Test
    void groupAncestorGrant() throws Exception
    {
        // user-groups > powerplant > powerplant-admins already exists (2 levels).
        final Category powerplant = userGroups().getCategory("powerplant");
        final Category powerplantAdmins = powerplant.getCategory("powerplant-admins");
        assertNotNull(powerplantAdmins);

        final User lisa = createUser("lisa");
        addUserToGroup(lisa, powerplantAdmins);
        final User residentLisa = facade.getUser("lisa");

        final Allocatable room = newRoom("ancestor-grant-room");
        grant(room, powerplant, null, AccessLevel.READ);
        facade.store(room);

        assertIndexEqualsCanRead(residentLisa);
        assertTrue(index().readableAllocatables(residentLisa).contains(resident(room).getId()),
                "READ on the parent group must reach a user in the child subgroup");
    }

    /** A2: READ on a group the user is NOT in -> not readable. */
    @Test
    void siblingGroupNotGranted() throws Exception
    {
        final Category powerplant = userGroups().getCategory("powerplant");
        final Category staff = powerplant.getCategory("powerplant-staff");
        final Category admins = powerplant.getCategory("powerplant-admins");

        final User bart = createUser("bart");
        addUserToGroup(bart, staff);
        final User residentBart = facade.getUser("bart");

        final Allocatable room = newRoom("sibling-room");
        grant(room, admins, null, AccessLevel.READ); // granted to the sibling group
        facade.store(room);

        assertIndexEqualsCanRead(residentBart);
        assertFalse(index().readableAllocatables(residentBart).contains(resident(room).getId()),
                "a grant to a sibling group must not be visible");
    }

    /** A3: READ via group + a DENIED permission on the user -> not readable (max-effect). */
    @Test
    void deniedOverridesGroupRead() throws Exception
    {
        final Category myGroup = userGroups().getCategory("my-group");

        final User ned = createUser("ned");
        addUserToGroup(ned, myGroup);
        final User residentNed = facade.getUser("ned");

        final Allocatable room = newRoom("denied-room");
        grant(room, myGroup, null, AccessLevel.READ);
        grant(room, null, residentNed, AccessLevel.DENIED);
        facade.store(room);

        assertIndexEqualsCanRead(residentNed);
        assertFalse(index().readableAllocatables(residentNed).contains(resident(room).getId()),
                "a user-level DENIED must override a group READ");
    }

    /**
     * A4: ONLY a READ_NO_ALLOCATION grant -> NOT in readableAllocatables
     * (READ_NO_ALLOCATION(50) < READ(100); canRead excludes it).
     *
     * <p>READ_NO_ALLOCATION visibility is a deliberately SEPARATE path:
     * {@code canReadInformation} returns true for it (the user may see the
     * resource exists / its info, but not its allocations). The read INDEX is
     * intentionally keyed off {@code canRead} (full READ), so such a resource is
     * absent from the readable set. Locking current behaviour as correct.
     */
    @Test
    void readNoAllocationOnlyHiddenFromReadableSet() throws Exception
    {
        final Category myGroup = userGroups().getCategory("my-group");

        final User lisa = createUser("lisa");
        addUserToGroup(lisa, myGroup);
        final User residentLisa = facade.getUser("lisa");

        final Allocatable room = newRoom("read-no-alloc-room");
        grant(room, myGroup, null, AccessLevel.READ_NO_ALLOCATION);
        facade.store(room);

        assertIndexEqualsCanRead(residentLisa);
        assertFalse(index().readableAllocatables(residentLisa).contains(resident(room).getId()),
                "READ_NO_ALLOCATION must not put a resource in the READ index");

        // Separate canReadInformation path: the user CAN see info on it.
        assertTrue(controller().canReadInformation(resident(room), residentLisa),
                "READ_NO_ALLOCATION still grants canReadInformation visibility (separate path)");
    }

    /** A5: ALLOCATE_CONFLICTS grant -> readable, and accessLevel includes ALLOCATE. */
    @Test
    void allocateConflictsReportsAllocate() throws Exception
    {
        final Category myGroup = userGroups().getCategory("my-group");

        final User bart = createUser("bart");
        addUserToGroup(bart, myGroup);
        final User residentBart = facade.getUser("bart");

        final Allocatable room = newRoom("allocate-conflicts-room");
        grant(room, myGroup, null, AccessLevel.ALLOCATE_CONFLICTS);
        facade.store(room);

        assertIndexEqualsCanRead(residentBart);
        final String id = resident(room).getId();
        assertTrue(index().readableAllocatables(residentBart).contains(id));

        final AccessLevel level = index().accessLevel(residentBart, id);
        assertNotNull(level, "allocate_conflicts grant must report a level");
        assertTrue(level.includes(AccessLevel.ALLOCATE),
                "allocate_conflicts implies at least ALLOCATE");
    }

    /**
     * A6: ALLOCATE_CONFLICTS grant whose [start,end] window is entirely in the
     * PAST -> resource STILL readable, and accessLevel still includes ALLOCATE.
     *
     * <p>LOCK: READ ignores time bounds (a time-bounded grant still makes the
     * resource readable), and {@code accessLevel} probes
     * {@code hasUserAccessAtLeast} with {@code today=null}, so the expired
     * window is ignored. Faithful to {@code hasUserAccessAtLeast}; the
     * "lost ALLOCATE via expiry" Part-B semantics are intentionally not
     * expressed by this index.
     */
    @Test
    void expiredTimeBoundGrantStillReadableAndAllocate() throws Exception
    {
        final Category myGroup = userGroups().getCategory("my-group");

        final User ned = createUser("ned");
        addUserToGroup(ned, myGroup);
        final User residentNed = facade.getUser("ned");

        final Allocatable room = newRoom("expired-grant-room");
        final Permission p = grant(room, myGroup, null, AccessLevel.ALLOCATE_CONFLICTS);
        p.setStart(LocalDateTime.of(2000, 1, 1, 0, 0));
        p.setEnd(LocalDateTime.of(2000, 12, 31, 0, 0));
        facade.store(room);

        assertIndexEqualsCanRead(residentNed);
        final String id = resident(room).getId();
        assertTrue(index().readableAllocatables(residentNed).contains(id),
                "READ ignores the (expired) time window");

        final AccessLevel level = index().accessLevel(residentNed, id);
        assertNotNull(level);
        assertTrue(level.includes(AccessLevel.ALLOCATE),
                "accessLevel probes with today=null, so the expired window is ignored");
    }

    /** A7: owner without any explicit permission entry -> readable; accessLevel non-null. */
    @Test
    void ownerWithoutExplicitPermission() throws Exception
    {
        final User maggie = createUser("maggie");
        final User residentMaggie = facade.getUser("maggie");

        final Allocatable room = facade.newAllocatable(roomType().newClassification(), residentMaggie);
        room.getClassification().setValue("name", "owned-room");
        // no permission entries at all
        facade.store(room);

        assertIndexEqualsCanRead(residentMaggie);
        final String id = resident(room).getId();
        assertTrue(index().readableAllocatables(residentMaggie).contains(id),
                "owner reads own resource without an explicit permission");
        assertNotNull(index().accessLevel(residentMaggie, id),
                "owner must report a non-null access level");
    }

    /** A8: a no-restriction (world) READ permission -> readable for an arbitrary non-admin. */
    @Test
    void worldReadable() throws Exception
    {
        final User stranger = createUser("stranger");
        final User residentStranger = facade.getUser("stranger");
        assertFalse(residentStranger.isAdmin());

        final Allocatable room = newRoom("world-room");
        grant(room, null, null, AccessLevel.READ); // no group, no user -> world
        facade.store(room);

        assertIndexEqualsCanRead(residentStranger);
        assertTrue(index().readableAllocatables(residentStranger).contains(resident(room).getId()),
                "a world READ permission is visible to any user");
    }

    /**
     * A9: a DynamicType-level READ_TYPE grant restricted to a group gates its
     * instances via {@code canReadType}. We build a NEW resource type, replace
     * its default world READ_TYPE with a group-scoped one, store the type, create
     * an instance, and assert index == canRead for a user not in the group
     * (instance unreadable) and a user in the group (readable).
     */
    @Test
    void typeLevelReadGrant() throws Exception
    {
        final Category myGroup = userGroups().getCategory("my-group");

        // a brand-new resource type with default (world) permissions
        final DynamicType type = facade.newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
        type.getName().setName("en", "gated-type");
        // replace any world READ_TYPE with a group-scoped READ_TYPE so only
        // members of my-group can read the type (and thus its instances).
        for (Permission existing : new ArrayList<>(type.getPermissionList()))
        {
            type.removePermission(existing);
        }
        final Permission typeRead = type.newPermission();
        typeRead.setGroup(myGroup);
        typeRead.setAccessLevel(AccessLevel.READ_TYPE);
        type.addPermission(typeRead);
        facade.store(type);

        final DynamicType storedType = facade.getDynamicType(type.getKey());
        final Allocatable instance = facade.newAllocatable(storedType.newClassification(), getAdmin());
        instance.getClassification().setValue("name", "gated-instance");
        // instance is world-READable at the allocatable level; visibility is
        // gated purely by canReadType (the type's group-scoped READ_TYPE).
        for (Permission inherited : new ArrayList<>(instance.getPermissionList()))
        {
            instance.removePermission(inherited);
        }
        grant(instance, null, null, AccessLevel.READ);
        facade.store(instance);

        final User inGroup = createUser("inGroupUser");
        addUserToGroup(inGroup, myGroup);
        final User residentIn = facade.getUser("inGroupUser");

        final User outGroup = createUser("outGroupUser");
        final User residentOut = facade.getUser("outGroupUser");

        final String id = resident(instance).getId();

        assertIndexEqualsCanRead(residentIn);
        assertIndexEqualsCanRead(residentOut);
        assertTrue(index().readableAllocatables(residentIn).contains(id),
                "a member of the type's READ_TYPE group reads its instances");
        assertFalse(index().readableAllocatables(residentOut).contains(id),
                "a non-member must not read instances of a group-gated type");
    }

    // =======================================================================
    // B — caching / invalidation
    // =======================================================================

    /** B10: invalidateAll picks up a newly-added READ grant for the user. */
    @Test
    void invalidateAllReflectsNewGrant() throws Exception
    {
        final User lisa = createUser("lisa");
        final User residentLisa = facade.getUser("lisa");

        final Allocatable room = newRoom("late-grant-room");
        facade.store(room);
        final String id = resident(room).getId();

        final PermissionIndex index = index();
        assertFalse(index.readableAllocatables(residentLisa).contains(id),
                "not granted yet -> absent");

        // add a user READ grant and store
        final Allocatable edit = facade.edit(resident(room));
        grant(edit, null, residentLisa, AccessLevel.READ);
        facade.store(edit);

        index.invalidateAll();
        assertTrue(index.readableAllocatables(residentLisa).contains(id),
                "after invalidateAll the new grant is picked up");
    }

    /** B11: gaining group membership grows the readable set. */
    @Test
    void membershipGainGrowsSet() throws Exception
    {
        final Category myGroup = userGroups().getCategory("my-group");

        final User bart = createUser("bart");
        final User residentBart = facade.getUser("bart");

        final Allocatable room = newRoom("group-grant-room");
        grant(room, myGroup, null, AccessLevel.READ);
        facade.store(room);
        final String id = resident(room).getId();

        final PermissionIndex index = index();
        assertFalse(index.readableAllocatables(residentBart).contains(id),
                "not in the group yet -> absent");

        addUserToGroup(residentBart, myGroup);
        final User afterJoin = facade.getUser("bart");
        index.invalidate(afterJoin);

        assertTrue(index.readableAllocatables(afterJoin).contains(id),
                "after joining the group the resource appears");
    }

    /** B12: losing group membership shrinks the set (§12: stale cache must not keep showing it). */
    @Test
    void membershipLossShrinksSet() throws Exception
    {
        final Category myGroup = userGroups().getCategory("my-group");

        final User ned = createUser("ned");
        addUserToGroup(ned, myGroup);
        final User residentNed = facade.getUser("ned");

        final Allocatable room = newRoom("loss-room");
        grant(room, myGroup, null, AccessLevel.READ);
        facade.store(room);
        final String id = resident(room).getId();

        final PermissionIndex index = index();
        assertTrue(index.readableAllocatables(residentNed).contains(id),
                "in the group -> readable");

        removeUserFromGroup(residentNed, myGroup);
        final User afterLeave = facade.getUser("ned");
        index.invalidate(afterLeave);

        assertFalse(index.readableAllocatables(afterLeave).contains(id),
                "after leaving the group the resource must disappear");
    }

    /** B13: removing the allocatable drops it from the set after invalidateAll. */
    @Test
    void removedAllocatableDropsFromSet() throws Exception
    {
        final User stranger = createUser("stranger");
        final User residentStranger = facade.getUser("stranger");

        final Allocatable room = newRoom("removable-room");
        grant(room, null, null, AccessLevel.READ);
        facade.store(room);
        final Allocatable stored = resident(room);
        final String id = stored.getId();

        final PermissionIndex index = index();
        assertTrue(index.readableAllocatables(residentStranger).contains(id),
                "world-readable -> present");

        facade.remove(stored);
        index.invalidateAll();

        assertFalse(index.readableAllocatables(residentStranger).contains(id),
                "removed allocatable must drop out of the set");
    }

    /** B14: two threads racing on the first compute for a fresh user get equal, correct sets. */
    @Test
    void concurrentFirstComputeIsConsistent() throws Exception
    {
        final User monty = operator.getUser("monty");
        final PermissionIndex index = index();
        final Set<String> expected = canReadIds(controller(), monty);

        final ExecutorService exec = Executors.newFixedThreadPool(2);
        try
        {
            final CountDownLatch go = new CountDownLatch(1);
            final Future<Set<String>> f1 = exec.submit(() -> { go.await(); return index.readableAllocatables(monty); });
            final Future<Set<String>> f2 = exec.submit(() -> { go.await(); return index.readableAllocatables(monty); });
            go.countDown();
            final Set<String> r1 = f1.get(5, TimeUnit.SECONDS);
            final Set<String> r2 = f2.get(5, TimeUnit.SECONDS);
            assertEquals(r1, r2, "concurrent first-compute results must be equal");
            assertEquals(expected, r1, "concurrent compute must equal the canRead set");
        }
        finally
        {
            exec.shutdownNow();
        }
    }

    /**
     * B15: a synthetic User with a null id computes directly (no cache key),
     * returns the correct canRead set, no NPE, and repeated calls are consistent.
     *
     * <p>An unsaved facade {@code newUser()} already has a non-null id (assigned
     * at creation), so we cannot easily obtain a real null-id User here. We
     * therefore assert the explicit null-user branch: {@code readableAllocatables(null)}
     * is empty and {@code accessLevel(null, ...)} is null — and that the
     * computeReadable path is exercised directly for an uncached non-admin (its
     * repeated calls stay consistent).
     */
    @Test
    void unsavedUserComputesDirectlyNoCache() throws Exception
    {
        final PermissionIndex index = index();

        // null-user branch
        assertTrue(index.readableAllocatables(null).isEmpty(),
                "null user reads nothing");
        assertNull(index.accessLevel(null, "anything"),
                "null user has no access level");

        // direct (uncached) compute path stays consistent across repeated calls
        final User monty = operator.getUser("monty");
        final Set<String> expected = canReadIds(controller(), monty);
        final Set<String> first = index.readableAllocatables(monty);
        final Set<String> second = index.readableAllocatables(monty);
        assertEquals(expected, first);
        assertEquals(first, second, "repeated compute must be consistent");
    }

    // =======================================================================
    // C — accessLevel specifics
    // =======================================================================

    /** C16: a non-global-admin holding an ADMIN permission on the resource -> accessLevel == ADMIN. */
    @Test
    void adminGrantOnResourceReportsAdmin() throws Exception
    {
        final Category myGroup = userGroups().getCategory("my-group");

        final User lisa = createUser("lisa");
        addUserToGroup(lisa, myGroup);
        final User residentLisa = facade.getUser("lisa");
        assertFalse(residentLisa.isAdmin(), "must be a non-global-admin");

        final Allocatable room = newRoom("resource-admin-room");
        grant(room, myGroup, null, AccessLevel.ADMIN);
        facade.store(room);

        final String id = resident(room).getId();
        assertIndexEqualsCanRead(residentLisa);
        assertEquals(AccessLevel.ADMIN, index().accessLevel(residentLisa, id),
                "a resource-level ADMIN grant reports ADMIN");
    }

    /** C17: downgrading ALLOCATE -> READ is reflected after invalidateAll. */
    @Test
    void downgradeReflectedAfterInvalidate() throws Exception
    {
        final Category myGroup = userGroups().getCategory("my-group");

        final User bart = createUser("bart");
        addUserToGroup(bart, myGroup);
        final User residentBart = facade.getUser("bart");

        final Allocatable room = newRoom("downgrade-room");
        grant(room, myGroup, null, AccessLevel.ALLOCATE);
        facade.store(room);
        final String id = resident(room).getId();

        final PermissionIndex index = index();
        AccessLevel level = index.accessLevel(residentBart, id);
        assertNotNull(level);
        assertTrue(level.includes(AccessLevel.ALLOCATE), "starts with ALLOCATE");

        // downgrade the grant to plain READ
        final Allocatable edit = facade.edit(resident(room));
        for (Permission p : new ArrayList<>(edit.getPermissionList()))
        {
            edit.removePermission(p);
        }
        grant(edit, myGroup, null, AccessLevel.READ);
        facade.store(edit);

        index.invalidateAll();
        level = index.accessLevel(residentBart, id);
        assertNotNull(level, "still readable after downgrade");
        assertFalse(level.includes(AccessLevel.ALLOCATE),
                "after downgrade the level must not include ALLOCATE");
    }

    /** C18: unknown id and hidden-existing id both report null (no existence leak). */
    @Test
    void unknownVsHiddenIdentical() throws Exception
    {
        final User stranger = createUser("stranger");
        final User residentStranger = facade.getUser("stranger");

        // a resource the stranger cannot read (granted only to my-group)
        final Category myGroup = userGroups().getCategory("my-group");
        final Allocatable hidden = newRoom("hidden-room");
        grant(hidden, myGroup, null, AccessLevel.READ);
        facade.store(hidden);
        final String hiddenId = resident(hidden).getId();

        final PermissionIndex index = index();
        assertFalse(index.readableAllocatables(residentStranger).contains(hiddenId),
                "precondition: stranger cannot read the hidden resource");

        final AccessLevel unknown = index.accessLevel(residentStranger, "no-such-id");
        final AccessLevel hiddenLevel = index.accessLevel(residentStranger, hiddenId);
        assertNull(unknown, "unknown id -> null");
        assertNull(hiddenLevel, "hidden existing id -> null (byte-identical to unknown)");
        assertEquals(unknown, hiddenLevel, "unknown and hidden must be indistinguishable");
    }
}
