package org.rapla.client.swing.internal.adminpanels;

import org.rapla.plugin.adminpanels.Field;

import javax.swing.JLabel;

/** DISPLAY_ONLY — read-only computed value rendered as a plain label.
 *  Server-pushed via {@code values}; user can't edit. Also used by the
 *  factory as a no-op placeholder for {@code ACTION_BUTTON} field rows
 *  (the dialog renders the actual button separately). */
final class DisplayOnlyFieldRenderer implements FieldRenderer
{
    private final JLabel label = new JLabel();

    DisplayOnlyFieldRenderer(Field field)
    {
        if (field.helpText() != null) label.setToolTipText(field.helpText());
    }

    @Override public JLabel getEditor() { return label; }

    @Override public void setValue(Object value)
    {
        label.setText(value == null ? "" : value.toString());
    }

    @Override public Object getValue()
    {
        // DISPLAY_ONLY values are never sent back on save (server-computed).
        return null;
    }
}
