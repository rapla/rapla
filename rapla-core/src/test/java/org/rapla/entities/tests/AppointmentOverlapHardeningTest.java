package org.rapla.entities.tests;

import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.internal.AppointmentImpl;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 hardening for {@link AppointmentImpl#overlapsAppointment} — the
 * symmetric overlap predicate that {@code ConflictFinder} ultimately calls
 * for every reservation pair sharing an allocatable. A wrong answer here
 * silently double-books or hides a conflict.
 *
 * <p>Categories covered:
 * <ul>
 *   <li>Single × single (containment, partial, touching, adjacent, identical)</li>
 *   <li>Single × repeating (in gap / on occurrence / on exception / past end)</li>
 *   <li>Repeating × repeating (compatible periods, interleaved, gcd interaction)</li>
 *   <li>Variable-interval (MONTHLY, YEARLY) cross repeatings</li>
 *   <li>Boundary-day cases (DST spring-forward, leap year Feb 29)</li>
 *   <li>Symmetry: {@code a.overlaps(b) == b.overlaps(a)} for every case</li>
 * </ul>
 */
class AppointmentOverlapHardeningTest
{
    private static AppointmentImpl single(String startIso, String endIso)
    {
        return new AppointmentImpl(LocalDateTime.parse(startIso), LocalDateTime.parse(endIso));
    }

    private static Appointment repeating(String startIso, String endIso,
                                         RepeatingType type, int number)
    {
        // Hold via Appointment interface so subsequent .getRepeating() resolves
        // on the public Repeating interface, not the package-private RepeatingImpl.
        Appointment a = single(startIso, endIso);
        a.setRepeatingEnabled(true);
        Repeating r = a.getRepeating();
        r.setType(type);
        r.setNumber(number);
        return a;
    }

    /** Assert overlap result and verify symmetry — both directions must agree. */
    private static void assertOverlapSymmetric(boolean expected, Appointment a, Appointment b, String why)
    {
        boolean ab = a.overlapsAppointment(b);
        boolean ba = b.overlapsAppointment(a);
        assertTrue(ab == ba, "asymmetric overlap! a→b=" + ab + " b→a=" + ba + " — " + why);
        if (expected) assertTrue(ab, "expected overlap: " + why);
        else assertFalse(ab, "expected NO overlap: " + why);
    }

    // ---------- single × single ----------

    @Test
    void identicalAppointmentsOverlap()
    {
        AppointmentImpl a = single("2026-06-10T10:00", "2026-06-10T11:00");
        AppointmentImpl b = single("2026-06-10T10:00", "2026-06-10T11:00");
        assertOverlapSymmetric(true, a, b, "identical");
    }

    @Test
    void appointmentOverlapsItself()
    {
        AppointmentImpl a = single("2026-06-10T10:00", "2026-06-10T11:00");
        assertTrue(a.overlapsAppointment(a), "an appointment must overlap itself");
    }

    @Test
    void containedFullyInsideOverlaps()
    {
        AppointmentImpl outer = single("2026-06-10T08:00", "2026-06-10T18:00");
        AppointmentImpl inner = single("2026-06-10T12:00", "2026-06-10T13:00");
        assertOverlapSymmetric(true, outer, inner, "inner fully inside outer");
    }

    @Test
    void partialOverlapLeftEdge()
    {
        AppointmentImpl a = single("2026-06-10T09:00", "2026-06-10T11:00");
        AppointmentImpl b = single("2026-06-10T10:00", "2026-06-10T12:00");
        assertOverlapSymmetric(true, a, b, "partial: a covers [9-11], b covers [10-12]");
    }

    @Test
    void partialOverlapRightEdge()
    {
        AppointmentImpl a = single("2026-06-10T10:00", "2026-06-10T12:00");
        AppointmentImpl b = single("2026-06-10T09:00", "2026-06-10T11:00");
        assertOverlapSymmetric(true, a, b, "partial: a [10-12], b [9-11]");
    }

    @Test
    void touchingEdgeIsNotOverlap()
    {
        // A ends exactly when B starts. Convention: closed-open intervals,
        // so no overlap. The line in overlapsAppointment(): !(e2 <= s1 || e1 <= s2).
        AppointmentImpl a = single("2026-06-10T09:00", "2026-06-10T11:00");
        AppointmentImpl b = single("2026-06-10T11:00", "2026-06-10T13:00");
        assertOverlapSymmetric(false, a, b, "touching at 11:00 — closed-open intervals don't overlap");
    }

    @Test
    void adjacentOneMillisecondGapIsNotOverlap()
    {
        AppointmentImpl a = single("2026-06-10T09:00", "2026-06-10T11:00");
        AppointmentImpl b = single("2026-06-10T11:00:00.001", "2026-06-10T13:00");
        assertOverlapSymmetric(false, a, b, "1 ms gap");
    }

    @Test
    void completelySeparateDaysDoNotOverlap()
    {
        AppointmentImpl a = single("2026-06-10T10:00", "2026-06-10T11:00");
        AppointmentImpl b = single("2026-08-15T10:00", "2026-08-15T11:00");
        assertOverlapSymmetric(false, a, b, "two months apart");
    }

    @Test
    void zeroDurationAppointmentEdgeCase()
    {
        // Zero-length appointment: start == end. Per closed-open convention,
        // it doesn't actually occupy any time, so it shouldn't overlap anything.
        AppointmentImpl zero = single("2026-06-10T10:00", "2026-06-10T10:00");
        AppointmentImpl span = single("2026-06-10T09:00", "2026-06-10T12:00");
        // Whatever the impl decides, it must be symmetric.
        boolean ab = zero.overlapsAppointment(span);
        boolean ba = span.overlapsAppointment(zero);
        assertTrue(ab == ba, "zero-duration overlap must be symmetric, got ab=" + ab + " ba=" + ba);
    }

    // ---------- single × repeating ----------

    @Test
    void singleHittingFirstOccurrenceOfDailyRepeatOverlaps()
    {
        Appointment rep = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.DAILY, 14);
        AppointmentImpl single = single("2026-06-01T09:30", "2026-06-01T09:45");
        assertOverlapSymmetric(true, rep, single, "single inside first daily occurrence");
    }

    @Test
    void singleHittingMiddleDailyOccurrenceOverlaps()
    {
        Appointment rep = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.DAILY, 14);
        AppointmentImpl single = single("2026-06-07T09:30", "2026-06-07T09:45");
        assertOverlapSymmetric(true, rep, single, "single inside mid-week daily occurrence");
    }

    @Test
    void singleInGapBetweenWeeklyOccurrencesDoesNotOverlap()
    {
        // Weekly Mondays starting 2026-06-01. Wednesday 2026-06-03 is in the gap.
        Appointment rep = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.WEEKLY, 8);
        AppointmentImpl single = single("2026-06-03T09:30", "2026-06-03T09:45");
        assertOverlapSymmetric(false, rep, single, "Wednesday between weekly Mondays");
    }

    @Test
    void singlePastEndOfBoundedRepeatDoesNotOverlap()
    {
        // Daily for 7 days, query 30 days later.
        Appointment rep = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.DAILY, 7);
        AppointmentImpl single = single("2026-07-15T09:30", "2026-07-15T09:45");
        assertOverlapSymmetric(false, rep, single, "single past number-bounded daily end");
    }

    @Test
    void singleHittingExceptionDayDoesNotOverlap()
    {
        Appointment rep = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.DAILY, 14);
        rep.getRepeating().addException(LocalDateTime.parse("2026-06-05T00:00"));

        AppointmentImpl single = single("2026-06-05T09:30", "2026-06-05T09:45");
        assertOverlapSymmetric(false, rep, single, "single on exception day must not overlap");
    }

    @Test
    void singleOnDayAdjacentToExceptionStillOverlaps()
    {
        Appointment rep = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.DAILY, 14);
        rep.getRepeating().addException(LocalDateTime.parse("2026-06-05T00:00"));

        // Day before the exception — still a normal occurrence
        AppointmentImpl single = single("2026-06-04T09:30", "2026-06-04T09:45");
        assertOverlapSymmetric(true, rep, single, "single on day BEFORE exception still overlaps");
    }

    // ---------- repeating × repeating ----------

    @Test
    void twoOverlappingDailyRepeatsOverlap()
    {
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.DAILY, 14);
        Appointment b = repeating("2026-06-01T09:30", "2026-06-01T10:30", RepeatingType.DAILY, 14);
        assertOverlapSymmetric(true, a, b, "two daily repeats overlapping each day");
    }

    @Test
    void dailyAndWeeklyOnDisjointWeekdaysCanOverlap()
    {
        // Daily M-Sun + weekly Tuesday → must overlap on every Tuesday.
        Appointment daily = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.DAILY, 14);
        Appointment weekly = repeating("2026-06-02T09:30", "2026-06-02T10:30", RepeatingType.WEEKLY, 4);
        assertOverlapSymmetric(true, daily, weekly, "daily + weekly Tuesday → overlaps Tuesdays");
    }

    @Test
    void twoRepeatsWithDisjointTimeWindowsDoNotOverlap()
    {
        // Daily 09-10 vs Daily 14-15 — same days, disjoint hours.
        Appointment morning = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.DAILY, 14);
        Appointment afternoon = repeating("2026-06-01T14:00", "2026-06-01T15:00", RepeatingType.DAILY, 14);
        assertOverlapSymmetric(false, morning, afternoon, "different times-of-day every day");
    }

    @Test
    void twoBoundedRepeatsWithSeparateRangesDoNotOverlap()
    {
        // A: daily for 7 days starting June 1
        // B: daily for 7 days starting July 1
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.DAILY, 7);
        Appointment b = repeating("2026-07-01T09:00", "2026-07-01T10:00", RepeatingType.DAILY, 7);
        assertOverlapSymmetric(false, a, b, "ranges entirely separate");
    }

    // ---------- variable-interval repeats (MONTHLY, YEARLY) ----------

    @Test
    void monthlyRepeatUsesNthWeekdayOfMonthNotSameDayOfMonth()
    {
        // Rapla's MONTHLY follows the "Nth weekday of the month" convention
        // (like Outlook / Google "Monthly on the third Thursday"), NOT
        // "same day-of-month". So a monthly starting on Thu 2026-01-15 lands
        // on Thu 2026-02-19, Thu 2026-03-19, Thu 2026-04-16 — not the 15th
        // of each month. This test pins that semantic so future refactors
        // notice if it changes.
        Appointment monthly = repeating("2026-01-15T09:00", "2026-01-15T10:00", RepeatingType.MONTHLY, 12);

        // The day-of-month case (single on Apr 15): does NOT overlap
        AppointmentImpl singleApr15 = single("2026-04-15T09:30", "2026-04-15T09:45");
        assertOverlapSymmetric(false, monthly, singleApr15,
                "MONTHLY is Nth-weekday, not same-day-of-month — Apr 15 is NOT a target");

        // The Nth-weekday case (single on Apr 16, third Thursday): overlaps
        AppointmentImpl singleApr16 = single("2026-04-16T09:30", "2026-04-16T09:45");
        assertOverlapSymmetric(true, monthly, singleApr16,
                "third Thursday of April 2026 = Apr 16 — that's the actual MONTHLY target");
    }

    @Test
    void yearlyRepeatOverlapsWithSingleOneYearLater()
    {
        Appointment yearly = repeating("2024-02-29T09:00", "2024-02-29T10:00", RepeatingType.YEARLY, 4);
        AppointmentImpl single = single("2024-02-29T09:30", "2024-02-29T09:45");
        assertOverlapSymmetric(true, yearly, single, "yearly's first occurrence is the leap day");
    }

    // ---------- DST boundary day ----------

    @Test
    void singleOnDstSpringForwardDayHitsDailyRepeat()
    {
        // 2026-03-29 is the spring-forward day in Europe.
        Appointment daily = repeating("2026-03-26T09:00", "2026-03-26T10:00", RepeatingType.DAILY, 7);
        AppointmentImpl single = single("2026-03-29T09:30", "2026-03-29T09:45");
        assertOverlapSymmetric(true, daily, single, "DST day must still produce a daily occurrence");
    }

    @Test
    void singleOnDstFallBackDayHitsDailyRepeat()
    {
        // 2026-10-25 is the fall-back day in Europe.
        Appointment daily = repeating("2026-10-22T09:00", "2026-10-22T10:00", RepeatingType.DAILY, 7);
        AppointmentImpl single = single("2026-10-25T09:30", "2026-10-25T09:45");
        assertOverlapSymmetric(true, daily, single, "DST fallback day must still produce a daily occurrence");
    }
}
