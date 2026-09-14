package org.rapla.storage.impl.server.readmodel;

import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.storage.PermissionController;
import org.rapla.test.util.FacadeTestSupport;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 (FacadeTestSupport, real entities from testdefault.xml). The headline
 * guarantee is the AGENTS.md §12 equivalence: {@code readableAllocatables(user)}
 * must equal exactly the id set {@code PermissionController.canRead} would
 * accept over all allocatables — never more.
 */
class PermissionIndexTest extends FacadeTestSupport
{
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

    @Test
    void readableSetEqualsCanReadForEveryUser() throws Exception
    {
        final PermissionController controller = controller();
        final PermissionIndex index = index();

        final Collection<User> users = operator.getUsers();
        assertFalse(users.isEmpty(), "fixture must have users");

        for (User user : users)
        {
            final Set<String> expected = canReadIds(controller, user);
            final Set<String> actual = index.readableAllocatables(user);
            assertEquals(expected, actual,
                    "index readable set must equal canRead set for user " + user.getId());
        }
    }

    @Test
    void adminReadsEveryAllocatable() throws Exception
    {
        final User homer = operator.getUser("homer");
        assertNotNull(homer);
        assertTrue(homer.isAdmin(), "homer is the fixture admin");

        final Set<String> all = new LinkedHashSet<>();
        for (Allocatable a : operator.getAllocatables(null))
        {
            all.add(a.getId());
        }
        assertEquals(all, index().readableAllocatables(homer));
    }

    @Test
    void nonAdminSeesSubsetOfAdmin() throws Exception
    {
        final User homer = operator.getUser("homer");
        final User monty = operator.getUser("monty");
        assertNotNull(monty);
        assertFalse(monty.isAdmin(), "monty is the fixture non-admin");

        final PermissionIndex index = index();
        final Set<String> adminSet = index.readableAllocatables(homer);
        final Set<String> montySet = index.readableAllocatables(monty);

        assertTrue(adminSet.containsAll(montySet),
                "non-admin readable set must be a subset of admin's");
    }

    @Test
    void accessLevelMatchesCanReadGate() throws Exception
    {
        final User monty = operator.getUser("monty");
        final PermissionController controller = controller();
        final PermissionIndex index = index();

        for (Allocatable a : operator.getAllocatables(null))
        {
            final AccessLevel level = index.accessLevel(monty, a.getId());
            if (controller.canRead(a, monty))
            {
                assertNotNull(level, "readable allocatable must report a level: " + a.getId());
                assertTrue(level.includes(AccessLevel.READ),
                        "reported level must be at least READ for " + a.getId());
            }
            else
            {
                assertNull(level, "unreadable allocatable must report null level: " + a.getId());
            }
        }
    }

    @Test
    void accessLevelHidesUnknownAndUnreadableIdentically() throws Exception
    {
        final User monty = operator.getUser("monty");
        final PermissionIndex index = index();

        // unknown id -> null (no existence leak)
        assertNull(index.accessLevel(monty, "no-such-allocatable-id"));

        // any id monty cannot read -> also null
        final PermissionController controller = controller();
        for (Allocatable a : operator.getAllocatables(null))
        {
            if (!controller.canRead(a, monty))
            {
                assertNull(index.accessLevel(monty, a.getId()),
                        "hidden id must be indistinguishable from unknown id");
            }
        }
    }

    @Test
    void nullUserReadsNothing()
    {
        assertTrue(index().readableAllocatables(null).isEmpty());
        assertNull(index().accessLevel(null, "anything"));
    }

    @Test
    void invalidateForcesRecompute() throws Exception
    {
        final User monty = operator.getUser("monty");
        final PermissionIndex index = index();

        final Set<String> first = index.readableAllocatables(monty);
        // cached call returns an equal set
        assertEquals(first, index.readableAllocatables(monty));

        index.invalidate(monty);
        final Set<String> afterInvalidate = index.readableAllocatables(monty);
        assertEquals(first, afterInvalidate, "recompute must yield the same set when nothing changed");

        index.invalidateAll();
        assertEquals(first, index.readableAllocatables(monty));
    }
}
