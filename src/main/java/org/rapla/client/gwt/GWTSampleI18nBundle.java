package org.rapla.client.gwt;

import java.util.Locale;
import java.util.MissingResourceException;

import javax.swing.ImageIcon;

import org.rapla.components.xmlbundle.I18nBundle;

final class GWTSampleI18nBundle implements I18nBundle {
    @Override
    public String getString(String key) throws MissingResourceException {
    	return key;
    }

    @Override
    public Locale getLocale() {
    	return null;
    }

    @Override
    public String getLang() {
    	return "en";
    }

    @Override
    public String format(String key, Object... obj)
    		throws MissingResourceException {
    	StringBuilder buf = new StringBuilder();
    	buf.append(key);
    	for (Object o:obj)
    	{
    	    buf.append(o);
    	}
        return buf.toString();
    }

    @Override
    public String format(String key, Object obj1, Object obj2)
    		throws MissingResourceException {
    	return key;
    }

    @Override
    public String format(String key, Object obj1)
    		throws MissingResourceException {
    	return key;
    }

    @Override
    public String getString(String key, Locale locale) {
    	return key;
    }
}