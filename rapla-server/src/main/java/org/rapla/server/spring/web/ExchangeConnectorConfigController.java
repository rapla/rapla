package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig;
import org.rapla.plugin.exchangeconnector.ExchangeUserSettings;
import org.rapla.plugin.exchangeconnector.server.ExchangeConnectorServerPlugin;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@ConditionalOnBean(RemoteSession.class)
@RequestMapping(value = "/exchange/config", produces = "application/json")
public class ExchangeConnectorConfigController
{
    private final RaplaFacade facade;
    private final RemoteSession session;

    public ExchangeConnectorConfigController(RaplaFacade facade, RemoteSession session)
    {
        this.facade = facade;
        this.session = session;
    }

    @GetMapping("/default")
    public RaplaConfiguration getConfig(HttpServletRequest request) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        if (!user.isAdmin())
        {
            throw new RaplaSecurityException("Access only for admin users");
        }
        Preferences preferences = facade.getSystemPreferences();
        return preferences.getEntry(ExchangeConnectorConfig.EXCHANGESERVER_CONFIG, new RaplaConfiguration());
    }

    @GetMapping("/timezones")
    public List<String> getTimezones(HttpServletRequest request) throws RaplaException
    {
        session.checkAndGetUser(request);
        return ExchangeConnectorServerPlugin.TIMEZONES;
    }

    @GetMapping("/user")
    public ExchangeUserSettings getUserSettings(HttpServletRequest request) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        Preferences prefs = facade.getPreferences(user);
        Boolean send = prefs.hasEntry(ExchangeConnectorConfig.EXCHANGE_SEND_INVITATION_AND_CANCELATION)
                ? prefs.getEntryAsBoolean(ExchangeConnectorConfig.EXCHANGE_SEND_INVITATION_AND_CANCELATION, false)
                : null;
        return new ExchangeUserSettings(send);
    }
}
