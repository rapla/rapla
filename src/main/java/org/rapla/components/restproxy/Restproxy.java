package org.rapla.components.restproxy;

import io.reactivex.rxjava3.core.Notification;
import org.rapla.rest.client.CustomConnector;
import org.rapla.rest.client.swing.JavaClientServerConnector;
import org.rapla.rest.client.swing.JavaJsonSerializer;

import javax.inject.Inject;
import javax.ws.rs.Path;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

public class Restproxy<T> {
    @Inject
    CustomConnector customConnector;

    T createProxy(Class<T> clazz) {
        ClassLoader classLoader = clazz.getClassLoader();
        InvocationHandler invocationHandler = (proxy,method,args) ->
        {
            Path annotation = method.getAnnotation(Path.class);
            String methodUrl = null;
            Map<String, String> additionalHeaders = null;
            Notification<Object> postBody = null;
            JavaJsonSerializer serializer_2 = null;
            Object result = JavaClientServerConnector.doInvoke("POST", methodUrl , additionalHeaders,postBody.toString(),serializer_2, "void", customConnector, false);
            return result;
        };
        return (T) Proxy.newProxyInstance(classLoader,new Class[] {clazz}, invocationHandler);
    }
}
