/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.components.i18n;

import org.rapla.components.util.DateTools;

import java.util.Collection;
import java.util.Locale;
import java.util.MissingResourceException;

public class AbstractBundle implements I18nBundle
{
    protected String className;
    protected final BundleManager bundleManager;
    protected final String packageId;

    public AbstractBundle(String packageId,BundleManager localeLoader)
    {
        this.bundleManager = localeLoader;
        this.packageId = packageId;
    }

    public String getPackageId()
    {
        return packageId;
    }
    public String format( String key, Object... obj )
    {
        return replaceArgs(getString(key), obj);
    }


    protected String replaceArgs(String string, Object[] args)
    {
        String msg = string;
        if (args != null)
        {
            for (int i = 0; i < args.length; i++)
            {
                final String argString = "\\{" + (i ) + "\\}";
                final Object arg = args[i];
                msg = msg.replaceAll(argString, arg != null ? arg.toString() : "null");
            }
        }
        return msg;
    }


    public Locale getLocale()
    {
        return bundleManager.getLocale();
    }

    public String getLang()
    {
        Locale locale = getLocale();
        return DateTools.getLang(locale);
    }

    public String getString( String key ) throws MissingResourceException
    {
		return bundleManager.getString(packageId, key);
    }


    public I18nIcon getIcon( String key ) throws MissingResourceException
    {
        return bundleManager.getIcon(packageId, key);
    }

    public String getString( String key, Locale locale) throws MissingResourceException
    {
		return bundleManager.getString(packageId,key, locale);
    }

    public Collection<String> getKeys(){
        return bundleManager.getKeys( packageId);
    }

}
