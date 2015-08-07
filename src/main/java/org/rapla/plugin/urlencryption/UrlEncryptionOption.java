package org.rapla.plugin.urlencryption;

import java.util.Locale;

import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContext;
import org.rapla.gui.DefaultPluginOption;

public class UrlEncryptionOption extends DefaultPluginOption 
{
	
	public UrlEncryptionOption(RaplaContext sm) 
	{
		super(sm);
	}
	
	@Override
	public Class<? extends PluginDescriptor<?>> getPluginClass()
	{
		return UrlEncryptionPlugin.class;
	}

    public String getName(Locale locale)
    {
        return "URL Encryption";
    }
}
