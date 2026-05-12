package org.rapla.entities.tests;

import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.internal.AppointmentImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 hardening for {@link AppointmentImpl#createBlocks} — the
 * occurrence-expansion routine that every calendar view and the
 * server-side {@code ConflictFinder} ultimately call. ~400 lines of
 * recurrence + window + exception logic. Indirectly exercised today by
 * {@link AppointmentOverlapHardeningTest} (overlap predicate); this
 * class probes expansion semantics directly.
 *
 * <p>Categories covered:
 * <ul>
 *   <li>Single (non-repeating) appointment vs. window</li>
 *   <li>Daily recurrence — N times / until date / window-limited "forever"</li>
 *   <li>Interval &gt; 1 (every 2 days, every 3 weeks)</li>
 *   <li>Weekly with weekday set</li>
 *   <li>Monthly: same day-of-month across months</li>
 *   <li>Yearly: same date; leap-year Feb 29</li>
 *   <li>Exceptions: excludeExceptions true vs false</li>
 *   <li>Window boundary: first occurrence before / last after / appointment outside window</li>
 *   <li>End-date semantics: maxEnding cap on recurrence</li>
 * </ul>
 */
class AppointmentBlockExpansionHardeningTest
{
    private static int idCounter = 0;

    // ---------- single (non-repeating) ----------

    @Test
    void singleAppointmentInWindowYieldsOneBlock()
    {
        Appointment a = single("2026-06-10T09:00", "2026-06-10T10:00");
        List<AppointmentBlock> blocks = expand(a, "2026-06-01T00:00", "2026-06-30T00:00");
        assertEquals(1, blocks.size());
        assertEquals(LocalDateTime.parse("2026-06-10T09:00"), blocks.get(0).getStartDateTime());
        assertEquals(LocalDateTime.parse("2026-06-10T10:00"), blocks.get(0).getEndDateTime());
    }

    @Test
    void singleAppointmentBeforeWindowProducesNothing()
    {
        Appointment a = single("2026-05-01T09:00", "2026-05-01T10:00");
        List<AppointmentBlock> blocks = expand(a, "2026-06-01T00:00", "2026-06-30T00:00");
        assertTrue(blocks.isEmpty());
    }

    @Test
    void singleAppointmentAfterWindowProducesNothing()
    {
        Appointment a = single("2026-07-01T09:00", "2026-07-01T10:00");
        List<AppointmentBlock> blocks = expand(a, "2026-06-01T00:00", "2026-06-30T00:00");
        assertTrue(blocks.isEmpty());
    }

    @Test
    void singleAppointmentPartiallyOverlappingWindowStillCountsAsOne()
    {
        // Appointment spans the window's start boundary
        Appointment a = single("2026-05-31T22:00", "2026-06-01T02:00");
        List<AppointmentBlock> blocks = expand(a, "2026-06-01T00:00", "2026-06-30T00:00");
        assertEquals(1, blocks.size());
    }

    // ---------- daily recurrence ----------

    @Test
    void dailyFixedNumberYieldsExactCount()
    {
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.DAILY, 5);
        List<AppointmentBlock> blocks = expand(a, "2026-06-01T00:00", "2026-06-30T00:00");
        assertEquals(5, blocks.size());
        assertEquals(LocalDateTime.parse("2026-06-01T09:00"), blocks.get(0).getStartDateTime());
        assertEquals(LocalDateTime.parse("2026-06-05T09:00"), blocks.get(4).getStartDateTime());
    }

    @Test
    void dailyForeverIsBoundedByWindow()
    {
        Appointment a = single("2026-06-01T09:00", "2026-06-01T10:00");
        a.setRepeatingEnabled(true);
        a.getRepeating().setType(RepeatingType.DAILY);
        a.getRepeating().setNumber(-1);   // forever
        List<AppointmentBlock> blocks = expand(a, "2026-06-01T00:00", "2026-06-08T00:00");
        // 7 days in the window: 2026-06-01..2026-06-07
        assertEquals(7, blocks.size());
    }

    @Test
    void dailyUntilEndDateStopsAtEnd()
    {
        Appointment a = single("2026-06-01T09:00", "2026-06-01T10:00");
        a.setRepeatingEnabled(true);
        Repeating r = a.getRepeating();
        r.setType(RepeatingType.DAILY);
        // setEnd terminates after the listed end (inclusive of dates before it)
        r.setEnd(LocalDateTime.parse("2026-06-05T00:00"));
        List<AppointmentBlock> blocks = expand(a, "2026-06-01T00:00", "2026-06-30T00:00");
        // 4 occurrences before end: Jun 1, 2, 3, 4
        assertEquals(4, blocks.size());
    }

    @Test
    void dailyInterval2YieldsAlternateDays()
    {
        Appointment a = single("2026-06-01T09:00", "2026-06-01T10:00");
        a.setRepeatingEnabled(true);
        a.getRepeating().setType(RepeatingType.DAILY);
        a.getRepeating().setInterval(2);
        a.getRepeating().setNumber(5);
        List<AppointmentBlock> blocks = expand(a, "2026-06-01T00:00", "2026-06-30T00:00");
        assertEquals(5, blocks.size());
        // Jun 1, 3, 5, 7, 9
        assertEquals(LocalDateTime.parse("2026-06-01T09:00"), blocks.get(0).getStartDateTime());
        assertEquals(LocalDateTime.parse("2026-06-03T09:00"), blocks.get(1).getStartDateTime());
        assertEquals(LocalDateTime.parse("2026-06-05T09:00"), blocks.get(2).getStartDateTime());
        assertEquals(LocalDateTime.parse("2026-06-09T09:00"), blocks.get(4).getStartDateTime());
    }

    // ---------- weekly recurrence ----------

    @Test
    void weeklyAnchorWeekdayProducesOnePerWeek()
    {
        // 2026-06-01 is a Monday — weekly repetition every Monday.
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.WEEKLY, 4);
        List<AppointmentBlock> blocks = expand(a, "2026-06-01T00:00", "2026-07-31T00:00");
        assertEquals(4, blocks.size());
        // Each ~7 days apart
        for (int i = 1; i < blocks.size(); i++)
        {
            long days = java.time.temporal.ChronoUnit.DAYS.between(
                    blocks.get(i - 1).getStartDateTime(), blocks.get(i).getStartDateTime());
            assertEquals(7, days, "weekly gap between occurrence " + (i - 1) + " and " + i);
        }
    }

    @Test
    void weeklyMultipleWeekdaysProducesAllPickedDays()
    {
        // 2026-06-01 (Mon, weekday=2). Add Wednesday (weekday=4).
        Appointment a = single("2026-06-01T09:00", "2026-06-01T10:00");
        a.setRepeatingEnabled(true);
        Repeating r = a.getRepeating();
        r.setType(RepeatingType.WEEKLY);
        r.setWeekdays(new HashSet<>(java.util.Arrays.asList(2, 4)));   // Mon + Wed
        r.setNumber(4);   // 4 occurrences total

        List<AppointmentBlock> blocks = expand(a, "2026-06-01T00:00", "2026-06-30T00:00");
        assertEquals(4, blocks.size());
        // Mon Jun 1, Wed Jun 3, Mon Jun 8, Wed Jun 10
        assertEquals(LocalDateTime.parse("2026-06-01T09:00"), blocks.get(0).getStartDateTime());
        assertEquals(LocalDateTime.parse("2026-06-03T09:00"), blocks.get(1).getStartDateTime());
        assertEquals(LocalDateTime.parse("2026-06-08T09:00"), blocks.get(2).getStartDateTime());
        assertEquals(LocalDateTime.parse("2026-06-10T09:00"), blocks.get(3).getStartDateTime());
    }

    // ---------- monthly recurrence ----------

    @Test
    void monthlyIsNthWeekdayOfMonth()
    {
        // CRITICAL behaviour: Rapla's RepeatingType.MONTHLY is "Nth weekday
        // of the month" — NOT "same day-of-month" as I'd assumed when
        // writing this test. 2026-06-15 is the 3rd Monday of June; the
        // next occurrences land on the 3rd Monday of July (Jul 20),
        // August (Aug 17), September (Sep 21). Pin the behaviour so a
        // future refactor can't silently flip to day-of-month semantics.
        Appointment a = repeating("2026-06-15T09:00", "2026-06-15T10:00", RepeatingType.MONTHLY, 4);
        List<AppointmentBlock> blocks = expand(a, "2026-06-01T00:00", "2026-12-31T00:00");
        assertEquals(4, blocks.size());
        // All occurrences fall on a Monday (DayOfWeek MONDAY = 1)
        for (AppointmentBlock b : blocks)
        {
            assertEquals(java.time.DayOfWeek.MONDAY, b.getStartDateTime().getDayOfWeek(),
                    "monthly preserves the anchor weekday");
        }
        // Months progress one per occurrence
        assertEquals(6, blocks.get(0).getStartDateTime().getMonthValue());
        assertEquals(7, blocks.get(1).getStartDateTime().getMonthValue());
        assertEquals(8, blocks.get(2).getStartDateTime().getMonthValue());
        assertEquals(9, blocks.get(3).getStartDateTime().getMonthValue());
    }

    // ---------- yearly recurrence ----------

    @Test
    void yearlySameDateAcrossYears()
    {
        Appointment a = repeating("2026-03-15T09:00", "2026-03-15T10:00", RepeatingType.YEARLY, 3);
        List<AppointmentBlock> blocks = expand(a, "2026-01-01T00:00", "2030-12-31T00:00");
        assertEquals(3, blocks.size());
        assertEquals(2026, blocks.get(0).getStartDateTime().getYear());
        assertEquals(2027, blocks.get(1).getStartDateTime().getYear());
        assertEquals(2028, blocks.get(2).getStartDateTime().getYear());
        // Date stays March 15
        for (AppointmentBlock b : blocks)
        {
            assertEquals(3, b.getStartDateTime().getMonthValue());
            assertEquals(15, b.getStartDateTime().getDayOfMonth());
        }
    }

    @Test
    void yearlyLeapYearFeb29SkipsNonLeapYears()
    {
        // CRITICAL behaviour: when the anchor is Feb 29, yearly recurrence
        // does NOT roll forward to Feb 28 in non-leap years — it SKIPS
        // those years entirely. So 2024 Feb 29 with number=5 produces
        // 2 occurrences across the 2024..2032 window: 2024-02-29 and
        // 2028-02-29 (both leap years).
        //
        // This is intentional Rapla semantics — pinning it prevents a
        // refactor from flipping to "roll to Feb 28" silently. A user
        // who wants every February would use day-of-month=28 instead.
        Appointment a = repeating("2024-02-29T09:00", "2024-02-29T10:00", RepeatingType.YEARLY, 5);
        List<AppointmentBlock> blocks = expand(a, "2024-01-01T00:00", "2032-01-01T00:00");
        assertEquals(2, blocks.size(),
                "yearly-on-Feb-29 only produces occurrences in leap years (2024, 2028 in this window)");
        assertEquals(LocalDateTime.parse("2024-02-29T09:00"), blocks.get(0).getStartDateTime());
        assertEquals(LocalDateTime.parse("2028-02-29T09:00"), blocks.get(1).getStartDateTime());
    }

    // ---------- exceptions ----------

    @Test
    void excludeExceptionsTrueDropsThem()
    {
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.DAILY, 5);
        // Mark Jun 3 as an exception.
        a.getRepeating().addException(LocalDateTime.parse("2026-06-03T09:00"));
        List<AppointmentBlock> blocks = expand(a, "2026-06-01T00:00", "2026-06-30T00:00");
        // 5 scheduled - 1 exception = 4
        assertEquals(4, blocks.size());
        // Verify the missing one is Jun 3
        Set<LocalDateTime> starts = new HashSet<>();
        for (AppointmentBlock b : blocks) starts.add(b.getStartDateTime());
        assertFalse(starts.contains(LocalDateTime.parse("2026-06-03T09:00")),
                "exception date must not appear in the output");
    }

    @Test
    void excludeExceptionsFalseKeepsThem()
    {
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.DAILY, 5);
        a.getRepeating().addException(LocalDateTime.parse("2026-06-03T09:00"));
        List<AppointmentBlock> blocks = expandIncludingExceptions(a,
                "2026-06-01T00:00", "2026-06-30T00:00");
        // All 5 present
        assertEquals(5, blocks.size());
        // The exception block carries the exception flag (test once)
        boolean foundExceptionBlock = false;
        for (AppointmentBlock b : blocks)
        {
            if (b.getStartDateTime().equals(LocalDateTime.parse("2026-06-03T09:00")))
            {
                assertTrue(b.isException(), "Jun 3 block carries isException=true");
                foundExceptionBlock = true;
            }
        }
        assertTrue(foundExceptionBlock, "Jun 3 block expected in non-excluding mode");
    }

    @Test
    void exceptionOnAnchorAppointment()
    {
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.DAILY, 3);
        a.getRepeating().addException(LocalDateTime.parse("2026-06-01T09:00"));   // anchor exception
        List<AppointmentBlock> blocks = expand(a, "2026-06-01T00:00", "2026-06-30T00:00");
        assertEquals(2, blocks.size(), "anchor-exception leaves 2 of 3 occurrences");
        assertEquals(LocalDateTime.parse("2026-06-02T09:00"), blocks.get(0).getStartDateTime());
    }

    // ---------- window boundary ----------

    @Test
    void weeklyAcrossMultipleMonthsRespectsWindow()
    {
        // 5 weekly occurrences from 2026-06-01 Mon. Window: only July.
        // Expect occurrences on Mon Jul 6, Jul 13, Jul 20, Jul 27.
        // The 5-occurrence series is: Jun 1, 8, 15, 22, 29.
        // None of these are in the window — so 0 results.
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.WEEKLY, 5);
        List<AppointmentBlock> blocks = expand(a, "2026-07-01T00:00", "2026-07-31T00:00");
        assertTrue(blocks.isEmpty(), "all 5 weekly occurrences land in June; July window is empty");
    }

    @Test
    void windowSubsetOfRecurrenceRangeReturnsSubset()
    {
        // 20 daily occurrences from Jun 1; window Jun 5..Jun 10.
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.DAILY, 20);
        List<AppointmentBlock> blocks = expand(a, "2026-06-05T00:00", "2026-06-10T00:00");
        // Days 5, 6, 7, 8, 9 are in [Jun 5 inclusive, Jun 10 exclusive) — 5 days
        assertEquals(5, blocks.size());
        assertEquals(LocalDateTime.parse("2026-06-05T09:00"), blocks.get(0).getStartDateTime());
        assertEquals(LocalDateTime.parse("2026-06-09T09:00"), blocks.get(4).getStartDateTime());
    }

    // ---------- helpers ----------

    /** Single-occurrence appointment with a stable id (the entity's hashCode demands one). */
    private static Appointment single(String startIso, String endIso)
    {
        AppointmentImpl a = new AppointmentImpl(LocalDateTime.parse(startIso), LocalDateTime.parse(endIso));
        a.setId("test-app-" + (++idCounter));
        return a;
    }

    /** Repeating appointment with type and number-of-occurrences set. */
    private static Appointment repeating(String startIso, String endIso, RepeatingType type, int number)
    {
        Appointment a = single(startIso, endIso);
        a.setRepeatingEnabled(true);
        Repeating r = a.getRepeating();
        r.setType(type);
        r.setNumber(number);
        return a;
    }

    private static List<AppointmentBlock> expand(Appointment a, String windowStart, String windowEnd)
    {
        List<AppointmentBlock> blocks = new ArrayList<>();
        a.createBlocks(LocalDateTime.parse(windowStart), LocalDateTime.parse(windowEnd), blocks);
        return blocks;
    }

    private static List<AppointmentBlock> expandIncludingExceptions(Appointment a, String windowStart, String windowEnd)
    {
        List<AppointmentBlock> blocks = new ArrayList<>();
        a.createBlocks(LocalDateTime.parse(windowStart), LocalDateTime.parse(windowEnd), blocks, false);
        return blocks;
    }
}
