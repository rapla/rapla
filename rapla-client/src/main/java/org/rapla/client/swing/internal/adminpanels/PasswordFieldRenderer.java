package org.rapla.client.swing.internal.adminpanels;

import org.rapla.plugin.adminpanels.Field;

import javax.swing.JPasswordField;

final class PasswordFieldRenderer implements FieldRenderer
{
    private final JPasswordField field = new JPasswordField(30);

    PasswordFieldRenderer(Field f)
    {
        field.setEditable(!f.readOnly());
        if (f.helpText() != null) field.setToolTipText(f.helpText());
    }

    @Override public JPasswordField getEditor() { return field; }

    @Override public void setValue(Object value)
    {
        field.setText(value == null ? "" : value.toString());
    }

    @Override public Object getValue()
    {
        char[] pw = field.getPassword();
        return pw.length == 0 ? null : new String(pw);
    }
}
