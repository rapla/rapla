/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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

    Map<String,String> stringCache = Collections.synchronizedMap( new TreeMap<String,String>() );
    Map<String,Icon> iconCache = Collections.synchronizedMap( new TreeMap<String,Icon>() );
    ResourceBundle resourceBundle;
    String dictionaryFile;
    RaplaDictionary dict;
    
    String parentId = null;

    /**
     * @throws RaplaException when the resource-file is missing or can't be accessed
     or can't be parsed
     */
    public I18nBundleImpl( RaplaContext context, Configuration config, Logger logger ) throws RaplaException
    {
        enableLogging( logger );
        dictionaryFile = config.getChild( "file" ).getValue( null );
        try
        {
            if ( dictionaryFile == null )
            {
                className = config.getChild( "classname" ).getValue( null );
                if ( className == null )
                    className = config.getAttribute( "id" );
                else
                    className = className.trim();
            }

            if ( dictionaryFile != null )
            {
            	String path = new File( dictionaryFile ).getCanonicalPath();
      		    getLogger().info( "getting lanaguageResources  from " + path );
      		    FileInputStream in = new FileInputStream( new File( dictionaryFile ) );
      			dict = new TranslationParser().parse( in );
      			in.close();
            }
        }
        catch ( Exception ex )
        {
            throw new RaplaException( ex );
        }

        m_localeSelector =  (LocaleSelectorImpl) context.lookup( LocaleSelector.class ) ;

        if ( m_localeSelector != null )
        {
            m_localeSelector.addLocaleChangeListenerFirst( this );
            setLocale( m_localeSelector.getLocale() );
        }
        else
        {
            setLocale( Locale.getDefault() );
        }

        try
        {
            parentId = lookup( TranslationParser.PARENT_BUNDLE_IDENTIFIER );
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

    /*
     private void init(I18nBundle parentBundle,LocaleSelector  ) {
     if (m_localeSelector != null) {
     m_localeSelector.addLocaleChangeListenerFirst(this);
     setLocale(m_localeSelector.getLocale());
     } else {
     setLocale(Locale.getDefault());
     }
     }
     */

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

    public String format( String key, Object[] obj )
    {
        MessageFormat msg = new MessageFormat( getString( key ) );
        return msg.format( obj );
    }

    private final byte[] loadResource( String fileName ) throws IOException
    {
        return IOUtil.readBytes( getResourceFromFile( fileName ) );
    }

    private URL getResourceFromFile( String fileName ) throws IOException
    {
        URL resource = null;
        String base;
        if ( dict == null )
        {
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

    public URL getResource( String key ) throws MissingResourceException
    {
        String resourceFile;
        try
        {
            resourceFile = lookup( key );
        }
        catch ( MissingResourceException ex )
        {
            throw ex;
        }
        try
        {
            return getResourceFromFile( resourceFile );
        }
        catch ( Exception ex )
        {
            String message = "Resourcefile " + resourceFile + " not found: " + ex.getMessage();
            getLogger().error( message );
            throw new MissingResourceException( message, className, key );
        }

    }

    public ImageIcon getIcon( String key ) throws MissingResourceException
    {
        String iconfile; 
        try
        {
            iconfile = lookup( key );
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

    /** this method imitates the orginal
     * <code>ResourceBundle.getBundle(String className,Locale
     * locale)</code> which causes problems when the locale is changed
     * to the base locale (english). For a full description see
     * ResourceBundle.getBundle(String className) in the java-api.*/
    protected ResourceBundle loadResourceBundle( String className, Locale locale )
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
            getLogger().debug( "Trying to load bundle " + name );
            String pathName = getPropertyFileNameFromClassName( name );
            io = this.getClass().getResourceAsStream( pathName );
            if ( io != null )
            {
                return new PropertyResourceBundleWrapper(io , name);
            }
            ResourceBundle bundle = (ResourceBundle) this.getClass().getClassLoader().loadClass( name ).newInstance();
            getLogger().debug( "Bundle found " + name );
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

   

  
    private String lookup( String key ) throws MissingResourceException
    {
        if ( dict == null )
        {
            return resourceBundle.getString( key );
        }
        else
        {
            String result = dict.lookup( key, getLang() );
            if ( result != null )
                return result;
            String message = "Can't find resourcestring " + key + " in class " + className;
            throw new MissingResourceException( message, className, key );
        } // end of else
    }

    public String getString( String key ) throws MissingResourceException
    {
        String result =  stringCache.get( key );
        if ( result != null )
            return result;
        result = getUncachedString( key );
        stringCache.put( key, result );
        return result;
    }

    private String getUncachedString( String key ) throws MissingResourceException
    {
        String result;
        try
        {
            result = lookup( key );
        }
        catch ( MissingResourceException ex )
        {
            throw ex;
        }

        if ( getLogger() != null && getLogger().isDebugEnabled() )
            getLogger().debug( "string requested: " + result );

        return filterXHTML( result );
    }


    /* replaces XHTML with HTML because swing can't display proper XHTML*/
    String filterXHTML( String text )
    {
        if ( text.indexOf( "<br/>" ) >= 0 )
        {
            return applyXHTMLFilter( text );
        }
        else
        {
            return text;
        } // end of else
    }

    public static String replaceAll( String text, String token, String with )
    {
        StringBuffer buf = new StringBuffer();
        int i = 0;
        int lastpos = 0;
        while ( ( i = text.indexOf( token, lastpos ) ) >= 0 )
        {
            if ( i > 0 )
                buf.append( text.substring( lastpos, i ) );
            buf.append( with );
            i = ( lastpos = i + token.length() );
        } // end of if ()
        buf.append( text.substring( lastpos, text.length() ) );
        return buf.toString();
    }

    private String applyXHTMLFilter( String text )
    {
        return replaceAll( text, "<br/>", "<br></br>" );
    }

    public void setLocale( Locale locale )
    {
         this.locale = locale;
        stringCache.clear();
        iconCache.clear();
        getLogger().debug( "Locale changed to " + locale );
        if ( dict == null )
        {
            try
            {
            	resourceBundle = loadResourceBundle( className, locale );
            }
            catch ( MissingResourceException ex)
            {
            	try {
            		dictionaryFile = "" + className.replaceAll("\\.", "/") + ".xml";
            		InputStream resource = getClass().getClassLoader().getResourceAsStream(dictionaryFile);
					if ( resource == null)
					{
						throw ex;
					}
            		dict = new TranslationParser().parse(  resource );
            		resource.close();
				} catch (Exception e) {
					getLogger().error("Can't parse file", e);
					throw ex;
				}
            }
            	
           
            //resourceBundle = ResourceBundle.getBundle(className, locale);
        }
    }

}
