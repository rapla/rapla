package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfigRemote;
import org.rapla.plugin.exchangeconnector.ExchangeUserSettings;
import org.rapla.plugin.exchangeconnector.server.ExchangeConnectorServerPlugin;
import org.rapla.server.ApiKeyScopeContext;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@ConditionalOnBean(RemoteSession.class)
public class ExchangeConnectorConfigController implements ExchangeConnectorConfigRemote
{
    private final RaplaFacade facade;
    private final RemoteSession session;
    private final HttpServletRequest request;

    public ExchangeConnectorConfigController(RaplaFacade facade, RemoteSession session, HttpServletRequest request)
    {
        this.facade = facade;
        this.session = session;
        this.request = request;
    }

    @Override
    public RaplaConfiguration getConfig() throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        if (!user.isAdmin())
        {
            throw new RaplaSecurityException("Access only for admin users");
        }
        // server-side Exchange credentials — interactive admin session only, never an api-key.
        ApiKeyScopeContext.requireInteractiveSession("exchange config");
        Preferences preferences = facade.getSystemPreferences();
        return preferences.getEntry(ExchangeConnectorConfig.EXCHANGESERVER_CONFIG, new RaplaConfiguration());
    }

    @Override
    public List<String> getTimezones() throws RaplaException
    {
        session.checkAndGetUser(request);
        return ExchangeConnectorServerPlugin.TIMEZONES;
    }

    @Override
    public ExchangeUserSettings getUserSettings() throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        Preferences prefs = facade.getPreferences(user);
        Boolean send = prefs.hasEntry(ExchangeConnectorConfig.EXCHANGE_SEND_INVITATION_AND_CANCELATION)
                ? prefs.getEntryAsBoolean(ExchangeConnectorConfig.EXCHANGE_SEND_INVITATION_AND_CANCELATION, false)
                : null;
        return new ExchangeUserSettings(send);
    }
}
