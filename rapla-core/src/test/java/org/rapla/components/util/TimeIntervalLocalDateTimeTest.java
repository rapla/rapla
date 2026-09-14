package org.rapla.components.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TimeIntervalLocalDateTimeTest
{
    @Test
    void factoryAcceptsNulls()
    {
        TimeInterval interval = TimeInterval.of(null, null);
        assertNull(interval.getStart());
        assertNull(interval.getEnd());
        assertNull(interval.getStart());
        assertNull(interval.getEnd());
    }

    @Test
    void localDateTimeAccessorsConsistentWithDate()
    {
        LocalDateTime ldt = LocalDateTime.of(2024, 6, 15, 14, 30);
        TimeInterval interval = new TimeInterval(ldt, null);
        assertEquals(ldt, interval.getStart());
    }

    @Test
    void existingDateApiUnaffected()
    {
        LocalDateTime start = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(1700000000000L), java.time.ZoneOffset.UTC);
        LocalDateTime end = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(1700001000000L), java.time.ZoneOffset.UTC);
        TimeInterval interval = new TimeInterval(start, end);
        assertEquals(start, interval.getStart());
        assertEquals(end, interval.getEnd());
    }
}
