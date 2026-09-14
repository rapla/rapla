package org.rapla.test.util;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Test-only clock injected into presenters / models so date-dependent
 * logic is deterministic across runs. Set {@code today} explicitly per
 * test; default is {@code 2026-01-01}.
 * <p>
 * Used by {@link HeadlessPresenterTestSupport}.
 */
public final class MutableClock
{
    private LocalDate today = LocalDate.of(2026, 1, 1);
    private LocalDateTime now = today.atStartOfDay();

    public LocalDate today()
    {
        return today;
    }

    public LocalDateTime now()
    {
        return now;
    }

    /** Set {@code today} to an arbitrary date. {@code now()} becomes its start-of-day. */
    public void setToday(LocalDate date)
    {
        this.today = date;
        this.now = date.atStartOfDay();
    }

    /** Set {@code now()} to an arbitrary moment. {@code today()} is the date part. */
    public void setNow(LocalDateTime moment)
    {
        this.now = moment;
        this.today = moment.toLocalDate();
    }
}
