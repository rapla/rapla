package org.rapla.storage.impl.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.storage.PermissionController;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.test.util.FacadeTestSupport;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tier-2 security regression for the allocatable-merge write-permission gap
 * (the {@code // FIXME check write permissions} in
 * {@link LocalAbstractCachableOperator#merge}).
 *
 * <p>A merge deletes the merged-away allocatables and rewrites every reference
 * to point at the target. {@code RemoteStorageController.doMerge} only checks
 * write permission on the merge <em>target</em>, so without an operator-side
 * guard a non-admin who can write to one resource could destroy resources they
 * cannot modify by merging them away. This test merges a resource the non-admin
 * may not modify and asserts the operation is rejected and the resource survives.
 */
class AllocatableMergePermissionTest extends FacadeTestSupport
{
    private User admin;
    private User nonAdmin;

    @BeforeEach
    void resolveActors() throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (u.isAdmin() && admin == null) admin = u;
            else if (!u.isAdmin() && nonAdmin == null) nonAdmin = u;
        }
        assertNotNull(admin, "fixture must include an admin user (homer)");
        assertNotNull(nonAdmin, "fixture must include a non-admin user (monty)");
    }

    @Test
    void nonAdminCannotMergeAwayResourceTheyCannotModify() throws Exception
    {
        PermissionController pc = facade.getPermissionController();
        Allocatable forbidden = null;
        Allocatable target = null;
        for (Allocatable a : facade.getAllocatables())
        {
            if (forbidden == null && !pc.canModify(a, nonAdmin)) forbidden = a;
            else if (target == null) target = a;
        }
        assertNotNull(forbidden, "fixture must contain a resource the non-admin cannot modify");
        assertNotNull(target, "fixture must contain a second resource to merge into");

        Set<ReferenceInfo<Allocatable>> mergeAway = Collections.singleton(forbidden.getReference());
        final ReferenceInfo<Allocatable> forbiddenRef = forbidden.getReference();
        final Allocatable mergeTarget = target;

        assertThrows(RaplaSecurityException.class,
                () -> operator.doMergeSync(mergeTarget, mergeAway, nonAdmin),
                "merging away a resource the user cannot modify must be rejected");

        assertNotNull(operator.tryResolve(forbiddenRef),
                "an unauthorized merge must not have deleted the resource");
    }
}
