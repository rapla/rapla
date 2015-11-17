package org.rapla.plugin.exchangeconnector.server;

import javax.inject.Inject;

import org.rapla.inject.Extension;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorPlugin;
import org.rapla.server.extensionpoints.ServerExtension;

/** Starts the synchronization manager when the server is started*/
@Extension(id=ExchangeConnectorPlugin.PLUGIN_ID, provides=ServerExtension.class)
public class SynchronisationManagerInitializer implements ServerExtension{
	SynchronisationManager manager;
	@Inject
	public SynchronisationManagerInitializer(SynchronisationManager synchronisationManager) {
		manager = synchronisationManager;
	}
    @Override
    public void start()
    {
        
    }

}
