package org.rapla.plugin.eventtimecalculator.server;

import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.adminpanels.Field;
import org.rapla.plugin.adminpanels.FieldType;
import org.rapla.plugin.adminpanels.PanelDefinition;
import org.rapla.plugin.adminpanels.PanelScope;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;
import org.rapla.server.adminpanels.AbstractPluginPreferencesPanel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** SYSTEM-scoped admin panel for the event-time calculator plugin
 *  (interval/break/unit/format + allow-user-prefs toggle). Replaces the
 *  legacy {@code EventTimeCalculatorAdminOption} Swing panel. */
@Service
public class EventTimeCalculatorPreferencesPanel extends AbstractPluginPreferencesPanel
{
    @Autowired
    public EventTimeCalculatorPreferencesPanel(RaplaFacade facade) { super(facade); }

    @Override public String getId() { return EventTimeCalculatorPlugin.PLUGIN_ID; }
    @Override public String title(Locale locale) { return "Event Time Calculator"; }

    @Override
    protected PanelDefinition buildDefinition(Preferences prefs, Locale locale)
    {
        RaplaConfiguration config = prefs.getEntry(EventTimeCalculatorPlugin.SYSTEM_CONFIG, new RaplaConfiguration());

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("intervalNumber", (long) config.getChild(EventTimeCalculatorPlugin.INTERVAL_NUMBER)
                .getValueAsInteger(EventTimeCalculatorPlugin.DEFAULT_intervalNumber));
        values.put("breakNumber", (long) config.getChild(EventTimeCalculatorPlugin.BREAK_NUMBER)
                .getValueAsInteger(EventTimeCalculatorPlugin.DEFAULT_breakNumber));
        values.put("timeUnit", (long) config.getChild(EventTimeCalculatorPlugin.TIME_UNIT)
                .getValueAsInteger(EventTimeCalculatorPlugin.DEFAULT_timeUnit));
        values.put("timeFormat", config.getChild(EventTimeCalculatorPlugin.TIME_FORMAT)
                .getValue(EventTimeCalculatorPlugin.DEFAULT_timeFormat));
        values.put("allowUserPrefs", config.getChild(EventTimeCalculatorPlugin.USER_PREFS)
                .getValueAsBoolean(EventTimeCalculatorPlugin.DEFAULT_userPrefs));

        return new PanelDefinition(getId(), PanelScope.SYSTEM, path(), title(locale),
                "Defaults for the calculator. User-prefs toggle controls whether each user can override.",
                List.of(
                        new Field("intervalNumber", "Time until break (minutes)", FieldType.INT, null, false, Map.of()),
                        new Field("breakNumber",    "Break duration (minutes)",   FieldType.INT, null, false, Map.of()),
                        new Field("timeUnit",       "Time unit (minutes)",        FieldType.INT, null, false, Map.of()),
                        new Field("timeFormat",     "Time format",                FieldType.TEXT, null, false, Map.of()),
                        new Field("allowUserPrefs", "Allow user prefs",           FieldType.BOOL, null, false, Map.of())),
                List.of(),
                values);
    }

    @Override
    protected void applyValues(Preferences preferences, Map<String, Object> values) throws RaplaException
    {
        int intervalNumber = toInt(values.get("intervalNumber"), EventTimeCalculatorPlugin.DEFAULT_intervalNumber);
        int breakNumber    = toInt(values.get("breakNumber"),    EventTimeCalculatorPlugin.DEFAULT_breakNumber);
        int timeUnit       = toInt(values.get("timeUnit"),       EventTimeCalculatorPlugin.DEFAULT_timeUnit);
        String timeFormat  = String.valueOf(values.getOrDefault("timeFormat", EventTimeCalculatorPlugin.DEFAULT_timeFormat));
        boolean allowUserPrefs = Boolean.TRUE.equals(values.get("allowUserPrefs"));

        boolean matchesDefault = intervalNumber == EventTimeCalculatorPlugin.DEFAULT_intervalNumber
                && breakNumber == EventTimeCalculatorPlugin.DEFAULT_breakNumber
                && timeUnit    == EventTimeCalculatorPlugin.DEFAULT_timeUnit
                && EventTimeCalculatorPlugin.DEFAULT_timeFormat.equals(timeFormat)
                && allowUserPrefs == EventTimeCalculatorPlugin.DEFAULT_userPrefs;

        RaplaConfiguration config = new RaplaConfiguration(EventTimeCalculatorPlugin.PLUGIN_ID);
        config.getMutableChild(EventTimeCalculatorPlugin.INTERVAL_NUMBER, true).setValue(intervalNumber);
        config.getMutableChild(EventTimeCalculatorPlugin.BREAK_NUMBER,    true).setValue(breakNumber);
        config.getMutableChild(EventTimeCalculatorPlugin.TIME_UNIT,       true).setValue(timeUnit);
        config.getMutableChild(EventTimeCalculatorPlugin.TIME_FORMAT,     true).setValue(timeFormat);
        config.getMutableChild(EventTimeCalculatorPlugin.USER_PREFS,      true).setValue(allowUserPrefs);

        putConfigOrRemove(preferences, EventTimeCalculatorPlugin.SYSTEM_CONFIG, config, matchesDefault);
    }

    private static int toInt(Object v, int fallback)
    {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return fallback;
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return fallback; }
    }
}
