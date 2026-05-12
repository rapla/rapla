package org.rapla.plugin.tableview;

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
 * Pins the wire shape of {@link TableViewService} (PRD 030 Phase 2).
 * Any path / method / return-type / DTO-field rename breaks this test,
 * forcing the Angular team and the server controller to coordinate.
 */
class TableViewServiceContractTest
{
    @Test
    void rootPathIsTable()
    {
        HttpExchange root = TableViewService.class.getAnnotation(HttpExchange.class);
        assertNotNull(root, "Service must be annotated @HttpExchange");
        assertEquals("/table", root.value());
    }

    @Test
    void reservationsEndpointIsGetExchangeUnderRoot()
    {
        Method m = methodNamed("reservations");
        GetExchange ge = m.getAnnotation(GetExchange.class);
        assertNotNull(ge, "reservations() must be @GetExchange");
        assertEquals("/reservations", ge.value());
        assertEquals(TablePage.class, m.getReturnType());
    }

    @Test
    void appointmentsEndpointIsGetExchangeUnderRoot()
    {
        Method m = methodNamed("appointments");
        GetExchange ge = m.getAnnotation(GetExchange.class);
        assertNotNull(ge, "appointments() must be @GetExchange");
        assertEquals("/appointments", ge.value());
        assertEquals(TablePage.class, m.getReturnType());
    }

    @Test
    void reservationsHasSixQueryParams()
    {
        Method m = methodNamed("reservations");
        assertParamLayoutForTableEndpoint(m);
    }

    @Test
    void appointmentsHasSixQueryParams()
    {
        Method m = methodNamed("appointments");
        assertParamLayoutForTableEndpoint(m);
    }

    private static void assertParamLayoutForTableEndpoint(Method m)
    {
        Parameter[] ps = m.getParameters();
        assertEquals(6, ps.length, "expected: from, to, columns, sort, cursor, pageSize");
        assertRequestParam(ps[0], "from",     String.class,  true);
        assertRequestParam(ps[1], "to",       String.class,  true);
        assertRequestParam(ps[2], "columns",  List.class,    false);
        assertRequestParam(ps[3], "sort",     List.class,    false);
        assertRequestParam(ps[4], "cursor",   String.class,  false);
        assertRequestParam(ps[5], "pageSize", Integer.class, false);
    }

    // ---------- wire record shapes ----------

    @Test
    void tablePageHasExpectedRecordComponents()
    {
        assertRecordComponents(TablePage.class,
                List.of("columns", "rows", "totalCount", "nextCursor", "incomplete"));
    }

    @Test
    void tableColumnDescriptorHasExpectedRecordComponents()
    {
        assertRecordComponents(TableColumnDescriptor.class,
                List.of("id", "label", "type"));
    }

    @Test
    void tableRowHasExpectedRecordComponents()
    {
        assertRecordComponents(TableRow.class,
                List.of("id", "cells"));
    }

    @Test
    void tableCellTypeEnumValues()
    {
        Set<String> got = Arrays.stream(TableCellType.values()).map(Enum::name).collect(Collectors.toSet());
        // Adding new types is forward-compatible; removing them is not. This
        // assertion locks the v1 set so a deletion is loud.
        assertTrue(got.containsAll(Set.of("STRING", "INTEGER", "LONG", "DOUBLE", "DATE", "BOOLEAN")),
                "v1 wire-format cell types: " + got);
    }

    // ---------- helpers ----------

    private static Method methodNamed(String name)
    {
        for (Method m : TableViewService.class.getDeclaredMethods())
        {
            if (m.getName().equals(name)) return m;
        }
        fail("no method named " + name + " on TableViewService");
        return null;
    }

    private static void assertRequestParam(Parameter p, String expectedName, Class<?> expectedType, boolean required)
    {
        RequestParam rp = p.getAnnotation(RequestParam.class);
        assertNotNull(rp, "param " + p + " must be @RequestParam");
        String actualName = rp.value().isEmpty() ? rp.name() : rp.value();
        if (actualName.isEmpty()) actualName = p.getName();
        assertEquals(expectedName, actualName, "param name");
        assertEquals(expectedType, p.getType(), "param java type for " + expectedName);
        assertEquals(required, rp.required(), "param required-flag for " + expectedName);
    }

    private static void assertRecordComponents(Class<?> recordClass, List<String> expectedComponentNames)
    {
        assertTrue(recordClass.isRecord(), recordClass.getSimpleName() + " must be a record");
        List<String> actual = Arrays.stream(recordClass.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toList());
        assertEquals(expectedComponentNames, actual,
                recordClass.getSimpleName() + " record component declaration order");
    }
}
