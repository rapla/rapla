package org.rapla.plugin.urlencryption.client.swing;

import java.util.Locale;

import javax.inject.Inject;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.PluginOptionPanel;
import org.rapla.client.swing.DefaultPluginOption;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.plugin.urlencryption.UrlEncryptionPlugin;

@Extension(provides = PluginOptionPanel.class , id= UrlEncryptionPlugin.PLUGIN_ID)
public class UrlEncryptionOption extends DefaultPluginOption 
{

    @Inject
	public UrlEncryptionOption(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger)
	{
		super(facade, i18n, raplaLocale, logger);
	}
	
    public String getName(Locale locale)
    {
        return "URL Encryption";
    }
}
