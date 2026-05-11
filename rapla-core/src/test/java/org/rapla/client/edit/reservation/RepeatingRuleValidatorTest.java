package org.rapla.client.edit.reservation;

import org.junit.jupiter.api.Test;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.EndingMode;
import org.rapla.client.edit.reservation.RepeatingRuleValidator.Code;
import org.rapla.client.edit.reservation.RepeatingRuleValidator.Result;
import org.rapla.entities.domain.RepeatingType;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatingRuleValidatorTest
{
    private static final LocalDateTime START = LocalDateTime.parse("2026-06-01T09:00");

    @Test
    void validDailyForever()
    {
        Result r = RepeatingRuleValidator.validate(daily(1, EndingMode.FOREVER, null, -1), START);
        assertTrue(r.isValid());
    }

    @Test
    void intervalZeroReportsClamp()
    {
        Result r = RepeatingRuleValidator.validate(daily(0, EndingMode.FOREVER, null, -1), START);
        assertFalse(r.isValid());
        assertHasCode(r, Code.INTERVAL_LESS_THAN_ONE);
    }

    @Test
    void intervalNegativeReportsClamp()
    {
        Result r = RepeatingRuleValidator.validate(daily(-5, EndingMode.FOREVER, null, -1), START);
        assertHasCode(r, Code.INTERVAL_LESS_THAN_ONE);
    }

    @Test
    void weeklyWithoutWeekdaysReportsIssue()
    {
        RepeatingRuleModel m = new RepeatingRuleModel(
                RepeatingType.WEEKLY, 1, Set.of(), EndingMode.FOREVER, null, -1);
        Result r = RepeatingRuleValidator.validate(m, START);
        assertHasCode(r, Code.WEEKLY_WITH_NO_WEEKDAYS);
    }

    @Test
    void weeklyWithWeekdaysIsValid()
    {
        RepeatingRuleModel m = new RepeatingRuleModel(
                RepeatingType.WEEKLY, 1, new TreeSet<>(Set.of(2)),
                EndingMode.FOREVER, null, -1);
        Result r = RepeatingRuleValidator.validate(m, START);
        assertTrue(r.isValid());
    }

    @Test
    void dailyEmptyWeekdaysIsValid()
    {
        // Weekday-empty is only reported for WEEKLY
        RepeatingRuleModel m = new RepeatingRuleModel(
                RepeatingType.DAILY, 1, Set.of(), EndingMode.FOREVER, null, -1);
        Result r = RepeatingRuleValidator.validate(m, START);
        assertTrue(r.isValid());
    }

    @Test
    void untilWithEndBeforeStartReportsIssue()
    {
        RepeatingRuleModel m = daily(1, EndingMode.UNTIL, START.minusDays(3), -1);
        Result r = RepeatingRuleValidator.validate(m, START);
        assertHasCode(r, Code.UNTIL_END_BEFORE_START);
    }

    @Test
    void untilWithNullEndReportsIssue()
    {
        RepeatingRuleModel m = daily(1, EndingMode.UNTIL, null, -1);
        Result r = RepeatingRuleValidator.validate(m, START);
        assertHasCode(r, Code.UNTIL_END_BEFORE_START);
    }

    @Test
    void untilWithEndAfterStartIsValid()
    {
        RepeatingRuleModel m = daily(1, EndingMode.UNTIL, START.plusDays(7), -1);
        Result r = RepeatingRuleValidator.validate(m, START);
        assertTrue(r.isValid());
    }

    @Test
    void untilWithEndOnSameDayIsValid()
    {
        RepeatingRuleModel m = daily(1, EndingMode.UNTIL, START.plusHours(1), -1);
        Result r = RepeatingRuleValidator.validate(m, START);
        assertTrue(r.isValid());
    }

    @Test
    void nTimesWithMinusOneReportsUnbounded()
    {
        RepeatingRuleModel m = daily(1, EndingMode.N_TIMES, null, -1);
        Result r = RepeatingRuleValidator.validate(m, START);
        assertHasCode(r, Code.N_TIMES_COUNT_UNBOUNDED);
    }

    @Test
    void nTimesWithZeroReportsLessThanOne()
    {
        RepeatingRuleModel m = daily(1, EndingMode.N_TIMES, null, 0);
        Result r = RepeatingRuleValidator.validate(m, START);
        assertHasCode(r, Code.N_TIMES_COUNT_LESS_THAN_ONE);
    }

    @Test
    void nTimesWithOneIsValid()
    {
        RepeatingRuleModel m = daily(1, EndingMode.N_TIMES, null, 1);
        Result r = RepeatingRuleValidator.validate(m, START);
        assertTrue(r.isValid());
    }

    @Test
    void multipleIssuesAllReported()
    {
        RepeatingRuleModel m = new RepeatingRuleModel(
                RepeatingType.WEEKLY, 0, Set.of(), EndingMode.N_TIMES, null, 0);
        Result r = RepeatingRuleValidator.validate(m, START);
        assertHasCode(r, Code.INTERVAL_LESS_THAN_ONE);
        assertHasCode(r, Code.WEEKLY_WITH_NO_WEEKDAYS);
        assertHasCode(r, Code.N_TIMES_COUNT_LESS_THAN_ONE);
        assertEquals(3, r.issues().size());
    }

    private static RepeatingRuleModel daily(int interval, EndingMode mode, LocalDateTime end, int count)
    {
        return new RepeatingRuleModel(RepeatingType.DAILY, interval, Set.of(), mode, end, count);
    }

    private static void assertHasCode(Result r, Code expected)
    {
        boolean found = r.issues().stream().anyMatch(i -> i.code() == expected);
        assertTrue(found, "expected issue " + expected + " in " + r.issues());
    }
}
