package org.rapla.server.internal;

import java.net.URL;

import org.rapla.ConnectInfo;
import org.rapla.RaplaStartupEnvironment;
import org.rapla.client.internal.RaplaClientServiceImpl;
import org.rapla.entities.User;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.SimpleProvider;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.logger.Logger;
import org.rapla.server.ServerServiceContainer;
import org.rapla.storage.dbrm.RemoteServiceCaller;

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
        ServerServiceImpl server = serverStarter.startServer();
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

    private void startStandaloneGUI(RaplaStartupEnvironment env, ConnectInfo connectInfo, final ServerServiceImpl server) throws RaplaException, Exception {
        final RemoteSessionImpl standaloneSession = new RemoteSessionImpl(logger);
        server.setPasswordCheckDisabled(true);
        String reconnectUser = connectInfo.getConnectAs() != null ? connectInfo.getConnectAs() : connectInfo.getUsername();
        User user = server.getOperator().getUser(reconnectUser);
        if (user == null)
        {
            throw new RaplaException("Can't find user with username " + reconnectUser);
        }
        standaloneSession.setUser( user);
        SimpleProvider<RemoteServiceCaller> provider = new SimpleProvider<RemoteServiceCaller>();
        provider.setValue( new RemoteServiceCaller() {
            @Override
            public <T> T getRemoteMethod(Class<T> a) throws RaplaContextException {
                return server.getRemoteMethod(a, standaloneSession);
            }
        });
        client = new RaplaClientServiceImpl( env, provider);
        startGUI( client,connectInfo);
    }

    protected void exit() {
        ServerServiceContainer server = serverStarter.getServer();
        if ( server != null)
        {
            server.dispose();
        }
        super.exit();
       
    }

}