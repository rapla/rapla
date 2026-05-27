package org.rapla.plugin.tableview;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

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
 * Pins the wire shape of {@link TableViewService}. Any path / method /
 * return-type / DTO-field rename breaks this test, forcing the Angular
 * team and the server controller to coordinate.
 */
class TableViewServiceContractTest
{
    @Test
    void rootPathIsTable()
    {
        HttpExchange root = TableViewService.class.getAnnotation(HttpExchange.class);
        assertNotNull(root, "Service must be annotated @HttpExchange");
        assertEquals("/api/table", root.value());
    }

    @Test
    void reservationsEndpointIsPostExchangeUnderRoot()
    {
        Method m = methodNamed("reservations");
        PostExchange pe = m.getAnnotation(PostExchange.class);
        assertNotNull(pe, "reservations() must be @PostExchange");
        assertEquals("/reservations", pe.value());
        assertEquals(TablePage.class, m.getReturnType());
    }

    @Test
    void appointmentsEndpointIsPostExchangeUnderRoot()
    {
        Method m = methodNamed("appointments");
        PostExchange pe = m.getAnnotation(PostExchange.class);
        assertNotNull(pe, "appointments() must be @PostExchange");
        assertEquals("/appointments", pe.value());
        assertEquals(TablePage.class, m.getReturnType());
    }

    @Test
    void reservationsTakesTableQueryRequestBody()
    {
        assertSingleRequestBodyParam(methodNamed("reservations"));
    }

    @Test
    void appointmentsTakesTableQueryRequestBody()
    {
        assertSingleRequestBodyParam(methodNamed("appointments"));
    }

    private static void assertSingleRequestBodyParam(Method m)
    {
        Parameter[] ps = m.getParameters();
        assertEquals(1, ps.length, "expected a single @RequestBody parameter");
        assertNotNull(ps[0].getAnnotation(RequestBody.class),
                "param must be @RequestBody, was " + Arrays.toString(ps[0].getAnnotations()));
        assertEquals(TableQueryRequest.class, ps[0].getType(),
                "param type must be TableQueryRequest");
    }

    // ---------- wire record shapes ----------

    @Test
    void tableQueryRequestHasExpectedRecordComponents()
    {
        assertRecordComponents(TableQueryRequest.class,
                List.of("from", "to", "allocatables", "types", "owners",
                        "reservationFilter", "columns", "sort"));
    }

    @Test
    void reservationFilterDtoHasExpectedRecordComponents()
    {
        assertRecordComponents(TableQueryRequest.ReservationFilter.class,
                List.of("typeId", "rules"));
    }

    @Test
    void ruleDtoHasExpectedRecordComponents()
    {
        assertRecordComponents(TableQueryRequest.Rule.class,
                List.of("attributeKey", "conditions"));
    }

    @Test
    void conditionDtoHasExpectedRecordComponents()
    {
        assertRecordComponents(TableQueryRequest.Condition.class,
                List.of("operator", "value"));
    }

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

    // ---------- /config + /columns/catalog ----------

    @Test
    void configEndpointIsGetExchangeUnderRoot()
    {
        Method m = methodNamed("config");
        GetExchange ge = m.getAnnotation(GetExchange.class);
        assertNotNull(ge, "config() must be @GetExchange");
        assertEquals("/config", ge.value());
        assertEquals(TableColumnsResponse.class, m.getReturnType());
    }

    @Test
    void columnsCatalogEndpointIsGetExchangeUnderRoot()
    {
        Method m = methodNamed("columnsCatalog");
        GetExchange ge = m.getAnnotation(GetExchange.class);
        assertNotNull(ge, "columnsCatalog() must be @GetExchange");
        assertEquals("/columns/catalog", ge.value());
        assertEquals(TableColumnsResponse.class, m.getReturnType());
    }

    @Test
    void configHasSingleTableNameParam()
    {
        Method m = methodNamed("config");
        Parameter[] ps = m.getParameters();
        assertEquals(1, ps.length);
        assertRequestParam(ps[0], "tableName", String.class, true);
    }

    @Test
    void columnsCatalogHasSingleTableNameParam()
    {
        Method m = methodNamed("columnsCatalog");
        Parameter[] ps = m.getParameters();
        assertEquals(1, ps.length);
        assertRequestParam(ps[0], "tableName", String.class, true);
    }

    @Test
    void tableColumnsResponseHasExpectedRecordComponents()
    {
        assertRecordComponents(TableColumnsResponse.class,
                List.of("tableName", "columns"));
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
