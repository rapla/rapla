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

import java.net.URL;

import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.logger.ConsoleLogger;
import org.rapla.logger.Logger;

final public class RaplaStartupEnvironment implements StartupEnvironment
{
    private int startupMode = CONSOLE;
    //private LoadingProgress progressbar;
    private Logger bootstrapLogger = new ConsoleLogger( ConsoleLogger.LEVEL_WARN );
    private URL downloadURL;

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

	public URL getDownloadURL() throws RaplaException
    {
	    return downloadURL;
    }

    public void setDownloadURL( URL downloadURL )
    {
        this.downloadURL = downloadURL;
    }

}
