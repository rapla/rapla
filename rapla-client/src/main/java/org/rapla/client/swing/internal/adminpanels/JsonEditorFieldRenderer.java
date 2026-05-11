package org.rapla.client.swing.internal.adminpanels;

import org.rapla.plugin.adminpanels.Field;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** JSON_EDITOR — universal escape hatch for arbitrary structured config
 *  (lists, nested objects, anything the server can't reduce to one of the
 *  other field types). Wire shape: any JSON-serializable Object; presented
 *  as pretty-printed JSON in a multi-line text area. Server gets back the
 *  re-parsed value on save (or null if the buffer is blank). */
final class JsonEditorFieldRenderer implements FieldRenderer
{
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private final JTextArea area = new JTextArea(12, 60);
    private final JScrollPane scroll;

    JsonEditorFieldRenderer(Field field)
    {
        area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        area.setEditable(!field.readOnly());
        area.setTabSize(2);
        if (field.helpText() != null) area.setToolTipText(field.helpText());
        scroll = new JScrollPane(area);
    }

    @Override public JComponent getEditor() { return scroll; }

    @Override public void setValue(Object value)
    {
        if (value == null)
        {
            area.setText("");
        }
        else
        {
            try
            {
                area.setText(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value));
            }
            catch (JacksonException e)
            {
                area.setText(String.valueOf(value));
            }
        }
        area.setCaretPosition(0);
    }

    @Override public Object getValue()
    {
        String text = area.getText().trim();
        if (text.isEmpty()) return null;
        try
        {
            return MAPPER.readValue(text, Object.class);
        }
        catch (JacksonException e)
        {
            // Hand the raw string back; controller-level mapping will surface a
            // typed parse error to the user. Don't swallow silently.
            return text;
        }
    }
}
