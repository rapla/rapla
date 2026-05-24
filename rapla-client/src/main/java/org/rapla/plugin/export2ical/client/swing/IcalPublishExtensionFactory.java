package org.rapla.plugin.export2ical.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.PublishExtensionFactory;
import org.rapla.client.swing.PublishExtension;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.plugin.export2ical.ICalConfigService;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;
import java.beans.PropertyChangeListener;

@Service

public class IcalPublishExtensionFactory implements PublishExtensionFactory
{
    private static final Logger LOGGER = LoggerFactory.getLogger(IcalPublishExtensionFactory.class);
    private final ClientFacade facade;
    private final RaplaResources i18n;
    private final RaplaLocale raplaLocale;
    private final IOInterface ioInterface;
    private final ICalConfigService configService;
    /** Cached enabled flag — isEnabled() is called on every UI render. Null = not yet fetched. */
    private volatile Boolean cachedEnabled;

    @Autowired
	public IcalPublishExtensionFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, IOInterface ioInterface, ICalConfigService configService)
	{
        this.facade = facade;
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
        this.ioInterface = ioInterface;
        this.configService = configService;
	}

    @Override
    public boolean isEnabled()
    {
        Boolean cached = cachedEnabled;
        if (cached != null) return cached;
        try
        {
            // Previously read .server.-named ICAL_CONFIG directly from
            // /storage/resources system prefs. That key is now stripped for
            // admins too (PRD 026 §5 security fix), so we go via the
            // dedicated endpoint instead. Server-side reads are unchanged.
            Configuration config = configService.getUserDefaultConfig();
            boolean enabled = config != null && config.getAttributeAsBoolean("enabled", Export2iCalPlugin.ENABLE_BY_DEFAULT);
            cachedEnabled = enabled;
            return enabled;
        }
        catch (RaplaException e)
        {
            LOGGER.warn("Failed to load iCal config via /ical/config/default; falling back to default", e);
            return Export2iCalPlugin.ENABLE_BY_DEFAULT;
        }
    }

	public PublishExtension creatExtension(CalendarSelectionModel model,
			PropertyChangeListener revalidateCallback) throws RaplaException 
	{
		return new IcalPublishExtension(facade, i18n, raplaLocale, model, ioInterface);
	}

	
}