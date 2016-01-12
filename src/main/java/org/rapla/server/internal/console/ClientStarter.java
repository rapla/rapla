package org.rapla.server.internal.console;

import org.rapla.ConnectInfo;
import org.rapla.RaplaStartupEnvironment;
import org.rapla.client.ClientService;
import org.rapla.client.swing.internal.dagger.DaggerClientCreator;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.logger.Logger;
import org.rapla.server.internal.RaplaJNDIContext;

import java.net.URL;
import java.util.Collection;

public class ClientStarter extends GUIStarter
{
    URL downloadUrl_;

    private ClientService create(RaplaStartupEnvironment env) throws Exception
    {
        return DaggerClientCreator.create(env);
    }

    
    public ClientStarter(Logger logger,RaplaJNDIContext jndi, String servletContextPath) throws Exception
    {
        super(logger, jndi);
        Collection<String> instanceCounter = null;
        String selectedContextPath = null;
        @SuppressWarnings("unchecked")
        Collection<String> instanceCounterLookup = (Collection<String>)  jndi.lookup("rapla_instance_counter", false);
        instanceCounter = instanceCounterLookup;
        selectedContextPath = jndi.lookupEnvString("rapla_startup_context", false);

        String contextPath = servletContextPath;
        if ( !contextPath.startsWith("/"))
        {
            contextPath = "/" + contextPath;
        }
        // don't startup server if contextPath is not selected
        if ( selectedContextPath != null)
        {
            if( !contextPath.equals(selectedContextPath))
                return;
        }
        else if ( instanceCounter != null)
        {
            instanceCounter.add( contextPath);
            if ( instanceCounter.size() > 1)
            {
                String msg = ("Ignoring webapp ["+ contextPath +"]. Multiple context found in jetty container " + instanceCounter + " You can specify one via -Dorg.rapla.context=REPLACE_WITH_CONTEXT");
                logger.error(msg);
                return;
            }
        }
        Integer port = null;
        String downloadUrl = null;
        if ( jndi.hasContext())
        {
            port = (Integer) jndi.lookup("rapla_startup_port", false);
            downloadUrl = (String) jndi.lookup("rapla_download_url", false);
        } 
        if ( port == null && downloadUrl == null)
        {
            throw new RaplaException("Neither port nor download url specified in enviroment! Can't start client");
        }
        if ( downloadUrl == null)
        {
            String url = "http://localhost:" + port+ contextPath;
            if (! url.endsWith("/"))
            {
                url += "/";
            }
            downloadUrl_ = new URL(url);
        }
        else
        {
            downloadUrl_ = new URL(downloadUrl);
        }

    }
    
    public void startClient() throws Exception
    {
            
        ConnectInfo connectInfo =  startupUser != null ? new ConnectInfo(startupUser, "".toCharArray()): null;
        try {
            guiMutex.acquire();
        } catch (InterruptedException e) {
        }
        RaplaStartupEnvironment env = new RaplaStartupEnvironment();
        env.setStartupMode( StartupEnvironment.CONSOLE);
        env.setBootstrapLogger( logger);
        env.setDownloadURL( downloadUrl_ );
        try
        {
            client = create(env);
            startGUI(  client, connectInfo);
            try {
                guiMutex.acquire();
                while ( reconnect != null )
                {
                     client.dispose();
                     try {
                         client = create(env);
                         startGUI(  client, reconnect);
                         guiMutex.acquire();
                     } catch (Exception ex) {
                         logger.error("Error restarting client",ex);
                         exit();
                         return;
                     }
                }
            } catch (InterruptedException e) {
                
            }
        }
        catch (Exception ex)
        {
            exit();
            throw ex;
        }
    }


}