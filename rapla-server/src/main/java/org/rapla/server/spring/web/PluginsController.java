package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.rest.PluginsService;
import org.rapla.rest.dto.PluginInfo;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@ConditionalOnBean(RemoteSession.class)
public class PluginsController implements PluginsService
{
    private final RaplaFacade facade;
    private final RemoteSession session;
    private final HttpServletRequest request;
    private final Environment env;

    public PluginsController(RaplaFacade facade, RemoteSession session, HttpServletRequest request, Environment env)
    {
        this.facade = facade;
        this.session = session;
        this.request = request;
        this.env = env;
    }

    @Override
    public List<PluginInfo> list() throws RaplaException
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

    @Override
    public PluginInfo get(String id) throws RaplaException
    {
        session.checkAndGetUser(request);
        PluginRegistry.PluginEntry entry = lookup(id);
        Preferences prefs = facade.getSystemPreferences();
        return new PluginInfo(entry.id(), entry.displayName(), isEnabled(prefs, entry), entry.adminOnlyConfig());
    }

    @Override
    public PluginInfo setEnabled(String id, EnabledRequest body) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        if (!user.isAdmin())
        {
            throw new RaplaSecurityException("Only admins can toggle plugins");
        }
        PluginRegistry.PluginEntry entry = lookup(id);
        if (entry.envPropertyKey() != null)
        {
            throw new RaplaException("Plugin '" + id + "' is operator-managed via application.yml ("
                    + entry.envPropertyKey() + "); not runtime-toggleable");
        }
        Preferences prefs = facade.getSystemPreferences();
        Preferences edit = facade.edit(prefs);
        applyEnabled(edit, entry, body.enabled());
        facade.store(edit);
        return new PluginInfo(entry.id(), entry.displayName(), body.enabled(), entry.adminOnlyConfig());
    }

    private static PluginRegistry.PluginEntry lookup(String id) throws EntityNotFoundException
    {
        PluginRegistry.PluginEntry entry = PluginRegistry.byId(id);
        if (entry == null)
        {
            throw new EntityNotFoundException("Unknown plugin: " + id);
        }
        return entry;
    }

    private boolean isEnabled(Preferences prefs, PluginRegistry.PluginEntry entry)
    {
        if (entry.envPropertyKey() != null)
        {
            return env.getProperty(entry.envPropertyKey(), Boolean.class, entry.defaultEnabled());
        }
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
        // Mirror AbstractPluginPreferencesPanel.putOrRemove: if value matches the
        // deployment default, drop the entry instead of pinning a copy of it,
        // so future default changes propagate automatically.
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
