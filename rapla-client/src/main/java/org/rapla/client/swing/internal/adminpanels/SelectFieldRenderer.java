package org.rapla.client.swing.internal.adminpanels;

import org.rapla.plugin.adminpanels.Field;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** SELECT — single choice from a list. {@link Field#typeConfig()} carries
 *  {@code "options": List<Map<String, Object>>}, where each map has
 *  {@code "value"} (the wire value, typically String) and {@code "label"}. */
final class SelectFieldRenderer implements FieldRenderer
{
    private final JComboBox<Option> combo = new JComboBox<>();

    SelectFieldRenderer(Field field)
    {
        DefaultComboBoxModel<Option> model = new DefaultComboBoxModel<>();
        for (Option o : extractOptions(field)) model.addElement(o);
        combo.setModel(model);
        combo.setEnabled(!field.readOnly());
        if (field.helpText() != null) combo.setToolTipText(field.helpText());
    }

    @Override public JComboBox<?> getEditor() { return combo; }

    @Override public void setValue(Object value)
    {
        for (int i = 0; i < combo.getItemCount(); i++)
        {
            Option o = combo.getItemAt(i);
            if (o.value == null ? value == null : o.value.equals(value))
            {
                combo.setSelectedIndex(i);
                return;
            }
        }
        if (combo.getItemCount() > 0) combo.setSelectedIndex(0);
    }

    @Override public Object getValue()
    {
        Option o = (Option) combo.getSelectedItem();
        return o == null ? null : o.value;
    }

    @SuppressWarnings("unchecked")
    static List<Option> extractOptions(Field field)
    {
        List<Option> result = new ArrayList<>();
        Object raw = field.typeConfig().get("options");
        if (raw instanceof List<?> list)
        {
            for (Object entry : list)
            {
                if (entry instanceof Map<?, ?> m)
                {
                    Object value = m.get("value");
                    Object label = m.get("label");
                    result.add(new Option(value, label == null ? String.valueOf(value) : label.toString()));
                }
            }
        }
        return result;
    }

    /** Pair of (wire value, display label). */
    record Option(Object value, String label)
    {
        @Override public String toString() { return label; }
    }
}
