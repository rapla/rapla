package org.rapla.client.swing.internal.adminpanels;

import org.rapla.plugin.adminpanels.Field;

import javax.swing.JComponent;

/** Renders one server-defined {@link Field} as a Swing widget pair (label + editor)
 *  and converts between the server-side wire value (JSON-decoded as Object — typically
 *  Boolean / String / Number / Map / List) and what the widget displays.
 *
 *  <p>One implementation per {@link org.rapla.plugin.adminpanels.FieldType};
 *  see {@link FieldRendererFactory} for the mapping. */
interface FieldRenderer
{
    /** The interactive editor widget (text field, combo box, button, etc.).
     *  Doesn't include the label; the dialog lays out label + widget itself. */
    JComponent getEditor();

    /** Push a server-supplied value into the widget. Called after {@link #getEditor()}
     *  to seed the initial state. {@code null} means "no value" — render the default. */
    void setValue(Object value);

    /** Read the current widget state for save. The wire layer will JSON-encode
     *  whatever this returns. May return {@code null} if the user cleared it. */
    Object getValue();

    /** Whether the editor has uncommitted changes vs. the last {@link #setValue(Object)}.
     *  Drives the "modified" flag in the dialog. */
    default boolean isDirty() { return false; }
}
