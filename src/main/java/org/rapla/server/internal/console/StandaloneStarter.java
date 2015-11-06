package org.rapla.server.internal.console;

import org.rapla.ConnectInfo;
import org.rapla.RaplaStartupEnvironment;
import org.rapla.entities.User;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.logger.Logger;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.internal.RaplaJNDIContext;
import org.rapla.server.internal.RemoteAuthentificationServiceImpl;
import org.rapla.server.internal.ServerStarter;

import java.net.URL;

public class StandaloneStarter extends GUIStarter
{
    ServerStarter serverStarter;
    URL mockDownloadUrl;
    
    public StandaloneStarter(Logger logger, RaplaJNDIContext jndi, URL mockDownloadUrl) 
    {
        super(logger, jndi);
        serverStarter  = new ServerStarter(logger, jndi);
        this.mockDownloadUrl = mockDownloadUrl;
    }

    public void startStandalone( ) throws Exception 
    {
        ServerServiceContainer server = serverStarter.startServer();
        String username = startupUser != null ? startupUser:server.getFirstAdmin();
        ConnectInfo connectInfo =  new ConnectInfo(username, "".toCharArray());
        RaplaStartupEnvironment env = new RaplaStartupEnvironment();
        env.setStartupMode( StartupEnvironment.CONSOLE);
        env.setBootstrapLogger( logger);
        env.setDownloadURL( mockDownloadUrl );
        try {
            guiMutex.acquire();
        } catch (InterruptedException e) {
        }
        startStandaloneGUI(env, connectInfo, server);
        try {
            guiMutex.acquire();
            while ( reconnect != null )
            {
                client.dispose();
                server.dispose();
                try {
                    server = serverStarter.startServer();
                    if (  reconnect.getUsername() == null)
                    {
                        if ( startupUser != null)
                        {
                            reconnect= new ConnectInfo(startupUser, "".toCharArray());
                        }
                    } 
                    startStandaloneGUI(env, reconnect, server);
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

    private void startStandaloneGUI(RaplaStartupEnvironment env, ConnectInfo connectInfo, final ServerServiceContainer server) throws RaplaException, Exception {
        RemoteAuthentificationServiceImpl.setPasswordCheckDisabled(true);
        String reconnectUser = connectInfo.getConnectAs() != null ? connectInfo.getConnectAs() : connectInfo.getUsername();
        User user = server.getOperator().getUser(reconnectUser);
        if (user == null)
        {
            throw new RaplaException("Can't find user with username " + reconnectUser);
        }
        client = null;//new RaplaClientServiceImpl( env);
        startGUI( client,connectInfo);
    }

    protected void exit() {
        ServerServiceContainer server = serverStarter.getServer();
        if (server != null && server instanceof  Disposable)
        {
            ((Disposable)server).dispose();
        }
        super.exit();
       
    }

}