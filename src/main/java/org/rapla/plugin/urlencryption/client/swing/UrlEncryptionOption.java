package org.rapla.plugin.urlencryption.client.swing;

import java.util.Locale;

import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContext;
import org.rapla.gui.DefaultPluginOption;
import org.rapla.plugin.urlencryption.UrlEncryptionPlugin;

public class UrlEncryptionOption extends DefaultPluginOption 
{
	
	public UrlEncryptionOption(RaplaContext sm) 
	{
		super(sm);
	}
	
    public String getName(Locale locale)
    {
        return "URL Encryption";
    }
}
