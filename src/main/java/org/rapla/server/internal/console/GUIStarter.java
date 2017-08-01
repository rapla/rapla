package org.rapla.server.internal.console;

import org.rapla.ConnectInfo;
import org.rapla.client.ClientService;
import org.rapla.client.RaplaClientListenerAdapter;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;

import java.util.concurrent.Semaphore;

class GUIStarter
{
    protected ConnectInfo reconnect;
    protected Semaphore guiMutex = new Semaphore(1);
    ClientService client;
    protected Logger logger;
    Runnable shutdownCommand;
    protected String startupUser;
    
    public GUIStarter(Logger logger,String startupUser, Runnable shutdownCommand)
    {
        this.logger = logger;
        this.startupUser = startupUser;
        this.shutdownCommand = shutdownCommand;
    }
      
    
    protected void startGUI( ClientService raplaContainer, ConnectInfo connectInfo) throws Exception {
        try
        {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            try
            {
                Thread.currentThread().setContextClassLoader( ClassLoader.getSystemClassLoader());
                raplaContainer.addRaplaClientListener(new RaplaClientListenerAdapter() {
                         public void clientClosed(ConnectInfo reconnect) {
                             GUIStarter.this.reconnect = reconnect;
                             if ( reconnect != null) {
                                guiMutex.release();
                             } else {
                                 exit();
                             }
                         }
                        
                        public void clientAborted()
                        {
                            exit();
                        }
                     });
                raplaContainer.start(connectInfo);
            }
            finally
            {
                Thread.currentThread().setContextClassLoader( contextClassLoader);
            }
        }
        catch( Exception e )
        {
            logger.error("Could not start client", e);
            if ( raplaContainer != null)
            {
                raplaContainer.dispose();
            }
            throw new RaplaException( "Error during initialization see logs for details: " + e.getMessage(), e );
        }

    }

    protected void exit() {
        if ( client != null)
        {
            client.dispose();
        }
        GUIStarter.this.reconnect = null;
        guiMutex.release();
        if ( shutdownCommand != null)
        {
            shutdownCommand.run();
        }
       
    }

}