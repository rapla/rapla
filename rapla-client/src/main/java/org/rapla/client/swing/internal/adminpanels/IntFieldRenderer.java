package org.rapla.client.swing.internal.adminpanels;

import org.rapla.plugin.adminpanels.Field;

import javax.swing.JFormattedTextField;
import javax.swing.text.NumberFormatter;
import java.text.NumberFormat;

final class IntFieldRenderer implements FieldRenderer
{
    private final JFormattedTextField textField;

    IntFieldRenderer(Field field)
    {
        NumberFormatter formatter = new NumberFormatter(NumberFormat.getIntegerInstance());
        formatter.setValueClass(Long.class);
        formatter.setAllowsInvalid(false);
        textField = new JFormattedTextField(formatter);
        textField.setColumns(10);
        textField.setEditable(!field.readOnly());
        if (field.helpText() != null) textField.setToolTipText(field.helpText());
    }

    @Override public JFormattedTextField getEditor() { return textField; }

    @Override public void setValue(Object value)
    {
        if (value instanceof Number n) textField.setValue(n.longValue());
        else if (value == null)        textField.setValue(null);
        else                           textField.setText(value.toString());
    }

    @Override public Object getValue()
    {
        Object v = textField.getValue();
        return v instanceof Number n ? n.longValue() : null;
    }
}
