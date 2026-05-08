package org.rapla.framework;

import org.rapla.logger.Logger;

import java.net.URL;

public interface StartupEnvironment
{
    int CONSOLE = 1;
    int WEBSTART = 2;

    URL getDownloadURL() throws RaplaException;

    /** Either CONSOLE or WEBSTART. (Java applets unsupported since Java 11; APPLET removed.) */
    int getStartupMode();

    Logger getBootstrapLogger();
}