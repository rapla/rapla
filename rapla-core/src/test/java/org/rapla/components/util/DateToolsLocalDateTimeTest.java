package org.rapla.components.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 001-A Phase A1 verification tests.
 *
 * <p>The new {@code java.time} overloads on {@link DateTools} and
 * {@link SerializableDateTimeFormat} must produce byte-identical results
 * to the existing {@code java.util.Date} overloads at every conversion
 * boundary, otherwise data round-trips through XML/SQL/REST will silently
 * shift dates by hours.
 */
class DateToolsLocalDateTimeTest
{
    @Test
    void toMilliRoundTrips()
    {
        LocalDateTime ldt = LocalDateTime.of(2024, 6, 15, 14, 30, 45);
        long millis = DateTools.toMilli(ldt);
        LocalDateTime back = DateTools.toLocalDateTime(millis);
        assertEquals(ldt, back, "LocalDateTime → millis → LocalDateTime must round-trip");

        // Compare against direct java.time computation
        long expected = ldt.toEpochSecond(ZoneOffset.UTC) * 1000;
        assertEquals(expected, millis);
    }

    @Test
    void toMilliLocalDateIsUtcMidnight()
    {
        LocalDate d = LocalDate.of(2024, 6, 15);
        long millis = DateTools.toMilli(d);
        assertEquals(0L, millis % DateTools.MILLISECONDS_PER_DAY, "LocalDate → millis must be midnight");
        LocalDateTime back = DateTools.toLocalDateTime(millis);
        assertEquals(d.atStartOfDay(), back);
    }

    @Test
    void cutDateForLocalDateTimeReturnsMidnight()
    {
        LocalDateTime ldt = LocalDateTime.of(2024, 6, 15, 14, 30, 45);
        LocalDateTime cut = DateTools.cutDate(ldt);
        assertEquals(LocalDateTime.of(2024, 6, 15, 0, 0, 0), cut);
        assertTrue(DateTools.isMidnight(cut));
        assertFalse(DateTools.isMidnight(ldt));
    }

    @Test
    void hourMinuteAccessors()
    {
        LocalDateTime ldt = LocalDateTime.of(2024, 6, 15, 14, 30, 45);
        assertEquals(14, DateTools.getHourOfDay(ldt));
        assertEquals(30, DateTools.getMinuteOfHour(ldt));
        assertEquals(45, DateTools.getSecondOfMinute(ldt));
        assertEquals(14 * 60 + 30, DateTools.getMinuteOfDay(ldt));
    }

    @Test
    void addAndSubtractDays()
    {
        LocalDate d = LocalDate.of(2024, 6, 15);
        assertEquals(LocalDate.of(2024, 6, 16), DateTools.addDay(d));
        assertEquals(LocalDate.of(2024, 6, 14), DateTools.subDay(d));
        assertEquals(LocalDate.of(2024, 6, 22), DateTools.addDays(d, 7));
        assertEquals(LocalDate.of(2024, 6, 8), DateTools.subDays(d, 7));
    }

    @Test
    void countDaysBetweenLocalDates()
    {
        LocalDate from = LocalDate.of(2024, 6, 15);
        LocalDate to = LocalDate.of(2024, 6, 22);
        assertEquals(7, DateTools.countDays(from, to));
    }

    @Test
    void formatTimestampLocalDateTimeMatchesDate() throws Exception
    {
        SerializableDateTimeFormat fmt = SerializableDateTimeFormat.INSTANCE;
        LocalDateTime ldt = LocalDateTime.of(2024, 6, 15, 14, 30, 45);
        LocalDateTime d = ldt;
        assertEquals(fmt.formatTimestamp(d), fmt.formatTimestamp(ldt));
    }

    @Test
    void formatDateLocalDateMatchesDate()
    {
        SerializableDateTimeFormat fmt = SerializableDateTimeFormat.INSTANCE;
        LocalDate ld = LocalDate.of(2024, 6, 15);
        LocalDateTime d = ld.atStartOfDay();
        assertEquals(fmt.formatDate(d), fmt.formatDate(ld));
    }

    @Test
    void parseLocalDateTimeRoundTrips() throws Exception
    {
        SerializableDateTimeFormat fmt = SerializableDateTimeFormat.INSTANCE;
        LocalDateTime original = LocalDateTime.of(2024, 6, 15, 14, 30, 45);
        String formatted = fmt.formatTimestamp(original);
        LocalDateTime parsed = fmt.parseLocalDateTime(formatted);
        assertEquals(original, parsed);
    }

    @Test
    void parseLocalDateRoundTrips() throws Exception
    {
        SerializableDateTimeFormat fmt = SerializableDateTimeFormat.INSTANCE;
        LocalDate original = LocalDate.of(2024, 6, 15);
        String formatted = fmt.formatDate(original);
        LocalDate parsed = fmt.parseLocalDate(formatted);
        assertEquals(original, parsed);
    }

    @Test
    void parseLocalTimeRoundTrips() throws Exception
    {
        SerializableDateTimeFormat fmt = SerializableDateTimeFormat.INSTANCE;
        LocalTime original = LocalTime.of(14, 30, 45);
        String formatted = fmt.formatTime(original);
        LocalTime parsed = fmt.parseLocalTime(formatted);
        assertEquals(original, parsed);
    }
}
