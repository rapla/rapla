package org.rapla.plugin.appointmentnote.server;

import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.adminpanels.Field;
import org.rapla.plugin.adminpanels.FieldType;
import org.rapla.plugin.adminpanels.PanelDefinition;
import org.rapla.plugin.adminpanels.PanelScope;
import org.rapla.plugin.appointmentnote.AppointmentNotePlugin;
import org.rapla.server.adminpanels.AbstractPluginPreferencesPanel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Server-driven replacement for the legacy {@code AppointmentNotePluginOption}. */
@Service
public class AppointmentNotePreferencesPanel extends AbstractPluginPreferencesPanel
{
    @Autowired
    public AppointmentNotePreferencesPanel(RaplaFacade facade) { super(facade); }

    @Override public String getId() { return AppointmentNotePlugin.PLUGIN_ID; }
    @Override public String title(Locale locale) { return "Appointment Comment"; }

    @Override
    protected PanelDefinition buildDefinition(Preferences prefs, Locale locale)
    {
        boolean enabled = prefs.getEntryAsBoolean(AppointmentNotePlugin.ENABLED, false);
        return new PanelDefinition(getId(), PanelScope.SYSTEM, path(), title(locale),
                "Toggle per-appointment comment field.",
                List.of(new Field("enabled", "Enabled", FieldType.BOOL, null, false, Map.of())),
                List.of(),
                Map.of("enabled", enabled));
    }

    @Override
    protected void applyValues(Preferences preferences, Map<String, Object> values) throws RaplaException
    {
        preferences.putEntry(AppointmentNotePlugin.ENABLED, Boolean.TRUE.equals(values.get("enabled")));
    }
}
