package org.rapla.storage.dbrm;

import org.rapla.ConnectInfo;
import org.rapla.RaplaResources;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.rest.SerializableExceptionInformation;
import org.rapla.rest.client.AuthenticationException;
import org.rapla.rest.client.CustomConnector;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.CompletablePromise;
import org.rapla.scheduler.Promise;

import javax.inject.Inject;
import javax.inject.Provider;

@DefaultImplementation(of=CustomConnector.class,context = InjectionContext.client)
public class MyCustomConnector implements CustomConnector
{
    private final RemoteConnectionInfo remoteConnectionInfo;
    private Provider<RemoteAuthentificationService> authentificationService;
    //private final String errorString;
    private final CommandScheduler commandQueue;
    Provider<RaplaResources> i18n;
    Logger logger;

    @Inject public MyCustomConnector(RemoteConnectionInfo remoteConnectionInfo, Provider<RaplaResources> i18n,Provider<RemoteAuthentificationService> authentificationService,
            CommandScheduler commandQueue, Logger logger)
    {
        this.remoteConnectionInfo = remoteConnectionInfo;
        this.authentificationService = authentificationService;
        this.commandQueue = commandQueue;
        this.i18n = i18n;
        this.logger = logger.getChildLogger("connector");
    }


    @Override public String reauth(Class proxy) throws Exception
    {
        final boolean isAuthentificationService = proxy.getCanonicalName().contains(RemoteAuthentificationService.class.getCanonicalName());
        if (isAuthentificationService )
        {
            return null;
        }

        // We dont reauth for authentification services
        final RemoteAuthentificationService remoteAuthentificationService = authentificationService.get();
        if ( remoteAuthentificationService == null)
        {
            return  null;
        }
        final ConnectInfo connectInfo = remoteConnectionInfo.connectInfo;
        final String username = connectInfo.getUsername();
        final String password = new String(connectInfo.getPassword());
        final String connectAs = connectInfo.getConnectAs();
        LoginCredentials credentials = new LoginCredentials(username,password, connectAs);
        final LoginTokens loginTokens = remoteAuthentificationService.login(username, password, connectAs);
        final String accessToken = loginTokens.getAccessToken();
        remoteConnectionInfo.setAccessToken( accessToken);
        return accessToken;
    }

    @Override public Exception deserializeException(SerializableExceptionInformation exe, int statusCode)
    {
        final String message = exe.getMessage();
        if (message != null && message.indexOf(RemoteStorage.USER_WAS_NOT_AUTHENTIFIED) >= 0 && remoteConnectionInfo != null)
        {
            return new AuthenticationException(message);
        }
        if ( exe.getExceptionClass().equals(org.rapla.rest.client.RemoteConnectException.class.getName()))
        {
            String server = remoteConnectionInfo.getServerURL();
            final RaplaResources raplaResources = i18n.get();
            String errorString = raplaResources.format("error.connect", server) + " ";
            return new RaplaConnectException(errorString + exe.getMessage());
        }
        final RaplaExceptionDeserializer raplaExceptionDeserializer = new RaplaExceptionDeserializer();
        RaplaException ex = raplaExceptionDeserializer.deserializeException(exe,statusCode);
        return ex;
    }


    @Override
    public <T> CompletablePromise<T> createCompletable()
    {
        return commandQueue.createCompletable();
    }

    @Override
    public <T> Promise<T> call(CommandScheduler.Callable<T> callable)
    {
        return commandQueue.supply( callable);
    }
    /*
    public Exception getConnectError(IOException ex)
    {
        String server = remoteConnectionInfo.getServerURL();
        final RaplaResources raplaResources = i18n.get();
        String errorString = raplaResources.format("error.connect", server) + " ";
        return new RaplaConnectException(errorString + ex.getMessage());
    }
    */

    @Override public String getAccessToken()
    {
        return remoteConnectionInfo.getAccessToken();
    }

    @Override
    public String getFullQualifiedUrl(String relativePath)
    {
        return remoteConnectionInfo.getServerURL() + "/" + relativePath;
    }

    @Override public Logger getLogger()
    {
        return logger;
    }


}
