package org.rapla.plugin.exchangeconnector.server;

import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfigRemote;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import java.util.List;

public class ExchangeConnectorRemoteConfigFactory implements ExchangeConnectorConfigRemote
{
			
    @Autowired
    RemoteSession remoteSession;
    @Autowired
    RaplaFacade raplaFacade;
    private final HttpServletRequest request;

    @Autowired
	public ExchangeConnectorRemoteConfigFactory(@Context HttpServletRequest request) {
        this.request = request;
	}

    @Override
    public DefaultConfiguration getConfig() throws RaplaException
    {
        User user = remoteSession.checkAndGetUser(request);
        if ( !user.isAdmin())
        {
            throw new RaplaSecurityException("Access only for admin users");
        }
        Preferences preferences = raplaFacade.getSystemPreferences();
        RaplaConfiguration config = preferences.getEntry( ExchangeConnectorConfig.EXCHANGESERVER_CONFIG, new RaplaConfiguration());
        return config;
    }

    @Override
    public List<String> getTimezones() throws RaplaException
    {
        return ExchangeConnectorServerPlugin.TIMEZONES;
    }
	

}
