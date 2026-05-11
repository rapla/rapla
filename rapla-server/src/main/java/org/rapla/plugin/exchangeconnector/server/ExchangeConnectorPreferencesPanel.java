package org.rapla.plugin.exchangeconnector.server;

import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.adminpanels.Field;
import org.rapla.plugin.adminpanels.FieldType;
import org.rapla.plugin.adminpanels.PanelDefinition;
import org.rapla.plugin.adminpanels.PanelScope;
import org.rapla.plugin.adminpanels.ActionResult;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorPlugin;
import org.rapla.server.adminpanels.PreferencesPanel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Server-driven replacement for the legacy {@code ExchangeConnectorAdminOptions}
 *  Swing panel.
 *
 *  <p>Persists two preference entries:
 *  <ul>
 *    <li>{@link ExchangeConnectorConfig#EXCHANGESERVER_CONFIG} —
 *        server-side bits (FQDN, category, sync-past window, timezone).</li>
 *    <li>{@link ExchangeConnectorConfig#EXCHANGE_CLIENT_CONFIG} —
 *        client-visible flags ({@code enabled_by_admin}).</li>
 *  </ul>
 *  Mirrors what the legacy panel wrote so {@code SynchronisationManager}'s
 *  config reader continues to work without code change. */
@Service
public class ExchangeConnectorPreferencesPanel implements PreferencesPanel
{
    private final RaplaFacade facade;

    @Autowired
    public ExchangeConnectorPreferencesPanel(RaplaFacade facade) { this.facade = facade; }

    @Override public String getId() { return ExchangeConnectorPlugin.PLUGIN_ID; }
    @Override public PanelScope scope() { return PanelScope.SYSTEM; }
    @Override public List<String> path() { return List.of("Plugins"); }
    @Override public String title(Locale locale) { return "Exchange Connector Plugin"; }

    @Override
    public PanelDefinition getDefinition(Locale locale, User user) throws RaplaException
    {
        Preferences prefs = facade.getSystemPreferences();
        RaplaConfiguration serverConfig = prefs.getEntry(ExchangeConnectorConfig.EXCHANGESERVER_CONFIG, new RaplaConfiguration());
        RaplaConfiguration clientConfig = prefs.getEntry(ExchangeConnectorConfig.EXCHANGE_CLIENT_CONFIG, new RaplaConfiguration());

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", clientConfig.getChild(ExchangeConnectorConfig.ENABLED_BY_ADMIN_STRING)
                .getValueAsBoolean(ExchangeConnectorConfig.DEFAULT_ENABLED_BY_ADMIN));
        values.put("fqdn", serverConfig.getChild(ExchangeConnectorConfig.EXCHANGE_WS_FQDN.getId())
                .getValue(ExchangeConnectorConfig.DEFAULT_EXCHANGE_WS_FQDN));
        values.put("appointmentCategory", serverConfig.getChild(ExchangeConnectorConfig.EXCHANGE_APPOINTMENT_CATEGORY.getId())
                .getValue(ExchangeConnectorConfig.DEFAULT_EXCHANGE_APPOINTMENT_CATEGORY));
        values.put("syncPeriodPast", (long) serverConfig.getChild(ExchangeConnectorConfig.SYNCING_PERIOD_PAST.getId())
                .getValueAsInteger(ExchangeConnectorConfig.DEFAULT_SYNCING_PERIOD_PAST));
        values.put("timezone", serverConfig.getChild(ExchangeConnectorConfig.EXCHANGE_TIMEZONE.getId())
                .getValue(ExchangeConnectorConfig.DEFAULT_EXCHANGE_TIMEZONE));

        // Timezone options — same list the legacy ConfigService.getTimezones()
        // shipped over the wire. Sourced from the JVM's available IDs so we
        // never need a separate server round-trip.
        List<Map<String, Object>> tzOptions = new ArrayList<>();
        for (String id : TimeZone.getAvailableIDs())
        {
            tzOptions.add(Map.of("value", id, "label", id));
        }

        return new PanelDefinition(getId(), scope(), path(), title(locale),
                "MS Exchange sync. Enable flag mirrors the per-deployment kill "
                        + "switch in application.yml (rapla.exchange.enabled).",
                List.of(
                        new Field("enabled", "Enabled", FieldType.BOOL, null, false, Map.of()),
                        new Field("fqdn", "Exchange Webservice FQDN", FieldType.TEXT, null, false, Map.of()),
                        new Field("appointmentCategory", "Default category", FieldType.TEXT, null, false, Map.of()),
                        new Field("syncPeriodPast", "Sync past (days)", FieldType.INT, null, false, Map.of()),
                        new Field("timezone", "Timezone", FieldType.SELECT, null, false, Map.of("options", tzOptions))),
                List.of(),
                values);
    }

    @Override
    public PanelDefinition save(User user, Map<String, Object> wireValues) throws RaplaException
    {
        Preferences editable = facade.edit(facade.getSystemPreferences());

        RaplaConfiguration serverConfig = new RaplaConfiguration("config");
        serverConfig.getMutableChild(ExchangeConnectorConfig.EXCHANGE_WS_FQDN.getId(), true)
                .setValue(String.valueOf(wireValues.getOrDefault("fqdn", ExchangeConnectorConfig.DEFAULT_EXCHANGE_WS_FQDN)));
        serverConfig.getMutableChild(ExchangeConnectorConfig.EXCHANGE_APPOINTMENT_CATEGORY.getId(), true)
                .setValue(String.valueOf(wireValues.getOrDefault("appointmentCategory", ExchangeConnectorConfig.DEFAULT_EXCHANGE_APPOINTMENT_CATEGORY)));
        serverConfig.getMutableChild(ExchangeConnectorConfig.SYNCING_PERIOD_PAST.getId(), true)
                .setValue(toInt(wireValues.get("syncPeriodPast"), ExchangeConnectorConfig.DEFAULT_SYNCING_PERIOD_PAST));
        serverConfig.getMutableChild(ExchangeConnectorConfig.EXCHANGE_TIMEZONE.getId(), true)
                .setValue(String.valueOf(wireValues.getOrDefault("timezone", ExchangeConnectorConfig.DEFAULT_EXCHANGE_TIMEZONE)));
        editable.putEntry(ExchangeConnectorConfig.EXCHANGESERVER_CONFIG, serverConfig);

        RaplaConfiguration clientConfig = new RaplaConfiguration("clientConfig");
        clientConfig.getMutableChild(ExchangeConnectorConfig.ENABLED_BY_ADMIN_STRING, true)
                .setValue(Boolean.TRUE.equals(wireValues.get("enabled")));
        editable.putEntry(ExchangeConnectorConfig.EXCHANGE_CLIENT_CONFIG, clientConfig);

        facade.storeAndRemove(new Entity[]{editable}, Entity.ENTITY_ARRAY, user);
        return getDefinition(Locale.getDefault(), user);
    }

    @Override
    public ActionResult invokeAction(User user, String actionId, Map<String, Object> currentValues)
    {
        return ActionResult.fail("Action not supported: " + actionId);
    }

    private static int toInt(Object v, int fallback)
    {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return fallback;
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return fallback; }
    }
}
