package org.rapla.facade;

import java.time.LocalDateTime;

/**
 * Pure-Java helper for "does this work-time range cross midnight"
 * (PRD 023 carve-out from {@code CalendarOption.dateChanged}).
 *
 * <p>The Swing calendar-options dialog flashes an error indicator when the
 * configured work-time end is at or before the work-time start, OR when
 * the end is set to {@code 00:00} (interpreted as midnight-next-day, which
 * is also "overnight" in the legacy logic). This helper encodes that rule
 * so it can be tier-1 tested without booting Swing — same rule will apply
 * to the Angular calendar-options form.
 *
 * <p>The {@link LocalDateTime} arguments contribute only their hour and
 * minute components; the date part is ignored.
 *
 * <h2>Carve-out notes</h2>
 *
 * The legacy {@code dateChanged} code carried a documented bug: both
 * {@code startTime} and {@code endTime} read from {@code worktimeEnd}.
 * Per the in-source comment that bug was "preserved during PRD 014
 * migration to avoid behaviour change". This carve-out fixes it — the
 * helper takes two distinct inputs, and the caller (after the refactor)
 * passes {@code worktimeStart} and {@code worktimeEnd} correctly.
 */
public final class WorktimeRange
{
    private WorktimeRange() {}

    /**
     * @return {@code true} if {@code start &gt;= end} (end-time before or
     *         equal to start-time) OR {@code end == 00:00} (interpreted
     *         as midnight-next-day).
     */
    public static boolean isOvernight(LocalDateTime start, LocalDateTime end)
    {
        int s = start == null ? 0 : start.getHour() * 60 + start.getMinute();
        int e = end == null ? 0 : end.getHour() * 60 + end.getMinute();
        if (e == 0) e = 24 * 60;
        return s >= e || e == 24 * 60;
    }
}
