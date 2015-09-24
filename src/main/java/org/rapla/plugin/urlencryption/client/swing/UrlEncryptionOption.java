package org.rapla.plugin.urlencryption.client.swing;

import java.util.Locale;

import org.rapla.framework.RaplaContext;
import org.rapla.gui.DefaultPluginOption;
import org.rapla.client.extensionpoints.PluginOptionPanel;
import org.rapla.inject.Extension;
import org.rapla.plugin.urlencryption.UrlEncryptionPlugin;

import javax.inject.Inject;

@Extension(provides = PluginOptionPanel.class , id= UrlEncryptionPlugin.PLUGIN_ID)
public class UrlEncryptionOption extends DefaultPluginOption 
{

    @Inject
	public UrlEncryptionOption(RaplaContext sm) 
	{
		super(sm);
	}
	
    public String getName(Locale locale)
    {
        return "URL Encryption";
    }
}
