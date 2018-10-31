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

import org.rapla.components.util.JNLPUtil;
import org.rapla.components.util.xml.RaplaContentHandler;
import org.rapla.components.util.xml.RaplaErrorHandler;
import org.rapla.components.util.xml.RaplaNonValidatedInput;
import org.rapla.components.util.xml.RaplaSAXHandler;
import org.rapla.components.util.xml.XMLReaderAdapter;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

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
        Map<String,String> map = new HashMap<>();
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
//    public static Configuration createConfig( String configURL ) throws RaplaException
//    {
//        try
//        {
//            URLConnection conn = new URL( configURL).openConnection();
//            InputStreamReader stream = new InputStreamReader(conn.getInputStream());
//            StringBuilder builder = new StringBuilder();
//            //System.out.println( "File Content:");
//            int s= -1; 
//            do {
//                s = stream.read();
//              //  System.out.print( (char)s );
//                if ( s != -1)
//                {
//                    builder.append( (char) s );
//                }
//            } while ( s != -1);
//            stream.close();
//            Assert.notNull( stream );
//            Logger TEST_LOGGER = new NullLogger();
//			RaplaNonValidatedInput parser = new RaplaReaderImpl();
//			final SAXConfigurationHandler handler = new SAXConfigurationHandler();
//			String xml = builder.toString();
//			parser.read(xml, handler, TEST_LOGGER);
//            Configuration config = handler.getConfiguration();
//            Assert.notNull( config );
//            return config;
//        }
//        catch ( EOFException ex )
//        {
//            throw new RaplaException( "Can't load configuration-file at " + configURL );
//        }
//        catch ( Exception ex )
//        {
//            throw new RaplaException( ex );
//        }
//    }
    
    static public class RaplaReaderImpl implements RaplaNonValidatedInput
    {
		public void read(String xml, RaplaSAXHandler handler, Logger logger) throws RaplaException
		{
		    InputSource source = new InputSource( new StringReader(xml));
	        try {
	        	XMLReader reader = XMLReaderAdapter.createXMLReader(false);
	        	reader.setContentHandler( new RaplaContentHandler(handler));
	        	reader.parse(source );
	        	reader.setErrorHandler( new RaplaErrorHandler( logger));
	        } catch (SAXException ex) {
	            Throwable cause = ex.getException();
	            if (cause instanceof SAXParseException) {
	                ex = (SAXParseException) cause;
	                cause = ex.getException();
	            }
	            if (ex instanceof SAXParseException) {
	                throw new RaplaException("Line: " + ((SAXParseException)ex).getLineNumber()
	                                         + " Column: "+ ((SAXParseException)ex).getColumnNumber() + " "
	                                         +  ((cause != null) ? cause.getMessage() : ex.getMessage())
	                                         ,(cause != null) ? cause : ex );
	            }
	            if (cause == null) {
	                throw new RaplaException( ex);
	            }
	            if (cause instanceof RaplaException)
	                throw (RaplaException) cause;
	            else
	                throw new RaplaException( cause);
	        } catch (IOException ex) {
	        	throw new RaplaException( ex.getMessage(), ex);
	        }
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
            throw new RaplaException( "Can't get configuration file in webstart mode.", ex );
        }
    }

}
