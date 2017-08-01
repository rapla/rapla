package org.rapla.server.internal.console;

import org.rapla.ConnectInfo;
import org.rapla.RaplaStartupEnvironment;
import org.rapla.client.ClientService;
import org.rapla.client.swing.internal.dagger.DaggerClientCreator;
import org.rapla.framework.StartupEnvironment;
import org.rapla.logger.Logger;

import java.net.URL;

public class ClientStarter extends GUIStarter
{
    URL downloadUrl_;

    private ClientService create(RaplaStartupEnvironment env) throws Exception
    {
        return DaggerClientCreator.create(env);
    }

    
    public ClientStarter(Logger logger,String startupUser, Runnable shutdownCommand, URL downloadUrl) throws Exception
    {
        super(logger, startupUser, shutdownCommand);
        this.downloadUrl_ = downloadUrl;
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