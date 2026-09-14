package org.rapla.storage.impl.server.readmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests (tier 1, plain JUnit, no facade) for {@link IntervalIndex}
 * using synthetic long intervals and String payloads.
 */
public class IntervalIndexTest
{
    /** A cap of 100 time-units: anything longer than 100 routes to the side-set. */
    private static final long CAP = 100L;

    private static Set<String> set(Collection<String> c)
    {
        return new HashSet<>(c);
    }

    private static Set<String> setOf(String... v)
    {
        return new HashSet<>(java.util.Arrays.asList(v));
    }

    @Test
    public void overlappingReturnsExactlyOverlappersForSeveralWindows()
    {
        IntervalIndex<String, String> idx = new IntervalIndex<>(CAP);
        // bounded blocks under key "A"
        idx.put("A", "a", 0L, 10L, false);
        idx.put("A", "b", 10L, 20L, false);
        idx.put("A", "c", 25L, 30L, false);
        idx.put("A", "d", 50L, 60L, false);

        // window [5,12): overlaps a (end 10 > 5) and b (start 10 < 12)
        assertEquals(setOf("a", "b"), set(idx.overlapping("A", 5L, 12L)));
        // window covering everything
        assertEquals(setOf("a", "b", "c", "d"), set(idx.overlapping("A", 0L, 100L)));
        // window in a gap [20,25): touches no interval
        assertEquals(setOf(), set(idx.overlapping("A", 20L, 25L)));
        // a different key is independent
        assertEquals(setOf(), set(idx.overlapping("B", 0L, 100L)));
    }

    @Test
    public void touchingEdgesDoNotOverlapHalfOpenSemantics()
    {
        IntervalIndex<String, String> idx = new IntervalIndex<>(CAP);
        idx.put("A", "a", 0L, 10L, false);
        // window [10,20): a ends exactly at 10 -> end(10) > winStart(10) is false -> no overlap
        assertEquals(setOf(), set(idx.overlapping("A", 10L, 20L)));
        // window [-5,0): a starts at 0 -> start(0) < winEnd(0) is false -> no overlap
        assertEquals(setOf(), set(idx.overlapping("A", -5L, 0L)));
        // window [9,10): end(10) > 9 and start(0) < 10 -> overlap
        assertEquals(setOf("a"), set(idx.overlapping("A", 9L, 10L)));
    }

    @Test
    public void longRunnerGoesToSideSetAndIsFoundFarFromItsStart()
    {
        IntervalIndex<String, String> idx = new IntervalIndex<>(CAP);
        // duration 500 > CAP(100) -> side-set
        idx.put("A", "long", 0L, 500L, false);
        // a few short bounded blocks so the byStart sub-range stays tight
        idx.put("A", "s", 400L, 405L, false);

        // window [450,460) is far past the long block's start(0); a tight
        // [winStart-maxDur .. winEnd) sub-range would miss it if it relied only on
        // byStart, proving the side-set is always scanned.
        assertEquals(setOf("long"), set(idx.overlapping("A", 450L, 460L)));
        // window touching only the short block
        assertEquals(setOf("long", "s"), set(idx.overlapping("A", 401L, 402L)));
    }

    @Test
    public void openEndedEntryIsFoundForAnyForwardWindow()
    {
        IntervalIndex<String, String> idx = new IntervalIndex<>(CAP);
        idx.put("A", "open", 100L, 0L, true);
        // far-forward window
        assertEquals(setOf("open"), set(idx.overlapping("A", 1_000_000L, 1_000_010L)));
        // window starting exactly at its start
        assertEquals(setOf("open"), set(idx.overlapping("A", 100L, 110L)));
        // window entirely before its start -> start(100) < winEnd(50) false -> no overlap
        assertEquals(setOf(), set(idx.overlapping("A", 0L, 50L)));
        // unbounded-end window
        assertEquals(setOf("open"), set(idx.overlapping("A", 200L, Long.MAX_VALUE)));
    }

    @Test
    public void maxBlockDurationStaysCappedAndLowerBoundStaysTight()
    {
        IntervalIndex<String, String> idx = new IntervalIndex<>(CAP);
        // a long entry (duration 500) must NOT widen maxBlockDuration: it is side-set.
        idx.put("A", "long", 0L, 500L, false);
        // a short entry that started well before (winStart - CAP) and ended before
        // winStart: it must NOT be returned, proving the lower bound is tight (CAP),
        // not widened by the long entry.
        // Choose winStart = 1000. winStart - CAP = 900. This short block starts at
        // 850 (< 900) and ends at 860 (< winStart) -> must be excluded.
        idx.put("A", "early", 850L, 860L, false);
        // a real overlapper inside the window
        idx.put("A", "hit", 1005L, 1010L, false);

        Set<String> r = set(idx.overlapping("A", 1000L, 1020L));
        assertTrue(r.contains("hit"), "the in-window block must be returned");
        // "long" is 0..500 and does not reach the [1000,1020) window -> the side-set
        // is scanned but the long-runner still fails the actual overlap test.
        assertTrue(!r.contains("long"), "the long-runner does not overlap this window");
        assertTrue(!r.contains("early"),
                "a short block before winStart-CAP that ended before winStart must be excluded");
        assertEquals(setOf("hit"), r);
    }

    @Test
    public void boundedBlockStartingBeforeWindowButOverlappingIsReturned()
    {
        IntervalIndex<String, String> idx = new IntervalIndex<>(CAP);
        // duration 80 (<= CAP) so it stays in byStart and bumps maxBlockDuration to 80.
        idx.put("A", "spanning", 940L, 1020L, false);
        // window [1000,1010): spanning starts at 940 = winStart-60 (>= winStart-80) and
        // ends at 1020 > 1000 -> overlap; the tight lower bound (winStart-80=920) keeps it.
        assertEquals(setOf("spanning"), set(idx.overlapping("A", 1000L, 1010L)));
    }

    @Test
    public void removeDeletesAndDoubleRemoveIsNoOp()
    {
        IntervalIndex<String, String> idx = new IntervalIndex<>(CAP);
        idx.put("A", "a", 0L, 10L, false);
        idx.put("A", "long", 0L, 500L, false);
        idx.put("A", "open", 100L, 0L, true);

        // window [0,10): bounded "a" overlaps, and the long-runner (0..500) also
        // overlaps (end 500 > 0 && start 0 < 10).
        assertEquals(setOf("a", "long"), set(idx.overlapping("A", 0L, 10L)));
        idx.remove("A", "a", 0L, 10L, false);
        assertEquals(setOf("long"), set(idx.overlapping("A", 0L, 10L)));
        // double remove: no-op, no exception
        idx.remove("A", "a", 0L, 10L, false);
        assertEquals(setOf("long"), set(idx.overlapping("A", 0L, 10L)));

        // remove side-set entries
        idx.remove("A", "long", 0L, 500L, false);
        idx.remove("A", "open", 100L, 0L, true);
        assertEquals(setOf(), set(idx.overlapping("A", Long.MIN_VALUE, Long.MAX_VALUE)));
        // removing from an unknown key is a no-op
        idx.remove("ZZ", "x", 0L, 1L, false);
    }

    @Test
    public void distinctEntriesWithSameStartAreBothRetained()
    {
        IntervalIndex<String, String> idx = new IntervalIndex<>(CAP);
        // two distinct payloads, identical start, different ends -> identity tiebreaker
        // must keep both (no dedup by start).
        idx.put("A", "x", 5L, 10L, false);
        idx.put("A", "y", 5L, 15L, false);
        // also two with identical start AND end
        idx.put("A", "p", 5L, 8L, false);
        idx.put("A", "q", 5L, 8L, false);

        assertEquals(setOf("x", "y", "p", "q"), set(idx.overlapping("A", 5L, 16L)));

        // removing one equal-start entry leaves the others
        idx.remove("A", "x", 5L, 10L, false);
        assertEquals(setOf("y", "p", "q"), set(idx.overlapping("A", 5L, 16L)));
    }

    @Test
    public void unboundedWindowBothSidesReturnsEverything()
    {
        IntervalIndex<String, String> idx = new IntervalIndex<>(CAP);
        idx.put("A", "a", -1000L, -990L, false);
        idx.put("A", "long", 0L, 5000L, false);
        idx.put("A", "open", 9000L, 0L, true);
        assertEquals(setOf("a", "long", "open"),
                set(idx.overlapping("A", Long.MIN_VALUE, Long.MAX_VALUE)));
    }

    /**
     * PRD 086 window-first — the singleton-key usage (everything filed under one GLOBAL key) gives a
     * global "all values overlapping the window" query with no per-key iteration. Open-ended entries
     * match any later window; remove drops them.
     */
    @Test
    public void singletonKeyActsAsGlobalWindowQuery()
    {
        final Object GLOBAL = new Object();
        IntervalIndex<Object, String> idx = new IntervalIndex<>(CAP);
        idx.put(GLOBAL, "a", 100L, 200L, false);
        idx.put(GLOBAL, "b", 150L, 300L, false);
        idx.put(GLOBAL, "c", 400L, 500L, false);
        idx.put(GLOBAL, "open", 250L, 0L, true);   // open-ended from 250

        assertEquals(setOf("a", "b"), set(idx.overlapping(GLOBAL, 120L, 160L)));
        assertEquals(setOf("c", "open"), set(idx.overlapping(GLOBAL, 450L, 470L)));   // c + open-ended matches a later window
        assertEquals(setOf(), set(idx.overlapping(GLOBAL, 0L, 50L)));                  // before every interval

        idx.remove(GLOBAL, "open", 250L, 0L, true);
        assertEquals(setOf("c"), set(idx.overlapping(GLOBAL, 450L, 470L)));            // open removed; only c
    }
}
