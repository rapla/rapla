package org.rapla.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.Ownable;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.test.util.FacadeTestSupport;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 backfill (PRD 017 Phase 4 #11) for the
 * {@link org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl}
 * + {@link PermissionController} matrix.
 *
 * <p>testdefault.xml fixture used:
 * <ul>
 *   <li>{@code homer} — admin, owner of two resources</li>
 *   <li>{@code monty} — non-admin, member of {@code my-group} + {@code powerplant}</li>
 *   <li>resources mostly carry the default {@code allocate_conflicts} permission
 *       (everyone can read + allocate), some have a homer owner</li>
 *   <li>dynamic types can only be edited by admins (admin-gated by
 *       {@code RaplaDefaultPermissionImpl.hasAccess} on {@code DynamicType.class})</li>
 * </ul>
 */
class PermissionMatrixTest extends FacadeTestSupport
{
    private PermissionController controller;
    private User admin;
    private User nonAdmin;

    @BeforeEach
    void resolveActors() throws Exception
    {
        controller = facade.getPermissionController();

        for (User u : facade.getUsers())
        {
            if (u.isAdmin() && admin == null) admin = u;
            else if (!u.isAdmin() && nonAdmin == null) nonAdmin = u;
        }
        assertNotNull(admin, "fixture must include an admin user (homer)");
        assertNotNull(nonAdmin, "fixture must include a non-admin user (monty)");
    }

    // ---------- admin shortcut: every check passes ----------

    @Test
    void adminCanModifyEveryAllocatable() throws Exception
    {
        for (Allocatable a : facade.getAllocatables())
        {
            assertTrue(controller.canModify(a, admin),
                    "admin must be able to modify " + a.getId());
        }
    }

    @Test
    void adminCanAdminEveryAllocatable() throws Exception
    {
        for (Allocatable a : facade.getAllocatables())
        {
            assertTrue(controller.canAdmin(a, admin),
                    "admin must be able to admin " + a.getId());
        }
    }

    @Test
    void adminCanDeleteEveryAllocatable() throws Exception
    {
        for (Allocatable a : facade.getAllocatables())
        {
            assertTrue(controller.canDelete(a, admin),
                    "admin must be able to delete " + a.getId());
        }
    }

    @Test
    void adminCanCreateAndReadAllDynamicTypes() throws Exception
    {
        for (DynamicType type : facade.getDynamicTypes(null))
        {
            assertTrue(controller.canCreate(type, admin), "admin canCreate " + type.getKey());
            assertTrue(controller.canRead(type, admin), "admin canRead " + type.getKey());
        }
    }

    // ---------- non-admin: default-permission read/allocate granted ----------

    @Test
    void nonAdminCanReadDefaultGrantedAllocatables() throws Exception
    {
        // The fixture's default permission is `allocate_conflicts` which
        // includes ALLOCATE → REQUEST → READ. So every allocatable should
        // be readable by everyone, including non-admin monty.
        Allocatable[] all = facade.getAllocatables();
        assertTrue(all.length > 0, "fixture must have allocatables");

        long readableCount = 0;
        for (Allocatable a : all)
        {
            if (controller.canReadInformation(a, nonAdmin)) readableCount++;
        }
        assertTrue(readableCount > 0, "non-admin should be able to read at least one allocatable");
    }

    @Test
    void nonAdminCanAllocateDefaultGrantedResources() throws Exception
    {
        Allocatable[] all = facade.getAllocatables();
        long allocatableCount = 0;
        for (Allocatable a : all)
        {
            if (controller.canAllocate(a, nonAdmin, LocalDate.now())) allocatableCount++;
        }
        assertTrue(allocatableCount > 0,
                "non-admin should be able to allocate at least one default-granted resource");
    }

    // ---------- non-admin: blocked from EDIT / ADMIN ----------

    @Test
    void nonAdminCannotModifyResourceTheyDontOwn() throws Exception
    {
        // Find an allocatable not owned by monty (should be all of them in this fixture).
        for (Allocatable a : facade.getAllocatables())
        {
            if (a instanceof Ownable && PermissionController.isOwner((Ownable) a, nonAdmin))
            {
                continue;
            }
            assertFalse(controller.canModify(a, nonAdmin),
                    "non-admin must not be able to modify " + a.getId() + " — no ownership / EDIT / ADMIN grant");
        }
    }

    @Test
    void nonAdminCannotAdminUnownedResource() throws Exception
    {
        for (Allocatable a : facade.getAllocatables())
        {
            if (a instanceof Ownable && PermissionController.isOwner((Ownable) a, nonAdmin))
            {
                continue;
            }
            assertFalse(controller.canAdmin(a, nonAdmin),
                    "non-admin must not be able to admin unowned " + a.getId());
        }
    }

    @Test
    void nonAdminCannotCreateInstancesOfRestrictedDynamicTypes() throws Exception
    {
        // Types in the fixture that DON'T explicitly grant CREATE (room,
        // lecturer, resource1, resource2 — only carry read_type/allocate_conflicts).
        // The `event` type DOES grant CREATE to everyone, so it's allowed for
        // non-admins by design — verify that's a no-op for this test.
        boolean foundRestrictedType = false;
        for (DynamicType type : facade.getDynamicTypes(null))
        {
            if (controller.canCreate(type, admin)
                    && !controller.canCreate(type, nonAdmin))
            {
                foundRestrictedType = true;
            }
        }
        assertTrue(foundRestrictedType,
                "fixture must include at least one type that admin can create but non-admin cannot");
    }

    // ---------- isOwner semantics ----------

    @Test
    void ownerCheckMatchesOwnerRefId() throws Exception
    {
        // Find a resource with an owner; verify isOwner is true for that user
        // and false for the other. The fixture has 2 resources owned by homer
        // (the admin) — so we test the pure-static isOwner predicate against
        // both possible users.
        boolean foundOwned = false;
        for (Allocatable a : facade.getAllocatables())
        {
            if (a.getOwnerRef() == null) continue;
            foundOwned = true;

            if (a.getOwnerRef().isSame(admin.getReference()))
            {
                assertTrue(PermissionController.isOwner(a, admin),
                        "isOwner should be true when ownerRef matches user");
                assertFalse(PermissionController.isOwner(a, nonAdmin),
                        "isOwner should be false for the other user");
            }
        }
        assertTrue(foundOwned, "fixture must include at least one owned allocatable");
    }

    @Test
    void isOwnerFalseWhenOwnerRefMissing() throws Exception
    {
        // Resources without an explicit owner attribute should always return
        // false from isOwner — regardless of who's asking.
        boolean foundUnowned = false;
        for (Allocatable a : facade.getAllocatables())
        {
            if (a.getOwnerRef() != null) continue;
            foundUnowned = true;
            assertFalse(PermissionController.isOwner(a, admin),
                    "isOwner false when no owner: " + a.getId());
            assertFalse(PermissionController.isOwner(a, nonAdmin),
                    "isOwner false when no owner: " + a.getId());
        }
        assertTrue(foundUnowned, "fixture must include at least one unowned allocatable");
    }

    // ---------- create-reservation gate ----------

    @Test
    void canCreateReservationGate()
    {
        // Admin always can. Non-admin needs the create-events group OR similar.
        assertTrue(controller.canCreateReservation(admin),
                "admin must be able to create reservations");
        // Don't assert on non-admin: depends on group setup which the fixture
        // may or may not grant. The point of THIS test is that admin path works
        // and the call itself doesn't NPE on either user.
        controller.canCreateReservation(nonAdmin);
    }
}
