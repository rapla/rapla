package org.rapla.client.swing.internal.adminpanels;

import org.rapla.plugin.adminpanels.Field;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

final class TextFieldRenderer implements FieldRenderer
{
    private final JTextField textField = new JTextField(30);
    private final JComponent editor;

    TextFieldRenderer(Field field)
    {
        textField.setEditable(!field.readOnly());
        if (field.helpText() != null) textField.setToolTipText(field.helpText());
        // Opt-in copy button (typeConfig "copyable": true) — for read-only computed
        // values the admin needs to paste elsewhere, e.g. the stele export URL.
        if (Boolean.TRUE.equals(field.typeConfig().get("copyable")))
        {
            JPanel panel = new JPanel(new BorderLayout(6, 0));
            panel.add(textField, BorderLayout.CENTER);
            JButton copyButton = new JButton("Kopieren");
            copyButton.setToolTipText("In die Zwischenablage kopieren");
            copyButton.addActionListener(e -> copyToClipboard());
            panel.add(copyButton, BorderLayout.EAST);
            editor = panel;
        }
        else
        {
            editor = textField;
        }
    }

    private void copyToClipboard()
    {
        try
        {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(textField.getText()), null);
        }
        catch (RuntimeException ex)
        {
            // clipboard unavailable (headless / sandbox) — ignore, as SwingURLCopyService does
        }
    }

    @Override public JComponent getEditor() { return editor; }

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
