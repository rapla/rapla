package org.rapla.framework;

import org.rapla.logger.Logger;

import java.net.URL;

public interface StartupEnvironment
{
    int CONSOLE = 1;
    int WEBSTART = 2;
    int APPLET = 3;

    URL getDownloadURL() throws RaplaException;
    
    /** either EMBEDDED, CONSOLE, WEBSTART, APPLET,SERVLET or CLIENT */
    int getStartupMode();

    Logger getBootstrapLogger();
}