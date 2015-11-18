package org.rapla.plugin.exchangeconnector.server;

import java.util.List;

import javax.inject.Inject;

import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfigRemote;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;

@DefaultImplementation(context = InjectionContext.server, of = ExchangeConnectorConfigRemote.class)
public class ExchangeConnectorRemoteConfigFactory implements ExchangeConnectorConfigRemote
{
			
    private final RemoteSession remoteSession;
    private final ClientFacade clientFacade;

    @Inject
	public ExchangeConnectorRemoteConfigFactory(final RemoteSession remoteSession, ClientFacade clientFacade) {
        this.remoteSession = remoteSession;
        this.clientFacade = clientFacade;
	}

    @Override
    public DefaultConfiguration getConfig() throws RaplaException
    {
        User user = remoteSession.getUser();
        if ( !user.isAdmin())
        {
            throw new RaplaSecurityException("Access only for admin users");
        }
        Preferences preferences = clientFacade.getSystemPreferences();
        RaplaConfiguration config = preferences.getEntry( ExchangeConnectorConfig.EXCHANGESERVER_CONFIG, new RaplaConfiguration());
        return config;
    }

    @Override
    public List<String> getTimezones() throws RaplaException
    {
        return ExchangeConnectorServerPlugin.TIMEZONES;
    }
	

}
