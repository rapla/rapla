package org.rapla.client.swing.internal.adminpanels;

import org.rapla.plugin.adminpanels.Field;

import javax.swing.JCheckBox;

final class BoolFieldRenderer implements FieldRenderer
{
    private final JCheckBox checkBox;

    BoolFieldRenderer(Field field)
    {
        this.checkBox = new JCheckBox();
        checkBox.setEnabled(!field.readOnly());
        if (field.helpText() != null) checkBox.setToolTipText(field.helpText());
    }

    @Override public JCheckBox getEditor() { return checkBox; }

    @Override public void setValue(Object value)
    {
        checkBox.setSelected(value instanceof Boolean b && b);
    }

    @Override public Object getValue()
    {
        return checkBox.isSelected();
    }
}
