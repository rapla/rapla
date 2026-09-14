package org.rapla.rest.dto;

/**
 * Wire-format DTO for /plugins endpoint. Mirrors {@code PluginsController.PluginInfo}.
 *
 * @param id              short URL-safe id, e.g. "csvexport"
 * @param displayName     human-readable, English only (SPA does i18n)
 * @param enabled         resolved enabled state — from boolean ENABLED key or
 *                        the "enabled" attribute of the plugin's config blob
 * @param adminOnlyConfig hint: the plugin has additional config beyond the
 *                        enabled flag, with secrets that only admins should
 *                        edit (mail / jndi / exchange / archiver). Toggle still
 *                        works through {@code /plugins/{id}/enabled}; the rest
 *                        of the config has dedicated endpoints.
 */
public record PluginInfo(
        String id,
        String displayName,
        boolean enabled,
        boolean adminOnlyConfig
)
{
}
