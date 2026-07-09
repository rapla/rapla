package org.rapla.client.internal;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;

/**
 * Regression for the copy-SINGLE midnight bug (ReservationControllerImpl:638-640):
 * copying a single occurrence of a repeating appointment stripped the time-of-day,
 * so the pasted clone landed at 00:00 instead of its real start time. Root cause was
 * {@code DateTools.toTime(cutDate(start))} — toTime of a midnight value is always 0.
 * The single-copy start must keep the appointment's own time-of-day (the paste step
 * supplies the date).
 */
@RunWith(JUnit4.class)
public class SingleCopyStartTest
{
    @Test
    public void keepsTimeOfDay()
    {
        LocalDateTime mon10 = LocalDateTime.of(2026, 7, 13, 10, 0);
        // before the fix this returned 2026-07-13T00:00 (midnight)
        assertEquals(mon10, ReservationControllerImpl.singleCopyStart(mon10));
    }

    @Test
    public void keepsMinutesAndSeconds()
    {
        LocalDateTime withMinutes = LocalDateTime.of(2026, 7, 13, 9, 45, 30);
        assertEquals(withMinutes, ReservationControllerImpl.singleCopyStart(withMinutes));
    }

    @Test
    public void midnightStaysMidnight()
    {
        LocalDateTime midnight = LocalDateTime.of(2026, 7, 13, 0, 0);
        assertEquals(midnight, ReservationControllerImpl.singleCopyStart(midnight));
    }
}
