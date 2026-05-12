package org.rapla.facade.internal;

import org.junit.jupiter.api.Test;
import org.rapla.facade.WorktimeRange;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 contract pin for {@link WorktimeRange#isOvernight} — the
 * detection behind {@code CalendarOption.dateChanged}'s
 * "the work-time crosses midnight" error indicator.
 *
 * <p>Rule (faithful to the legacy code at {@code CalendarOption:308–319},
 * minus the pre-existing bug where both bounds read from
 * {@code worktimeEnd}):
 *
 * <ul>
 *   <li>End == 00:00 → treated as midnight-next-day → always counts as overnight.</li>
 *   <li>Start &gt;= End → overnight (the user picked an end-time before
 *       or equal to start-time).</li>
 *   <li>Otherwise → not overnight.</li>
 * </ul>
 *
 * <p>The {@link LocalDateTime} arguments contribute only their
 * hour-of-day + minute-of-hour; the date part is ignored.
 */
class WorktimeRangeTest
{
    private static LocalDateTime t(int hour, int minute)
    {
        return LocalDateTime.of(2026, 6, 1, hour, minute);
    }

    @Test
    void normalDayShiftIsNotOvernight()
    {
        assertFalse(WorktimeRange.isOvernight(t(8, 0), t(18, 0)));
    }

    @Test
    void endBeforeStartIsOvernight()
    {
        assertTrue(WorktimeRange.isOvernight(t(22, 0), t(6, 0)));
    }

    @Test
    void endEqualsStartIsOvernight()
    {
        // 9:00–9:00 is a degenerate range — equivalent to "no end" semantically;
        // legacy code flagged this as overnight.
        assertTrue(WorktimeRange.isOvernight(t(9, 0), t(9, 0)));
    }

    @Test
    void endAtMidnightIsOvernight()
    {
        // End == 00:00 is treated as 24:00 (next-day midnight), so any
        // start hits the e == 24*60 branch — always overnight.
        assertTrue(WorktimeRange.isOvernight(t(8, 0), t(0, 0)));
        assertTrue(WorktimeRange.isOvernight(t(23, 59), t(0, 0)));
        assertTrue(WorktimeRange.isOvernight(t(0, 0), t(0, 0)));
    }

    @Test
    void exactlyAtEndOfDayIsNotOvernight()
    {
        // End == 23:59 (1 minute before midnight) is the latest "not overnight"
        // value when start < 23:59.
        assertFalse(WorktimeRange.isOvernight(t(8, 0), t(23, 59)));
    }

    @Test
    void minuteGranularity()
    {
        // 09:30 → 09:31 is one minute of work, not overnight.
        assertFalse(WorktimeRange.isOvernight(t(9, 30), t(9, 31)));
        // 09:31 → 09:30 is overnight (end before start).
        assertTrue(WorktimeRange.isOvernight(t(9, 31), t(9, 30)));
    }

    @Test
    void dateComponentIgnored()
    {
        // Different dates with same time-of-day → result depends only on time.
        LocalDateTime monday    = LocalDateTime.of(2026, 6, 1, 8, 0);
        LocalDateTime fridayPm  = LocalDateTime.of(2026, 6, 5, 18, 0);
        assertFalse(WorktimeRange.isOvernight(monday, fridayPm));
    }

    @Test
    void nullStartFallsBackToZeroOffsetting()
    {
        // Defensive: null start → treat as 00:00. Pairs with a non-midnight
        // end → not overnight (since end > 0).
        assertFalse(WorktimeRange.isOvernight(null, t(18, 0)));
    }

    @Test
    void nullEndFallsBackToZeroWhichBecomesMidnight()
    {
        // Defensive: null end → treat as 00:00 → midnight semantics → overnight.
        assertTrue(WorktimeRange.isOvernight(t(8, 0), null));
    }
}
