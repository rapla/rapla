package org.rapla.examples;

import java.net.MalformedURLException;
import java.net.URL;

import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.logger.Logger;

/** Startup environment that creates an Facade Object to communicate with an rapla server instance.
 */
public class SimpleConnectorStartupEnvironment implements StartupEnvironment
{
    DefaultConfiguration config;
    URL server;
    Logger logger;
    public SimpleConnectorStartupEnvironment(final String host, final Logger logger) throws MalformedURLException 
    {
        this( host, 8051, "/",false, logger);
    }
   
    public SimpleConnectorStartupEnvironment(final String host, final int hostPort, String contextPath,boolean isSecure, final Logger logger) throws MalformedURLException {
        this.logger = logger;

        config = new DefaultConfiguration("rapla-config");
        final DefaultConfiguration facadeConfig = new DefaultConfiguration("facade");
        facadeConfig.setAttribute("id","facade");
        final DefaultConfiguration remoteConfig = new DefaultConfiguration("remote-storage");
        remoteConfig.setAttribute("id","remote");

        DefaultConfiguration serverHost =new DefaultConfiguration("server");
        serverHost.setValue( "${download-url}" );
        remoteConfig.addChild( serverHost );

        config.addChild( facadeConfig );
        config.addChild( remoteConfig );
      
        String protocoll = "http";
        if ( isSecure )
        {
            protocoll = "https";
        }
        if ( !contextPath.startsWith("/"))
        {
            contextPath = "/" + contextPath ;
        }
        if ( !contextPath.endsWith("/"))
        {
            contextPath = contextPath + "/";
        }
        server = new URL(protocoll,host, hostPort, contextPath);
     }


    public Configuration getStartupConfiguration() throws RaplaException
    {
        return config;
    }

    public int getStartupMode()
    {
        return CONSOLE;
    }

    public URL getContextRootURL() throws RaplaException
    {
        return null;
    }

    public Logger getBootstrapLogger()
    {
        return logger;
    }


    public URL getDownloadURL() throws RaplaException
    {
        return server;
    }

	public URL getConfigURL() throws RaplaException {
		return null;
	}


}
