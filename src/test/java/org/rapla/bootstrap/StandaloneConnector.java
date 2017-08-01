package org.rapla.bootstrap;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.io.ChunkedInputStream;
import org.apache.http.impl.io.ContentLengthInputStream;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.rapla.rest.client.swing.JsonRemoteConnector;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class StandaloneConnector  implements JsonRemoteConnector
{
    private final LocalConnector connector;
    private final Semaphore waitForSemarphore = new Semaphore(0);
    private final Semaphore waitForStart = new Semaphore(0);

    public StandaloneConnector(LocalConnector connector)
    {
        this.connector = connector;

        connector.addLifeCycleListener(new AbstractLifeCycle.AbstractLifeCycleListener()
        {
            @Override public void lifeCycleStarted(LifeCycle event)
            {
                waitForStart.release();
            }
        });
    }

    @Override public CallResult sendCallWithString(String requestMethod, URL methodURL, String body, String authenticationToken, String contentType,
            Map<String, String> additionalHeaders) throws IOException
    {
        final String rawHttpRequest = createRawHttpRequest(requestMethod, methodURL, body, authenticationToken, contentType, additionalHeaders);
        final ByteBuffer rawResult = doSend(rawHttpRequest);
        return parseResult(rawResult);
    }

    // Called with reflection. Dont delete
    public void requestFinished()
    {
        waitForSemarphore.release();
    }

    protected ByteBuffer doSend(String rawHttpRequest)
    {
        if (!connector.isRunning() && !connector.isStopped() && !connector.isStopped())
        {
            try
            {
                waitForStart.tryAcquire(10, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                throw new IllegalStateException("Server did not answer within 60 sec: " + e.getMessage(), e);
            }
        }
        LocalConnector.LocalEndPoint endpoint = connector.executeRequest(rawHttpRequest);
        try
        {
            // Wait max 60 seconds
            waitForSemarphore.tryAcquire(10, TimeUnit.SECONDS);
            //endpoint.waitUntilClosed();
            Thread.sleep(500);
        }
        catch (InterruptedException e)
        {
            throw new IllegalStateException("Server did not answer within 60 sec: " + e.getMessage(), e);
        }

        final ByteBuffer byteBuffer = endpoint.takeOutput();

        return byteBuffer;
    }

    protected CallResult parseResult(ByteBuffer rawResult)
    {
        Integer responseCode;
        String body = "";
        try
        {
            final HttpTransportMetricsImpl httpTransportMetrics = new HttpTransportMetricsImpl();
            MessageConstraints constraints = null;
            CharsetDecoder charsetDecoder = Charset.forName("UTF-8").newDecoder();
            int minChunkLimit = 0;
            final byte[] bytes1 = BufferUtil.toArray(rawResult);
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
        CallResult result = new CallResult(body, responseCode);
        return result;
    }

    public static String readString(InputStream inputStream, String charset) throws IOException
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
