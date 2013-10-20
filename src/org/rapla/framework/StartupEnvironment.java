package org.rapla.framework;

import java.net.URL;

import org.rapla.framework.logger.Logger;

public interface StartupEnvironment
{
    int CONSOLE = 1;
    int WEBSTART = 2;
    int APPLET = 3;

    Configuration getStartupConfiguration() throws RaplaException;

    URL getDownloadURL() throws RaplaException;
    URL getConfigURL() throws RaplaException; 
    
    /** either EMBEDDED, CONSOLE, WEBSTART, APPLET,SERVLET or CLIENT */
    int getStartupMode();

    URL getContextRootURL() throws RaplaException;

    Logger getBootstrapLogger();
}