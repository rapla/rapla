package org.rapla.storage.impl.server.readmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The repeating MATRIX with cap and window boundaries. For each repeating type (daily/weekly/monthly/yearly)
 * and each materialisation-cap boundary (count 51 = just under, 52 = at, 53 = just over, 200 = far over,
 * open-ended), asserts the three read paths agree across windows that exercise the boundary conditions of the
 * IntervalIndex: a window starting EXACTLY at an occurrence start (must overlap), a window ending EXACTLY at an
 * occurrence start (touching, must NOT overlap — half-open interval), a window strictly in the gap between two
 * occurrences (must be empty), and all-time (null,null). Two unrestricted allocatables.
 */
class RepeatingMatrixDifferentialTest extends DifferentialReadSupport
{
    private static final LocalDateTime BASE_START = LocalDateTime.parse("2025-04-02T14:00:00");
    private static final LocalDateTime BASE_END   = LocalDateTime.parse("2025-04-02T16:00:00");

    @BeforeEach
    void setUp() throws Exception { initDifferential(); }

    // ---- daily ---------------------------------------------------------------

    @Test
    void dailyBounded_capBoundaries() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(2);
        Appointment a = daily(appt(BASE_START, BASE_END), 53);   // just over cap (52)
        store("daily-53", allocs, a);

        // window starting exactly at occurrence 10's start -> must overlap
        LocalDateTime occ10 = BASE_START.plusDays(10);
        assertAllConsistent("daily start-at-occurrence", occ10, occ10.plusDays(1), allocs);

        // window ending exactly at occurrence 5's start -> touching, must NOT include it
        LocalDateTime occ5 = BASE_START.plusDays(5);
        assertAllConsistent("daily end-at-occurrence (touching)", occ5.minusDays(1), occ5, allocs);

        // base occurrence
        assertAllConsistent("daily base", BASE_START.toLocalDate().atStartOfDay(),
                BASE_START.toLocalDate().atStartOfDay().plusDays(1), allocs);

        // far beyond last occurrence -> empty
        assertAllConsistent("daily beyond last", BASE_START.plusDays(100), BASE_START.plusDays(101), allocs);

        assertAllConsistent("daily all-time", null, null, allocs);
    }

    // ---- weekly --------------------------------------------------------------

    @Test
    void weeklyBounded_capBoundaries() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(2);
        // three reservations straddling the cap so the matrix exercises 51/52/53 in one run
        store("weekly-51", allocs, weekly(appt(BASE_START, BASE_END), 51));   // just under cap
        store("weekly-52", allocs, weekly(appt(BASE_START, BASE_END), 52));   // exactly at cap
        store("weekly-53", allocs, weekly(appt(BASE_START, BASE_END), 53));   // just over cap

        LocalDateTime occ0 = BASE_START;
        // window starting exactly at the base occurrence start -> must overlap
        assertAllConsistent("weekly start-at-occurrence", occ0, occ0.plusWeeks(1), allocs);

        // window strictly in the gap between occurrence 2 and 3 (mid-week, no occurrence) -> empty
        LocalDateTime gapFrom = BASE_START.plusWeeks(2).plusDays(2);
        assertAllConsistent("weekly gap between occurrences", gapFrom, gapFrom.plusDays(1), allocs);

        // window ending exactly at occurrence 4's start -> touching, must NOT include it
        LocalDateTime occ4 = BASE_START.plusWeeks(4);
        assertAllConsistent("weekly end-at-occurrence (touching)", occ4.minusDays(3), occ4, allocs);

        // occurrence 50 -> inside the 51 res, inside 52 (past cap envelope), inside 53
        LocalDateTime occ50 = BASE_START.plusWeeks(50);
        assertAllConsistent("weekly occ50 (past cap envelope)",
                occ50.toLocalDate().atStartOfDay(), occ50.toLocalDate().atStartOfDay().plusWeeks(1), allocs);

        assertAllConsistent("weekly all-time", null, null, allocs);
    }

    @Test
    void weeklyOpenEnded_capBoundaries() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(2);
        Appointment a = weeklyOpenEnded(appt(BASE_START, BASE_END));   // number = -1 -> side-set / envelope
        store("weekly-open", allocs, a);

        LocalDateTime occ0 = BASE_START;
        assertAllConsistent("open start-at-occurrence", occ0, occ0.plusWeeks(1), allocs);

        // far-future occurrence -> must still be present (open-ended)
        LocalDateTime occ200 = BASE_START.plusWeeks(200);
        assertAllConsistent("open far-future occ200",
                occ200.toLocalDate().atStartOfDay(), occ200.toLocalDate().atStartOfDay().plusWeeks(1), allocs);

        // gap between occurrences -> empty
        LocalDateTime gapFrom = BASE_START.plusWeeks(3).plusDays(2);
        assertAllConsistent("open gap between occurrences", gapFrom, gapFrom.plusDays(1), allocs);

        assertAllConsistent("open all-time", null, null, allocs);
    }

    // ---- monthly -------------------------------------------------------------

    @Test
    void monthlyBounded_capBoundaries() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(2);
        Appointment a = monthly(appt(BASE_START, BASE_END), 53);   // just over cap
        store("monthly-53", allocs, a);

        LocalDateTime occ0 = BASE_START;
        // window starting exactly at occurrence 0 start -> overlap
        assertAllConsistent("monthly start-at-occurrence", occ0, occ0.plusMonths(1), allocs);

        // window ending exactly at occurrence 6's start -> touching, must NOT include it
        LocalDateTime occ6 = BASE_START.plusMonths(6);
        assertAllConsistent("monthly end-at-occurrence (touching)", occ6.minusDays(5), occ6, allocs);

        // gap strictly between occurrence 1 and 2 (mid-month) -> empty
        LocalDateTime gapFrom = BASE_START.plusMonths(1).plusDays(10);
        assertAllConsistent("monthly gap between occurrences", gapFrom, gapFrom.plusDays(1), allocs);

        // occurrence 50 -> past cap envelope
        LocalDateTime occ50 = BASE_START.plusMonths(50);
        assertAllConsistent("monthly occ50 (past cap)",
                occ50.toLocalDate().atStartOfDay(), occ50.toLocalDate().atStartOfDay().plusMonths(1), allocs);

        assertAllConsistent("monthly all-time", null, null, allocs);
    }

    // ---- yearly --------------------------------------------------------------

    @Test
    void yearlyBounded_capBoundaries() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(2);
        Appointment a = yearly(appt(BASE_START, BASE_END), 53);   // just over cap
        store("yearly-53", allocs, a);

        LocalDateTime occ0 = BASE_START;
        // window starting exactly at occurrence 0 start -> overlap
        assertAllConsistent("yearly start-at-occurrence", occ0, occ0.plusYears(1), allocs);

        // window ending exactly at occurrence 3's start -> touching, must NOT include it
        LocalDateTime occ3 = BASE_START.plusYears(3);
        assertAllConsistent("yearly end-at-occurrence (touching)", occ3.minusMonths(2), occ3, allocs);

        // gap strictly between occurrence 1 and 2 (mid-year) -> empty
        LocalDateTime gapFrom = BASE_START.plusYears(1).plusMonths(6);
        assertAllConsistent("yearly gap between occurrences", gapFrom, gapFrom.plusDays(1), allocs);

        // occurrence 50 -> past cap envelope
        LocalDateTime occ50 = BASE_START.plusYears(50);
        assertAllConsistent("yearly occ50 (past cap)",
                occ50.toLocalDate().atStartOfDay(), occ50.toLocalDate().atStartOfDay().plusYears(1), allocs);

        assertAllConsistent("yearly all-time", null, null, allocs);
    }

    // ---- far-over-cap count 200 ---------------------------------------------

    @Test
    void weeklyCount200_farOverCap() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(2);
        Appointment a = weekly(appt(BASE_START, BASE_END), 200);   // far over cap
        store("weekly-200", allocs, a);

        assertAllConsistent("200 base", BASE_START, BASE_START.plusWeeks(1), allocs);

        // occurrence 150 -> deep past cap, still present
        LocalDateTime occ150 = BASE_START.plusWeeks(150);
        assertAllConsistent("200 occ150 (deep past cap)",
                occ150.toLocalDate().atStartOfDay(), occ150.toLocalDate().atStartOfDay().plusWeeks(1), allocs);

        // beyond last occurrence (week 250) -> empty
        LocalDateTime occ250 = BASE_START.plusWeeks(250);
        assertAllConsistent("200 beyond last (week 250)",
                occ250.toLocalDate().atStartOfDay(), occ250.toLocalDate().atStartOfDay().plusWeeks(1), allocs);

        assertAllConsistent("200 all-time", null, null, allocs);
    }
}
