package org.rapla.storage.dbrm;

import org.rapla.ConnectInfo;
import org.rapla.RaplaResources;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.rest.client.AuthenticationException;
import org.rapla.rest.client.CustomConnector;
import org.rapla.rest.client.SerializableExceptionInformation;
import org.rapla.rest.client.gwt.MockProxy;
import org.rapla.scheduler.CommandScheduler;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.IOException;

@DefaultImplementation(of=CustomConnector.class,context = InjectionContext.client)
public class MyCustomConnector implements CustomConnector
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
    //private final String errorString;
    private final CommandScheduler commandQueue;
    Provider<RaplaResources> i18n;

    @Inject public MyCustomConnector(RemoteConnectionInfo remoteConnectionInfo, Provider<RaplaResources> i18n,
            CommandScheduler commandQueue)
    {
        this.remoteConnectionInfo = remoteConnectionInfo;
        this.commandQueue = commandQueue;
        this.i18n = i18n;
    }

    @Override public String reauth(Class proxy) throws Exception
    {
        final boolean isAuthentificationService = proxy.getCanonicalName().contains(RemoteAuthentificationService.class.getCanonicalName());
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

    @Override public Exception deserializeException(SerializableExceptionInformation exe)
    {
        final String message = exe.getMessage();
        if (message.indexOf(RemoteStorage.USER_WAS_NOT_AUTHENTIFIED) >= 0 && remoteConnectionInfo != null)
        {
            return new AuthenticationException(message);
        }
        if ( exe.getExceptionClass().equals(org.rapla.rest.client.RaplaConnectException.class.getName()))
        {
            String server = remoteConnectionInfo.getServerURL();
            final RaplaResources raplaResources = i18n.get();
            String errorString = raplaResources.format("error.connect", server) + " ";
            return new RaplaConnectException(errorString + exe.getMessage());
        }
        RaplaException ex = new RaplaExceptionDeserializer().deserializeException(exe);
        return ex;
    }

    @Override public Class[] getNonPrimitiveClasses()
    {
        return new Class[] { RaplaMapImpl.class };
    }

    public Exception getConnectError(IOException ex)
    {
        String server = remoteConnectionInfo.getServerURL();
        final RaplaResources raplaResources = i18n.get();
        String errorString = raplaResources.format("error.connect", server) + " ";
        return new RaplaConnectException(errorString + ex.getMessage());
    }

    @Override public String getAccessToken()
    {
        return remoteConnectionInfo.getAccessToken();
    }

    @Override
    public String getFullQualifiedUrl(String relativePath)
    {
        return remoteConnectionInfo.getServerURL() + "/" + relativePath;
    }
}
