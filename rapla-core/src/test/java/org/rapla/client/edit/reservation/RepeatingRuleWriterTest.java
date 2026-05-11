package org.rapla.client.edit.reservation;

import org.junit.jupiter.api.Test;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.EndingMode;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.internal.AppointmentImpl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 unit coverage of {@link RepeatingRuleWriter} — the pure-Java
 * counterpart of {@code AppointmentController.RepeatingEditor.mapToAppointment()}.
 */
class RepeatingRuleWriterTest
{
    private static final LocalDateTime START = LocalDateTime.parse("2026-06-01T09:00");

    @Test
    void clampsIntervalBelowOneToOne()
    {
        Repeating r = freshRepeating();
        RepeatingRuleModel m = new RepeatingRuleModel(
                RepeatingType.DAILY, 0, Set.of(), EndingMode.FOREVER, null, -1);
        RepeatingRuleWriter.writeTo(m, r, START);
        assertEquals(1, r.getInterval());
    }

    @Test
    void preservesIntervalAtOrAboveOne()
    {
        Repeating r = freshRepeating();
        RepeatingRuleModel m = new RepeatingRuleModel(
                RepeatingType.DAILY, 5, Set.of(), EndingMode.FOREVER, null, -1);
        RepeatingRuleWriter.writeTo(m, r, START);
        assertEquals(5, r.getInterval());
    }

    @Test
    void writesTypeWeeklyAndWeekdays()
    {
        Repeating r = freshRepeating();
        RepeatingRuleModel m = new RepeatingRuleModel(
                RepeatingType.WEEKLY, 1, new TreeSet<>(Set.of(2, 4, 6)),
                EndingMode.FOREVER, null, -1);
        RepeatingRuleWriter.writeTo(m, r, START);
        assertEquals(RepeatingType.WEEKLY, r.getType());
        assertEquals(new TreeSet<>(Set.of(2, 4, 6)), r.getWeekdays());
    }

    @Test
    void doesNotCallSetWeekdaysForNonWeekly()
    {
        // The writer must not call setWeekdays() for non-weekly types.
        // (Note: Repeating.setType() itself clears the weekday set —
        //  that's the entity's contract, not the writer's. This test
        //  confirms the writer's no-op semantic by passing an empty
        //  weekday set in the model and verifying we never throw / never
        //  populate.)
        Repeating r = freshRepeating();
        RepeatingRuleModel m = new RepeatingRuleModel(
                RepeatingType.DAILY, 1, Collections.emptySet(),
                EndingMode.FOREVER, null, -1);
        RepeatingRuleWriter.writeTo(m, r, START);
        assertTrue(r.getWeekdays() == null || r.getWeekdays().isEmpty());
    }

    @Test
    void untilEndDateGetsPlusOneDayWhenWritten()
    {
        Repeating r = freshRepeating();
        LocalDateTime userPicked = START.plusDays(10);
        RepeatingRuleModel m = new RepeatingRuleModel(
                RepeatingType.DAILY, 1, Set.of(), EndingMode.UNTIL, userPicked, -1);
        RepeatingRuleWriter.writeTo(m, r, START);
        assertEquals(DateTools.addDay(userPicked), r.getEnd());
    }

    @Test
    void untilEndDateBeforeStartIsSnappedToStart()
    {
        Repeating r = freshRepeating();
        LocalDateTime beforeStart = START.minusDays(5);
        RepeatingRuleModel m = new RepeatingRuleModel(
                RepeatingType.DAILY, 1, Set.of(), EndingMode.UNTIL, beforeStart, -1);
        RepeatingRuleWriter.writeTo(m, r, START);
        assertEquals(DateTools.addDay(START), r.getEnd());
    }

    @Test
    void untilNullEndDateIsSnappedToStart()
    {
        Repeating r = freshRepeating();
        RepeatingRuleModel m = new RepeatingRuleModel(
                RepeatingType.DAILY, 1, Set.of(), EndingMode.UNTIL, null, -1);
        RepeatingRuleWriter.writeTo(m, r, START);
        assertEquals(DateTools.addDay(START), r.getEnd());
    }

    @Test
    void nTimesClampsBelowOneToOne()
    {
        Repeating r = freshRepeating();
        RepeatingRuleModel m = new RepeatingRuleModel(
                RepeatingType.DAILY, 1, Set.of(), EndingMode.N_TIMES, null, 0);
        RepeatingRuleWriter.writeTo(m, r, START);
        assertEquals(1, r.getNumber());
        assertTrue(r.isFixedNumber());
    }

    @Test
    void nTimesPreservesValueAtOrAboveOne()
    {
        Repeating r = freshRepeating();
        RepeatingRuleModel m = new RepeatingRuleModel(
                RepeatingType.DAILY, 1, Set.of(), EndingMode.N_TIMES, null, 12);
        RepeatingRuleWriter.writeTo(m, r, START);
        assertEquals(12, r.getNumber());
    }

    @Test
    void foreverSetsEndNullAndNumberMinusOne()
    {
        Repeating r = freshRepeating();
        // Pre-seed with a fixed-number state so we can observe the override
        r.setType(RepeatingType.DAILY);
        r.setNumber(7);
        assertNotNull(r.getEnd());
        RepeatingRuleModel m = new RepeatingRuleModel(
                RepeatingType.DAILY, 1, Set.of(), EndingMode.FOREVER, null, -1);
        RepeatingRuleWriter.writeTo(m, r, START);
        assertNull(r.getEnd());
        assertEquals(-1, r.getNumber());
    }

    @Test
    void typeWritebackChangesRepeatingType()
    {
        Repeating r = freshRepeating();
        r.setType(RepeatingType.DAILY);
        RepeatingRuleModel m = new RepeatingRuleModel(
                RepeatingType.MONTHLY, 1, Set.of(), EndingMode.FOREVER, null, -1);
        RepeatingRuleWriter.writeTo(m, r, START);
        assertEquals(RepeatingType.MONTHLY, r.getType());
    }

    private static Repeating freshRepeating()
    {
        Appointment a = new AppointmentImpl(START, START.plusHours(1));
        a.setRepeatingEnabled(true);
        return a.getRepeating();
    }
}
