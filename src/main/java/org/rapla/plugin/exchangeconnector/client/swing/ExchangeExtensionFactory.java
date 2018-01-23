package org.rapla.plugin.exchangeconnector.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.PublishExtensionFactory;
import org.rapla.client.swing.PublishExtension;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig.ConfigReader;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorPlugin;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorRemote;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorResources;

import javax.inject.Inject;
import java.beans.PropertyChangeListener;

@Extension(id=ExchangeConnectorPlugin.PLUGIN_ID, provides=PublishExtensionFactory.class)
public class ExchangeExtensionFactory extends RaplaGUIComponent implements PublishExtensionFactory
{
	private final ExchangeConnectorRemote remote;
    private final ExchangeConnectorResources exchangeConnectorResources;
    private final ConfigReader config;
	@Inject
	public ExchangeExtensionFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, ExchangeConnectorRemote remote, ExchangeConnectorResources exchangeConnectorResources, ConfigReader config)
	{
		super(facade, i18n, raplaLocale, logger);
		this.remote = remote;
        this.exchangeConnectorResources = exchangeConnectorResources;
        this.config = config;
	}

	public PublishExtension creatExtension(CalendarSelectionModel model,
			PropertyChangeListener revalidateCallback) throws RaplaException 
	{
		return new ExchangePublishExtension(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), model,remote, exchangeConnectorResources);
	}
	
	@Override
	public boolean isEnabled()
	{
	    return config.isEnabled();
	}

	
}
