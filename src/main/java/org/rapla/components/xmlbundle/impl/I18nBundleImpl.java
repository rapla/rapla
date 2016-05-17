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
package org.rapla.components.xmlbundle.impl;

import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.internal.DefaultBundleManager;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.components.xmlbundle.LocaleChangeEvent;
import org.rapla.components.xmlbundle.LocaleChangeListener;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;

/** The default implementation of the xmlbundle component allows reading from
 a compiled ResourceBundle as well as directly from the source-xml-file.
 <p>
 Sample Configuration 1: (Resources are loaded from the compiled ResourceBundles)
 <pre>
 &lt;resource-bundle id="org.rapla.RaplaResources"/>
 </pre>
 </p>
 <p>
 Sample Configuration 2: (Resources will be loaded directly from the resource-file)
 <pre>
 &lt;resource-bundle id="org.rapla.plugin.periodwizard.WizardResources">
 &lt;file>/home/christopher/Rapla/src/org/rapla/periodwizard/WizardResources.xml&lt;/file>
 &lt;/resource-bundle>
 </pre>
 </p>
 <p>
 This class looks for a LocaleSelector on the context and registers itself as
 a LocaleChangeListener and switches to the new Locale on a LocaleChangeEvent.
 </p>
 */

public class I18nBundleImpl implements I18nBundle
{
    String className;
    Locale locale;
    Logger logger = null;
    final RaplaDictionary dict;
    
    LinkedHashMap<Locale,LanguagePack> packMap = new LinkedHashMap<Locale,LanguagePack>();
    class LanguagePack
    {
	    Locale locale;
	    ResourceBundle resourceBundle;
	    public String getString( String key ) throws MissingResourceException
	    {
	        String lang = locale.getLanguage();
			DictionaryEntry entry = dict.getEntry(key);
	        String string;
	        if ( entry != null)
	    	{
	    		string = entry.get( lang );
		    	if ( string == null )
		    	{
		    		string = resourceBundle.getString( key );
					entry.add(lang, key);		
		    	}
	    	}
	    	else
	    	{
	    		string = resourceBundle.getString( key );
	    		entry = new DictionaryEntry( key);
				entry.add(lang, string);
				try {
					dict.addEntry( entry);
				} catch (UniqueKeyException e) {
					// we can ignore it here
				}
	    	}
	        return string;
	    }	    
	    
	    Enumeration<String> getKeys(){
	        return resourceBundle.getKeys();
	    }
    }
    
    /**
     * @param localeSelector 
     * @throws RaplaException when the resource-file is missing or can't be accessed
     or can't be parsed
     */
    public I18nBundleImpl(  Logger logger, String classname, BundleManager localeSelector )
    {
        enableLogging( logger );
        this.className = classname;
        if ( className != null ) 
        {
            className = className.trim();
        }
        setLocale( localeSelector.getLocale());
        ((DefaultBundleManager)localeSelector).addLocaleChangeListener(new LocaleChangeListener()
        {
            
            @Override
            public void localeChanged(LocaleChangeEvent evt)
            {
                I18nBundleImpl.this.localeChanged(evt);
            }
        });
        dict = new RaplaDictionary(getLocale().getLanguage());
    }

    public void localeChanged( LocaleChangeEvent evt )
    {
        try
        {
            setLocale( evt.getLocale() );
        }
        catch ( Exception ex )
        {
            getLogger().error( "Can't set new locale " + evt.getLocale(), ex );
        }
    }

    public void enableLogging( Logger logger )
    {
        this.logger = logger;
    }

    protected Logger getLogger()
    {
        return logger;
    }

    public String format( String key, Object obj1 )
    {
        Object[] array1 = new Object[1];
        array1[0] = obj1;
        return format( key, array1 );
    }

    public String format( String key, Object obj1, Object obj2 )
    {
        Object[] array2 = new Object[2];
        array2[0] = obj1;
        array2[1] = obj2;
        return format( key, array2 );
    }

    public String format( String key, Object... obj )
    {
        MessageFormat msg = new MessageFormat( getString( key ) );
        return msg.format( obj );
    }

    public Locale getLocale()
    {
        return locale;
    }

    public String getLang()
    {
        Locale locale = getLocale();
        return locale.getLanguage();
    }

    public String getString( String key ) throws MissingResourceException
    {
        Locale locale = getLocale();
		return getString(key, locale);
    }

    public String getString( String key, Locale locale) throws MissingResourceException
    {
		LanguagePack pack = getPack(locale);
		return pack.getString(key);
    }
    
//    /* replaces XHTML with HTML because swing can't display proper XHTML*/
//    String filterXHTML( String text )
//    {
//        if ( text.indexOf( "<br/>" ) >= 0 )
//        {
//            return applyXHTMLFilter( text );
//        }
//        else
//        {
//            return text;
//        } // end of else
//    }

//    public static String replaceAll( String text, String token, String with )
//    {
//        StringBuffer buf = new StringBuffer();
//        int i = 0;
//        int lastpos = 0;
//        while ( ( i = text.indexOf( token, lastpos ) ) >= 0 )
//        {
//            if ( i > 0 )
//                buf.append( text.substring( lastpos, i ) );
//            buf.append( with );
//            i = ( lastpos = i + token.length() );
//        } // end of if ()
//        buf.append( text.substring( lastpos, text.length() ) );
//        return buf.toString();
//    }
//
//    private String applyXHTMLFilter( String text )
//    {
//        return replaceAll( text, "<br/>", "<br></br>" );
//    }

    private void setLocale( Locale locale )
    {
    	this.locale = locale;
    	if ( dict != null)
    	{
    	    this.dict.setDefaultLang( locale.getLanguage());
    	}
    	getLogger().debug( "Locale changed to " + locale );
    	try
    	{
    		getPack(locale);
    	}
    	catch (MissingResourceException ex)
    	{
    		getLogger().error(ex.getMessage(), ex);
    	}
    }

	private LanguagePack getPack(Locale locale)  throws MissingResourceException {
		LanguagePack pack = packMap.get(locale);
        if (pack != null)
        {
        	return pack;
        }
		synchronized ( packMap )
		{
			// again, now with synchronization
			pack = packMap.get(locale);
	        if (pack != null)
	        {
	        	return pack;
	        }
			pack = new LanguagePack();
			pack.locale = locale;
			pack.resourceBundle = ResourceBundleLoader.loadResourceBundle( className, locale );
		    packMap.put( locale, pack);
			return pack;
		}
	}

    @Override public String getPackageId()
    {
        return className;
    }
}
