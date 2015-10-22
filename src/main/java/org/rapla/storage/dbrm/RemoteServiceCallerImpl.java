package org.rapla.storage.dbrm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.rapla.ConnectInfo;
import org.rapla.RaplaResources;
import org.rapla.components.util.CommandScheduler;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.gwtjsonrpc.client.impl.EntryPointFactory;
import org.rapla.gwtjsonrpc.common.FutureResult;
import org.rapla.rest.client.*;
import org.rapla.rest.client.BasicRaplaHTTPConnector.CustomConnector;
import org.rapla.rest.client.RaplaConnectException;
import org.rapla.storage.dbrm.StatusUpdater.Status;

import javax.inject.Inject;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

public class RemoteServiceCallerImpl implements RemoteServiceCaller
{
    final CommandScheduler commandQueue;
    final Logger callLogger;
    final RemoteConnectionInfo remoteConnectionInfo;
    CustomConnector connector;
    
    @Inject
    public RemoteServiceCallerImpl(CommandScheduler commandQueue, Logger callLogger, RemoteConnectionInfo remoteConnectionInfo, RaplaResources i18n) {
        super();
        BasicRaplaHTTPConnector.setServiceEntryPointFactory(new EntryPointFactory()
        {
            @Override public String getEntryPoint(String interfaceName, String relativePath)
            {
                final String url = remoteConnectionInfo.getServerURL() + "rapla/json/" + (relativePath != null ? relativePath : interfaceName);
                return url;
            }
        });
        this.commandQueue = commandQueue;
        this.callLogger = callLogger;
        this.remoteConnectionInfo = remoteConnectionInfo;
        String server = remoteConnectionInfo.getServerURL();
        String errorString = i18n.format("error.connect", server) + " ";
        connector = new CustomConnector()
        {
            @Override public String reauth(BasicRaplaHTTPConnector proxy) throws Exception
            {
                return null;
            }

            @Override public Exception deserializeException(String classname, String message, List<String> params)
            {
                if (message.indexOf(RemoteStorage.USER_WAS_NOT_AUTHENTIFIED) >= 0 && remoteConnectionInfo != null)
                {
                          throw new BasicRaplaHTTPConnector.AuthenticationException(message);
                }
                RaplaException ex = new RaplaExceptionDeserializer().deserializeException(classname, message.toString(), params);

                return ex;
            }

            @Override public Class[] getNonPrimitiveClasses()
            {
                return new Class[] {RaplaMapImpl.class};
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
        };
    }

    class ReconnectInfo
    {
        Class service;
        Method method;
        Object[] args;
    }

    ReconnectInfo reconnectInfo;

    public void setReAuthentication(Class service, Method method, Object[] args)
    {
        reconnectInfo = new ReconnectInfo();
        reconnectInfo.service = service;
        reconnectInfo.method = method;
        reconnectInfo.args = args;
    }
    
    public <T> T getRemoteMethod(final Class<T> a) throws RaplaContextException  
    {
        try
        {
            final Class<? extends BasicRaplaHTTPConnector> aClass = (Class<? extends BasicRaplaHTTPConnector>) Class.forName(a.getCanonicalName() + "_JavaJsonProxy");
            final Constructor<? extends BasicRaplaHTTPConnector> constructor = aClass.getConstructor(CustomConnector.class);
            final BasicRaplaHTTPConnector basicRaplaHTTPConnector = constructor.newInstance(connector);
            return (T) basicRaplaHTTPConnector;
        }
        catch (Exception ex)
        {
             throw new RaplaContextException(ex.getMessage(), ex);
        }

//        InvocationHandler proxy = new InvocationHandler()
//        {
////                RemoteConnectionInfo localConnectInfo;
//
//            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
//            {
//                Class<?> returnType = method.getReturnType();
//                String methodName = method.getName();
//                final URL server;
//                try
//                {
//                    String serverURL = remoteConnectionInfo.getServerURL();
//                    server = new URL( serverURL);
//                }
//                catch (MalformedURLException e)
//                {
//                   throw new RaplaContextException(e.getMessage());
//                }
//                StatusUpdater statusUpdater = remoteConnectionInfo.getStatusUpdater();
//                if ( statusUpdater != null)
//                {
//                    statusUpdater.setStatus( Status.BUSY );
//                }
//                FutureResult result;
//                try
//                {
//                    result = call( a, methodName, args, remoteConnectionInfo);
//                    if (callLogger.isDebugEnabled())
//                    {
//                        callLogger.debug("Calling " + server + " " + a.getName() + "."+methodName);
//                    }
//                }
//                finally
//                {
//                    if ( statusUpdater != null)
//                    {
//                        statusUpdater.setStatus( Status.READY );
//                    }
//                }
//                if ( !FutureResult.class.isAssignableFrom(returnType))
//                {
//                    return result.get();
//                }
//                return result;
//            }
//        };
//        ClassLoader classLoader = a.getClassLoader();
//        @SuppressWarnings("unchecked")
//        Class<T>[] interfaces = new Class[] {a};
//        @SuppressWarnings("unchecked")
//        T proxyInstance = (T)Proxy.newProxyInstance(classLoader, interfaces, proxy);
//        return proxyInstance;
    }

//    private FutureResult call( Class<?> service, String methodName,Object[] args,RemoteConnectionInfo connectionInfo) throws NoSuchMethodException, SecurityException  {
//        ConnectInfo connectInfo = connectionInfo.getConnectInfo();
//        if ( connectInfo !=null)
//        {
//            Method method = RemoteAuthentificationService.class.getMethod("login", String.class, String.class,String.class);
//            connector.setReAuthentication(RemoteAuthentificationService.class, method, new Object[] {connectInfo.getUsername(), new String(connectInfo.getPassword()), connectInfo.getConnectAs()});
//        }
//        FutureResult result =connector.call(service, methodName, args, connectionInfo);
//        return result;
//    }



//    static private String reauth(BasicRaplaHTTPConnector proxy) throws Exception
//    {
//        String retryCode;
//        if (!loginCmd)
//        {
//            String newAuthCode;
//            // we only start one reauth call at a time. So check if reauth is in progress
//
//            if (!reAuthNode.tryAcquire())
//            {
//                // if yes
//                if (reAuthNode.tryAcquire(10000, TimeUnit.MILLISECONDS))
//                {
//                    reAuthNode.release();
//                    // try the recently acquired access token
//                    newAuthCode = serverInfo.getAccessToken();
//                }
//                else
//                {
//                    throw new RaplaConnectException("Login in progress. Taking longer than expected ");
//                }
//            }
//            else
//            {
//                // no reauth in progress so we start a new one
//                try
//                {
//                    newAuthCode = reAuth();
//                }
//                finally
//                {
//                    reAuthNode.release();
//                }
//            }
//            retryCode = newAuthCode;
//        }
//        else
//        {
//            retryCode = null;
//        }
//        return retryCode;
//    }
//
//    Semaphore reAuthNode = new Semaphore(1);
//
//
//    private String reAuth() throws Exception
//    {
//        URL loginURL = getMethodUrl(reconnectInfo.service, serverInfo);
//        JsonElement jsonObject = serializeCall(reconnectInfo.method, reconnectInfo.args);
//        JsonObject resultMessage = sendCall_("POST", loginURL, jsonObject, null);
//        checkError(resultMessage);
//        LoginTokens result = (LoginTokens) getResult(reconnectInfo.method, resultMessage);
//        String newAuthCode = result.getAccessToken();
//        serverInfo.setAccessToken(newAuthCode);
//        //logger.warn("TEST", new RaplaException("TEST Ex"));
//        return newAuthCode;
//    }
//
//
//    protected void checkError(JsonObject resultMessage) throws Exception
//    {
//        JsonElement errorElement = resultMessage.get("error");
//        if (errorElement != null)
//        {
//            Exception ex = deserializeExceptionObject(resultMessage);
//            //            String message = ex.getMessage();
//            //            if (loginCmd || message == null)
//            //            {
//            //                throw ex;
//            //            }
//            //            // test if error cause is an expired authorization
//
//            throw ex;
//
//        }
//    }

}