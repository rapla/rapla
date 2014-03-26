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

package org.rapla;

import java.net.MalformedURLException;
import java.net.URL;

import org.rapla.components.util.IOUtil;
import org.rapla.components.util.JNLPUtil;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.internal.ConfigTools;
import org.rapla.framework.logger.ConsoleLogger;
import org.rapla.framework.logger.Logger;

final public class RaplaStartupEnvironment implements StartupEnvironment
{
    private int startupMode = CONSOLE;
    //private LoadingProgress progressbar;
    private Logger bootstrapLogger = new ConsoleLogger( ConsoleLogger.LEVEL_WARN );
    private URL configURL;
    private URL contextRootURL;
    private URL downloadURL;

    public Configuration getStartupConfiguration() throws RaplaException
    {
        return ConfigTools.createConfig( getConfigURL().toExternalForm() );
    }

    public URL getConfigURL() throws RaplaException
    {
        if ( configURL != null )
        {
            return configURL;
        }
        else
        {
            return ConfigTools.configFileToURL( null, "rapla.xconf" );
        }
    }

    public Logger getBootstrapLogger()
    {
        return bootstrapLogger;
    }

    public void setStartupMode( int startupMode )
    {
        this.startupMode = startupMode;
    }

    /* (non-Javadoc)
     * @see org.rapla.framework.IStartupEnvironment#getStartupMode()
     */
    public int getStartupMode()
    {
        return startupMode;
    }

    public void setBootstrapLogger( Logger logger )
    {
        bootstrapLogger = logger;
    }

    public void setConfigURL( URL configURL )
    {
        this.configURL = configURL;
    }

    public URL getContextRootURL() throws RaplaException
    {
        if ( contextRootURL != null )
            return contextRootURL;
        return IOUtil.getBase( getConfigURL() );
    }

    public void setContextRootURL(URL contextRootURL) 
    {
		this.contextRootURL = contextRootURL;
	}

	public URL getDownloadURL() throws RaplaException
    {
        if ( downloadURL != null )
        {
            return downloadURL;
        }
        if ( startupMode == WEBSTART )
        {
            try
            {
                return JNLPUtil.getCodeBase();
            }
            catch ( Exception e )
            {
                throw new RaplaException( e );
            }
        }
        else
        {
        	URL base = IOUtil.getBase( getConfigURL() );
            if ( base != null)
            {
            	return base;
            }
        	try
            {
                return new URL( "http://localhost:8051" );
            }
            catch ( MalformedURLException e )
            {
                throw new RaplaException( "Invalid URL" );
            }
        }
    }

    public void setDownloadURL( URL downloadURL )
    {
        this.downloadURL = downloadURL;
    }

}
