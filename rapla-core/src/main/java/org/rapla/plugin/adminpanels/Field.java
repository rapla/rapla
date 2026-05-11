package org.rapla.plugin.adminpanels;

import java.util.Map;

/** One editable / display element within a panel.
 *
 *  @param key        wire key — used in the {@code values} map of
 *                    {@link PanelDefinition} and in the save payload
 *  @param label      user-visible label, locale-aware
 *  @param type       drives the renderer's widget choice
 *  @param helpText   optional, shown next to / below the field
 *  @param readOnly   true for display-only fields; the renderer disables
 *                    editing. {@link FieldType#DISPLAY_ONLY} forces this
 *                    regardless of the flag.
 *  @param typeConfig type-specific config (option list for SELECT, min/max
 *                    for INT, schema hint for JSON_EDITOR, etc.). See
 *                    {@link FieldType} javadoc for per-type expectations.
 */
public record Field(
        String key,
        String label,
        FieldType type,
        String helpText,
        boolean readOnly,
        Map<String, Object> typeConfig)
{
    public Field
    {
        if (typeConfig == null) typeConfig = Map.of();
    }
}
