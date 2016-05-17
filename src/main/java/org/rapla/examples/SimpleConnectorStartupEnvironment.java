package org.rapla.examples;

import java.net.MalformedURLException;
import java.net.URL;

import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.logger.Logger;

/**
 * Startup environment that creates an Facade Object to communicate with an
 * rapla server instance.
 */
public class SimpleConnectorStartupEnvironment implements StartupEnvironment
{
    URL server;
    Logger logger;
    
    public SimpleConnectorStartupEnvironment(final String host, final Logger logger) throws MalformedURLException
    {
        this(host, 8051, "/", false, logger);
    }

    public SimpleConnectorStartupEnvironment(final String host, final int hostPort, String contextPath, boolean isSecure, final Logger logger) throws MalformedURLException {
        this.logger = logger;
        String protocoll = "http";
        if (isSecure)
        {
            protocoll = "https";
        }
        if (!contextPath.startsWith("/"))
        {
            contextPath = "/" + contextPath;
        }
        if (!contextPath.endsWith("/"))
        {
            contextPath = contextPath + "/";
        }
        server = new URL(protocoll, host, hostPort, contextPath);
    }

    public int getStartupMode()
    {
        return CONSOLE;
    }

    public Logger getBootstrapLogger()
    {
        return logger;
    }

    public URL getDownloadURL() throws RaplaException
    {
        return server;
    }

}
