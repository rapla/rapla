package org.rapla.storage.dbrm;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;

import javax.inject.Inject;
import javax.inject.Named;

import org.rapla.ConnectInfo;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.logger.Logger;
import org.rapla.rest.gwtjsonrpc.common.FutureResult;
import org.rapla.storage.dbrm.StatusUpdater.Status;

public class RemoteServiceCallerImpl implements RemoteServiceCaller
{
    final CommandScheduler commandQueue;
    final Logger callLogger;
    final RemoteConnectionInfo remoteConnectionInfo;
    final I18nBundle i18n;
    
    @Inject
    public RemoteServiceCallerImpl(CommandScheduler commandQueue, Logger callLogger, RemoteConnectionInfo remoteConnectionInfo, @Named(RaplaComponent.RaplaResourcesId) I18nBundle i18n) {
        super();
        this.commandQueue = commandQueue;
        this.callLogger = callLogger;
        this.remoteConnectionInfo = remoteConnectionInfo;
        this.i18n = i18n;
    }

    public <T> T getRemoteMethod(final Class<T> a) throws RaplaContextException  
    {
        InvocationHandler proxy = new InvocationHandler() 
        {
//                RemoteConnectionInfo localConnectInfo;
            
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable 
            {
                Class<?> returnType = method.getReturnType();
                String methodName = method.getName();
                final URL server;
                try 
                {
                    String serverURL = remoteConnectionInfo.getServerURL();
                    server = new URL( serverURL);
                } 
                catch (MalformedURLException e) 
                {
                   throw new RaplaContextException(e.getMessage());
                }
                StatusUpdater statusUpdater = remoteConnectionInfo.getStatusUpdater();
                if ( statusUpdater != null)
                {
                    statusUpdater.setStatus( Status.BUSY );
                }  
                FutureResult result;
                try
                {
                    result = call(server, a, methodName, args, remoteConnectionInfo);
                    if (callLogger.isDebugEnabled())
                    {
                        callLogger.debug("Calling " + server + " " + a.getName() + "."+methodName);
                    }
                }
                finally
                {
                    if ( statusUpdater != null)
                    {
                        statusUpdater.setStatus( Status.READY );
                    }
                }
                if ( !FutureResult.class.isAssignableFrom(returnType))
                {
                    return result.get();
                }
                return result;
            }
        };
        ClassLoader classLoader = a.getClassLoader();
        @SuppressWarnings("unchecked")
        Class<T>[] interfaces = new Class[] {a};
        @SuppressWarnings("unchecked")
        T proxyInstance = (T)Proxy.newProxyInstance(classLoader, interfaces, proxy);
        return proxyInstance;
    }
    
    private FutureResult call( URL server,Class<?> service, String methodName,Object[] args,RemoteConnectionInfo connectionInfo) throws NoSuchMethodException, SecurityException  {
        String errorString = i18n.format("error.connect", server) + " ";
        RaplaHTTPConnector connector = new RaplaHTTPConnector( commandQueue, errorString);
        ConnectInfo connectInfo = connectionInfo.getConnectInfo();
        if ( connectInfo !=null)
        {
            Method method = RemoteServer.class.getMethod("login", String.class, String.class,String.class);
            connector.setReAuthentication(RemoteServer.class, method, new Object[] {connectInfo.getUsername(), new String(connectInfo.getPassword()), connectInfo.getConnectAs()});
        }
        FutureResult result =connector.call(service, methodName, args, connectionInfo);
        return result;
    }
}