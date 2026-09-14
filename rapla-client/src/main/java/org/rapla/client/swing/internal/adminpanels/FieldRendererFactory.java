package org.rapla.client.swing.internal.adminpanels;

import org.rapla.plugin.adminpanels.Field;

/** Builds the per-{@link org.rapla.plugin.adminpanels.FieldType} {@link FieldRenderer}.
 *
 *  <p>Switch over the enum so adding a new type is a one-line code change here +
 *  a new renderer class — no Spring lookup, no DI rewiring per type. */
final class FieldRendererFactory
{
    private FieldRendererFactory() {}

    static FieldRenderer create(Field field)
    {
        return switch (field.type())
        {
            case BOOL          -> new BoolFieldRenderer(field);
            case TEXT          -> new TextFieldRenderer(field);
            case LONG_TEXT     -> new LongTextFieldRenderer(field);
            case PASSWORD      -> new PasswordFieldRenderer(field);
            case INT           -> new IntFieldRenderer(field);
            case SELECT        -> new SelectFieldRenderer(field);
            case RADIO_GROUP   -> new RadioGroupFieldRenderer(field);
            case DISPLAY_ONLY  -> new DisplayOnlyFieldRenderer(field);
            case ACTION_BUTTON -> new DisplayOnlyFieldRenderer(field);   // ACTION_BUTTON is rendered by the dialog row, not as a regular field
            case JSON_EDITOR   -> new JsonEditorFieldRenderer(field);
        };
    }
}
