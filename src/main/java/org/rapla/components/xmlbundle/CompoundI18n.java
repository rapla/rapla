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
package org.rapla.components.xmlbundle;

import java.util.Locale;
import java.util.MissingResourceException;
/** Allows the combination of two resource-bundles.
    First the inner bundle will be searched.
    If the requested resource was not found the
    outer bundle will be searched.
 */
public class CompoundI18n implements I18nBundle {
    I18nBundle inner;
    I18nBundle outer;
    public CompoundI18n(I18nBundle inner,I18nBundle outer) {
        this.inner = inner;
        this.outer = outer;
    }

    public String format(String key,Object obj1) {
    	Object[] array1 = new Object[1];
    	array1[0] = obj1;
    	return format(key,array1);
    }

    public String format(String key,Object obj1,Object obj2) {
    	Object[] array2 = new Object[2];
    	array2[0] = obj1;
    	array2[1] = obj2;
    	return format(key,array2);
    }

    @Override
    public String format(String key,Object[] obj) {
    	 try {
             return inner.format(key, obj);
         } catch (MissingResourceException ex) {
             return outer.format( key, obj);
         }
    }

    public String getString(String key) {
    	try {
    	    return inner.getString(key);
    	} catch (MissingResourceException ex) {
    	    return outer.getString(key);
    	}
    }

    public String getPackageId()
    {
        return inner.getPackageId();
    }

    public String getLang() {
        return inner.getLang();
    }

    public Locale getLocale() {
        return inner.getLocale();
    }

	public String getString(String key, Locale locale) {
		try {
    	    return inner.getString(key,locale);
    	} catch (MissingResourceException ex) {
    	    return outer.getString(key, locale);
    	}
	}
}
