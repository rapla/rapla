package org.rapla.client.swing.internal.adminpanels;

import org.junit.jupiter.api.Test;
import org.rapla.plugin.adminpanels.Field;
import org.rapla.plugin.adminpanels.FieldType;
import org.rapla.plugin.adminpanels.PanelDefinition;
import org.rapla.plugin.adminpanels.PanelScope;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression: the Exchange Connector / JNDI plugin panels used to:
 *  <ul>
 *    <li>show a horizontal scrollbar driven by a wide combo (timezone list with
 *        hundreds of long entries like {@code America/Argentina/ComodRivadavia}), and</li>
 *    <li>hide the Save button below that scrollbar when the body was tall.</li>
 *  </ul>
 *  PanelRenderer must wrap its body in a JScrollPane with a viewport-tracking
 *  body, and pin the footer (Save + actions) outside that JScrollPane so it
 *  is always visible. */
class PanelRendererLayoutTest
{
    @Test
    void scrollableBodyTracksViewportWidthSoWideSelectDoesNotForceHorizontalScroll()
    {
        PanelRenderer renderer = new PanelRenderer(buildPanelWithWideSelect(),
                (a, v) -> null, v -> {}, "Save");
        JComponent root = (JComponent) renderer.getComponent();

        JScrollPane scroll = findFirst(root, JScrollPane.class);
        assertNotNull(scroll, "PanelRenderer must own a JScrollPane around its body");
        Component view = scroll.getViewport().getView();
        assertTrue(view instanceof Scrollable,
                "Scrolled body must implement Scrollable to control viewport-tracking");
        Scrollable s = (Scrollable) view;
        assertTrue(s.getScrollableTracksViewportWidth(),
                "tracksViewportWidth must be true — wide combo options must not force horizontal scroll");
        assertFalse(s.getScrollableTracksViewportHeight(),
                "tracksViewportHeight must be false — tall content still needs a vertical scrollbar");
    }

    @Test
    void saveButtonIsOutsideScrollPaneSoItStaysVisibleRegardlessOfBodyHeight()
    {
        PanelRenderer renderer = new PanelRenderer(buildPanelWithWideSelect(),
                (a, v) -> null, v -> {}, "Save");
        JComponent root = (JComponent) renderer.getComponent();

        JButton save = findSaveButton(root);
        JScrollPane scroll = findFirst(root, JScrollPane.class);
        assertNotNull(save, "Save button must be rendered");
        assertNotNull(scroll, "JScrollPane must be present");
        assertFalse(SwingUtilities.isDescendingFrom(save, scroll),
                "Save button must live OUTSIDE the JScrollPane so it is pinned to the bottom of the dialog");
    }

    private static PanelDefinition buildPanelWithWideSelect()
    {
        // Mimic the Exchange Connector timezone field — many options, all long.
        List<Map<String, Object>> tzOptions = new ArrayList<>();
        for (int i = 0; i < 400; i++)
        {
            String id = "America/Argentina/ComodRivadavia_" + i;
            tzOptions.add(Map.of("value", id, "label", id));
        }
        return new PanelDefinition("test", PanelScope.SYSTEM, List.of(), "Wide Select Test", null,
                List.of(
                        new Field("enabled",  "Enabled",  FieldType.BOOL,   null, false, Map.of()),
                        new Field("fqdn",     "FQDN",     FieldType.TEXT,   null, false, Map.of()),
                        new Field("timezone", "Timezone", FieldType.SELECT, null, false,
                                Map.of("options", tzOptions))),
                List.of(),
                Map.of("enabled", false, "fqdn", "https://ex.dhbw.de",
                        "timezone", "America/Argentina/ComodRivadavia_0"));
    }

    private static JButton findSaveButton(Container root)
    {
        for (Component c : root.getComponents())
        {
            if (c instanceof JButton b && "Save".equals(b.getText())) return b;
            if (c instanceof Container ct)
            {
                JButton hit = findSaveButton(ct);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> T findFirst(Container root, Class<T> type)
    {
        for (Component c : root.getComponents())
        {
            if (type.isInstance(c)) return (T) c;
            if (c instanceof Container ct)
            {
                T hit = findFirst(ct, type);
                if (hit != null) return hit;
            }
        }
        return null;
    }
}
