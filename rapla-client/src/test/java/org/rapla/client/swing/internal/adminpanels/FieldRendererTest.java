package org.rapla.client.swing.internal.adminpanels;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.rapla.plugin.adminpanels.Field;
import org.rapla.plugin.adminpanels.FieldType;

import javax.swing.JButton;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Round-trips wire values through each {@link FieldRenderer}: the value
 *  pushed via {@code setValue} should come back from {@code getValue}
 *  unchanged in shape (modulo type coercion the wire format expects, e.g.
 *  Integer→Long for INT). Catches subtle bugs like null vs empty-string,
 *  Long/Integer drift, or option lookup failures. */
class FieldRendererTest
{
    private static Field of(FieldType type) { return of(type, Map.of()); }

    private static Field of(FieldType type, Map<String, Object> typeConfig)
    {
        return new Field("k", "Label", type, null, false, typeConfig);
    }

    @Test
    void boolRoundTrips()
    {
        FieldRenderer r = FieldRendererFactory.create(of(FieldType.BOOL));
        r.setValue(true);
        assertEquals(true, r.getValue());
        r.setValue(false);
        assertEquals(false, r.getValue());
    }

    @Test
    void textRoundTripsAndEmptyIsNull()
    {
        FieldRenderer r = FieldRendererFactory.create(of(FieldType.TEXT));
        r.setValue("hello");
        assertEquals("hello", r.getValue());
        r.setValue("");
        assertNull(r.getValue());     // empty string ⇒ null on the wire
    }

    @Test
    void longTextRoundTrips()
    {
        FieldRenderer r = FieldRendererFactory.create(of(FieldType.LONG_TEXT));
        r.setValue("line1\nline2");
        assertEquals("line1\nline2", r.getValue());
    }

    @Test
    void passwordRoundTrips()
    {
        FieldRenderer r = FieldRendererFactory.create(of(FieldType.PASSWORD));
        r.setValue("s3cret");
        assertEquals("s3cret", r.getValue());
    }

    @Test
    void intCoercesNumberInputToLong()
    {
        FieldRenderer r = FieldRendererFactory.create(of(FieldType.INT));
        r.setValue(42);                          // Integer in
        assertEquals(42L, r.getValue());         // Long out — wire-format invariant
        r.setValue(123L);
        assertEquals(123L, r.getValue());
    }

    @Test
    void selectChoosesByValueAndRoundTripsString()
    {
        Field f = of(FieldType.SELECT, Map.of(
                "options", List.of(
                        Map.of("value", "a", "label", "A"),
                        Map.of("value", "b", "label", "B"))));
        FieldRenderer r = FieldRendererFactory.create(f);
        r.setValue("b");
        assertEquals("b", r.getValue());
        r.setValue("a");
        assertEquals("a", r.getValue());
    }

    @Test
    void radioGroupChoosesByValue()
    {
        Field f = of(FieldType.RADIO_GROUP, Map.of(
                "options", List.of(
                        Map.of("value", 1, "label", "One"),
                        Map.of("value", 2, "label", "Two"))));
        FieldRenderer r = FieldRendererFactory.create(f);
        r.setValue(2);
        assertEquals(2, r.getValue());
    }

    @Test
    void displayOnlyDoesNotRoundTrip()
    {
        FieldRenderer r = FieldRendererFactory.create(of(FieldType.DISPLAY_ONLY));
        r.setValue("read-only");
        assertNull(r.getValue());                // display-only never sends back on save
    }

    @Test
    void jsonEditorRoundTripsStructuredValue()
    {
        FieldRenderer r = FieldRendererFactory.create(of(FieldType.JSON_EDITOR));
        r.setValue(Map.of("a", 1, "b", List.of("x", "y")));
        Object out = r.getValue();
        assertInstanceOf(Map.class, out);
        Map<?, ?> m = (Map<?, ?>) out;
        assertEquals(1, ((Number) m.get("a")).intValue());
        assertEquals(List.of("x", "y"), m.get("b"));
    }

    @Test
    void jsonEditorEmptyIsNull()
    {
        FieldRenderer r = FieldRendererFactory.create(of(FieldType.JSON_EDITOR));
        r.setValue(null);
        assertNull(r.getValue());
    }

    @Test
    void copyableTextStillRoundTripsAndRendersCopyButton()
    {
        FieldRenderer r = FieldRendererFactory.create(of(FieldType.TEXT, Map.of("copyable", true)));
        r.setValue("https://host/api/dhbw/stele?key=abc");
        assertEquals("https://host/api/dhbw/stele?key=abc", r.getValue());   // value round-trip unaffected
        assertNotNull(findButton(r.getEditor()), "copyable TEXT field must render a copy button");
    }

    @Test
    void plainTextHasNoCopyButton()
    {
        FieldRenderer r = FieldRendererFactory.create(of(FieldType.TEXT));    // no copyable flag
        assertNull(findButton(r.getEditor()), "non-copyable TEXT field must not render a button");
    }

    @Test
    void copyButtonPutsCurrentFieldValueOnClipboard() throws Exception
    {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "clipboard needs a display");
        FieldRenderer r = FieldRendererFactory.create(of(FieldType.TEXT, Map.of("copyable", true)));
        r.setValue("copy-me-123");
        findButton(r.getEditor()).doClick();
        String clip = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        assertEquals("copy-me-123", clip);
    }

    private static JButton findButton(Container c)
    {
        for (Component comp : c.getComponents())
        {
            if (comp instanceof JButton b) return b;
            if (comp instanceof Container child)
            {
                JButton found = findButton(child);
                if (found != null) return found;
            }
        }
        return null;
    }
}
