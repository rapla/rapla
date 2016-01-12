package org.rapla.components.xmlbundle.impl;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class ResourceBundleLoader 
{
	
    /** this method imitates the orginal
     * <code>ResourceBundle.getBundle(String className,Locale
     * locale)</code> which causes problems when the locale is changed
     * to the base locale (english). For a full description see
     * ResourceBundle.getBundle(String className) in the java-api.*/
    static public ResourceBundle loadResourceBundle( String className, Locale locale ) throws MissingResourceException
    {
        String tries[] = new String[7];
        StringBuffer buf = new StringBuffer();
        tries[6] = className != null ? className : "";
        
        buf.append( className );
        if ( locale.getLanguage().length() > 0 )
        {
            if ( buf.length() > 0 )
            {
                buf.append( '_' );
            }
            buf.append( locale.getLanguage() );
            if(locale.getLanguage().equalsIgnoreCase("en")){
                tries[2] = buf.toString().substring(0, buf.toString().length()-3);
            }else{
                tries[2] = buf.toString();
            }
        }
        if ( locale.getCountry().length() > 0 )
        {
            if ( buf.length() > 0)
            {
                buf.append( '_' );
            }
            buf.append( locale.getCountry() );
            tries[1] = buf.toString();
        }
        if ( locale.getVariant().length() > 0 )
        {
            if ( buf.length() > 0)
            {
                buf.append( '_' );
            }
            buf.append( locale.getVariant() );
            tries[0] = buf.toString();
        }
        buf.delete( className.length(), buf.length() );
        Locale defaultLocale = Locale.getDefault();
        if ( defaultLocale.getLanguage().length() > 0 )
        {
            if ( buf.length() > 0)
            {
                buf.append( '_' );
            }
            buf.append( defaultLocale.getLanguage() );
            if(locale.getLanguage().equalsIgnoreCase("en")){
                tries[5] = buf.toString();
            }else{
                tries[5] = buf.toString().substring(0, buf.toString().length()-3);
            }
        }
        if ( defaultLocale.getCountry().length() > 0 )
        {
            if ( buf.length() > 0)
            {
                buf.append( '_' );
            }
            buf.append( defaultLocale.getCountry() );
            tries[4] = buf.toString();
        }
        if ( defaultLocale.getVariant().length() > 0 )
        {
            if ( buf.length() > 0)
            {
                buf.append( '_' );
            }
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
    
    static public ResourceBundle loadBundle( String name )
    {
        InputStream io = null;
        try
        {
            String pathName = getPropertyFileNameFromClassName( name );
            io = ResourceBundleLoader.class.getResourceAsStream( pathName );
            if ( io != null )
            {
                return new PropertyResourceBundleWrapper(io , name);
            }
            ResourceBundle bundle = (ResourceBundle) ResourceBundleLoader.class.getClassLoader().loadClass( name ).newInstance();
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

    static private void loadParent( String[] tries, int i, ResourceBundle bundle )
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

    static private void setParent( ResourceBundle bundle, ResourceBundle parent )
    {
        try
        {
            Method method = bundle.getClass().getMethod( "setParent", ResourceBundle.class);
            method.invoke( bundle, parent);
        }
        catch ( Exception ex )
        {
        }
    }

    static private String getPropertyFileNameFromClassName( String classname )
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