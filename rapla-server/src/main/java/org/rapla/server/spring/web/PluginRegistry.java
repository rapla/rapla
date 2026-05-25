package org.rapla.server.spring.web;

import java.util.List;

/**
 * Curated registry of Rapla plugins that can be enabled / disabled via
 * {@link PluginsController}. Each entry knows how its "enabled" state is
 * persisted in system preferences — either as a standalone boolean key
 * ({@code boolEnabledKey}) or as the {@code enabled} attribute of a
 * {@link org.rapla.entities.configuration.RaplaConfiguration} blob
 * ({@code configKey}).
 *
 * <p>Some plugins carry additional configuration beyond the enabled flag
 * (e.g. SMTP credentials for mail, LDAP for jndi). Those have
 * {@code adminOnlyConfig = true} as a hint for the SPA — the toggle still
 * works through this controller, but the rest of the config has its own
 * dedicated endpoint (/mail/config, /jndi, /exchange/config, /ical/config).
 *
 * <p>Adding a new plugin: append one row. No reflection, no autodiscovery —
 * keeping the list curated so renaming a plugin can't silently change the
 * REST contract.
 */
final class PluginRegistry
{
    private PluginRegistry() {}

    /** Exactly one of {@code boolEnabledKey}, {@code configKey}, {@code envPropertyKey}
     *  is set (the other two are null). Indicates where the enabled state is read from:
     *  DB preferences (boolean entry / RaplaConfiguration attribute) or the Spring
     *  Environment (operator-managed via application.yml). Env-property plugins are
     *  read-only via {@code PluginsController} — setEnabled() rejects them.
     *  {@code id} is the short form used in URLs (e.g. "csvexport").
     *  {@code displayName} is human-readable, English only — i18n is the SPA's job. */
    record PluginEntry(
            String id,
            String displayName,
            String boolEnabledKey,           // DB pref key for stand-alone Boolean flag — or null
            String configKey,                // DB pref key for RaplaConfiguration blob — or null
            String envPropertyKey,           // Spring Environment property name (YAML-managed) — or null
            boolean defaultEnabled,
            boolean adminOnlyConfig          // hint: plugin has secrets beyond the enabled flag
    ) {}

    /** All plugins toggleable via /plugins/{id}/enabled. */
    static final List<PluginEntry> ALL = List.of(
            // --- DB-pref: stand-alone Boolean ENABLED keys ---
            new PluginEntry("csvexport",          "CSV Export",          "org.rapla.plugin.csvexport.enabled",       null, null, false, false),
            new PluginEntry("appointmentnote",    "Appointment Notes",   "org.rapla.plugin.appointmentnote.enabled", null, null, false, false),
            new PluginEntry("planningstatus",     "Planning Status",     "org.rapla.plugin.planningstatus.enabled",  null, null, false, false),
            new PluginEntry("eventimport",        "Event Import",        "org.rapla.plugin.eventimport.enabled",     null, null, false, false),
            new PluginEntry("templatewizard",     "Template Wizard",     "org.rapla.plugin.templatewizard.enabled",  null, null, true,  false),
            new PluginEntry("defaultwizard",      "Default Wizard",      "org.rapla.plugin.defaultwizard.enabled",   null, null, true,  false),

            // --- DB-pref: enabled-attribute-inside-RaplaConfiguration keys ---
            new PluginEntry("export2ical",        "iCal Export",         null, "org.rapla.plugin.export2ical.server.Config",  null, true,  false),
            new PluginEntry("timeslot",           "Timeslot Views",      null, "org.rapla.plugin.timeslot.config",             null, true,  false),
            new PluginEntry("monthview",          "Month View",          null, "org.rapla.plugin.monthview.config",            null, true,  false),
            new PluginEntry("compactweekview",    "Compact Week View",   null, "org.rapla.plugin.compactweekview.config",      null, true,  false),
            new PluginEntry("dayresource",        "Day Resource View",   null, "org.rapla.plugin.dayresource.config",          null, true,  false),
            new PluginEntry("tableview",          "Table View",          null, "org.rapla.plugin.tableview.config",            null, true,  false),
            new PluginEntry("eventtimecalculator","Event Time Calc",     null, "org.rapla.plugin.eventtimecalculator",         null, true,  false),

            // --- DB-pref: admin-only config (toggle here, edit via dedicated endpoints) ---
            new PluginEntry("mail",               "Mail (SMTP)",         null, "org.rapla.plugin.mail.server.Config",          null, false, true),
            new PluginEntry("jndi",               "JNDI / LDAP",         null, "org.rapla.plugin.jndi.server.config",          null, false, true),
            new PluginEntry("exchangeconnector",  "Exchange Connector",  null, "org.rapla.plugin.exchangeconnector.server.Config", null, false, true),
            new PluginEntry("archiver",           "Archiver",            null, "org.rapla.plugin.archiver",                    null, false, true),

            // --- Spring Environment (YAML-managed by operator, NOT runtime-toggleable). ---
            // The enabled state is the single source of truth for both server-side bean
            // wiring (@ConditionalOnProperty) AND client-side menu visibility. Clients
            // discover via GET /api/plugins/{id} — no client-side flag duplicated.
            new PluginEntry("externaleventimport","External Event Import", null, null, "rapla.externalevents.enabled", false, false)
    );

    static PluginEntry byId(String id)
    {
        return ALL.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }
}
