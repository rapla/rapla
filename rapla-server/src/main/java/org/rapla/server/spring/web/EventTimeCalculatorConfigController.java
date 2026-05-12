package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;
import org.rapla.server.RemoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server-side counterpart for {@code EventTimeCalculatorConfigService}.
 * Replaces the bulk-bootstrap preference reads in
 * {@code EventTimeCalculatorUserOption}.
 *
 * <p>Both endpoints require authentication; neither is admin-gated (the
 * Swing client uses these to render the user-options panel for any user).
 */
@RestController
@ConditionalOnBean(RemoteSession.class)
@RequestMapping("/eventtimecalculator")
public class EventTimeCalculatorConfigController
{
    private final RaplaFacade facade;
    private final RemoteSession session;

    public EventTimeCalculatorConfigController(RaplaFacade facade, RemoteSession session)
    {
        this.facade = facade;
        this.session = session;
    }

    @GetMapping("/system-config")
    public DefaultConfiguration getSystemConfig(HttpServletRequest request) throws RaplaException
    {
        session.checkAndGetUser(request);
        Preferences prefs = facade.getSystemPreferences();
        DefaultConfiguration config = prefs.getEntry(EventTimeCalculatorPlugin.SYSTEM_CONFIG);
        return config != null ? config : new DefaultConfiguration("eventtime");
    }

    @GetMapping("/user-config")
    public DefaultConfiguration getUserConfig(HttpServletRequest request) throws RaplaException
    {
        // Returns null (HTTP 200 with empty body) when the user hasn't set a
        // per-user override — the Swing client then falls back to the system
        // config, matching the legacy preferences.getEntry() behaviour.
        User user = session.checkAndGetUser(request);
        Preferences prefs = facade.getPreferences(user);
        return prefs.getEntry(EventTimeCalculatorPlugin.USER_CONFIG);
    }
}
