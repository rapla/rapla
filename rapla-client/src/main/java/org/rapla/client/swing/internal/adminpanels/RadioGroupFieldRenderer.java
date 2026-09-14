package org.rapla.client.swing.internal.adminpanels;

import org.rapla.plugin.adminpanels.Field;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import java.util.ArrayList;
import java.util.List;

/** RADIO_GROUP — same option list shape as SELECT (see
 *  {@link SelectFieldRenderer}); just renders with stacked radio buttons
 *  instead of a dropdown. Use this when the option count is small (≤4) and
 *  always-visible options aid the user choice. */
final class RadioGroupFieldRenderer implements FieldRenderer
{
    private final JPanel panel = new JPanel();
    private final ButtonGroup group = new ButtonGroup();
    private final List<SelectFieldRenderer.Option> options;
    private final List<JRadioButton> buttons = new ArrayList<>();

    RadioGroupFieldRenderer(Field field)
    {
        this.options = SelectFieldRenderer.extractOptions(field);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (SelectFieldRenderer.Option o : options)
        {
            JRadioButton b = new JRadioButton(o.label());
            b.setEnabled(!field.readOnly());
            if (field.helpText() != null) b.setToolTipText(field.helpText());
            group.add(b);
            buttons.add(b);
            panel.add(b);
        }
    }

    @Override public JComponent getEditor() { return panel; }

    @Override public void setValue(Object value)
    {
        for (int i = 0; i < options.size(); i++)
        {
            Object v = options.get(i).value();
            if (v == null ? value == null : v.equals(value))
            {
                buttons.get(i).setSelected(true);
                return;
            }
        }
        if (!buttons.isEmpty()) buttons.get(0).setSelected(true);
    }

    @Override public Object getValue()
    {
        for (int i = 0; i < buttons.size(); i++)
        {
            if (buttons.get(i).isSelected()) return options.get(i).value();
        }
        return null;
    }
}
