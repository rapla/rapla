package org.rapla.plugin.autoexport.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.PublishExtensionFactory;
import org.rapla.client.swing.PublishExtension;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.autoexport.AutoExportResources;

import javax.inject.Inject;
import java.beans.PropertyChangeListener;

@Extension(provides=PublishExtensionFactory.class,id="html")
public class HTMLPublicExtensionFactory implements PublishExtensionFactory
{
    private final ClientFacade facade;
    private final RaplaResources i18n;
    private final RaplaLocale raplaLocale;
    private final Logger logger;
    private final AutoExportResources autoExportI18n;
    private final IOInterface ioInterface;

    @Inject
	public HTMLPublicExtensionFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, AutoExportResources autoExportI18n, IOInterface ioInterface) {
		this.facade = facade;
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
        this.logger = logger;
        this.autoExportI18n = autoExportI18n;
        this.ioInterface = ioInterface;
	}
    
    @Override
    public boolean isEnabled()
    {
        return true;
    }

	public PublishExtension creatExtension(CalendarSelectionModel model,PropertyChangeListener revalidateCallback) throws RaplaException 
	{
		return new HTMLPublishExtension(facade, i18n, raplaLocale, logger, model, autoExportI18n, ioInterface);
	}

}