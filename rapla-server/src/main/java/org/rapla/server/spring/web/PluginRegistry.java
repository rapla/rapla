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

    /** Either {@code boolEnabledKey} or {@code configKey} is set, never both null.
     *  {@code id} is the short form used in URLs (e.g. "csvexport").
     *  {@code displayName} is human-readable, English only — i18n is the SPA's job. */
    record PluginEntry(
            String id,
            String displayName,
            String boolEnabledKey,           // pref key for stand-alone Boolean flag — or null
            String configKey,                // pref key for RaplaConfiguration blob — or null
            boolean defaultEnabled,
            boolean adminOnlyConfig          // hint: plugin has secrets beyond the enabled flag
    ) {}

    /** All plugins toggleable via /plugins/{id}/enabled. */
    static final List<PluginEntry> ALL = List.of(
            // --- Stand-alone Boolean ENABLED keys ---
            new PluginEntry("csvexport",          "CSV Export",          "org.rapla.plugin.csvexport.enabled",       null, false, false),
            new PluginEntry("appointmentnote",    "Appointment Notes",   "org.rapla.plugin.appointmentnote.enabled", null, false, false),
            new PluginEntry("planningstatus",     "Planning Status",     "org.rapla.plugin.planningstatus.enabled",  null, false, false),
            new PluginEntry("eventimport",        "Event Import",        "org.rapla.plugin.eventimport.enabled",     null, false, false),
            new PluginEntry("templatewizard",     "Template Wizard",     "org.rapla.plugin.templatewizard.enabled",  null, true,  false),
            new PluginEntry("defaultwizard",      "Default Wizard",      "org.rapla.plugin.defaultwizard.enabled",   null, true,  false),

            // --- enabled-attribute-inside-RaplaConfiguration keys ---
            new PluginEntry("export2ical",        "iCal Export",         null, "org.rapla.plugin.export2ical.server.Config",  true,  false),
            new PluginEntry("timeslot",           "Timeslot Views",      null, "org.rapla.plugin.timeslot.config",             true,  false),
            new PluginEntry("monthview",          "Month View",          null, "org.rapla.plugin.monthview.config",            true,  false),
            new PluginEntry("compactweekview",    "Compact Week View",   null, "org.rapla.plugin.compactweekview.config",      true,  false),
            new PluginEntry("dayresource",        "Day Resource View",   null, "org.rapla.plugin.dayresource.config",          true,  false),
            new PluginEntry("tableview",          "Table View",          null, "org.rapla.plugin.tableview.config",            true,  false),
            new PluginEntry("eventtimecalculator","Event Time Calc",     null, "org.rapla.plugin.eventtimecalculator",         true,  false),

            // --- admin-only config (toggle here, edit via dedicated endpoints) ---
            new PluginEntry("mail",               "Mail (SMTP)",         null, "org.rapla.plugin.mail.server.Config",          false, true),
            new PluginEntry("jndi",               "JNDI / LDAP",         null, "org.rapla.plugin.jndi.server.config",          false, true),
            new PluginEntry("exchangeconnector",  "Exchange Connector",  null, "org.rapla.plugin.exchangeconnector.server.Config", false, true),
            new PluginEntry("archiver",           "Archiver",            null, "org.rapla.plugin.archiver",                    false, true)
    );

    static PluginEntry byId(String id)
    {
        return ALL.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }
}
