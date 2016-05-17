package org.rapla.plugin.export2ical.client.swing;

import java.beans.PropertyChangeListener;

import javax.inject.Inject;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.PublishExtensionFactory;
import org.rapla.client.swing.PublishExtension;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.plugin.export2ical.Export2iCalPlugin;

@Extension(provides=PublishExtensionFactory.class,id="ical")
public class IcalPublicExtensionFactory implements PublishExtensionFactory
{
	private final RaplaImages raplaImages;
    private final ClientFacade facade;
    private final RaplaResources i18n;
    private final RaplaLocale raplaLocale;
    private final Logger logger;
    private final IOInterface ioInterface;

    @Inject
	public IcalPublicExtensionFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, RaplaImages raplaImages, IOInterface ioInterface)
	{
        this.facade = facade;
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
        this.logger = logger;
        this.raplaImages = raplaImages;
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
		return new IcalPublishExtension(facade, i18n, raplaLocale, logger, model, raplaImages, ioInterface);
	}

	
}