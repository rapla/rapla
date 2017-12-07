package org.rapla.plugin.exchangeconnector.server;

import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfigRemote;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import java.util.List;

@DefaultImplementation(context=InjectionContext.server, of=ExchangeConnectorConfigRemote.class)
public class ExchangeConnectorRemoteConfigFactory implements ExchangeConnectorConfigRemote
{
			
    @Inject
    RemoteSession remoteSession;
    @Inject
    RaplaFacade raplaFacade;
    private final HttpServletRequest request;

    @Inject
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
