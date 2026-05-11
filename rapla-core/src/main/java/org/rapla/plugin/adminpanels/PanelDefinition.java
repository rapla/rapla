package org.rapla.plugin.adminpanels;

import java.util.List;
import java.util.Map;

/** Full content of a panel, returned by {@code PreferencesAdminService.getPanel(id)}
 *  and as the response to a save call (so the client can rebind to the
 *  refreshed values).
 *
 *  @param id          matches the {@link PanelSummary} id
 *  @param scope       SYSTEM or PER_USER (also on the summary; repeated here so a
 *                     client that fetched a definition directly has full context)
 *  @param path        breadcrumb path; same as on the summary
 *  @param title       leaf-node label, locale-aware
 *  @param description optional help text shown above the field list
 *  @param fields      ordered list of fields to render
 *  @param actions     ordered list of action buttons (may be empty)
 *  @param values      current values keyed by {@link Field#key}; type per field
 *                     follows {@link FieldType}'s wire-format rules
 */
public record PanelDefinition(
        String id,
        PanelScope scope,
        List<String> path,
        String title,
        String description,
        List<Field> fields,
        List<ActionButton> actions,
        Map<String, Object> values)
{
    public PanelDefinition
    {
        if (fields == null) fields = List.of();
        if (actions == null) actions = List.of();
        if (values == null) values = Map.of();
    }
}
