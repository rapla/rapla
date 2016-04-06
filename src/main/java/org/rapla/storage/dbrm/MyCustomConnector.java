package org.rapla.storage.dbrm;

import org.rapla.ConnectInfo;
import org.rapla.RaplaResources;
import org.rapla.components.util.CommandScheduler;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.jsonrpc.client.gwt.MockProxy;
import org.rapla.jsonrpc.client.swing.BasicRaplaHTTPConnector;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;

@DefaultImplementation(of=BasicRaplaHTTPConnector.CustomConnector.class,context = InjectionContext.swing)
public class MyCustomConnector implements BasicRaplaHTTPConnector.CustomConnector
{
    private final RemoteConnectionInfo remoteConnectionInfo;

    public RemoteAuthentificationService getAuthentificationService()
    {
        return authentificationService;
    }

    public void setAuthentificationService(RemoteAuthentificationService authentificationService)
    {
        this.authentificationService = authentificationService;
    }

    private RemoteAuthentificationService authentificationService;
    private final String errorString;
    private final CommandScheduler commandQueue;

    @Inject public MyCustomConnector(RemoteConnectionInfo remoteConnectionInfo,  RaplaResources i18n,
            CommandScheduler commandQueue)
    {
        this.remoteConnectionInfo = remoteConnectionInfo;
        String server = remoteConnectionInfo.getServerURL();
        this.errorString = i18n.format("error.connect", server) + " ";
        this.commandQueue = commandQueue;
    }

    @Override public String reauth(BasicRaplaHTTPConnector proxy) throws Exception
    {
        final boolean isAuthentificationService = proxy.getClass().getCanonicalName().contains(RemoteAuthentificationService.class.getCanonicalName());
        // We dont reauth for authentification services
        if (isAuthentificationService || authentificationService == null)
        {
            return null;
        }
        final ConnectInfo connectInfo = remoteConnectionInfo.connectInfo;
        final String username = connectInfo.getUsername();
        final String password = new String(connectInfo.getPassword());
        final String connectAs = connectInfo.getConnectAs();
        final LoginTokens loginTokens = authentificationService.login(username, password, connectAs);
        final String accessToken = loginTokens.getAccessToken();
        return accessToken;
    }

    @Override public Exception deserializeException(String classname, String message, List<String> params)
    {
        if (message.indexOf(RemoteStorage.USER_WAS_NOT_AUTHENTIFIED) >= 0 && remoteConnectionInfo != null)
        {
            return new BasicRaplaHTTPConnector.AuthenticationException(message);
        }
        RaplaException ex = new RaplaExceptionDeserializer().deserializeException(classname, message.toString(), params);
        return ex;
    }

    @Override public Class[] getNonPrimitiveClasses()
    {
        return new Class[] { RaplaMapImpl.class };
    }

    @Override public Exception getConnectError(IOException ex)
    {
        return new RaplaConnectException(errorString + ex.getMessage());
    }

    @Override public Executor getScheduler()
    {
        return commandQueue;
    }

    @Override public String getAccessToken()
    {
        return remoteConnectionInfo.getAccessToken();
    }

    @Override public MockProxy getMockProxy()
    {
        return remoteConnectionInfo.getMockProxy();
    }
}
