/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
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
package org.rapla.framework.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import org.rapla.components.util.Assert;
import org.rapla.components.util.JNLPUtil;
import org.rapla.components.util.xml.XMLReaderAdapter;
import org.rapla.framework.Configuration;
import org.rapla.framework.ConfigurationException;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/** Tools for configuring the rapla-system. */
public abstract class ConfigTools
{
    /** parse startup parameters. The parse format:
     <pre>
     [-?|-c PATH_TO_CONFIG_FILE] [ACTION]
     </pre>
     Possible map entries:
     <ul>
     <li>config: the config-file</li>
     <li>action: the start action</li>
     </ul>

     @return a map with the parameter-entries or  null if format is invalid or -? is used
     */
    public static Map<String,String> parseParams( String[] args )
    {
        boolean bInvalid = false;
        Map<String,String> map = new HashMap<String,String>();
        String config = null;
        String action = null;

        // Investigate the passed arguments
        for ( int i = 0; i < args.length; i++ )
        {
            if ( args[i].toLowerCase().equals( "-c" ) )
            {
                if ( i + 1 == args.length )
                {
                    bInvalid = true;
                    break;
                }
                config = args[++i];
                continue;
            }
            if ( args[i].toLowerCase().equals( "-?" ) )
            {
                bInvalid = true;
                break;
            }
            if ( args[i].toLowerCase().substring( 0, 1 ).equals( "-" ) )
            {
                bInvalid = true;
                break;
            }
            action = args[i].toLowerCase();
        }

        if ( bInvalid )
        {
            return null;
        }

        if ( config != null )
            map.put( "config", config );
        if ( action != null )
            map.put( "action", action );
        return map;
    }

    /** Creates a configuration from a URL.*/
    public static Configuration createConfig( String configURL ) throws RaplaException
    {
        try
        {
            URLConnection conn = new URL( configURL).openConnection();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream stream = conn.getInputStream();
            //System.out.println( "File Content:");
            int s= -1; 
            do {
                s = stream.read();
              //  System.out.print( (char)s );
                if ( s != -1)
                {
                    out.write(  s );
                }
            } while ( s != -1);
            out.close();
            stream.close();
            Assert.notNull( stream );
            InputStream in = new ByteArrayInputStream(out.toByteArray());
            XMLReader reader = XMLReaderAdapter.createXMLReader( false );
            SAXConfigurationHandler handler = new SAXConfigurationHandler();
            reader.setContentHandler( handler);
            reader.parse( new InputSource( in));
            Configuration config = handler.getConfiguration();
            in.close();
            Assert.notNull( config );
            return config;
        }
        catch ( org.xml.sax.SAXParseException ex )
        {
            throw new RaplaException( "Error parsing configuration "
                    + configURL
                    + " Line:"
                    + ex.getLineNumber()
                    + " Column:"
                    + ex.getColumnNumber()
                    + " "
                    + ex.getMessage() ,ex);
        }
        catch ( EOFException ex )
        {
            throw new RaplaException( "Can't load configuration-file at " + configURL );
        }
        catch ( Exception ex )
        {
            throw new RaplaException( ex );
        }
    }

    /** Creates an configuration URL from a configuration path.
     If path is null the URL of the defaultPropertiesFile
     will be returned.
     */
    public static URL configFileToURL( String path, String defaultPropertiesFile ) throws RaplaException
    {
        URL configURL = null;
        try
        {
            if ( path != null )
            {
                File file = new File( path );
                if ( file.exists() )
                {
                    configURL = ( file.getCanonicalFile() ).toURI().toURL();
                }
            }
            if ( configURL == null )
            {
                configURL = ConfigTools.class.getClassLoader().getResource( defaultPropertiesFile );
                if ( configURL == null )
                {
                    File file = new File( defaultPropertiesFile );
                    if ( !file.exists() )
                    {
                        file = new File( "war/WEB-INF/" + defaultPropertiesFile );
                    }
                    if ( !file.exists() )
                    {
                        file = new File( "war/webclient/" + defaultPropertiesFile );
                    }
                    if ( file.exists() )
                    {
                        configURL = file.getCanonicalFile().toURI().toURL();
                    }
                }
            }
        }
        catch ( MalformedURLException ex )
        {
            throw new RaplaException( "malformed config path" + path );
        }
        catch ( IOException ex )
        {
            throw new RaplaException( "Can't resolve config path" + path );
        }

        if ( configURL == null )
        {
            throw new RaplaException( defaultPropertiesFile
                    + " not found on classpath and in working folder "
                    + " Path config file with -c argument. "
                    + " For more information start rapla -? or read the api-docs." );
        }
        return configURL;
    }

    /** Creates an configuration URL from a configuration filename and
     the webstart codebae.
     If filename is null the URL of the defaultPropertiesFile
     will be returned.
     */
    public static URL webstartConfigToURL(  String defaultPropertiesFilename ) throws RaplaException
    {
        try
        {
            URL base = JNLPUtil.getCodeBase();
            URL configURL = new URL( base, defaultPropertiesFilename );
            return configURL;
        }
        catch ( Exception ex )
        {
            throw new RaplaException( "Can't get configuration file in webstart mode." );
        }
    }

    /** resolves a context value in the passed string. 
     If the string begins with <code>${</code> the method will lookup the String-Object in the context and returns it.
     If it doesn't, the method returns the unmodified string.
     Example:
     <code>resolveContext("${download-server}")</code> returns the same as
     context.get("download-server").toString();

     @throws ConfigurationException when no contex-object was found for the given variable.
     */
    public static String resolveContext( String s, RaplaContext context ) throws RaplaContextException
    {
       return resolveContextObject(s, context).toString();
    }
    
    public static Object resolveContextObject( String s, RaplaContext context ) throws RaplaContextException
    {
        StringBuffer value = new StringBuffer();
        s = s.trim();
        int startToken = s.indexOf( "${" );
        if ( startToken < 0 )
            return s;
        int endToken = s.indexOf( "}", startToken );
        String token = s.substring( startToken + 2, endToken );
        String preToken = s.substring( 0, startToken );
		String unresolvedRest = s.substring( endToken + 1 );
        TypedComponentRole<Object> untypedIdentifier = new TypedComponentRole<Object>(token);
        Object lookup = context.lookup( untypedIdentifier );
		if ( preToken.length() == 0 && unresolvedRest.length() == 0 )
		{
			return lookup;
		}
		String contextObject = lookup.toString();
		value.append( preToken );
        String stringRep = contextObject.toString();
        value.append( stringRep );
        
        Object resolvedRest = resolveContext(unresolvedRest, context );
        value.append( resolvedRest.toString());
        return value.toString();
    }

}
