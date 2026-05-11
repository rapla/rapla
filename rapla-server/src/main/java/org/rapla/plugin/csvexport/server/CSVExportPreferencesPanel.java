package org.rapla.plugin.csvexport.server;

import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.adminpanels.Field;
import org.rapla.plugin.adminpanels.FieldType;
import org.rapla.plugin.adminpanels.PanelDefinition;
import org.rapla.plugin.adminpanels.PanelScope;
import org.rapla.plugin.csvexport.CSVExportPlugin;
import org.rapla.server.adminpanels.AbstractPluginPreferencesPanel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Server-driven replacement for the legacy {@code CSVExportPluginOption}. */
@Service
public class CSVExportPreferencesPanel extends AbstractPluginPreferencesPanel
{
    @Autowired
    public CSVExportPreferencesPanel(RaplaFacade facade) { super(facade); }

    @Override public String getId() { return CSVExportPlugin.PLUGIN_ID; }
    @Override public String title(Locale locale) { return "CSV Export Menu"; }

    @Override
    protected PanelDefinition buildDefinition(Preferences prefs, Locale locale)
    {
        boolean enabled = prefs.getEntryAsBoolean(CSVExportPlugin.ENABLED, false);
        return new PanelDefinition(getId(), PanelScope.SYSTEM, path(), title(locale),
                "Toggle the CSV export menu entry.",
                List.of(new Field("enabled", "Enabled", FieldType.BOOL, null, false, Map.of())),
                List.of(),
                Map.of("enabled", enabled));
    }

    @Override
    protected void applyValues(Preferences preferences, Map<String, Object> values) throws RaplaException
    {
        preferences.putEntry(CSVExportPlugin.ENABLED, Boolean.TRUE.equals(values.get("enabled")));
    }
}
