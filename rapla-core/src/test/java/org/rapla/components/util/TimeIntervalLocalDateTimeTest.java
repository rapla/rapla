package org.rapla.components.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TimeIntervalLocalDateTimeTest
{
    @Test
    void factoryAcceptsLocalDateTime()
    {
        LocalDateTime start = LocalDateTime.of(2024, 6, 15, 9, 0);
        LocalDateTime end = LocalDateTime.of(2024, 6, 15, 17, 0);
        TimeInterval interval = TimeInterval.of(start, end);
        assertNotNull(interval.getStart());
        assertNotNull(interval.getEnd());
        assertEquals(start, interval.getStartAsLocalDateTime());
        assertEquals(end, interval.getEndAsLocalDateTime());
    }

    @Test
    void factoryAcceptsNulls()
    {
        TimeInterval interval = TimeInterval.of(null, null);
        assertNull(interval.getStart());
        assertNull(interval.getEnd());
        assertNull(interval.getStartAsLocalDateTime());
        assertNull(interval.getEndAsLocalDateTime());
    }

    @Test
    void localDateTimeAccessorsConsistentWithDate()
    {
        LocalDateTime ldt = LocalDateTime.of(2024, 6, 15, 14, 30);
        TimeInterval interval = new TimeInterval(DateTools.toDate(ldt), null);
        assertEquals(ldt, interval.getStartAsLocalDateTime());
    }

    @Test
    void existingDateApiUnaffected()
    {
        Date start = new Date(1700000000000L);
        Date end = new Date(1700001000000L);
        TimeInterval interval = new TimeInterval(start, end);
        assertEquals(start, interval.getStart());
        assertEquals(end, interval.getEnd());
    }
}
