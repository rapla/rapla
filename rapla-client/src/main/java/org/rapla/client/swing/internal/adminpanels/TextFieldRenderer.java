package org.rapla.client.swing.internal.adminpanels;

import org.rapla.plugin.adminpanels.Field;

import javax.swing.JTextField;

final class TextFieldRenderer implements FieldRenderer
{
    private final JTextField textField = new JTextField(30);

    TextFieldRenderer(Field field)
    {
        textField.setEditable(!field.readOnly());
        if (field.helpText() != null) textField.setToolTipText(field.helpText());
    }

    @Override public JTextField getEditor() { return textField; }

    @Override public void setValue(Object value)
    {
        textField.setText(value == null ? "" : value.toString());
    }

    @Override public Object getValue()
    {
        String s = textField.getText();
        return s.isEmpty() ? null : s;
    }
}
