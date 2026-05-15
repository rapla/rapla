package org.rapla.plugin.calendarview;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins the wire shape of {@link CalendarViewService} (PRD 024 Phase 3).
 * Any path / method / return-type / DTO-field rename breaks this test,
 * forcing the Angular team and the server controller to coordinate.
 */
class CalendarViewServiceContractTest
{
    @Test
    void rootPathIsCalendar()
    {
        HttpExchange root = CalendarViewService.class.getAnnotation(HttpExchange.class);
        assertNotNull(root, "Service must be annotated @HttpExchange");
        assertEquals("/api/calendar", root.value());
    }

    @Test
    void viewEndpointIsGetExchangeUnderRoot()
    {
        Method m = methodNamed("view");
        GetExchange ge = m.getAnnotation(GetExchange.class);
        assertNotNull(ge, "view() must be @GetExchange");
        assertEquals("/view", ge.value());
        assertEquals(CalendarPage.class, m.getReturnType());
    }

    @Test
    void viewEndpointHasFiveQueryParams()
    {
        Method m = methodNamed("view");
        Parameter[] ps = m.getParameters();
        assertEquals(5, ps.length, "expected: from, to, strategy, groupBy, allocatables");
        assertRequestParam(ps[0], "from",         String.class,           true);
        assertRequestParam(ps[1], "to",           String.class,           true);
        assertRequestParam(ps[2], "strategy",     LayoutStrategyId.class, true);
        assertRequestParam(ps[3], "groupBy",      GroupBy.class,          true);
        assertRequestParam(ps[4], "allocatables", List.class,             false);
    }

    @Test
    void layoutStrategyIdHasGroupStartTimesAndBestFit()
    {
        Set<String> names = Arrays.stream(LayoutStrategyId.values())
                .map(Enum::name).collect(Collectors.toSet());
        assertEquals(Set.of("GROUP_START_TIMES", "BEST_FIT"), names);
    }

    @Test
    void groupByHasDayAndResource()
    {
        Set<String> names = Arrays.stream(GroupBy.values())
                .map(Enum::name).collect(Collectors.toSet());
        assertEquals(Set.of("DAY", "RESOURCE"), names);
    }

    @Test
    void calendarPageHasExpectedComponents()
    {
        assertRecordComponents(CalendarPage.class,
                "from", "to", "groupBy", "strategy", "columns", "blocks");
    }

    @Test
    void columnHasIdLabelIndex()
    {
        assertRecordComponents(Column.class, "id", "label", "index");
    }

    @Test
    void renderedBlockHasExpectedComponents()
    {
        // Order matters because the JSON shape is index-order-stable. If anyone
        // reorders, the wire format flips silently for serializers that look at
        // declaration order. Pin it.
        assertRecordComponents(RenderedBlock.class,
                "reservationId", "appointmentId",
                "columnIndex", "slotIndex", "slotCount",
                "start", "end",
                "colorsHex",
                "isException", "isRequest",
                "name", "tooltip");
    }

    private static void assertRequestParam(Parameter p, String expectedName,
                                           Class<?> expectedType, boolean expectedRequired)
    {
        RequestParam rp = p.getAnnotation(RequestParam.class);
        assertNotNull(rp, "parameter " + p.getName() + " must be @RequestParam");
        // Spring lets you pick value() or name(); both default to "".
        String name = rp.value().isEmpty() ? rp.name() : rp.value();
        assertEquals(expectedName, name);
        assertEquals(expectedType, p.getType());
        assertEquals(expectedRequired, rp.required(), "required mismatch on " + expectedName);
    }

    private static void assertRecordComponents(Class<?> recordClass, String... expected)
    {
        if (!recordClass.isRecord())
        {
            fail(recordClass.getName() + " must be a record");
        }
        RecordComponent[] comps = recordClass.getRecordComponents();
        String[] actual = Arrays.stream(comps).map(RecordComponent::getName).toArray(String[]::new);
        assertTrue(Arrays.equals(expected, actual),
                "record components mismatch for " + recordClass.getSimpleName()
                + ":\n  expected " + Arrays.toString(expected)
                + "\n  actual   " + Arrays.toString(actual));
    }

    private static Method methodNamed(String name)
    {
        return Arrays.stream(CalendarViewService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseGet(() -> { fail("no method named " + name); return null; });
    }
}
