package org.rapla.server.internal.console;

import org.rapla.ConnectInfo;
import org.rapla.RaplaStartupEnvironment;
import org.rapla.client.ClientService;
import org.rapla.client.swing.internal.dagger.DaggerClientCreator;
import org.rapla.entities.User;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.logger.Logger;
import org.rapla.jsonrpc.client.gwt.MockProxy;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.internal.RemoteAuthentificationServiceImpl;
import org.rapla.server.internal.ServerContainerContext;
import org.rapla.server.internal.ServerStarter;
import org.rapla.storage.dbrm.RemoteConnectionInfo;

import java.net.URL;

public class StandaloneStarter extends GUIStarter
{
    ServerStarter serverStarter;
    URL mockDownloadUrl;

    private ClientService create(RaplaStartupEnvironment env) throws Exception
    {
        return DaggerClientCreator.create(env);
    }

    public StandaloneStarter(Logger logger, ServerContainerContext backendContext, URL mockDownloadUrl, String startupUser)
    {
        super(logger, startupUser, backendContext.getShutdownCommand());
        serverStarter  = new ServerStarter(logger, backendContext);
        this.mockDownloadUrl = mockDownloadUrl;
    }

    ServerServiceContainer server;
    public void startStandalone( ) throws Exception 
    {
        server = serverStarter.startServer();
        ConnectInfo connectInfo = getStartupConnectInfo();
        RemoteConnectionInfo.setMockProxy(new MockProxy()
        {
            @Override public <T> T create(Class<T> tClass, String accessToken)
            {
                return server.getMockService(tClass, accessToken);
            }

        });
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
                        reconnect= getStartupConnectInfo();
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

    private ConnectInfo getStartupConnectInfo()
    {
        String username = startupUser != null ? startupUser : server.getFirstAdmin();
        return new ConnectInfo(username, "".toCharArray());
    }

    private void startStandaloneGUI(RaplaStartupEnvironment env, ConnectInfo connectInfo, final ServerServiceContainer server) throws Exception {
        RemoteAuthentificationServiceImpl.setPasswordCheckDisabled(true);
        String reconnectUser = connectInfo.getConnectAs() != null ? connectInfo.getConnectAs() : connectInfo.getUsername();
        User user = server.getOperator().getUser(reconnectUser);
        if (user == null)
        {
            throw new RaplaException("Can't find user with username " + reconnectUser);
        }
        client = create( env );
        startGUI( client,connectInfo);
    }

    protected void exit() {
        ServerServiceContainer server = serverStarter.getServer();
        if (server != null && server instanceof  Disposable)
        {
            server.dispose();
        }
        super.exit();
       
    }

}