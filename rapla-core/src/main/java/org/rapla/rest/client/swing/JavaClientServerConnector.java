package org.rapla.rest.client.swing;

import org.rapla.rest.client.AuthenticationException;
import org.rapla.rest.client.CustomConnector;
import org.rapla.rest.client.ExceptionDeserializer;
import org.rapla.rest.client.RemoteConnectException;
import org.rapla.rest.SerializableExceptionInformation;
import org.rapla.rest.SerializableExceptionInformation.SerializableExceptionStacktraceInformation;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.CompletablePromise;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.sync.SynchronizedCompletablePromise;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JavaClientServerConnector
{

    private static JsonRemoteConnector remoteConnector = new HTTPConnector();
    public JavaClientServerConnector()
    {
    }

    static public void setJsonRemoteConnector( JsonRemoteConnector remote)
    {
        remoteConnector = remote;
    }

    public static  Object doInvoke(final String requestMethodType, final String url,  final Map<String, String> additionalHeaders,String body,
            final JavaJsonSerializer ser, String resultType,CustomConnector connector, boolean isPromise) throws Exception
    {
        final JavaClientServerConnector javaClientServerConnector = new JavaClientServerConnector();
        final CommandScheduler.Callable callable = () ->javaClientServerConnector.send(requestMethodType, url, additionalHeaders, body, ser, connector);
        if ( !isPromise)
        {
            Object result =  callable.call() ;
            return result;
        }
        else
        {
            final Promise resultPromise = connector.call(callable);
            return resultPromise;
        }
    }

    synchronized private Object send( String requestMethodType, String url, Map<String, String> additionalHeaders,String body,
             JavaJsonSerializer serializer,CustomConnector customConnector) throws Exception
    {
        JsonRemoteConnector remote = remoteConnector;
        String contentType =  "application/json";
        String authenticationToken = customConnector.getAccessToken();
        JsonRemoteConnector.CallResult resultMessage;
        try
        {
            resultMessage = remote.sendCallWithString(requestMethodType, new URL(url), body, authenticationToken, contentType,additionalHeaders);
        }
        catch ( IOException ex)
        {
            throw getWrappedIOException(customConnector, ex);
        }
        Exception error = getError(customConnector, resultMessage, serializer);
        if ( error != null)
        {
            if (error instanceof AuthenticationException)
            {
                String newAuthCode = customConnector.reauth(this.getClass());
                // try the same call again with the new result, this time with the newAuthCode
                if (newAuthCode != null)
                {
                    try
                    {
                        resultMessage = remote.sendCallWithString(requestMethodType, new URL(url), body, newAuthCode, contentType, additionalHeaders);
                    }
                    catch (IOException ex)
                    {
                        throw getWrappedIOException(customConnector, ex);
                    }
                    error = getError(customConnector, resultMessage, serializer);
                    if ( error != null)
                    {
                        throw  error;
                    }
                }
                else
                {
                    throw error;
                }
            }
            else
            {
                throw error;
            }
        }
        final Object result = serializer.deserializeResult(resultMessage.getResult());
        return result;
    }

    // TODO: Workaround to allow internationalization of internal connect errors
    private Exception getWrappedIOException(CustomConnector customConnector, IOException ex)
    {
        SerializableExceptionInformation exInfo = new SerializableExceptionInformation(new RemoteConnectException(ex.getMessage()));
        int resultStatus =502;
        return customConnector.deserializeException(exInfo, resultStatus);
    }

    protected Exception getError(ExceptionDeserializer customConnector, JsonRemoteConnector.CallResult resultMessage, JavaJsonSerializer serializer) throws Exception
    {
        final int responseCode = resultMessage.getResponseCode();
        if (responseCode != 200 && responseCode != 204)
        {

            Exception ex = deserializeExceptionObject(customConnector, resultMessage, serializer);
            return ex;
        }
        return null;
    }

    private Exception deserializeExceptionObject(ExceptionDeserializer customConnector, JsonRemoteConnector.CallResult resultMessage, JavaJsonSerializer serializer)
    {
        try
        {
            SerializableExceptionInformation deserializedException = serializer.deserializeException(resultMessage.getResult());
            if (deserializedException == null)
            {
                return new RemoteConnectException(resultMessage.getResponseCode() + ":" + resultMessage.getResult());
            }
            final Exception ex = customConnector.deserializeException(deserializedException,resultMessage.getResponseCode());
            try
            {
                final List<SerializableExceptionStacktraceInformation> stacktrace = deserializedException.getStacktrace();
                if (stacktrace != null)
                {
                    List<StackTraceElement> trace = new ArrayList<StackTraceElement>();
                    for (SerializableExceptionStacktraceInformation element : stacktrace)
                    {
                        final StackTraceElement ste = new StackTraceElement(element.getClassName(), element.getMethodName(), element.getFileName(),
                                element.getLineNumber());
                        trace.add(ste);
                    }
                    ex.setStackTrace(trace.toArray(new StackTraceElement[] {}));
                }
            }
            catch (Exception ex3)
            {
                // Can't get stacktrace
            }
            return ex;
        }
        catch (Exception e)
        {
            final int responseCode = resultMessage.getResponseCode();
            if ( responseCode == 401) {
                return new AuthenticationException("Can't parse result of authentication error");
            }
            // unexpected exception occured, so throw RemoteConnectException
            return new RemoteConnectException(e.getMessage());
        }
    }

}
