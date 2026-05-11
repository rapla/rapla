package org.rapla.plugin.planningstatus.server;

import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.adminpanels.Field;
import org.rapla.plugin.adminpanels.FieldType;
import org.rapla.plugin.adminpanels.PanelDefinition;
import org.rapla.plugin.adminpanels.PanelScope;
import org.rapla.plugin.planningstatus.PlanningStatusPlugin;
import org.rapla.server.adminpanels.AbstractPluginPreferencesPanel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Server-driven replacement for the legacy {@code PlanningStatusPluginOption}.
 *  Single boolean: plugin enabled / disabled. */
@Service
public class PlanningStatusPreferencesPanel extends AbstractPluginPreferencesPanel
{
    @Autowired
    public PlanningStatusPreferencesPanel(RaplaFacade facade) { super(facade); }

    @Override public String getId() { return PlanningStatusPlugin.PLUGIN_ID; }
    @Override public String title(Locale locale) { return "Planning Status"; }

    @Override
    protected PanelDefinition buildDefinition(Preferences prefs, Locale locale)
    {
        boolean enabled = prefs.getEntryAsBoolean(PlanningStatusPlugin.ENABLED, PlanningStatusPlugin.ENABLE_BY_DEFAULT);
        return new PanelDefinition(getId(), PanelScope.SYSTEM, path(), title(locale),
                "Toggle the planning-status plugin.",
                List.of(new Field("enabled", "Enabled", FieldType.BOOL, null, false, Map.of())),
                List.of(),
                Map.of("enabled", enabled));
    }

    @Override
    protected void applyValues(Preferences preferences, Map<String, Object> values) throws RaplaException
    {
        preferences.putEntry(PlanningStatusPlugin.ENABLED, Boolean.TRUE.equals(values.get("enabled")));
    }
}
