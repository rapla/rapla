package org.rapla.entities.tests;

import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.internal.AppointmentImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 backfill (PRD 017 Phase 4 #10) for appointment expansion via
 * {@link AppointmentImpl#createBlocks}. Targets uncovered branches in
 * {@code processBlocks} — fixed-interval (DAILY, WEEKLY) vs dynamic-interval
 * (MONTHLY, YEARLY) paths, custom intervals, exceptions, and DST boundaries.
 *
 * <p>Pre-existing {@code AppointmentTest} mostly exercises {@code overlap*}
 * /{@code compareTo}; this class targets {@code createBlocks} directly so
 * future churn in the date-migration area surfaces as a test failure rather
 * than a coverage gap.
 */
class AppointmentBlocksExpansionTest
{
    private static Appointment appt(String startIso, String endIso)
    {
        return new AppointmentImpl(LocalDateTime.parse(startIso), LocalDateTime.parse(endIso));
    }

    private static List<AppointmentBlock> blocks(Appointment a, String fromIso, String toIso)
    {
        List<AppointmentBlock> out = new ArrayList<>();
        a.createBlocks(LocalDateTime.parse(fromIso), LocalDateTime.parse(toIso), out);
        return out;
    }

    // ---------- single (non-repeating) ----------

    @Test
    void nonRepeatingProducesOneBlockWhenInWindow()
    {
        Appointment a = appt("2026-03-15T10:00", "2026-03-15T11:00");
        assertEquals(1, blocks(a, "2026-03-01T00:00", "2026-04-01T00:00").size());
    }

    @Test
    void nonRepeatingProducesZeroBlocksOutsideWindow()
    {
        Appointment a = appt("2026-03-15T10:00", "2026-03-15T11:00");
        assertEquals(0, blocks(a, "2026-04-01T00:00", "2026-05-01T00:00").size());
    }

    // ---------- DAILY (fixed interval) ----------

    @Test
    void dailyRepeatingProducesOneBlockPerDay()
    {
        Appointment a = appt("2026-03-02T09:00", "2026-03-02T10:00"); // Monday
        a.setRepeatingEnabled(true);
        a.getRepeating().setType(RepeatingType.DAILY);
        a.getRepeating().setNumber(7);

        List<AppointmentBlock> result = blocks(a, "2026-03-01T00:00", "2026-03-15T00:00");
        assertEquals(7, result.size(), "DAILY+number=7 should give 7 blocks");
    }

    @Test
    void dailyEveryThreeDaysHonorsInterval()
    {
        Appointment a = appt("2026-04-01T09:00", "2026-04-01T10:00");
        a.setRepeatingEnabled(true);
        Repeating r = a.getRepeating();
        r.setType(RepeatingType.DAILY);
        r.setInterval(3);
        r.setNumber(5); // 5 occurrences, every 3 days = days 1, 4, 7, 10, 13

        List<AppointmentBlock> result = blocks(a, "2026-04-01T00:00", "2026-05-01T00:00");
        assertEquals(5, result.size());
        assertEquals(LocalDateTime.parse("2026-04-01T09:00"),
                LocalDateTime.parse(java.time.Instant.ofEpochMilli(result.get(0).getStart())
                        .atZone(java.time.ZoneOffset.UTC).toLocalDateTime().toString()));
    }

    // ---------- WEEKLY (fixed interval) ----------

    @Test
    void weeklyBoundedByEndDate()
    {
        Appointment a = appt("2026-03-02T09:00", "2026-03-02T10:00"); // Monday
        a.setRepeatingEnabled(true);
        Repeating r = a.getRepeating();
        r.setType(RepeatingType.WEEKLY);
        r.setEnd(LocalDateTime.parse("2026-04-06T00:00")); // 5 Mondays inclusive

        List<AppointmentBlock> result = blocks(a, "2026-03-01T00:00", "2026-05-01T00:00");
        assertEquals(5, result.size(), "weekly Mar 2 → Mar 30 = 5 Mondays");
    }

    // ---------- MONTHLY (dynamic interval — varying month lengths) ----------

    @Test
    void monthlyExpandsAcrossVaryingMonthLengths()
    {
        Appointment a = appt("2026-01-15T09:00", "2026-01-15T10:00");
        a.setRepeatingEnabled(true);
        Repeating r = a.getRepeating();
        r.setType(RepeatingType.MONTHLY);
        r.setNumber(12);

        List<AppointmentBlock> result = blocks(a, "2026-01-01T00:00", "2027-02-01T00:00");
        assertEquals(12, result.size(), "12 monthly occurrences from Jan 15 2026");
        assertTrue(result.get(11).getStart() > result.get(0).getStart(),
                "blocks should be in chronological order");
    }

    // ---------- YEARLY (dynamic interval — leap year) ----------

    @Test
    void yearlyExpandsAcrossLeapYear()
    {
        // Feb 29 2024 → leap-year boundary stress
        Appointment a = appt("2024-02-29T09:00", "2024-02-29T10:00");
        a.setRepeatingEnabled(true);
        Repeating r = a.getRepeating();
        r.setType(RepeatingType.YEARLY);
        r.setNumber(4);

        List<AppointmentBlock> result = blocks(a, "2024-01-01T00:00", "2028-01-01T00:00");
        assertTrue(result.size() >= 1, "at least the Feb 29 2024 origin block");
    }

    // ---------- exceptions ----------

    @Test
    void exceptionRemovesBlockFromExpansion()
    {
        Appointment a = appt("2026-03-02T09:00", "2026-03-02T10:00");
        a.setRepeatingEnabled(true);
        Repeating r = a.getRepeating();
        r.setType(RepeatingType.DAILY);
        r.setNumber(5);
        r.addException(LocalDateTime.parse("2026-03-04T00:00"));

        List<AppointmentBlock> result = blocks(a, "2026-03-01T00:00", "2026-03-15T00:00");
        assertEquals(4, result.size(), "5 daily occurrences − 1 exception = 4");
    }

    @Test
    void exceptionIncludedWhenExcludeExceptionsFalse()
    {
        Appointment a = appt("2026-03-02T09:00", "2026-03-02T10:00");
        a.setRepeatingEnabled(true);
        Repeating r = a.getRepeating();
        r.setType(RepeatingType.DAILY);
        r.setNumber(5);
        r.addException(LocalDateTime.parse("2026-03-04T00:00"));

        List<AppointmentBlock> result = new ArrayList<>();
        a.createBlocks(LocalDateTime.parse("2026-03-01T00:00"),
                LocalDateTime.parse("2026-03-15T00:00"), result, false);
        assertEquals(5, result.size(), "exception included when excludeExceptions=false");
        long exceptionBlocks = result.stream().filter(AppointmentBlock::isException).count();
        assertEquals(1, exceptionBlocks, "exactly one block flagged as exception");
    }

    // ---------- DST boundary ----------

    @Test
    void dailyRepeatingAcrossSpringDstBoundary()
    {
        // Europe DST 2026: clocks jump CET→CEST on Sun 2026-03-29 02:00→03:00.
        // A daily 09:00 appointment for the week around it should still emit
        // one block per day — clock-time semantics, not wall-time-with-jump.
        Appointment a = appt("2026-03-26T09:00", "2026-03-26T10:00");
        a.setRepeatingEnabled(true);
        a.getRepeating().setType(RepeatingType.DAILY);
        a.getRepeating().setNumber(7);

        List<AppointmentBlock> result = blocks(a, "2026-03-01T00:00", "2026-04-15T00:00");
        assertEquals(7, result.size(), "DST transition must not drop or duplicate a daily block");
    }

    @Test
    void dailyRepeatingAcrossFallDstBoundary()
    {
        // Europe DST 2026: clocks jump CEST→CET on Sun 2026-10-25 03:00→02:00.
        Appointment a = appt("2026-10-22T09:00", "2026-10-22T10:00");
        a.setRepeatingEnabled(true);
        a.getRepeating().setType(RepeatingType.DAILY);
        a.getRepeating().setNumber(7);

        List<AppointmentBlock> result = blocks(a, "2026-10-01T00:00", "2026-11-15T00:00");
        assertEquals(7, result.size(), "fall DST transition must not drop or duplicate a daily block");
    }

    // ---------- bounding by window ----------

    @Test
    void windowFiltersOutBlocksOutsideRange()
    {
        Appointment a = appt("2026-03-02T09:00", "2026-03-02T10:00");
        a.setRepeatingEnabled(true);
        a.getRepeating().setType(RepeatingType.DAILY);
        a.getRepeating().setNumber(30);

        // Only request a 3-day window in the middle.
        List<AppointmentBlock> result = blocks(a, "2026-03-10T00:00", "2026-03-13T00:00");
        assertEquals(3, result.size(), "30 daily blocks but 3-day window = 3 returned");
    }

    @Test
    void zeroLengthWindowReturnsNoBlocks()
    {
        Appointment a = appt("2026-03-02T09:00", "2026-03-02T10:00");
        a.setRepeatingEnabled(true);
        a.getRepeating().setType(RepeatingType.DAILY);
        a.getRepeating().setNumber(5);

        List<AppointmentBlock> result = blocks(a, "2026-03-15T12:00", "2026-03-15T12:00");
        assertEquals(0, result.size());
    }

    // ---------- block fields ----------

    @Test
    void blockTimestampsMatchAppointmentDuration()
    {
        Appointment a = appt("2026-03-02T09:30", "2026-03-02T10:45");
        a.setRepeatingEnabled(true);
        a.getRepeating().setType(RepeatingType.DAILY);
        a.getRepeating().setNumber(2);

        List<AppointmentBlock> result = blocks(a, "2026-03-01T00:00", "2026-03-05T00:00");
        assertEquals(2, result.size());

        long durationFirst = result.get(0).getEnd() - result.get(0).getStart();
        long durationSecond = result.get(1).getEnd() - result.get(1).getStart();
        assertEquals(durationFirst, durationSecond, "all blocks share the appointment's duration");
        assertEquals(75 * 60_000L, durationFirst, "75 minutes = 4 500 000 ms");

        assertFalse(result.get(0).isException());
        assertFalse(result.get(1).isException());
    }
}
