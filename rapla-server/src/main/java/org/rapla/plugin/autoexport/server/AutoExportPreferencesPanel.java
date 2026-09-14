package org.rapla.plugin.autoexport.server;

import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.adminpanels.Field;
import org.rapla.plugin.adminpanels.FieldType;
import org.rapla.plugin.adminpanels.PanelDefinition;
import org.rapla.plugin.adminpanels.PanelScope;
import org.rapla.plugin.autoexport.AutoExportPlugin;
import org.rapla.server.adminpanels.AbstractPluginPreferencesPanel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Server-driven replacement for the legacy {@code AutoExportPluginOption}.
 *  Two booleans: list-of-exported-calendars in HTML menu, tooltips in HTML
 *  exports. */
@Service
public class AutoExportPreferencesPanel extends AbstractPluginPreferencesPanel
{
    @Autowired
    public AutoExportPreferencesPanel(RaplaFacade facade) { super(facade); }

    @Override public String getId() { return AutoExportPlugin.PLUGIN_ID; }
    @Override public String title(Locale locale) { return "HTML Export Plugin"; }

    @Override
    protected PanelDefinition buildDefinition(Preferences prefs, Locale locale)
    {
        boolean showList = prefs.getEntryAsBoolean(AutoExportPlugin.SHOW_CALENDAR_LIST_IN_HTML_MENU, false);
        boolean showTooltips = prefs.getEntryAsBoolean(AutoExportPlugin.SHOW_TOOLTIP_IN_EXPORT_CONFIG_ENTRY, true);
        return new PanelDefinition(getId(), PanelScope.SYSTEM, path(), title(locale),
                "HTML export presentation options.",
                List.of(
                        new Field("showList", "Show list of exported calendars in HTML menu", FieldType.BOOL, null, false, Map.of()),
                        new Field("showTooltips", "Show tooltips in HTML exports", FieldType.BOOL, null, false, Map.of())),
                List.of(),
                Map.of("showList", showList, "showTooltips", showTooltips));
    }

    @Override
    protected void applyValues(Preferences preferences, Map<String, Object> values) throws RaplaException
    {
        // showList default: false (legacy panel showed an unchecked checkbox on first open).
        // showTooltips default: true (matches legacy read default in AutoExportPluginOption).
        putOrRemove(preferences, AutoExportPlugin.SHOW_CALENDAR_LIST_IN_HTML_MENU,
                Boolean.TRUE.equals(values.get("showList")), false);
        putOrRemove(preferences, AutoExportPlugin.SHOW_TOOLTIP_IN_EXPORT_CONFIG_ENTRY,
                Boolean.TRUE.equals(values.get("showTooltips")), true);
    }
}
