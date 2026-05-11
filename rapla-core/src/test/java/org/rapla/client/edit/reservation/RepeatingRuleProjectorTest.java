package org.rapla.client.edit.reservation;

import org.junit.jupiter.api.Test;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.DayChooserMode;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.DayChooserState;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.EndDateBinding;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.EndingMode;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.EndingPanelVisibility;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.ExceptionButtonState;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.ExceptionCountStyle;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.RepeatingPanelVisibility;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.WeekdaySelection;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.internal.AppointmentImpl;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 unit coverage of {@link RepeatingRuleProjector} — the pure logic
 * extracted from {@code AppointmentController.RepeatingEditor}.
 * No Swing, no facade, no Spring.
 */
class RepeatingRuleProjectorTest
{
    // ---------- formatExceptionCountLabel ----------

    @Test
    void exceptionLabelZeroCountPaddedSingleDigit()
    {
        assertEquals("exceptions ( 0 )",
                RepeatingRuleProjector.formatExceptionCountLabel("exceptions", 0));
    }

    @Test
    void exceptionLabelOneCountPaddedSingleDigit()
    {
        assertEquals("exceptions ( 1 )",
                RepeatingRuleProjector.formatExceptionCountLabel("exceptions", 1));
    }

    @Test
    void exceptionLabelEightCountPaddedSingleDigit()
    {
        assertEquals("exceptions ( 8 )",
                RepeatingRuleProjector.formatExceptionCountLabel("exceptions", 8));
    }

    @Test
    void exceptionLabelNineCountNotPadded()
    {
        assertEquals("exceptions (9)",
                RepeatingRuleProjector.formatExceptionCountLabel("exceptions", 9));
    }

    @Test
    void exceptionLabelTenCountNotPadded()
    {
        assertEquals("exceptions (10)",
                RepeatingRuleProjector.formatExceptionCountLabel("exceptions", 10));
    }

    // ---------- styleForExceptionCount ----------

    @Test
    void exceptionStyleNormalWhenZero()
    {
        assertEquals(ExceptionCountStyle.NORMAL, RepeatingRuleProjector.styleForExceptionCount(0));
    }

    @Test
    void exceptionStyleHighlightedWhenPositive()
    {
        assertEquals(ExceptionCountStyle.HIGHLIGHTED, RepeatingRuleProjector.styleForExceptionCount(1));
        assertEquals(ExceptionCountStyle.HIGHLIGHTED, RepeatingRuleProjector.styleForExceptionCount(42));
    }

    // ---------- exceptionButtonState ----------

    @Test
    void exceptionButtonStateNullRepeating()
    {
        ExceptionButtonState s = RepeatingRuleProjector.exceptionButtonState("prefix", null);
        assertEquals(0, s.count());
        assertEquals(ExceptionCountStyle.NORMAL, s.style());
        assertEquals("prefix ( 0 )", s.label());
    }

    @Test
    void exceptionButtonStateNoExceptions()
    {
        Appointment a = daily("2026-06-01T09:00", "2026-06-01T10:00", 7);
        ExceptionButtonState s = RepeatingRuleProjector.exceptionButtonState("ex", a.getRepeating());
        assertEquals(0, s.count());
        assertEquals(ExceptionCountStyle.NORMAL, s.style());
    }

    @Test
    void exceptionButtonStateWithExceptions()
    {
        Appointment a = daily("2026-06-01T09:00", "2026-06-01T10:00", 14);
        a.getRepeating().addException(LocalDateTime.parse("2026-06-03T09:00"));
        a.getRepeating().addException(LocalDateTime.parse("2026-06-05T09:00"));
        ExceptionButtonState s = RepeatingRuleProjector.exceptionButtonState("ex", a.getRepeating());
        assertEquals(2, s.count());
        assertEquals(ExceptionCountStyle.HIGHLIGHTED, s.style());
        assertEquals("ex ( 2 )", s.label());
    }

    // ---------- endingMode ----------

    @Test
    void endingModeNullRepeating()
    {
        assertEquals(EndingMode.FOREVER, RepeatingRuleProjector.endingMode(null));
    }

    @Test
    void endingModeForeverWhenEndNull()
    {
        Appointment a = daily("2026-06-01T09:00", "2026-06-01T10:00", -1);
        // setNumber(-1) leaves end=null, isFixedNumber=false → FOREVER
        assertNull(a.getRepeating().getEnd());
        assertEquals(EndingMode.FOREVER, RepeatingRuleProjector.endingMode(a.getRepeating()));
    }

    @Test
    void endingModeNTimesWhenFixedNumber()
    {
        Appointment a = daily("2026-06-01T09:00", "2026-06-01T10:00", 7);
        // setNumber(7) → isFixedNumber=true and end is computed
        assertTrue(a.getRepeating().isFixedNumber());
        assertNotNull(a.getRepeating().getEnd());
        assertEquals(EndingMode.N_TIMES, RepeatingRuleProjector.endingMode(a.getRepeating()));
    }

    @Test
    void endingModeUntilWhenEndSetButNotFixedNumber()
    {
        Appointment a = daily("2026-06-01T09:00", "2026-06-01T10:00", 7);
        // Switch to end-date-driven mode: setEnd → isFixedNumber=false
        a.getRepeating().setEnd(LocalDateTime.parse("2026-07-01T00:00"));
        assertFalse(a.getRepeating().isFixedNumber());
        assertNotNull(a.getRepeating().getEnd());
        assertEquals(EndingMode.UNTIL, RepeatingRuleProjector.endingMode(a.getRepeating()));
    }

    // ---------- endingPanelVisibility ----------

    @Test
    void endingPanelsUntilWithPeriod()
    {
        EndingPanelVisibility v = RepeatingRuleProjector.endingPanelVisibility(EndingMode.UNTIL, true);
        assertTrue(v.endDateVisible());
        assertTrue(v.endDatePeriodPanelVisible());
        assertFalse(v.numberPanelVisible());
    }

    @Test
    void endingPanelsUntilWithoutPeriod()
    {
        EndingPanelVisibility v = RepeatingRuleProjector.endingPanelVisibility(EndingMode.UNTIL, false);
        assertTrue(v.endDateVisible());
        assertFalse(v.endDatePeriodPanelVisible());
        assertFalse(v.numberPanelVisible());
    }

    @Test
    void endingPanelsNTimes()
    {
        EndingPanelVisibility v = RepeatingRuleProjector.endingPanelVisibility(EndingMode.N_TIMES, true);
        assertFalse(v.endDateVisible());
        assertFalse(v.endDatePeriodPanelVisible());
        assertTrue(v.numberPanelVisible());
    }

    @Test
    void endingPanelsForever()
    {
        EndingPanelVisibility v = RepeatingRuleProjector.endingPanelVisibility(EndingMode.FOREVER, true);
        assertFalse(v.endDateVisible());
        assertFalse(v.endDatePeriodPanelVisible());
        assertFalse(v.numberPanelVisible());
    }

    // ---------- endDateBinding ----------

    @Test
    void endDateBindingNullForForever()
    {
        Appointment a = daily("2026-06-01T09:00", "2026-06-01T10:00", -1);
        assertNull(RepeatingRuleProjector.endDateBinding(a.getRepeating()));
    }

    @Test
    void endDateBindingSubsDayAndCutsDate()
    {
        Appointment a = daily("2026-06-01T09:00", "2026-06-01T10:00", 7);
        LocalDateTime rawEnd = a.getRepeating().getEnd();
        assertNotNull(rawEnd);
        EndDateBinding b = RepeatingRuleProjector.endDateBinding(a.getRepeating());
        assertNotNull(b);
        assertEquals(DateTools.subDay(rawEnd), b.endDate());
        assertEquals(DateTools.cutDate(b.endDate()), b.endDatePeriodDate());
        assertEquals(7, b.number());
    }

    // ---------- repeatingPanelVisibility ----------

    @Test
    void panelVisibilityDailyWithPeriod()
    {
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.DAILY, 7);
        RepeatingPanelVisibility v = RepeatingRuleProjector.repeatingPanelVisibility(a.getRepeating(), true);
        assertFalse(v.weekdayInMonthPanelVisible());
        assertTrue(v.intervalPanelVisible());
        assertFalse(v.dayInMonthPanelVisible());
        assertTrue(v.startDatePeriodVisible());
        assertTrue(v.endDatePeriodVisible());
        assertFalse(v.weekdaysPanelVisible());
        assertTrue(v.dayLabelVisible());
        assertFalse(v.weekdayChooserVisible());
        assertFalse(v.monthChooserVisible());
    }

    @Test
    void panelVisibilityDailyWithoutPeriod()
    {
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.DAILY, 7);
        RepeatingPanelVisibility v = RepeatingRuleProjector.repeatingPanelVisibility(a.getRepeating(), false);
        // startDatePeriod follows periodVisible — must collapse to false
        assertFalse(v.startDatePeriodVisible());
        // endDatePeriod does NOT gate on periodVisible (verbatim from original)
        assertTrue(v.endDatePeriodVisible());
    }

    @Test
    void panelVisibilityWeekly()
    {
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.WEEKLY, 4);
        RepeatingPanelVisibility v = RepeatingRuleProjector.repeatingPanelVisibility(a.getRepeating(), true);
        assertFalse(v.weekdayInMonthPanelVisible());
        assertTrue(v.intervalPanelVisible());
        assertFalse(v.dayInMonthPanelVisible());
        assertTrue(v.startDatePeriodVisible());
        assertTrue(v.endDatePeriodVisible());
        assertTrue(v.weekdaysPanelVisible());
        assertFalse(v.dayLabelVisible());
        assertTrue(v.weekdayChooserVisible());
        assertFalse(v.monthChooserVisible());
    }

    @Test
    void panelVisibilityMonthly()
    {
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.MONTHLY, 3);
        RepeatingPanelVisibility v = RepeatingRuleProjector.repeatingPanelVisibility(a.getRepeating(), true);
        assertTrue(v.weekdayInMonthPanelVisible());
        assertFalse(v.intervalPanelVisible());
        assertFalse(v.dayInMonthPanelVisible());
        assertFalse(v.startDatePeriodVisible());
        assertFalse(v.endDatePeriodVisible());
        assertFalse(v.weekdaysPanelVisible());
        assertFalse(v.dayLabelVisible());
        assertTrue(v.weekdayChooserVisible());
        assertFalse(v.monthChooserVisible());
    }

    @Test
    void panelVisibilityYearly()
    {
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.YEARLY, 2);
        RepeatingPanelVisibility v = RepeatingRuleProjector.repeatingPanelVisibility(a.getRepeating(), true);
        assertFalse(v.weekdayInMonthPanelVisible());
        assertFalse(v.intervalPanelVisible());
        assertTrue(v.dayInMonthPanelVisible());
        assertFalse(v.startDatePeriodVisible());
        assertFalse(v.endDatePeriodVisible());
        assertFalse(v.weekdaysPanelVisible());
        assertFalse(v.dayLabelVisible());
        assertFalse(v.weekdayChooserVisible());
        assertTrue(v.monthChooserVisible());
    }

    @Test
    void panelVisibilityNullRepeatingAllHidden()
    {
        RepeatingPanelVisibility v = RepeatingRuleProjector.repeatingPanelVisibility(null, true);
        assertFalse(v.weekdayInMonthPanelVisible());
        assertFalse(v.intervalPanelVisible());
        assertFalse(v.dayInMonthPanelVisible());
        assertFalse(v.startDatePeriodVisible());
        assertFalse(v.endDatePeriodVisible());
        assertFalse(v.weekdaysPanelVisible());
        assertFalse(v.dayLabelVisible());
        assertFalse(v.weekdayChooserVisible());
        assertFalse(v.monthChooserVisible());
    }

    // ---------- dayChooserState ----------

    @Test
    void dayChooserSameDay()
    {
        DayChooserState s = RepeatingRuleProjector.dayChooserState(0);
        assertEquals(DayChooserMode.SAME_DAY, s.mode());
        assertFalse(s.daysVisible());
        assertEquals(0, s.days());
    }

    @Test
    void dayChooserNextDay()
    {
        DayChooserState s = RepeatingRuleProjector.dayChooserState(1);
        assertEquals(DayChooserMode.NEXT_DAY, s.mode());
        assertFalse(s.daysVisible());
        assertEquals(1, s.days());
    }

    @Test
    void dayChooserXDaysTwo()
    {
        DayChooserState s = RepeatingRuleProjector.dayChooserState(2);
        assertEquals(DayChooserMode.X_DAYS, s.mode());
        assertTrue(s.daysVisible());
        assertEquals(2, s.days());
    }

    @Test
    void dayChooserXDaysLarge()
    {
        DayChooserState s = RepeatingRuleProjector.dayChooserState(30);
        assertEquals(DayChooserMode.X_DAYS, s.mode());
        assertTrue(s.daysVisible());
        assertEquals(30, s.days());
    }

    // ---------- weekdaySelections ----------

    @Test
    void weekdaySelectionsEmptyWhenNotWeekly()
    {
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.DAILY, 7);
        Set<Integer> keys = mondayThroughSunday();
        Map<Integer, WeekdaySelection> sels =
                RepeatingRuleProjector.weekdaySelections(a.getRepeating(), 2, keys);
        assertTrue(sels.isEmpty());
    }

    @Test
    void weekdaySelectionsAnchorWeekdaySelectedAndDisabled()
    {
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.WEEKLY, 4);
        // appointment start is Monday 2026-06-01; DateTools weekday 2 is Monday
        // (Sunday=1, Monday=2, ..., Saturday=7 per DateTools convention)
        int startWeekday = DateTools.getWeekday(LocalDateTime.parse("2026-06-01T09:00"));
        Set<Integer> keys = mondayThroughSunday();
        Map<Integer, WeekdaySelection> sels =
                RepeatingRuleProjector.weekdaySelections(a.getRepeating(), startWeekday, keys);
        WeekdaySelection anchor = sels.get(startWeekday);
        assertNotNull(anchor);
        assertTrue(anchor.selected(), "start weekday must be selected");
        assertFalse(anchor.enabled(), "start weekday must be disabled (cannot opt out)");
    }

    @Test
    void weekdaySelectionsAdditionalWeekdaysSelectedAndEnabled()
    {
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.WEEKLY, 4);
        int startWeekday = DateTools.getWeekday(LocalDateTime.parse("2026-06-01T09:00"));
        // Add Wednesday (weekday 4) to the active set
        Set<Integer> active = new TreeSet<>();
        active.add(4);
        a.getRepeating().setWeekdays(active);
        Set<Integer> keys = mondayThroughSunday();
        Map<Integer, WeekdaySelection> sels =
                RepeatingRuleProjector.weekdaySelections(a.getRepeating(), startWeekday, keys);
        WeekdaySelection wed = sels.get(4);
        assertNotNull(wed);
        assertTrue(wed.selected(), "Wednesday should be selected (in active set)");
        assertTrue(wed.enabled(), "Wednesday should be enabled (not the anchor)");
        // A non-active, non-anchor weekday is unselected but enabled
        WeekdaySelection thu = sels.get(5);
        assertNotNull(thu);
        assertFalse(thu.selected());
        assertTrue(thu.enabled());
    }

    @Test
    void weekdaySelectionsKeySetPreserved()
    {
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.WEEKLY, 4);
        Set<Integer> keys = mondayThroughSunday();
        Map<Integer, WeekdaySelection> sels =
                RepeatingRuleProjector.weekdaySelections(a.getRepeating(), 2, keys);
        assertEquals(keys, sels.keySet());
    }

    @Test
    void weekdaySelectionsEmptyKeySetReturnsEmptyMap()
    {
        Appointment a = repeating("2026-06-01T09:00", "2026-06-01T10:00", RepeatingType.WEEKLY, 4);
        Map<Integer, WeekdaySelection> sels =
                RepeatingRuleProjector.weekdaySelections(a.getRepeating(), 2, Set.of());
        assertTrue(sels.isEmpty());
    }

    // ---------- helpers ----------

    private static Appointment daily(String startIso, String endIso, int number)
    {
        return repeating(startIso, endIso, RepeatingType.DAILY, number);
    }

    private static Appointment repeating(String startIso, String endIso,
                                         RepeatingType type, int number)
    {
        Appointment a = new AppointmentImpl(LocalDateTime.parse(startIso), LocalDateTime.parse(endIso));
        a.setRepeatingEnabled(true);
        Repeating r = a.getRepeating();
        r.setType(type);
        r.setNumber(number);
        return a;
    }

    private static Set<Integer> mondayThroughSunday()
    {
        // DateTools weekday convention: Sunday=1, Monday=2, ..., Saturday=7.
        // Use a stable iteration order matching the production widget order.
        Set<Integer> s = new LinkedHashSet<>();
        for (int i = 1; i <= 7; i++) s.add(i);
        return s;
    }
}
