package org.rapla.plugin.export2ical.server;

import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.adminpanels.Field;
import org.rapla.plugin.adminpanels.FieldType;
import org.rapla.plugin.adminpanels.PanelDefinition;
import org.rapla.plugin.adminpanels.PanelScope;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.server.adminpanels.AbstractPluginPreferencesPanel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Server-driven replacement for the legacy {@code Export2iCalAdminOption}
 *  Swing panel. Persists to {@link Export2iCalPlugin#ICAL_CONFIG} preserving
 *  the legacy child-element layout so the existing iCal export readers
 *  (server-side) continue to work unchanged. */
@Service
public class Export2iCalPreferencesPanel extends AbstractPluginPreferencesPanel
{
    @Autowired
    public Export2iCalPreferencesPanel(RaplaFacade facade) { super(facade); }

    @Override public String getId() { return "org.rapla.plugin.export2ical"; }
    @Override public String title(Locale locale) { return "Export2iCal"; }

    @Override
    protected PanelDefinition buildDefinition(Preferences prefs, Locale locale)
    {
        RaplaConfiguration config = prefs.getEntry(Export2iCalPlugin.ICAL_CONFIG, new RaplaConfiguration());

        int lastModified = config.getChild(Export2iCalPlugin.LAST_MODIFIED_INTERVALL)
                .getValueAsInteger(Export2iCalPlugin.DEFAULT_lastModifiedIntervall);
        boolean doNotDeliverNewCalendar = lastModified == -1;
        if (doNotDeliverNewCalendar) lastModified = Export2iCalPlugin.DEFAULT_lastModifiedIntervall;

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", config.getChild(Export2iCalPlugin.ENABLED_STRING).getValueAsBoolean(false));
        values.put("intervalMode", config.getChild(Export2iCalPlugin.GLOBAL_INTERVAL)
                .getValueAsBoolean(Export2iCalPlugin.DEFAULT_globalIntervall) ? "global" : "user");
        values.put("daysBefore", (long) config.getChild(Export2iCalPlugin.DAYS_BEFORE)
                .getValueAsInteger(Export2iCalPlugin.DEFAULT_daysBefore));
        values.put("daysAfter", (long) config.getChild(Export2iCalPlugin.DAYS_AFTER)
                .getValueAsInteger(Export2iCalPlugin.DEFAULT_daysAfter));
        values.put("doNotDeliverNewCalendar", doNotDeliverNewCalendar);
        values.put("lastModifiedIntervall", (long) lastModified);
        values.put("exportAttendees", config.getChild(Export2iCalPlugin.EXPORT_ATTENDEES)
                .getValueAsBoolean(Export2iCalPlugin.DEFAULT_exportAttendees));
        values.put("attendeeEmailAttribute", config.getChild(Export2iCalPlugin.EXPORT_ATTENDEES_EMAIL_ATTRIBUTE)
                .getValue(Export2iCalPlugin.DEFAULT_attendee_resource_attribute));
        values.put("attendeeParticipationStatus", config.getChild(Export2iCalPlugin.EXPORT_ATTENDEES_PARTICIPATION_STATUS)
                .getValue(Export2iCalPlugin.DEFAULT_attendee_participation_status));

        Map<String, Object> intervalOptions = Map.of("options", List.of(
                Map.of("value", "global", "label", "Global interval setting"),
                Map.of("value", "user",   "label", "User interval setting")));
        Map<String, Object> participationOptions = Map.of("options", List.of(
                Map.of("value", "ACCEPTED",  "label", "ACCEPTED"),
                Map.of("value", "TENTATIVE", "label", "TENTATIVE")));

        return new PanelDefinition(getId(), PanelScope.SYSTEM, path(), title(locale),
                "iCal export defaults. Per-user overrides happen separately (Edit > Options).",
                List.of(
                        new Field("enabled",            "Enabled",                 FieldType.BOOL,        null, false, Map.of()),
                        new Field("intervalMode",       "Export interval scope",   FieldType.RADIO_GROUP, null, false, intervalOptions),
                        new Field("daysBefore",         "Previous days",           FieldType.INT,         null, false, Map.of()),
                        new Field("daysAfter",          "Subsequent days",         FieldType.INT,         null, false, Map.of()),
                        new Field("doNotDeliverNewCalendar", "Do not deliver new calendar", FieldType.BOOL, null, false, Map.of()),
                        new Field("lastModifiedIntervall",   "Delivery interval (days)",    FieldType.INT,  null, false, Map.of()),
                        new Field("exportAttendees",        "Export VEVENT attendees", FieldType.BOOL, null, false, Map.of()),
                        new Field("attendeeEmailAttribute", "Email attribute key",     FieldType.TEXT, null, false, Map.of()),
                        new Field("attendeeParticipationStatus", "Participation status default", FieldType.SELECT, null, false, participationOptions)),
                List.of(),
                values);
    }

    @Override
    protected void applyValues(Preferences preferences, Map<String, Object> values) throws RaplaException
    {
        RaplaConfiguration config = new RaplaConfiguration("config");

        config.getMutableChild(Export2iCalPlugin.ENABLED_STRING, true)
                .setValue(Boolean.TRUE.equals(values.get("enabled")));
        config.getMutableChild(Export2iCalPlugin.GLOBAL_INTERVAL, true)
                .setValue("global".equals(values.getOrDefault("intervalMode", "global")));
        config.getMutableChild(Export2iCalPlugin.DAYS_BEFORE, true)
                .setValue(toInt(values.get("daysBefore"), Export2iCalPlugin.DEFAULT_daysBefore));
        config.getMutableChild(Export2iCalPlugin.DAYS_AFTER, true)
                .setValue(toInt(values.get("daysAfter"), Export2iCalPlugin.DEFAULT_daysAfter));

        boolean doNotDeliver = Boolean.TRUE.equals(values.get("doNotDeliverNewCalendar"));
        int interval = doNotDeliver ? -1 : toInt(values.get("lastModifiedIntervall"), Export2iCalPlugin.DEFAULT_lastModifiedIntervall);
        config.getMutableChild(Export2iCalPlugin.LAST_MODIFIED_INTERVALL, true).setValue(Integer.toString(interval));

        config.getMutableChild(Export2iCalPlugin.EXPORT_ATTENDEES, true)
                .setValue(Boolean.TRUE.equals(values.get("exportAttendees")));
        config.getMutableChild(Export2iCalPlugin.EXPORT_ATTENDEES_EMAIL_ATTRIBUTE, true)
                .setValue(String.valueOf(values.getOrDefault("attendeeEmailAttribute", Export2iCalPlugin.DEFAULT_attendee_resource_attribute)));
        config.getMutableChild(Export2iCalPlugin.EXPORT_ATTENDEES_PARTICIPATION_STATUS, true)
                .setValue(String.valueOf(values.getOrDefault("attendeeParticipationStatus", Export2iCalPlugin.DEFAULT_attendee_participation_status)));

        preferences.putEntry(Export2iCalPlugin.ICAL_CONFIG, config);
    }

    private static int toInt(Object v, int fallback)
    {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return fallback;
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return fallback; }
    }
}
