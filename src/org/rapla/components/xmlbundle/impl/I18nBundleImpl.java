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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.TreeMap;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.rapla.components.util.IOUtil;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.components.xmlbundle.LocaleChangeEvent;
import org.rapla.components.xmlbundle.LocaleChangeListener;
import org.rapla.components.xmlbundle.LocaleSelector;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;

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
 @see TranslationParser
 @see LocaleSelector
 */

public class I18nBundleImpl implements I18nBundle, LocaleChangeListener, Disposable
{
    String className;
    Locale locale;
    Logger logger = null;
    LocaleSelectorImpl m_localeSelector;

    final String dictionaryFile;
    final RaplaDictionary dict;
    
    LinkedHashMap<Locale,LanguagePack> packMap = new LinkedHashMap<Locale,LanguagePack>();
    String parentId = null;
    class LanguagePack
    {
	    Locale locale;
	    Map<String,Icon> iconCache = Collections.synchronizedMap( new TreeMap<String,Icon>() );
	    ResourceBundle resourceBundle;
	    public String getString( String key ) throws MissingResourceException
	    {
	        String lang = locale.getLanguage();
			if ( dictionaryFile != null )
	        {
	        	String lookup = dict.lookup(key, lang);
	        	if ( lookup == null)
	        	{
	        		throw new MissingResourceException("Entry not found for "+ key, dictionaryFile, key);
	        	}
				return lookup;
	        }
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
	    
	    public ImageIcon getIcon( String key ) throws MissingResourceException
	    {
	        String iconfile; 
	        try
	        {
	            iconfile = getString( key );
	        }
	        catch ( MissingResourceException ex )
	        {
	            getLogger().debug( ex.getMessage() ); //BJO
	            throw ex;
	        }
	        try
	        {
	            ImageIcon icon = (ImageIcon) iconCache.get( iconfile );
	            if ( icon == null )
	            {
	                icon = new ImageIcon( loadResource( iconfile ), key );
	                iconCache.put( iconfile, icon );
	            } // end of if ()
	            return icon;
	        }
	        catch ( Exception ex )
	        {
	            String message = "Icon " + iconfile + " can't be created: " + ex.getMessage();
	            getLogger().error( message );
	            throw new MissingResourceException( message, className, key );
	        }
	    }
	    
	    private final byte[] loadResource( String fileName ) throws IOException
	    {
	        return IOUtil.readBytes( getResourceFromFile( fileName ) );
	    }

	    private URL getResourceFromFile( String fileName ) throws IOException
	    {
	        URL resource = null;
	        String base;
	        if ( dictionaryFile == null )
	        {
	        	if ( resourceBundle == null)
	        	{
	        		throw new IOException("Resource Bundle for locale " + locale + " is missing while looking up " + fileName);
	        	}
				if ( resourceBundle instanceof PropertyResourceBundleWrapper)
	        	{
	        		base = ((PropertyResourceBundleWrapper) resourceBundle).getName();
	        	}
	        	else
	        	{        		
	        		base = resourceBundle.getClass().getName();
	        	}
	            base = base.substring(0,base.lastIndexOf("."));
	        	base = base.replaceAll("\\.", "/");
	        	String file = "/" + base + "/" + fileName;
	            resource = I18nBundleImpl.class.getResource( file );
	    		
	        }
	        else
	        {
	        	if ( getLogger().isDebugEnabled() )
		                getLogger().debug( "Looking for resourcefile " + fileName + " in classpath ");
		         
	        	URL resourceBundleURL = getClass().getClassLoader().getResource(dictionaryFile);
	        	if (resourceBundleURL != null)
	        	{
	        		resource = new URL( resourceBundleURL, fileName);
	        		base = resource.getPath();
	        	}
	        	else
	        	{
	        		base = ( new File( dictionaryFile ) ).getParent();
	        		if ( base != null)
	        		{	
	        			if ( getLogger().isDebugEnabled() )
	        				getLogger().debug( "Looking for resourcefile " + fileName + " in directory " + base );
	        			File resourceFile = new File( base, fileName );
	        			if ( resourceFile.exists() )
	        				resource = resourceFile.toURI().toURL();
	        		}
	        	}
	        }
	        if ( resource == null )
	            throw new IOException( "File '"
	                    + fileName
	                    + "' not found. "
	                    + " in bundle "
	                    + className
	                    + " It must be in the same location as '"
	                    + base
	                    + "'" );
	        return resource;
	    }
    }
    
    /**
     * @throws RaplaException when the resource-file is missing or can't be accessed
     or can't be parsed
     */
    public I18nBundleImpl( RaplaContext context, Configuration config, Logger logger ) throws RaplaException
    {
        enableLogging( logger );
        Locale locale;
        m_localeSelector =  (LocaleSelectorImpl) context.lookup( LocaleSelector.class ) ;
        if ( m_localeSelector != null )
        {
            m_localeSelector.addLocaleChangeListenerFirst( this );
            locale = m_localeSelector.getLocale();
            
        }
        else
        {
        	locale =  Locale.getDefault();
        }
        String filePath = config.getChild( "file" ).getValue( null );
        try
        {
        	InputStream resource;
            if ( filePath == null )
            {
                className = config.getChild( "classname" ).getValue( null );
                if ( className == null ) 
                {
                    className = config.getAttribute( "id" );
                }
                else
                {
                    className = className.trim();
                }
                String resourceFile = "" + className.replaceAll("\\.", "/") + ".xml";
                resource = getClass().getClassLoader().getResourceAsStream(resourceFile);
                dictionaryFile = resource != null ? resourceFile : null;
            }
            else
            {
            	File file = new File( filePath);
      		    getLogger().info( "getting lanaguageResources  from " + file.getCanonicalPath() );
      		    resource = new FileInputStream( file );
      		    dictionaryFile = filePath;
            }
            if ( resource != null)
            {
            	dict = new TranslationParser().parse( resource );
            	resource.close();
            }
            else
            {
            	dict = new RaplaDictionary(locale.getLanguage());
            }
        }
        catch ( Exception ex )
        {
            throw new RaplaException( ex );
        }
        setLocale( locale );
        try
        {
            parentId = getPack(locale).getString( TranslationParser.PARENT_BUNDLE_IDENTIFIER );
        }
        catch ( MissingResourceException ex )
        {
        }
    }

    public String getParentId()
    {
        return parentId;
    }

    public static Configuration createConfig( String resourceFile )
    {
        DefaultConfiguration config = new DefaultConfiguration( "component");
        config.setAttribute( "id", resourceFile.toString() );
        return config;
    }

    public void dispose()
    {
        if ( m_localeSelector != null )
            m_localeSelector.removeLocaleChangeListener( this );
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

    public ImageIcon getIcon( String key ) throws MissingResourceException
    {
    	ImageIcon icon = getPack(getLocale()).getIcon( key);
    	return icon;
    }

    public Locale getLocale()
    {
        if ( locale == null )
            throw new IllegalStateException( "Call setLocale first!" );
        return locale;
    }

    public String getLang()
    {
        if ( locale == null )
            throw new IllegalStateException( "Call setLocale first!" );
        return locale.getLanguage();
    }

    public String getString( String key ) throws MissingResourceException
    {
    	if ( locale == null )
             throw new IllegalStateException( "Call setLocale first!" );
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

    public void setLocale( Locale locale )
    {
    	this.locale = locale;
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
			if ( dictionaryFile == null )
			{
				pack.resourceBundle = new ResourceBundleLoader().loadResourceBundle( className, locale );
		    }
			packMap.put( locale, pack);
			return pack;
		}
	}

}

class ResourceBundleLoader 
{
	
    /** this method imitates the orginal
     * <code>ResourceBundle.getBundle(String className,Locale
     * locale)</code> which causes problems when the locale is changed
     * to the base locale (english). For a full description see
     * ResourceBundle.getBundle(String className) in the java-api.*/
    public ResourceBundle loadResourceBundle( String className, Locale locale ) throws MissingResourceException
    {
        String tries[] = new String[7];
        StringBuffer buf = new StringBuffer();
        tries[6] = className;
        buf.append( className );
        if ( locale.getLanguage().length() > 0 )
        {
            buf.append( '_' );
            buf.append( locale.getLanguage() );
            tries[2] = buf.toString();
        }
        if ( locale.getCountry().length() > 0 )
        {
            buf.append( '_' );
            buf.append( locale.getCountry() );
            tries[1] = buf.toString();
        }
        if ( locale.getVariant().length() > 0 )
        {
            buf.append( '_' );
            buf.append( locale.getVariant() );
            tries[0] = buf.toString();
        }
        buf.delete( className.length(), buf.length() - 1 );
        Locale defaultLocale = Locale.getDefault();
        if ( defaultLocale.getLanguage().length() > 0 )
        {
            buf.append( defaultLocale.getLanguage() );
            tries[5] = buf.toString();
        }
        if ( defaultLocale.getCountry().length() > 0 )
        {
            buf.append( '_' );
            buf.append( defaultLocale.getCountry() );
            tries[4] = buf.toString();
        }
        if ( defaultLocale.getVariant().length() > 0 )
        {
            buf.append( '_' );
            buf.append( defaultLocale.getVariant() );
            tries[3] = buf.toString();
        }

        ResourceBundle bundle = null;
        for ( int i = 0; i < tries.length; i++ )
        {
            if ( tries[i] == null )
                continue;
            bundle = loadBundle( tries[i] );
            if ( bundle != null )
            {
                loadParent( tries, i, bundle );
                return bundle;
            }
        }
        throw new MissingResourceException( "'" + className + "' not found. The resource-file is missing.", className,
                                            "" );
    }
    
    private ResourceBundle loadBundle( String name )
    {
        InputStream io = null;
        try
        {
            String pathName = getPropertyFileNameFromClassName( name );
            io = this.getClass().getResourceAsStream( pathName );
            if ( io != null )
            {
                return new PropertyResourceBundleWrapper(io , name);
            }
            ResourceBundle bundle = (ResourceBundle) this.getClass().getClassLoader().loadClass( name ).newInstance();
            return bundle;
        }
        catch ( Exception ex )
        {
            return null;
        }
        catch ( ClassFormatError ex )
        {
            return null;
        }
        finally
        {
            if ( io != null)
            {
                try {
                    io.close();
                } catch (IOException e) {
                    return null;
                }
            }
        }
    }

	private void loadParent( String[] tries, int i, ResourceBundle bundle )
    {
        ResourceBundle parent = null;
        if ( i == 0 || i == 3 )
        {
            parent = loadBundle( tries[i++] );
            if ( parent != null )
                setParent( bundle, parent );
            bundle = parent;
        }
        if ( i == 1 || i == 4 )
        {
            parent = loadBundle( tries[i++] );
            if ( parent != null )
                setParent( bundle, parent );
            bundle = parent;
        }
        if ( i == 2 || i == 5 )
        {
            parent = loadBundle( tries[6] );
            if ( parent != null )
                setParent( bundle, parent );
        }
    }

    private void setParent( ResourceBundle bundle, ResourceBundle parent )
    {
        try
        {
            Method method = bundle.getClass().getMethod( "setParent", new Class[]
                { ResourceBundle.class } );
            method.invoke( bundle, new Object[]
                { parent } );
        }
        catch ( Exception ex )
        {
        }
    }

    private String getPropertyFileNameFromClassName( String classname )
    {
        StringBuffer result = new StringBuffer( classname );
        for ( int i = 0; i < result.length(); i++ )
        {
            if ( result.charAt( i ) == '.' )
                result.setCharAt( i, '/' );
        }
        result.insert( 0, '/' );
        result.append( ".properties" );
        return result.toString();
    }
}
