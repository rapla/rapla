package org.rapla.plugin.adminpanels;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Pins the wire shape of {@link PreferencesAdminService}. Any path/method/return-
 *  type/parameter change breaks this test, forcing coordination across the
 *  client renderer and every server-side {@code PreferencesPanel} impl. Pins
 *  {@link FieldType} enum names because the renderer dispatches on them by name. */
class PreferencesAdminServiceContractTest
{
    @Test
    void rootPathIsAdminPanels()
    {
        HttpExchange root = PreferencesAdminService.class.getAnnotation(HttpExchange.class);
        assertNotNull(root, "Service must be annotated @HttpExchange");
        assertEquals("/api/admin/panels", root.value());
    }

    @Test
    void listPanelsExposed()
    {
        Method m = methodNamed("listPanels");
        GetExchange ge = m.getAnnotation(GetExchange.class);
        assertNotNull(ge, "listPanels must be GET");
        assertEquals("", ge.value(), "listPanels lives at the root path");
        assertEquals(List.class, m.getReturnType());
        assertEquals(1, m.getParameterCount());
        Parameter p = m.getParameters()[0];
        assertEquals(PanelScope.class, p.getType());
        RequestParam rp = p.getAnnotation(RequestParam.class);
        assertNotNull(rp, "scope must be @RequestParam");
        assertEquals("scope", rp.value());
    }

    @Test
    void getPanelExposed()
    {
        Method m = methodNamed("getPanel");
        GetExchange ge = m.getAnnotation(GetExchange.class);
        assertNotNull(ge);
        assertEquals("/{id}", ge.value());
        assertEquals(PanelDefinition.class, m.getReturnType());
        assertEquals(1, m.getParameterCount());
        Parameter p = m.getParameters()[0];
        assertEquals(String.class, p.getType());
        assertNotNull(p.getAnnotation(PathVariable.class));
    }

    @Test
    void savePanelExposed()
    {
        Method m = methodNamed("savePanel");
        PostExchange pe = m.getAnnotation(PostExchange.class);
        assertNotNull(pe);
        assertEquals("/{id}/save", pe.value());
        assertEquals(PanelDefinition.class, m.getReturnType());
        assertEquals(2, m.getParameterCount());
        Parameter[] ps = m.getParameters();
        assertEquals(String.class, ps[0].getType());
        assertNotNull(ps[0].getAnnotation(PathVariable.class));
        assertEquals(Map.class, ps[1].getType());
        assertNotNull(ps[1].getAnnotation(RequestBody.class));
    }

    @Test
    void invokeActionExposed()
    {
        Method m = methodNamed("invokeAction");
        PostExchange pe = m.getAnnotation(PostExchange.class);
        assertNotNull(pe);
        assertEquals("/{id}/action/{actionId}", pe.value());
        assertEquals(ActionResult.class, m.getReturnType());
        assertEquals(3, m.getParameterCount());
        Parameter[] ps = m.getParameters();
        assertEquals(String.class, ps[0].getType());
        assertEquals(String.class, ps[1].getType());
        assertEquals(Map.class, ps[2].getType());
        assertNotNull(ps[2].getAnnotation(RequestBody.class));
    }

    @Test
    void fieldTypeEnumStable()
    {
        // The renderer dispatches on these by name. Renaming a value silently
        // breaks JSON deserialization. Adding a new value is fine — old clients
        // just don't render it (and we should add a graceful fallback in the
        // renderer).
        String[] expected = {
                "BOOL", "TEXT", "LONG_TEXT", "PASSWORD", "INT",
                "SELECT", "RADIO_GROUP", "DISPLAY_ONLY", "ACTION_BUTTON", "JSON_EDITOR"
        };
        FieldType[] actual = FieldType.values();
        assertEquals(expected.length, actual.length, "v1 ships exactly 10 field types");
        for (int i = 0; i < expected.length; i++)
        {
            assertEquals(expected[i], actual[i].name(), "field type ordering / naming must stay stable");
        }
    }

    @Test
    void panelScopeStable()
    {
        assertEquals(2, PanelScope.values().length);
        assertEquals("SYSTEM", PanelScope.SYSTEM.name());
        assertEquals("PER_USER", PanelScope.PER_USER.name());
    }

    @Test
    void dtoDefaultsAreNonNull()
    {
        // PanelDefinition / Field / ActionResult should never expose null fields/actions/values.
        PanelDefinition def = new PanelDefinition("id", PanelScope.SYSTEM, List.of("a"), "t",
                "d", null, null, null);
        assertNotNull(def.fields());
        assertNotNull(def.actions());
        assertNotNull(def.values());

        Field f = new Field("k", "Label", FieldType.TEXT, null, false, null);
        assertNotNull(f.typeConfig());

        ActionResult r = new ActionResult(true, "ok", null);
        assertNotNull(r.updatedValues());
    }

    @Test
    void actionResultHelpers()
    {
        assertTrue(ActionResult.ok("done").success());
        assertTrue(!ActionResult.fail("nope").success());
        assertEquals("done", ActionResult.ok("done").message());
        assertEquals(Map.of("computed", "x"),
                ActionResult.ok("done", Map.of("computed", "x")).updatedValues());
    }

    private static Method methodNamed(String name)
    {
        for (Method m : PreferencesAdminService.class.getDeclaredMethods())
        {
            if (m.getName().equals(name)) return m;
        }
        fail("Method not found: " + name);
        return null;
    }
}
