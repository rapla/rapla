package org.rapla.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.Conflict;
import org.rapla.facade.internal.ConflictImpl;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 audit tests against {@link LocalCache} that exercise package-private
 * state directly — no facade, no operator, no SAX parser. Each test pins a
 * single contract about cache cleanup that the higher-tier
 * {@code CacheStaleReferenceAuditTest} can't see precisely (because the facade
 * may mask or augment the cache state).
 */
class LocalCacheAuditTest
{
    private LocalCache newCache()
    {
        PermissionController pc = new PermissionController(Collections.emptySet(), null);
        return new LocalCache(pc);
    }

    /**
     * Audit case 2 — {@code conflictLastChanged} entry leaks on conflict
     * removal.
     *
     * <p>Suspected defect: {@link LocalCache#removeWithId} for type
     * {@link Conflict} clears {@code disabledConflictApp1} and
     * {@code disabledConflictApp2} but leaves a stale entry in
     * {@code conflictLastChanged}. Per-conflict timestamp leak per remove.
     */
    @Test
    @DisplayName("removing a conflict clears its conflictLastChanged entry")
    void conflictLastChangedClearedOnRemove() throws Exception
    {
        LocalCache cache = newCache();

        // Build a minimal valid conflict id: CONFLICT;<alloc>;<app1>;<app2>
        String allocId = "alloc-1";
        String app1Id  = "app-1";
        String app2Id  = "app-2";
        String conflictId = "CONFLICT;" + allocId + ";" + app1Id + ";" + app2Id;

        LocalDateTime today       = LocalDateTime.of(2026, 5, 10, 12, 0);
        LocalDateTime lastChanged = LocalDateTime.of(2026, 5, 10, 12, 0);
        ConflictImpl conflict = new ConflictImpl(conflictId, today, lastChanged);
        // Disable one appointment so the disabled-app and lastChanged maps populate.
        conflict.setAppointment1Enabled(false);

        cache.put(conflict);

        // Pre-condition: maps populated.
        assertTrue(cache.disabledConflictApp1.containsKey(conflictId),
                "disabledConflictApp1 should contain the disabled conflict");
        assertTrue(cache.conflictLastChanged.containsKey(conflictId),
                "conflictLastChanged should be populated for a conflict with a disabled appointment");

        // Remove the conflict.
        ReferenceInfo<Conflict> ref = new ReferenceInfo<>(conflictId, Conflict.class);
        cache.removeWithId(ref);

        // Cleanup contract: ALL three conflict-side maps must clear the id.
        assertFalse(cache.disabledConflictApp1.containsKey(conflictId),
                "disabledConflictApp1 should be cleared on remove");
        assertFalse(cache.disabledConflictApp2.containsKey(conflictId),
                "disabledConflictApp2 should be cleared on remove");
        assertFalse(cache.conflictLastChanged.containsKey(conflictId),
                "BUG: conflictLastChanged is NOT cleared on remove — "
              + "LocalCache.removeWithId for Conflict only clears the two disabled-app maps. "
              + "Stale id=" + conflictId);
    }

    /**
     * Audit case 3 — graph stale outgoing edge after attribute update.
     *
     * <p>Suspected defect: {@link LocalCache.GraphNode#removeConnections}
     * with {@code onlyOutgoing=true} clears the back-references on TARGET
     * nodes but leaves THIS node's own outgoing entries. After updating an
     * Allocatable's BelongsTo / Packages target, the source's
     * {@code connections} map keeps the stale outgoing entry, and
     * {@code getDependent()} traversals can report ghost dependents.
     */
    @Test
    @DisplayName("removeConnections(onlyOutgoing=true) clears source's own outgoing edges")
    void graphRemoveOutgoingClearsSourceMap()
    {
        ReferenceInfo<Allocatable> refA = new ReferenceInfo<>("alloc-A", Allocatable.class);
        ReferenceInfo<Allocatable> refB = new ReferenceInfo<>("alloc-B", Allocatable.class);

        LocalCache.GraphNode nodeA = new LocalCache.GraphNode(refA);
        LocalCache.GraphNode nodeB = new LocalCache.GraphNode(refB);

        // A → B as BelongsTo, plus the back-ref B → A as BelongsToTarget
        nodeA.addConnection(nodeB, LocalCache.GraphNode.ConnectionType.BelongsTo);
        nodeB.addConnection(nodeA, LocalCache.GraphNode.ConnectionType.BelongsToTarget);

        // Simulate the "A's classification updated; old outgoing target removed" call.
        // updateDependencies() in LocalCache.put(Allocatable) does exactly this.
        nodeA.removeConnections(/* onlyOutgoing= */ true);

        // The back-ref on B is correctly cleared.
        assertFalse(nodeB.connections.containsKey(nodeA),
                "back-ref on target should be cleared by removeConnections(true)");

        // The source's own outgoing edge SHOULD also be cleared but isn't —
        // removeConnections only iterates and calls connection.removeConnection(this);
        // it never removes from THIS node's own map.
        assertFalse(nodeA.connections.containsKey(nodeB),
                "BUG: source's own outgoing edge to B is still present after "
              + "removeConnections(onlyOutgoing=true). LocalCache.GraphNode.removeConnections "
              + "clears back-refs on targets but never clears this node's own connections "
              + "map. After an Allocatable update, getDependent(A) walks through stale "
              + "outgoing edges and reports dependencies that no longer exist.");
    }
}
