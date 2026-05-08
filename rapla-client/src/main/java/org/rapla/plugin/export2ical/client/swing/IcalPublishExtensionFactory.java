package org.rapla.plugin.export2ical.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.PublishExtensionFactory;
import org.rapla.client.swing.PublishExtension;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;
import java.beans.PropertyChangeListener;

@Service

public class IcalPublishExtensionFactory implements PublishExtensionFactory
{
    private final ClientFacade facade;
    private final RaplaResources i18n;
    private final RaplaLocale raplaLocale;
    private final Logger logger;
    private final IOInterface ioInterface;

    @Autowired
	public IcalPublishExtensionFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, IOInterface ioInterface)
	{
        this.facade = facade;
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
        this.logger = logger;
        this.ioInterface = ioInterface;
	}
    
    @Override
    public boolean isEnabled()
    {
        RaplaConfiguration config;
        try
        {
            config = facade.getRaplaFacade().getSystemPreferences().getEntry(Export2iCalPlugin.ICAL_CONFIG, new RaplaConfiguration());
        }
        catch (RaplaException e)
        {
            return false;
        }
        final boolean enabled = config.getAttributeAsBoolean("enabled", Export2iCalPlugin.ENABLE_BY_DEFAULT);
        return enabled;
    }

	public PublishExtension creatExtension(CalendarSelectionModel model,
			PropertyChangeListener revalidateCallback) throws RaplaException 
	{
		return new IcalPublishExtension(facade, i18n, raplaLocale, logger, model, ioInterface);
	}

	
}