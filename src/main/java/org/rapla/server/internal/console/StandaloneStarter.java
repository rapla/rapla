package org.rapla.server.internal.console;

import org.rapla.ConnectInfo;
import org.rapla.RaplaStartupEnvironment;
import org.rapla.client.ClientService;
import org.rapla.client.swing.internal.dagger.DaggerClientCreator;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.logger.Logger;
import org.rapla.rest.client.swing.JavaClientServerConnector;
import org.rapla.rest.client.swing.JsonRemoteConnector;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.internal.RemoteAuthentificationServiceImpl;
import org.rapla.server.internal.ServerContainerContext;
import org.rapla.server.internal.ServerStarter;

import java.lang.reflect.Method;
import java.net.URL;

public class StandaloneStarter extends GUIStarter
{
    URL mockDownloadUrl;
    final JsonRemoteConnector jsonRemoteConnector;
    ServerStarter serverStarter;
    final Method requestFinished;

    private ClientService create(RaplaStartupEnvironment env) throws Exception
    {
        return DaggerClientCreator.create(env);
    }

    public StandaloneStarter(Logger logger, ServerContainerContext backendContext, ServerStarter serverStarter, URL mockDownloadUrl, String startupUser,
            Object localconnector)
    {
        super(logger, startupUser, backendContext.getShutdownCommand());
        this.serverStarter = serverStarter;
        this.mockDownloadUrl = mockDownloadUrl;
        String LocalConnectorC = "org.eclipse.jetty.server.LocalConnector";
        String StandaloneConnectorC = "org.rapla.server.connector.StandaloneConnector";
        final ClassLoader classLoader = this.getClass().getClassLoader();
        try
        {
            final Class<?> LocalConnectorClass = classLoader.loadClass(LocalConnectorC);
            final Class<?> StandaloneConnectorClass = classLoader.loadClass(StandaloneConnectorC);
            requestFinished = StandaloneConnectorClass.getMethod("requestFinished");
            jsonRemoteConnector = (JsonRemoteConnector) StandaloneConnectorClass.getConstructor(LocalConnectorClass).newInstance(localconnector);
        }
        catch (Exception e)
        {
            throw new RaplaInitializationException(e);
        }
    }

    public void startClient() throws Exception
    {
        //((org.eclipse.jetty.server.LocalConnector) localconnector).start();
        JavaClientServerConnector.setJsonRemoteConnector(jsonRemoteConnector);
        final ConnectInfo connectInfo = getStartupConnectInfo();
        final RaplaStartupEnvironment env = new RaplaStartupEnvironment();
        env.setStartupMode(StartupEnvironment.CONSOLE);
        env.setBootstrapLogger(logger);
        env.setDownloadURL(mockDownloadUrl);
        Thread thread = new Thread(() -> {
            try
            {
                try
                {
                    guiMutex.acquire();
                }
                catch (InterruptedException e)
                {
                }
                {
                    ServerServiceContainer server = serverStarter.getServer();
                    startStandaloneGUI(env, connectInfo, server);
                }
                try
                {
                    guiMutex.acquire();
                    while (reconnect != null)
                    {
                        client.dispose();
                        try
                        {
                            if (reconnect.getUsername() == null)
                            {
                                reconnect = getStartupConnectInfo();
                            }
                            ServerServiceContainer server = serverStarter.getServer();
                            startStandaloneGUI(env, reconnect, server);
                            guiMutex.acquire();
                        }
                        catch (Exception ex)
                        {
                            logger.error("Error restarting client", ex);
                            exit();
                            return;
                        }
                    }
                }
                catch (InterruptedException e)
                {

                }
            }
            catch (Exception e)
            {
                logger.error(e.getMessage(), e);
            }
        });
        thread.setDaemon(true);
        thread.start();

    }

    private ConnectInfo getStartupConnectInfo() throws RaplaException
    {
        String username = startupUser != null ? startupUser : serverStarter.getServer().getFirstAdmin();
        return new ConnectInfo(username, "".toCharArray());
    }

    private void startStandaloneGUI(RaplaStartupEnvironment env, ConnectInfo connectInfo, final ServerServiceContainer server) throws Exception
    {
        RemoteAuthentificationServiceImpl.setPasswordCheckDisabled(true);
        String reconnectUser = connectInfo.getConnectAs() != null ? connectInfo.getConnectAs() : connectInfo.getUsername();
        User user = server.getOperator().getUser(reconnectUser);
        if (user == null)
        {
            throw new RaplaException("Can't find user with username " + reconnectUser);
        }
        client = create(env);
        startGUI(client, connectInfo);
    }

    protected void exit()
    {
        final ServerServiceContainer server = serverStarter.getServer();
        if (server != null)
        {
            server.dispose();
        }
        super.exit();

    }

    public void requestFinished()
    {
        try
        {
            requestFinished.invoke(jsonRemoteConnector);
        }
        catch (Exception e)
        {
            logger.error(e.getMessage(), e);
        }
    }
}