package org.rapla.client.swing.internal.adminpanels;

import org.rapla.plugin.adminpanels.Field;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

final class LongTextFieldRenderer implements FieldRenderer
{
    private final JTextArea textArea = new JTextArea(6, 40);
    private final JScrollPane scroll;

    LongTextFieldRenderer(Field field)
    {
        textArea.setEditable(!field.readOnly());
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        if (field.helpText() != null) textArea.setToolTipText(field.helpText());
        scroll = new JScrollPane(textArea);
    }

    @Override public JComponent getEditor() { return scroll; }

    @Override public void setValue(Object value)
    {
        textArea.setText(value == null ? "" : value.toString());
        textArea.setCaretPosition(0);
    }

    @Override public Object getValue()
    {
        String s = textArea.getText();
        return s.isEmpty() ? null : s;
    }
}
