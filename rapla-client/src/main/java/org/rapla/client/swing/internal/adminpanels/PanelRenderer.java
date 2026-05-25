package org.rapla.client.swing.internal.adminpanels;

import org.rapla.plugin.adminpanels.ActionButton;
import org.rapla.plugin.adminpanels.ActionResult;
import org.rapla.plugin.adminpanels.Field;
import org.rapla.plugin.adminpanels.FieldType;
import org.rapla.plugin.adminpanels.PanelDefinition;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Renders one {@link PanelDefinition} into a Swing {@link JPanel}.
 *
 *  <p>Layout (top-to-bottom):
 *  <ul>
 *    <li>Header: bold title + optional grey description, with a separator below.</li>
 *    <li>Fields in {@link GridBagLayout}. Compact field types (BOOL/TEXT/INT/SELECT/
 *        PASSWORD/RADIO_GROUP/DISPLAY_ONLY) get label-on-left, editor-on-right with
 *        a consistent label column width. Wide types (LONG_TEXT, JSON_EDITOR) span
 *        both columns with the label above and the editor full-width below.</li>
 *    <li>Optional grey help-text under each editor when {@code Field.helpText} is set.</li>
 *    <li>Footer: panel-specific action buttons left-aligned, Save on the right —
 *        separated by horizontal glue. A separator above the footer visually splits
 *        the form from the actions.</li>
 *  </ul>
 *
 *  <p>Stateful — the same instance owns the editor widgets and the action+save row.
 *  One-shot: re-render with a fresh instance after a successful save (the
 *  server returns the refreshed {@link PanelDefinition}). */
final class PanelRenderer
{
    /** Minimum label-column width — keeps editors aligned across rows that
     *  have differently-sized labels. */
    private static final int LABEL_MIN_WIDTH_PX = 180;

    /** Help-text color (medium grey — readable but visibly secondary). */
    private static final Color HELP_TEXT_COLOR = new Color(0x66, 0x66, 0x66);

    private final PanelDefinition def;
    private final Map<String, FieldRenderer> renderers = new LinkedHashMap<>();
    private final JPanel rootPanel;

    /** Invoked when the user clicks an action button. Receives the action key
     *  and a snapshot of current field values; returns the server's
     *  {@link ActionResult}, which the renderer surfaces as a toast / dialog
     *  and applies to the relevant fields via {@code updatedValues}. */
    PanelRenderer(PanelDefinition def,
            BiFunction<String, Map<String, Object>, ActionResult> actionInvoker,
            Consumer<Map<String, Object>> saveAction,
            String saveLabel)
    {
        this.def = def;
        this.rootPanel = build(actionInvoker, saveAction, saveLabel);
    }

    JComponent getComponent() { return rootPanel; }

    /** Returns the widget state in wire form, suitable for
     *  {@code PreferencesAdminService.savePanel(...)}. {@link FieldType#DISPLAY_ONLY}
     *  fields are excluded (their values are server-computed). */
    Map<String, Object> collectValues()
    {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Field f : def.fields())
        {
            if (f.type() == FieldType.DISPLAY_ONLY) continue;
            if (f.type() == FieldType.ACTION_BUTTON) continue;
            FieldRenderer r = renderers.get(f.key());
            if (r == null) continue;
            values.put(f.key(), r.getValue());
        }
        return values;
    }

    private JPanel build(BiFunction<String, Map<String, Object>, ActionResult> actionInvoker,
            Consumer<Map<String, Object>> saveAction, String saveLabel)
    {
        // Body (header + fields) lives inside a JScrollPane so long forms
        // become scrollable; the footer (Save / action buttons) lives in the
        // outer SOUTH so it is always pinned to the bottom and visible.
        ScrollablePanel body = new ScrollablePanel(new BorderLayout(0, 8));
        body.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        body.add(buildHeader(), BorderLayout.NORTH);
        body.add(buildFields(), BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JComponent footer = buildFooter(actionInvoker, saveAction, saveLabel);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));

        JPanel container = new JPanel(new BorderLayout(0, 0));
        container.add(scroll, BorderLayout.CENTER);
        container.add(footer, BorderLayout.SOUTH);
        return container;
    }

    /** Inner panel for the scrollable body. Tracks viewport width so wide
     *  combo options (e.g. the Exchange Connector timezone list) shrink to
     *  fit the viewport instead of forcing a horizontal scrollbar. Does
     *  NOT track viewport height — content taller than the viewport gets
     *  a vertical scrollbar as expected. */
    private static final class ScrollablePanel extends JPanel implements Scrollable
    {
        ScrollablePanel(LayoutManager lm) { super(lm); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle vis, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle vis, int orientation, int direction)
        {
            return orientation == SwingConstants.VERTICAL ? vis.height : vis.width;
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private JComponent buildHeader()
    {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("<html><b>" + escape(def.title()) + "</b></html>");
        Font titleFont = title.getFont();
        title.setFont(titleFont.deriveFont(Font.BOLD, titleFont.getSize2D() + 2f));
        title.setAlignmentX(0f);
        header.add(title);
        if (def.description() != null && !def.description().isBlank())
        {
            JLabel desc = new JLabel("<html><div style='color:#666;'>" + escape(def.description()) + "</div></html>");
            desc.setAlignmentX(0f);
            header.add(Box.createVerticalStrut(2));
            header.add(desc);
        }
        header.add(Box.createVerticalStrut(8));
        header.add(new JSeparator(SwingConstants.HORIZONTAL));
        header.add(Box.createVerticalStrut(8));
        return header;
    }

    private JComponent buildFields()
    {
        JPanel grid = new JPanel(new GridBagLayout());

        int row = 0;
        for (Field f : def.fields())
        {
            if (f.type() == FieldType.ACTION_BUTTON) continue;   // rendered in footer
            FieldRenderer renderer = FieldRendererFactory.create(f);
            renderer.setValue(def.values().get(f.key()));
            renderers.put(f.key(), renderer);

            String labelText = (f.label() == null ? f.key() : f.label()) + ":";
            if (isFullWidthField(f.type()))
            {
                row = addFullWidthRow(grid, row, labelText, renderer.getEditor(), f.helpText());
            }
            else
            {
                row = addLabeledRow(grid, row, labelText, renderer.getEditor(), f.helpText());
            }
        }
        // Vertical glue so fields stick to the top of the scroll viewport.
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = row;
        filler.weighty = 1.0;
        filler.gridwidth = 2;
        filler.fill = GridBagConstraints.BOTH;
        grid.add(Box.createGlue(), filler);
        return grid;
    }

    private static boolean isFullWidthField(FieldType type)
    {
        return type == FieldType.LONG_TEXT || type == FieldType.JSON_EDITOR;
    }

    /** Label on the left, editor on the right; optional help-text on the next
     *  row aligned under the editor. */
    private static int addLabeledRow(JPanel grid, int row, String labelText, JComponent editor, String helpText)
    {
        GridBagConstraints labelC = new GridBagConstraints();
        labelC.gridx = 0;
        labelC.gridy = row;
        labelC.anchor = GridBagConstraints.NORTHEAST;       // right-align labels for a tidy column
        labelC.insets = new Insets(6, 0, 6, 12);

        JLabel label = new JLabel(labelText);
        // Force a minimum width on the label column so editors line up across rows.
        Dimension pref = label.getPreferredSize();
        if (pref.width < LABEL_MIN_WIDTH_PX)
        {
            label.setPreferredSize(new Dimension(LABEL_MIN_WIDTH_PX, pref.height));
        }
        grid.add(label, labelC);

        GridBagConstraints editorC = new GridBagConstraints();
        editorC.gridx = 1;
        editorC.gridy = row;
        editorC.fill = GridBagConstraints.HORIZONTAL;
        editorC.weightx = 1.0;
        editorC.insets = new Insets(6, 0, 6, 0);
        grid.add(editor, editorC);
        row++;

        if (helpText != null && !helpText.isBlank())
        {
            GridBagConstraints helpC = new GridBagConstraints();
            helpC.gridx = 1;
            helpC.gridy = row;
            helpC.fill = GridBagConstraints.HORIZONTAL;
            helpC.weightx = 1.0;
            helpC.insets = new Insets(0, 0, 6, 0);
            grid.add(buildHelpLabel(helpText), helpC);
            row++;
        }
        return row;
    }

    /** Label on its own row, editor spanning both columns below it.
     *  Used for LONG_TEXT / JSON_EDITOR so the multi-line editor isn't
     *  squeezed into a narrow right column. */
    private static int addFullWidthRow(JPanel grid, int row, String labelText, JComponent editor, String helpText)
    {
        GridBagConstraints labelC = new GridBagConstraints();
        labelC.gridx = 0;
        labelC.gridy = row;
        labelC.gridwidth = 2;
        labelC.anchor = GridBagConstraints.WEST;
        labelC.insets = new Insets(10, 0, 2, 0);
        grid.add(new JLabel(labelText), labelC);
        row++;

        GridBagConstraints editorC = new GridBagConstraints();
        editorC.gridx = 0;
        editorC.gridy = row;
        editorC.gridwidth = 2;
        editorC.fill = GridBagConstraints.BOTH;
        editorC.weightx = 1.0;
        editorC.weighty = 0.5;       // multi-line editors get more vertical room
        editorC.insets = new Insets(0, 0, 6, 0);
        grid.add(editor, editorC);
        row++;

        if (helpText != null && !helpText.isBlank())
        {
            GridBagConstraints helpC = new GridBagConstraints();
            helpC.gridx = 0;
            helpC.gridy = row;
            helpC.gridwidth = 2;
            helpC.fill = GridBagConstraints.HORIZONTAL;
            helpC.weightx = 1.0;
            helpC.insets = new Insets(0, 0, 8, 0);
            grid.add(buildHelpLabel(helpText), helpC);
            row++;
        }
        return row;
    }

    private static JLabel buildHelpLabel(String helpText)
    {
        JLabel label = new JLabel("<html><div style='color:#666;'>" + escape(helpText) + "</div></html>");
        Font f = label.getFont();
        label.setFont(f.deriveFont(f.getSize2D() - 1f));
        label.setForeground(HELP_TEXT_COLOR);
        return label;
    }

    private JComponent buildFooter(BiFunction<String, Map<String, Object>, ActionResult> actionInvoker,
            Consumer<Map<String, Object>> saveAction, String saveLabel)
    {
        JPanel footer = new JPanel(new BorderLayout(0, 6));
        footer.add(new JSeparator(SwingConstants.HORIZONTAL), BorderLayout.NORTH);

        // Action buttons on the left, Save on the right — single row, glue between.
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        boolean firstAction = true;
        for (ActionButton action : def.actions())
        {
            if (!firstAction) row.add(Box.createHorizontalStrut(8));
            JButton button = new JButton(action.label());
            if (action.description() != null) button.setToolTipText(action.description());
            button.addActionListener(e -> runAction(action, actionInvoker));
            row.add(button);
            firstAction = false;
        }
        row.add(Box.createHorizontalGlue());

        if (saveAction != null)
        {
            JButton save = new JButton(saveLabel);
            save.addActionListener(e -> saveAction.accept(collectValues()));
            // Default button — pressing Enter inside any field saves the form.
            // The button isn't in a window yet, so register a hierarchy listener
            // ON THE BUTTON ITSELF (not on rootPanel — that's still null during
            // build()) and wire up setDefaultButton once a rootPane is reachable.
            save.addHierarchyListener(e ->
            {
                javax.swing.JRootPane rp = javax.swing.SwingUtilities.getRootPane(save);
                if (rp != null) rp.setDefaultButton(save);
            });
            row.add(save);
        }

        footer.add(row, BorderLayout.CENTER);
        return footer;
    }

    private void runAction(ActionButton action, BiFunction<String, Map<String, Object>, ActionResult> actionInvoker)
    {
        if (action.confirmRequired())
        {
            String msg = action.confirmMessage() == null ? "Run action: " + action.label() + "?" : action.confirmMessage();
            int choice = JOptionPane.showConfirmDialog(rootPanel, msg, action.label(), JOptionPane.OK_CANCEL_OPTION);
            if (choice != JOptionPane.OK_OPTION) return;
        }
        ActionResult result = actionInvoker.apply(action.key(), collectValues());
        if (result == null) return;

        for (Map.Entry<String, Object> e : result.updatedValues().entrySet())
        {
            FieldRenderer r = renderers.get(e.getKey());
            if (r != null) r.setValue(e.getValue());
        }
        int messageType = result.success() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE;
        JOptionPane.showMessageDialog(rootPanel, result.message(), action.label(), messageType);
    }

    private static String escape(String s)
    {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
