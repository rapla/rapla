package org.rapla.storage.impl.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ReferenceInfo;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 audit tests for {@link EntityHistory}. Targets caching/history bugs
 * the user flagged: stale references, NPE-on-unknown-id, history-list
 * lifecycle. Uses stub JSON to avoid the JSON parser path (which requires
 * real entity impls); tests that don't probe entity-deserialization paths.
 */
class EntityHistoryAuditTest
{
    /**
     * Audit case 4 — {@link EntityHistory#getLastChangedUntil(ReferenceInfo, LocalDateTime)}
     * NPEs when the id has no history.
     *
     * <p>Suspected defect: line 304 of EntityHistory:
     * {@code synchronized(list)} where {@code list = map.get(id)}. Without a
     * null guard, calling on an unknown id throws NPE inside the synchronized
     * statement.
     *
     * <p>Reachable in production: {@code RaplaSQL.java:2980} calls
     * {@code history.getLastChangedUntil(id, connectionTimestamp)} during DB
     * refresh, without first checking {@code hasHistory(id)}. When refresh
     * encounters an entity whose history was pruned or never recorded, the
     * refresh fails with NPE rather than skipping the id.
     */
    @Test
    @DisplayName("getLastChangedUntil should not NPE on unknown id (should return null instead)")
    void getLastChangedUntilOnUnknownIdDoesNotNPE()
    {
        EntityHistory history = new EntityHistory(/* resolver= */ null);
        ReferenceInfo<Reservation> unknown = new ReferenceInfo<>("never-recorded", Reservation.class);

        assertFalse(history.hasHistory(unknown),
                "precondition: id should not be in history");
        assertDoesNotThrow(() -> {
            EntityHistory.HistoryEntry e = history.getLastChangedUntil(unknown, LocalDateTime.now());
            assertNull(e, "BUG: getLastChangedUntil(unknownId, _) should return null (not NPE)");
        }, "BUG: getLastChangedUntil NPEs on unknown id — RaplaSQL refresh path can hit this "
        +  "when an id is in allIds but its history was pruned");
    }

    /**
     * Audit case 5 — {@link EntityHistory#removeUnneeded} keeps at least one
     * entry per id (this is the implementation contract; pin it).
     */
    @Test
    @DisplayName("removeUnneeded always retains at least one history entry per id")
    void removeUnneededAlwaysKeepsAtLeastOneEntry()
    {
        EntityHistory history = new EntityHistory(null);
        ReferenceInfo<Reservation> id = new ReferenceInfo<>("r-1", Reservation.class);

        // Add three entries with timestamps t=10, 20, 30
        history.addHistoryEntry(id, "{\"v\":1}", LocalDateTime.of(2026, 1, 1, 0, 0, 10), false);
        history.addHistoryEntry(id, "{\"v\":2}", LocalDateTime.of(2026, 1, 1, 0, 0, 20), false);
        history.addHistoryEntry(id, "{\"v\":3}", LocalDateTime.of(2026, 1, 1, 0, 0, 30), false);

        assertEquals(3, history.getHistoryList(id).size(), "precondition: 3 entries");

        // Prune everything before t=100s. Should keep ONE entry (the most recent).
        history.removeUnneeded(LocalDateTime.of(2026, 1, 1, 0, 1, 40));
        List<EntityHistory.HistoryEntry> remaining = history.getHistoryList(id);
        assertNotNull(remaining, "history list must not be removed from the map by pruning");
        assertEquals(1, remaining.size(), "pruning must keep exactly 1 most-recent entry");
        long retainedMs = remaining.get(0).getTimestamp();
        long expectedMs = org.rapla.components.util.DateTools.toMilli(
                LocalDateTime.of(2026, 1, 1, 0, 0, 30));
        assertEquals(expectedMs, retainedMs,
                "the retained entry must be the most recent (t=30s in fixture)");
    }

    /**
     * Audit case 6 — out-of-order timestamp inserts maintain sorted order.
     */
    @Test
    @DisplayName("inserts with decreasing timestamps end up sorted by timestamp")
    void outOfOrderInsertsAreSorted()
    {
        EntityHistory history = new EntityHistory(null);
        ReferenceInfo<Reservation> id = new ReferenceInfo<>("r-ooo", Reservation.class);

        // Insert in reverse order (decreasing timestamps) — exercises the recursive
        // insert() with index decrement.
        for (int i = 100; i >= 1; i--)
        {
            history.addHistoryEntry(id, "{\"v\":" + i + "}",
                    LocalDateTime.of(2026, 1, 1, 0, 0).plusSeconds(i), false);
        }

        List<EntityHistory.HistoryEntry> list = history.getHistoryList(id);
        assertEquals(100, list.size(), "all 100 inserts should land");
        for (int i = 0; i < list.size() - 1; i++)
        {
            assertTrue(list.get(i).getTimestamp() <= list.get(i + 1).getTimestamp(),
                    "list must remain sorted by timestamp; violation at index " + i);
        }
    }

    /**
     * Audit case 7 — pruning leaves a stale tombstone-only history entry for
     * a long-deleted id. This documents the contract (the map entry survives
     * forever even after the entity is deleted and the pre-delete versions
     * pruned). Memory leak per deleted entity in long-lived servers.
     */
    @Test
    @DisplayName("pruned-then-deleted entity still occupies a history map slot indefinitely")
    void deletedEntityHistoryLeaksMapSlot()
    {
        EntityHistory history = new EntityHistory(null);
        ReferenceInfo<Reservation> id = new ReferenceInfo<>("r-zombie", Reservation.class);

        history.addHistoryEntry(id, "{\"v\":1}", LocalDateTime.of(2026, 1, 1, 0, 0, 10), false);
        history.addHistoryEntry(id, "{\"v\":2}", LocalDateTime.of(2026, 1, 1, 0, 0, 20), true /* isDelete */);

        // Prune past both — the implementation contract keeps the last entry,
        // which is now a delete tombstone. The id slot in the map never goes away.
        history.removeUnneeded(LocalDateTime.of(2030, 1, 1, 0, 0));
        assertTrue(history.hasHistory(id),
                "the delete tombstone keeps the entity's history slot alive forever — "
              + "documented behavior, but a real memory leak for IDs that churn create/delete");
        assertEquals(1, history.getHistoryList(id).size());
        assertTrue(history.getHistoryList(id).get(0).isDelete(),
                "the surviving entry should be the delete tombstone");
    }
}
