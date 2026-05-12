package org.rapla.plugin.tableview;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins the wire shape of {@link ExportService} (PRD 030 Phase 5).
 * Any path / method / param rename breaks this test, forcing the Angular
 * team and the server controller to coordinate.
 */
class ExportServiceContractTest
{
    @Test
    void rootPathIsExport()
    {
        HttpExchange root = ExportService.class.getAnnotation(HttpExchange.class);
        assertNotNull(root, "Service must be annotated @HttpExchange");
        assertEquals("/export", root.value());
    }

    @Test
    void csvEndpointIsGetExchangeUnderRoot()
    {
        Method m = methodNamed("csv");
        GetExchange ge = m.getAnnotation(GetExchange.class);
        assertNotNull(ge, "csv() must be @GetExchange");
        assertEquals("/csv", ge.value());
        assertEquals(String.class, m.getReturnType());
    }

    @Test
    void csvEndpointAdvertisesCsvAccept()
    {
        Method m = methodNamed("csv");
        GetExchange ge = m.getAnnotation(GetExchange.class);
        assertEquals(1, ge.accept().length);
        assertEquals("text/csv", ge.accept()[0]);
    }

    @Test
    void csvHasFiveQueryParams()
    {
        Method m = methodNamed("csv");
        Parameter[] ps = m.getParameters();
        assertEquals(5, ps.length, "expected: tableName, from, to, columns, sort");
        assertRequestParam(ps[0], "tableName", String.class, true);
        assertRequestParam(ps[1], "from",      String.class, true);
        assertRequestParam(ps[2], "to",        String.class, true);
        assertRequestParam(ps[3], "columns",   List.class,   false);
        assertRequestParam(ps[4], "sort",      List.class,   false);
    }

    private static Method methodNamed(String name)
    {
        for (Method m : ExportService.class.getDeclaredMethods())
        {
            if (m.getName().equals(name)) return m;
        }
        fail("no method named " + name + " on ExportService");
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
}
