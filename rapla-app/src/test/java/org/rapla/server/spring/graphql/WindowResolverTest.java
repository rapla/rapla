package org.rapla.server.spring.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PRD 074 §"Window and inputs directives" — server-side anchor evaluation. Java twin of the
 * (deleted) SPA {@code resolveAnchorOffset}: same anchors, same UTC-free LocalDate math, same
 * {@code yyyy-MM-ddT00:00:00} output. Fixed "today" = Wednesday 2026-07-15.
 */
public class WindowResolverTest
{
    private static final LocalDate WED = LocalDate.of(2026, 7, 15);

    private static OperationDefinition op(String query)
    {
        Document doc = new Parser().parseDocument(query);
        return (OperationDefinition) doc.getDefinitions().get(0);
    }

    // --- anchor math (parity with the old TS resolveAnchorOffset) ---

    @Test
    public void weekStartResolvesToMonday()
    {
        assertEquals("2026-07-13T00:00:00", WindowResolver.resolve(ViewAnchor.WEEK_START, 0, ViewDateUnit.DAYS, WED));
    }

    @Test
    public void weekStartPlusSevenIsNextMonday()
    {
        assertEquals("2026-07-20T00:00:00", WindowResolver.resolve(ViewAnchor.WEEK_START, 7, ViewDateUnit.DAYS, WED));
    }

    @Test
    public void weekStartPlusFourteenIsTwoWeekSpan()
    {
        assertEquals("2026-07-27T00:00:00", WindowResolver.resolve(ViewAnchor.WEEK_START, 14, ViewDateUnit.DAYS, WED));
    }

    @Test
    public void todayPlusThreeDays()
    {
        assertEquals("2026-07-18T00:00:00", WindowResolver.resolve(ViewAnchor.TODAY, 3, ViewDateUnit.DAYS, WED));
    }

    @Test
    public void todayMinusSevenDays()
    {
        assertEquals("2026-07-08T00:00:00", WindowResolver.resolve(ViewAnchor.TODAY, -7, ViewDateUnit.DAYS, WED));
    }

    @Test
    public void monthStartIsFirstOfMonth()
    {
        assertEquals("2026-07-01T00:00:00", WindowResolver.resolve(ViewAnchor.MONTH_START, 0, ViewDateUnit.DAYS, WED));
    }

    @Test
    public void weeksUnitMultipliesBySeven()
    {
        assertEquals("2026-07-29T00:00:00", WindowResolver.resolve(ViewAnchor.TODAY, 2, ViewDateUnit.WEEKS, WED));
    }

    @Test
    public void monthsUnitAddsCalendarMonths()
    {
        assertEquals("2026-08-01T00:00:00", WindowResolver.resolve(ViewAnchor.MONTH_START, 1, ViewDateUnit.MONTHS, WED));
    }

    // --- @window directive off the operation AST ---

    @Test
    public void windowDirectiveResolvesBothEnds()
    {
        OperationDefinition o = op("""
                query W($filter: ReservationFilter!)
                  @view(title: "W")
                  @window(from: { anchor: WEEK_START, offset: 0, unit: DAYS },
                          to:   { anchor: WEEK_START, offset: 14, unit: DAYS })
                { reservations(filter: $filter) { name } }
                """);
        WindowResolver.Window w = WindowResolver.fromOperation(o, WED);
        assertEquals("2026-07-13T00:00:00", w.from());
        assertEquals("2026-07-27T00:00:00", w.to());
    }

    @Test
    public void missingUnitDefaultsToDays()
    {
        OperationDefinition o = op("""
                query W($filter: ReservationFilter!)
                  @window(from: { anchor: TODAY, offset: 0 }, to: { anchor: TODAY, offset: 3 })
                { reservations(filter: $filter) { name } }
                """);
        WindowResolver.Window w = WindowResolver.fromOperation(o, WED);
        assertEquals("2026-07-15T00:00:00", w.from());
        assertEquals("2026-07-18T00:00:00", w.to());
    }

    @Test
    public void noWindowDirectiveYieldsNull()
    {
        OperationDefinition o = op("""
                query W($filter: ReservationFilter!) @view(title: "W")
                { reservations(filter: $filter) { name } }
                """);
        assertNull(WindowResolver.fromOperation(o, WED));
    }

    // --- render-mode default fallback ---

    @Test
    public void weekModeDefaultsToCurrentIsoWeek()
    {
        WindowResolver.Window w = WindowResolver.defaultWindow(List.of("week"), WED);
        assertEquals("2026-07-13T00:00:00", w.from());
        assertEquals("2026-07-20T00:00:00", w.to());
    }

    @Test
    public void monthModeDefaultsToCurrentMonth()
    {
        WindowResolver.Window w = WindowResolver.defaultWindow(List.of("month"), WED);
        assertEquals("2026-07-01T00:00:00", w.from());
        assertEquals("2026-08-01T00:00:00", w.to());
    }

    @Test
    public void tableModeDefaultsToTodayMinusSevenPlusSeven()
    {
        WindowResolver.Window w = WindowResolver.defaultWindow(List.of("table", "grouped"), WED);
        assertEquals("2026-07-08T00:00:00", w.from());
        assertEquals("2026-07-22T00:00:00", w.to());
    }

    @Test
    public void emptyModesFallBackToTableWindow()
    {
        WindowResolver.Window w = WindowResolver.defaultWindow(List.of(), WED);
        assertEquals("2026-07-08T00:00:00", w.from());
        assertEquals("2026-07-22T00:00:00", w.to());
    }
}
