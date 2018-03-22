package org.rapla.server.internal.console;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.io.ChunkedInputStream;
import org.apache.http.impl.io.ContentLengthInputStream;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Map;

public class StandaloneStarter extends GUIStarter implements JsonRemoteConnector
{
    URL mockDownloadUrl;
    ServerStarter serverStarter;
    Object standaloneConnector;
    final Method requestFinished;
    final Method doSend;

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
        try
        {
            final Class<?> StandaloneConnectorClass = localconnector.getClass();
            requestFinished = StandaloneConnectorClass.getMethod("requestFinished");
            doSend = StandaloneConnectorClass.getMethod("doSend",String.class);
            standaloneConnector = localconnector;
        }
        catch (Exception e)
        {
            throw new RaplaInitializationException(e);
        }
    }

    @Override public JsonRemoteConnector.CallResult sendCallWithString(String requestMethod, URL methodURL, String body, String authenticationToken, String contentType,
            Map<String, String> additionalHeaders) throws IOException
    {
        final String rawHttpRequest = createRawHttpRequest(requestMethod, methodURL, body, authenticationToken, contentType, additionalHeaders);
        final byte[] rawResult;
        try
        {
            rawResult = (byte[])doSend.invoke( standaloneConnector,rawHttpRequest);
        }
        catch (IllegalAccessException e)
        {
            throw new IOException( e);
        }
        catch (InvocationTargetException e)
        {
            Throwable cause = e.getCause();
            while ( cause instanceof InvocationTargetException)
            {
                cause = e.getCause();
            }
            if ( cause instanceof IOException)
            {
                throw (IOException)cause;
            }
            else
            {
                throw new IOException(cause);
            }
        }
        return parseResult(rawResult);
    }


    public void startClient() throws Exception
    {
        //((org.eclipse.jetty.server.LocalConnector) localconnector).start();
        JavaClientServerConnector.setJsonRemoteConnector(this);
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
            requestFinished.invoke(standaloneConnector);
        }
        catch (Exception e)
        {
            logger.error(e.getMessage(), e);
        }
    }

    protected JsonRemoteConnector.CallResult parseResult(final byte[] bytes1)
    {
        Integer responseCode;
        String body = "";
        try
        {
            final HttpTransportMetricsImpl httpTransportMetrics = new HttpTransportMetricsImpl();
            MessageConstraints constraints = null;
            CharsetDecoder charsetDecoder = Charset.forName("UTF-8").newDecoder();
            int minChunkLimit = 0;
            SessionInputBufferImpl inputBuffer = new SessionInputBufferImpl(httpTransportMetrics, Math.max(1000,bytes1.length+100), minChunkLimit, constraints, charsetDecoder);
            inputBuffer.bind(new ByteArrayInputStream(bytes1));
            org.apache.http.impl.io.DefaultHttpResponseParser parser = new org.apache.http.impl.io.DefaultHttpResponseParser(inputBuffer);
            final HttpResponse response = parser.parse();
            responseCode = response.getStatusLine().getStatusCode();
            if (responseCode != null && responseCode != 204)
            {
                final InputStream inputStream;
                final Header[] contentLength = response.getHeaders("content-length");
                if (contentLength.length > 0)
                {
                    final String s = contentLength[0].getValue();
                    int length = Integer.parseInt(s);
                    inputStream = new ContentLengthInputStream(inputBuffer, length);
                }
                else
                {
                    inputStream = new ChunkedInputStream(inputBuffer, constraints);
                }

                body = readString( inputStream, "UTF-8");
            }
        }
        catch (Exception e)
        {
            throw new IllegalStateException("IOException thrown in String: " + e.getMessage(), e);
        }
        JsonRemoteConnector.CallResult result = new JsonRemoteConnector.CallResult(body, responseCode);
        return result;
    }

    private static String readString(InputStream inputStream, String charset) throws IOException
    {
        final InputStreamReader in = new InputStreamReader(inputStream, charset);
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[1024];
        int count = 0;
        do {
            builder.append(buffer,0, count);
            count = in.read(buffer, 0, buffer.length);
        } while (count != -1);
        return builder.toString();
    }

    private String createRawHttpRequest(String requestMethod, URL methodURL, String body, String authenticationToken, String contentType,
            Map<String, String> additionalHeaders)
    {
        final StringWriter out = new StringWriter();
        PrintWriter buf = new PrintWriter(out);
        buf.print(requestMethod + " " + methodURL.toString() + " HTTP/1.1");
        buf.print("\r\n");
        buf.print("Host: localhost");
        buf.print("\r\n");
        buf.print("Content-Type: " + contentType + ";charset=utf-8");
        buf.print("\r\n");
        buf.print("Content-Length: " + body.getBytes(Charset.forName("UTF-8")).length);
        buf.print("\r\n");
        buf.print("Accept: " + contentType);
        buf.print("\r\n");
        for (Map.Entry<String, String> et : additionalHeaders.entrySet())
        {
            final String key = et.getKey();
            final String value = et.getValue();
            buf.print(key);
            buf.print(": ");
            buf.print(value);
            buf.print("\r\n");
        }
        if (authenticationToken != null)
        {
            buf.print("Authorization: Bearer " + authenticationToken);
            buf.print("\r\n");
        }
        buf.print("\r\n");
        buf.print(body);
        buf.print("\r\n\r\n");
        final String s = out.toString();
        return s;

    }
}