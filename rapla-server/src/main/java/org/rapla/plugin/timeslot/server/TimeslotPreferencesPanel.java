package org.rapla.plugin.timeslot.server;

import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.adminpanels.Field;
import org.rapla.plugin.adminpanels.FieldType;
import org.rapla.plugin.adminpanels.PanelDefinition;
import org.rapla.plugin.adminpanels.PanelScope;
import org.rapla.plugin.timeslot.TimeslotPlugin;
import org.rapla.server.adminpanels.AbstractPluginPreferencesPanel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Server-driven replacement for the legacy {@code TimeslotOption}.
 *
 *  <p>Wire shape: a single {@link FieldType#JSON_EDITOR} field {@code "slots"}
 *  carrying a JSON array of {@code {"name": "...", "minuteOfDay": 480}}
 *  objects. On the server side, this is round-tripped through the legacy
 *  {@link RaplaConfiguration} XML format (children named {@code "timeslot"}
 *  with attributes {@code name} and {@code time=HH:MM:SS}). Storing in the
 *  legacy shape means the existing {@code TimeslotProvider.parseConfig(...)}
 *  reader continues to work unchanged. */
@Service
public class TimeslotPreferencesPanel extends AbstractPluginPreferencesPanel
{
    @Autowired
    public TimeslotPreferencesPanel(RaplaFacade facade) { super(facade); }

    @Override public String getId() { return TimeslotPlugin.PLUGIN_ID; }
    @Override public String title(Locale locale) { return "Timeslots"; }

    @Override
    protected PanelDefinition buildDefinition(Preferences prefs, Locale locale)
    {
        RaplaConfiguration config = prefs.getEntry(TimeslotPlugin.CONFIG, null);
        Object slotsValue;
        if (config != null && config.getChildren("timeslot").length > 0)
        {
            slotsValue = configToJsonList(config);
        }
        else
        {
            // Sensible default when no timeslot entry exists yet: a 7-slot
            // schedule at 2-hour intervals from 06:00 to 18:00 — covers a
            // typical working day with 5 daytime blocks plus an early and a
            // late slot, which beats the legacy 24-hourly default for most
            // school/university use cases. Admin can edit + save to override.
            slotsValue = defaultSlots();
        }
        return new PanelDefinition(getId(), PanelScope.SYSTEM, path(), title(locale),
                "List of named timeslots, sorted by minute-of-day. Edit as JSON: array of {\"name\": \"...\", \"minuteOfDay\": 480}.",
                List.of(new Field("slots", "Timeslots (JSON)", FieldType.JSON_EDITOR, null, false, Map.of())),
                List.of(),
                Map.of("slots", slotsValue));
    }

    private static List<Map<String, Object>> defaultSlots()
    {
        int[] starts = {6 * 60, 8 * 60, 10 * 60, 12 * 60, 14 * 60, 16 * 60, 18 * 60};
        List<Map<String, Object>> result = new ArrayList<>(starts.length);
        for (int mod : starts)
        {
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("name", String.format("%02d:%02d", mod / 60, mod % 60));
            slot.put("minuteOfDay", mod);
            result.add(slot);
        }
        return result;
    }

    @Override
    protected void applyValues(Preferences preferences, Map<String, Object> values) throws RaplaException
    {
        Object slotsValue = values.get("slots");
        if (!(slotsValue instanceof List<?> list))
        {
            throw new RaplaException("\"slots\" must be a JSON array");
        }
        // If the submitted list matches the current default, REMOVE the entry
        // instead of persisting it. That way an admin who saves without
        // changing anything doesn't pin this deployment to the current default —
        // when {@link TimeslotProvider#getDefaultTimeslots(...)} changes later,
        // deployments without explicit overrides pick the new default up.
        if (matchesDefault(list))
        {
            preferences.removeEntry(TimeslotPlugin.CONFIG.getId());
            return;
        }
        RaplaConfiguration config = jsonListToConfig(list);
        preferences.putEntry(TimeslotPlugin.CONFIG, config);
    }

    /** True iff {@code list} (the wire form a JSON_EDITOR submit) matches the
     *  default produced by {@link #defaultSlots()}. Compared by (name,
     *  minuteOfDay) pairs in order — same shape both producers emit. */
    private static boolean matchesDefault(List<?> list)
    {
        List<Map<String, Object>> defaults = defaultSlots();
        if (list.size() != defaults.size()) return false;
        for (int i = 0; i < defaults.size(); i++)
        {
            Object entry = list.get(i);
            if (!(entry instanceof Map<?, ?> m)) return false;
            Map<String, Object> expected = defaults.get(i);
            if (!String.valueOf(expected.get("name")).equals(String.valueOf(m.get("name")))) return false;
            Object mod = m.get("minuteOfDay");
            int actualMinute = (mod instanceof Number n) ? n.intValue() : -1;
            int expectedMinute = (int) expected.get("minuteOfDay");
            if (actualMinute != expectedMinute) return false;
        }
        return true;
    }

    private static List<Map<String, Object>> configToJsonList(RaplaConfiguration config)
    {
        List<Map<String, Object>> result = new ArrayList<>();
        for (org.rapla.framework.Configuration child : config.getChildren("timeslot"))
        {
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("name", child.getAttribute("name", ""));
            slot.put("minuteOfDay", parseMinuteOfDay(child.getAttribute("time", "0:00:00")));
            result.add(slot);
        }
        return result;
    }

    private static RaplaConfiguration jsonListToConfig(List<?> list) throws RaplaException
    {
        RaplaConfiguration root = new RaplaConfiguration("config");
        for (Object entry : list)
        {
            if (!(entry instanceof Map<?, ?> map))
            {
                throw new RaplaException("Each slot must be a JSON object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> slotMap = (Map<String, Object>) map;
            DefaultConfiguration child = new DefaultConfiguration("timeslot");
            child.setAttribute("name", String.valueOf(slotMap.getOrDefault("name", "")));
            Object modValue = slotMap.getOrDefault("minuteOfDay", 0);
            int mod = (modValue instanceof Number n) ? n.intValue() : 0;
            child.setAttribute("time", String.format("%02d:%02d:00", mod / 60, mod % 60));
            root.addChild(child);
        }
        return root;
    }

    private static int parseMinuteOfDay(String hms)
    {
        // "HH:MM:SS" — drop seconds
        String[] parts = hms.split(":");
        if (parts.length < 2) return 0;
        try
        {
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }
}
