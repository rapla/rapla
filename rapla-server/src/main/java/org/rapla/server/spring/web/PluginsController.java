package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.rest.dto.PluginInfo;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic plugin enable / disable controller. Replaces the bulk
 * /storage/resources reads that Swing factories did to decide whether
 * to register a feature. The SPA equivalent is one HTTP call to /plugins
 * at boot — returns each plugin's id, name, and current enabled state.
 *
 * <p>Toggle is admin-only and goes through {@link RaplaFacade#edit}/
 * {@link RaplaFacade#store}, so it lands in the normal storage path
 * with permission and version checks. No bypass.
 *
 * <p>Plugins with additional config (mail / jndi / exchange / ical)
 * have the rest of their config behind dedicated endpoints — this
 * controller is only for the enabled-flag dimension.
 */
@RestController
@ConditionalOnBean(RemoteSession.class)
@RequestMapping(value = "/api/plugins", produces = "application/json")
public class PluginsController
{
    public record EnabledRequest(boolean enabled) {}

    private final RaplaFacade facade;
    private final RemoteSession session;

    public PluginsController(RaplaFacade facade, RemoteSession session)
    {
        this.facade = facade;
        this.session = session;
    }

    /** List all known plugins with their current enabled state. Any authenticated user. */
    @GetMapping
    public List<PluginInfo> list(HttpServletRequest request) throws RaplaException
    {
        session.checkAndGetUser(request);
        Preferences prefs = facade.getSystemPreferences();
        return PluginRegistry.ALL.stream()
                .map(entry -> new PluginInfo(
                        entry.id(),
                        entry.displayName(),
                        isEnabled(prefs, entry),
                        entry.adminOnlyConfig()))
                .collect(Collectors.toList());
    }

    /** Read one plugin's enabled state. Any authenticated user. */
    @GetMapping("/{id}")
    public PluginInfo get(@PathVariable("id") String id, HttpServletRequest request) throws RaplaException
    {
        session.checkAndGetUser(request);
        PluginRegistry.PluginEntry entry = lookup(id);
        Preferences prefs = facade.getSystemPreferences();
        return new PluginInfo(entry.id(), entry.displayName(), isEnabled(prefs, entry), entry.adminOnlyConfig());
    }

    /** Toggle enabled. Admin only. Body: {"enabled": true|false}. */
    @PutMapping("/{id}/enabled")
    public PluginInfo setEnabled(@PathVariable("id") String id,
                                  @RequestBody EnabledRequest body,
                                  HttpServletRequest request) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        if (!user.isAdmin())
        {
            throw new RaplaSecurityException("Only admins can toggle plugins");
        }
        PluginRegistry.PluginEntry entry = lookup(id);
        Preferences prefs = facade.getSystemPreferences();
        Preferences edit = facade.edit(prefs);
        applyEnabled(edit, entry, body.enabled());
        facade.store(edit);
        return new PluginInfo(entry.id(), entry.displayName(), body.enabled(), entry.adminOnlyConfig());
    }

    // ---------- helpers ----------

    private static PluginRegistry.PluginEntry lookup(String id) throws EntityNotFoundException
    {
        PluginRegistry.PluginEntry entry = PluginRegistry.byId(id);
        if (entry == null)
        {
            throw new EntityNotFoundException("Unknown plugin: " + id);
        }
        return entry;
    }

    private static boolean isEnabled(Preferences prefs, PluginRegistry.PluginEntry entry)
    {
        if (entry.boolEnabledKey() != null)
        {
            TypedComponentRole<Boolean> role = new TypedComponentRole<>(entry.boolEnabledKey());
            return prefs.getEntryAsBoolean(role, entry.defaultEnabled());
        }
        if (entry.configKey() != null)
        {
            TypedComponentRole<RaplaConfiguration> role = new TypedComponentRole<>(entry.configKey());
            RaplaConfiguration config = prefs.getEntry(role);
            if (config == null) return entry.defaultEnabled();
            return config.getAttributeAsBoolean("enabled", entry.defaultEnabled());
        }
        return entry.defaultEnabled();
    }

    private static void applyEnabled(Preferences edit, PluginRegistry.PluginEntry entry, boolean value)
    {
        // Match the AbstractPluginPreferencesPanel.putOrRemove pattern: if the
        // value equals the deployment's default, remove the entry rather than
        // pinning a copy of the current default. Lets future default changes
        // propagate automatically.
        if (entry.boolEnabledKey() != null)
        {
            TypedComponentRole<Boolean> role = new TypedComponentRole<>(entry.boolEnabledKey());
            if (value == entry.defaultEnabled()) edit.removeEntry(role.getId());
            else                                 edit.putEntry(role, value);
            return;
        }
        if (entry.configKey() != null)
        {
            TypedComponentRole<RaplaConfiguration> role = new TypedComponentRole<>(entry.configKey());
            RaplaConfiguration existing = edit.getEntry(role);
            RaplaConfiguration next = (existing != null) ? existing.clone() : new RaplaConfiguration("plugin");
            next.setAttribute("enabled", value);
            edit.putEntry(role, next);
        }
    }
}
