package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorConfigService;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;
import org.rapla.server.RemoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean(RemoteSession.class)
public class EventTimeCalculatorConfigController implements EventTimeCalculatorConfigService
{
    private final RaplaFacade facade;
    private final RemoteSession session;
    private final HttpServletRequest request;

    public EventTimeCalculatorConfigController(RaplaFacade facade, RemoteSession session, HttpServletRequest request)
    {
        this.facade = facade;
        this.session = session;
        this.request = request;
    }

    @Override
    public DefaultConfiguration getSystemConfig() throws RaplaException
    {
        session.checkAndGetUser(request);
        Preferences prefs = facade.getSystemPreferences();
        DefaultConfiguration config = prefs.getEntry(EventTimeCalculatorPlugin.SYSTEM_CONFIG);
        return config != null ? config : new DefaultConfiguration("eventtime");
    }

    @Override
    public DefaultConfiguration getUserConfig() throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        Preferences prefs = facade.getPreferences(user);
        return prefs.getEntry(EventTimeCalculatorPlugin.USER_CONFIG);
    }
}
