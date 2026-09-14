package org.rapla.storage.impl.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.Conflict;
import org.rapla.test.util.FacadeTestSupport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 performance / stress test for {@code ConflictFinder} +
 * {@code AppointmentImpl.overlapsAppointment} under realistic load.
 * Tagged {@code @Tag("perf")} so it's excluded from the default
 * {@code mvn test} lane (run with {@code -Dtest.excludedGroups=}
 * or {@code -Dtest.excludedGroups=db,e2e}).
 *
 * <p>Builds {@value #RESERVATION_COUNT} reservations across a small pool of
 * allocatables (forcing genuine conflicts), with the patterns mixed:
 * <ul>
 *   <li>~30 % single-occurrence (1-2 h slots)</li>
 *   <li>~30 % DAILY repeating (number 10-30)</li>
 *   <li>~20 % WEEKLY repeating (number 4-12)</li>
 *   <li>~15 % MONTHLY repeating (number 3-6)</li>
 *   <li>~5 % with exceptions sprinkled in</li>
 * </ul>
 *
 * <p>Measurements are printed to stdout for the human reader and asserted
 * against generous sanity ceilings — a 10× regression in conflict
 * detection will fail the test, but normal noise won't.
 */
@Tag("perf")
class ConflictPerformanceTest extends FacadeTestSupport
{
    private static final int RESERVATION_COUNT = 1000;
    private static final long RANDOM_SEED = 20260510L;

    // Generous sanity ceilings — flag order-of-magnitude regressions, not noise.
    private static final long BULK_DISPATCH_BUDGET_MS = 60_000;
    private static final long GET_CONFLICTS_BUDGET_MS = 15_000;
    private static final long PER_RESERVATION_QUERY_BUDGET_MS = 5_000;
    private static final long INCREMENTAL_STORE_BUDGET_MS = 10_000;

    private User actingUser;
    private DynamicType eventType;
    private List<Allocatable> allocatablePool;
    private List<Reservation> stored;

    @BeforeEach
    void setupFixture() throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) { actingUser = u; break; }
        }
        assertNotNull(actingUser, "fixture must include an admin");

        DynamicType[] reservationTypes =
                facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        assertTrue(reservationTypes.length > 0);
        eventType = reservationTypes[0];

        allocatablePool = new ArrayList<>();
        Collections.addAll(allocatablePool, facade.getAllocatables());
        assertTrue(allocatablePool.size() >= 2,
                "need ≥ 2 allocatables to force conflicts; fixture has " + allocatablePool.size());

        stored = new ArrayList<>(RESERVATION_COUNT);
    }

    // ---------- fixture builder ----------

    private List<Reservation> buildFixture(int n) throws Exception
    {
        Random rng = new Random(RANDOM_SEED);
        List<Reservation> out = new ArrayList<>(n);

        LocalDateTime windowStart = LocalDateTime.parse("2030-01-01T08:00");

        for (int i = 0; i < n; i++)
        {
            if (i > 0 && i % 200 == 0)
            {
                liveSign("  ... built " + i + " / " + n);
            }
            // Distribute across a 6-month window.
            int dayOffset = rng.nextInt(180);
            int hourOffset = 8 + rng.nextInt(8); // 08:00–16:00 starts
            int durationHours = 1 + rng.nextInt(3); // 1-3 h slots

            LocalDateTime start = windowStart.plusDays(dayOffset).withHour(hourOffset).withMinute(0);
            LocalDateTime end = start.plusHours(durationHours);

            Classification c = eventType.newClassification();
            if (c.getType().getAttribute("name") != null)
            {
                c.setValue("name", "perf-" + i);
            }
            Reservation r = facade.newReservation(c, actingUser);

            Appointment a = facade.newAppointmentWithUser(start, end, actingUser);
            applyRandomRepeating(rng, a, i);
            r.addAppointment(a);

            // Pin to 1 of the small allocatable pool — forces conflicts
            r.addAllocatable(allocatablePool.get(rng.nextInt(allocatablePool.size())));
            // Some reservations bind a second allocatable too
            if (rng.nextInt(4) == 0)
            {
                r.addAllocatable(allocatablePool.get(rng.nextInt(allocatablePool.size())));
            }
            out.add(r);
        }
        return out;
    }

    private void applyRandomRepeating(Random rng, Appointment a, int seq)
    {
        int roll = rng.nextInt(100);
        if (roll < 30)
        {
            // single — leave as-is
            return;
        }
        a.setRepeatingEnabled(true);
        Repeating r = a.getRepeating();
        if (roll < 60)
        {
            r.setType(RepeatingType.DAILY);
            r.setNumber(10 + rng.nextInt(21)); // 10-30 days
        }
        else if (roll < 80)
        {
            r.setType(RepeatingType.WEEKLY);
            r.setNumber(4 + rng.nextInt(9)); // 4-12 weeks
        }
        else if (roll < 95)
        {
            r.setType(RepeatingType.MONTHLY);
            r.setNumber(3 + rng.nextInt(4)); // 3-6 months
        }
        else
        {
            // 5 %: long-running daily with exceptions sprinkled
            r.setType(RepeatingType.DAILY);
            r.setNumber(60 + rng.nextInt(60)); // 60-120 days
            int exceptions = 2 + rng.nextInt(4);
            for (int e = 0; e < exceptions; e++)
            {
                LocalDateTime ex = a.getStart().plusDays(rng.nextInt(60));
                r.addException(ex.withHour(0).withMinute(0));
            }
        }
        // Tag a few with seq so debug logs can find them
        if (seq % 200 == 0)
        {
            r.setNumber(r.getNumber() + 1);
        }
    }

    private static long timeMs(ThrowingRunnable r) throws Exception
    {
        long t0 = System.nanoTime();
        r.run();
        return (System.nanoTime() - t0) / 1_000_000L;
    }

    /** Print to stderr + flush — surefire passes stderr through live, so the
     *  human runner sees progress during multi-second phases instead of
     *  staring at a blank console. */
    private static void liveSign(String msg)
    {
        System.err.println(msg);
        System.err.flush();
    }

    private static long phase(String label, ThrowingRunnable r) throws Exception
    {
        liveSign("[perf] " + label + " — start");
        long ms = timeMs(r);
        liveSign("[perf] " + label + " — done in " + ms + " ms");
        return ms;
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }

    // ---------- measurements ----------

    @Test
    @DisplayName("perf: bulk store + conflict graph + queries on " + RESERVATION_COUNT + " reservations")
    void bulkDispatchAndConflictQueriesUnderLoad() throws Exception
    {
        liveSign("[perf] === ConflictPerformanceTest starting (~10–20 s expected) ===");

        phase("phase 1/6: build " + RESERVATION_COUNT + " reservations in memory",
                () -> stored.addAll(buildFixture(RESERVATION_COUNT)));

        // NOTE: `facade.dispatch(...)` (async) routes through
        // `LocalAbstractCachableOperator.storeAndRemoveAsync(...)` which is an
        // EMPTY STUB — returns an OK promise without persisting. Real bug
        // surfaced by this test, captured in PRD 017. Use storeObjects(T[]),
        // the SYNC bulk path that actually works.
        Reservation[] storeArray = stored.toArray(new Reservation[0]);
        long dispatchMs = phase("phase 2/6: bulk storeObjects " + RESERVATION_COUNT,
                () -> facade.storeObjects(storeArray));
        assertTrue(dispatchMs < BULK_DISPATCH_BUDGET_MS,
                "bulk store took " + dispatchMs + " ms; budget " + BULK_DISPATCH_BUDGET_MS);

        // Sanity: confirm the reservations actually landed.
        long postStoreReservations = waitFor(facade.getReservations(actingUser,
                LocalDateTime.parse("2030-01-01T00:00"),
                LocalDateTime.parse("2031-01-01T00:00"), null)).size();
        liveSign("[perf]   post-store facade.getReservations count = " + postStoreReservations);
        assertTrue(postStoreReservations >= RESERVATION_COUNT * 0.9,
                "expected ≥ 90 % of " + RESERVATION_COUNT + " stored, got " + postStoreReservations);

        long[] conflictCount = {0};
        long getConflictsMs = phase("phase 3/6: facade.getConflicts()", () -> {
            Collection<Conflict> conflicts = waitFor(facade.getConflicts());
            conflictCount[0] = conflicts.size();
        });
        liveSign("[perf]   conflict count = " + conflictCount[0]);
        assertTrue(getConflictsMs < GET_CONFLICTS_BUDGET_MS,
                "getConflicts took " + getConflictsMs + " ms; budget " + GET_CONFLICTS_BUDGET_MS);
        assertTrue(conflictCount[0] > 0,
                "with " + RESERVATION_COUNT + " reservations on " + allocatablePool.size()
                        + " allocatables, conflict count must be > 0");

        Reservation pivot = stored.get(stored.size() / 2);
        long perResMs = phase("phase 4/6: getConflictsForReservation(pivot)",
                () -> waitFor(facade.getConflictsForReservation(pivot)));
        assertTrue(perResMs < PER_RESERVATION_QUERY_BUDGET_MS,
                "per-reservation conflict query took " + perResMs + " ms; budget "
                        + PER_RESERVATION_QUERY_BUDGET_MS);

        Allocatable allocPivot = allocatablePool.get(0);
        long rangeMs = phase("phase 5/6: getReservationsForAllocatable (3-month window)",
                () -> waitFor(facade.getReservationsForAllocatable(
                        new Allocatable[]{allocPivot},
                        LocalDateTime.parse("2030-01-01T00:00"),
                        LocalDateTime.parse("2030-04-01T00:00"),
                        null)));
        assertTrue(rangeMs < PER_RESERVATION_QUERY_BUDGET_MS,
                "range query took " + rangeMs + " ms; budget " + PER_RESERVATION_QUERY_BUDGET_MS);

        Classification c = eventType.newClassification();
        if (c.getType().getAttribute("name") != null) c.setValue("name", "incremental");
        Reservation incremental = facade.newReservation(c, actingUser);
        incremental.addAppointment(facade.newAppointmentWithUser(
                LocalDateTime.parse("2030-02-15T10:00"),
                LocalDateTime.parse("2030-02-15T12:00"), actingUser));
        incremental.addAllocatable(allocatablePool.get(0));

        long incrementalMs = phase("phase 6/6: incremental store + ConflictFinder reindex",
                () -> facade.store(incremental));
        assertTrue(incrementalMs < INCREMENTAL_STORE_BUDGET_MS,
                "incremental store took " + incrementalMs + " ms; budget " + INCREMENTAL_STORE_BUDGET_MS);

        liveSign("[perf] === ConflictPerformanceTest finished ===");
    }

    @Test
    @DisplayName("perf: AppointmentImpl.overlapsAppointment under stress (1000 calls, two long repeats)")
    void overlapsAppointmentScalesLinearlyOnRepeatingPair() throws Exception
    {
        // Pure-CPU stress for AppointmentImpl.overlapsAppointment with two
        // long-running repeating appointments. No facade, no save — just
        // exercise the predicate on adversarial inputs.
        // A: daily for 365 days from 2030-01-01, 09-10
        // B: weekly Mondays for 52 weeks from 2030-01-07, 09:30-10:30
        // Truth: they overlap on every Monday.

        org.rapla.entities.domain.internal.AppointmentImpl a =
                new org.rapla.entities.domain.internal.AppointmentImpl(
                        LocalDateTime.parse("2030-01-01T09:00"),
                        LocalDateTime.parse("2030-01-01T10:00"));
        a.setRepeatingEnabled(true);
        Repeating ar = a.getRepeating();
        ar.setType(RepeatingType.DAILY);
        ar.setNumber(365);

        org.rapla.entities.domain.internal.AppointmentImpl b =
                new org.rapla.entities.domain.internal.AppointmentImpl(
                        LocalDateTime.parse("2030-01-07T09:30"),
                        LocalDateTime.parse("2030-01-07T10:30"));
        b.setRepeatingEnabled(true);
        Repeating br = b.getRepeating();
        br.setType(RepeatingType.WEEKLY);
        br.setNumber(52);

        int iterations = 1000;
        long ms = phase("overlapsAppointment × " + iterations + " (DAILY×365 vs WEEKLY×52)", () -> {
            for (int i = 0; i < iterations; i++)
            {
                if (!a.overlapsAppointment(b))
                {
                    throw new IllegalStateException("daily 365 + weekly 52 must overlap");
                }
            }
        });
        liveSign("[perf]   averaged " + (ms * 1000.0 / iterations) + " µs/call");
        // Loose ceiling: 100 µs per call is pathologically slow for this size.
        long perCallUs = ms * 1000 / iterations;
        // Budget tightened post-baseline (PRD 014 Phase 6c, 2026-05-10):
        // 3-run median = 36 µs/call, max = 48 µs/call. Phase 7's signature
        // flip is allowed ≤1.5× slowdown (~72 µs); 100 µs ceiling leaves
        // noise headroom while catching anything that doubles per-call cost.
        // Baseline: docs/perf/processblocks-baseline-2026-05-10.txt
        assertTrue(perCallUs < 100,
                "overlapsAppointment averaged " + perCallUs + " µs/call; budget 100 µs (was ~36 µs at baseline)");
    }

    @Test
    @DisplayName("perf: overlapsHard — variable×variable repeats hit the visitor short-circuit path")
    void overlapsHardVariableInterval() throws Exception
    {
        // overlapsHard is dispatched when at least one side is variable-interval
        // (MONTHLY / YEARLY / multi-weekday WEEKLY). DAILY×WEEKLY uses the gcd
        // fast-path; MONTHLY×MONTHLY does NOT — it goes via overlapsHard, which
        // (post-Phase-8 visitor refactor) walks `this`'s occurrences and asks
        // a2.overlaps(...) per occurrence, short-circuiting on first hit.
        //
        // a = MONTHLY × 36 starting 2026-01-15 (3rd Thursday)
        // b = MONTHLY × 36 starting 2026-01-15 09:30 (overlapping by design,
        //     so visitor returns true on the FIRST occurrence — measures the
        //     short-circuit path).
        // Pre-refactor: createBlocks materialised all 36 blocks before checking.
        // Post-refactor: 1 occurrence visited, return true.

        org.rapla.entities.domain.internal.AppointmentImpl a =
                new org.rapla.entities.domain.internal.AppointmentImpl(
                        LocalDateTime.parse("2026-01-15T09:00"),
                        LocalDateTime.parse("2026-01-15T10:00"));
        a.setRepeatingEnabled(true);
        Repeating ar = a.getRepeating();
        ar.setType(RepeatingType.MONTHLY);
        ar.setNumber(36);

        org.rapla.entities.domain.internal.AppointmentImpl b =
                new org.rapla.entities.domain.internal.AppointmentImpl(
                        LocalDateTime.parse("2026-01-15T09:30"),
                        LocalDateTime.parse("2026-01-15T10:30"));
        b.setRepeatingEnabled(true);
        Repeating br = b.getRepeating();
        br.setType(RepeatingType.MONTHLY);
        br.setNumber(36);

        int iterations = 1000;
        long ms = phase("overlapsHard MONTHLY×36 vs MONTHLY×36 (early-hit)", () -> {
            for (int i = 0; i < iterations; i++)
            {
                if (!a.overlapsAppointment(b))
                {
                    throw new IllegalStateException("two overlapping monthlies must overlap");
                }
            }
        });
        long perCallUs = ms * 1000 / iterations;
        liveSign("[perf]   averaged " + (ms * 1000.0 / iterations) + " µs/call");
        assertTrue(perCallUs < 1000,
                "overlapsHard MONTHLY×MONTHLY averaged " + perCallUs + " µs/call; budget 1000 µs");
    }

    @Test
    @DisplayName("perf: MONTHLY-far-window — exercises the variable-interval iterate-from-start path")
    void monthlyFarWindowOverlap() throws Exception
    {
        // Targets the path idea #1 (PRD 014 Phase 8c-full) optimises:
        // a MONTHLY appointment (variable interval — repeating.isFixedIntervalLength()=false)
        // with a query window in occurrence ~50 of 60. Without the analytical
        // skip-ahead, processBlocks iterates from occurrence #1 every call.
        // With the skip-ahead, it should jump to ~50 in O(1).
        //
        // a = MONTHLY × 60, third Thursday of each month from 2026-01-15
        // b = single, in month 50 (around 2030-02-XX)
        // overlapsAppointment must return false (b doesn't fall on a's days)
        // but processBlocks still has to walk the occurrences to verify.

        org.rapla.entities.domain.internal.AppointmentImpl a =
                new org.rapla.entities.domain.internal.AppointmentImpl(
                        LocalDateTime.parse("2026-01-15T09:00"),
                        LocalDateTime.parse("2026-01-15T10:00"));
        a.setRepeatingEnabled(true);
        Repeating ar = a.getRepeating();
        ar.setType(RepeatingType.MONTHLY);
        ar.setNumber(60);

        // single appointment in mid-month ~50 (around 2030-02-15) on a different
        // hour so it doesn't collide with the MONTHLY occurrence
        org.rapla.entities.domain.internal.AppointmentImpl b =
                new org.rapla.entities.domain.internal.AppointmentImpl(
                        LocalDateTime.parse("2030-03-10T14:00"),
                        LocalDateTime.parse("2030-03-10T15:00"));

        int iterations = 1000;
        long ms = phase("overlapsAppointment MONTHLY×60 vs single in occurrence ~50", () -> {
            for (int i = 0; i < iterations; i++)
            {
                // Truth: b doesn't overlap any MONTHLY occurrence (different time of day).
                // processBlocks has to walk to verify the negative answer.
                a.overlapsAppointment(b);
            }
        });
        long perCallUs = ms * 1000 / iterations;
        liveSign("[perf]   averaged " + (ms * 1000.0 / iterations) + " µs/call");
        // Pre-idea-#1 baseline: TBD (capture in docs/perf/processblocks-monthly-2026-05-XX.txt).
        // Loose ceiling for now to catch pathological regression; tighten after
        // idea #1 lands and we have post-jump numbers.
        assertTrue(perCallUs < 1000,
                "MONTHLY-far-window overlapsAppointment averaged " + perCallUs + " µs/call; budget 1000 µs");
    }
}
