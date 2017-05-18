package org.rapla.bootstrap;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.io.ChunkedInputStream;
import org.apache.http.impl.io.ContentLengthInputStream;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.server.LocalConnector.LocalEndPoint;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.rapla.components.util.IOUtil;
import org.rapla.rest.client.swing.JsonRemoteConnector;

import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class StandaloneConnector implements JsonRemoteConnector
{
    private final LocalConnector connector;
    private final Semaphore waitForSemarphore = new Semaphore(0);
    private final Semaphore waitForStart = new Semaphore(0);

    public StandaloneConnector(LocalConnector connector)
    {
        this.connector = connector;
        connector.addLifeCycleListener(new AbstractLifeCycle.AbstractLifeCycleListener()
        {
            @Override
            public void lifeCycleStarted(LifeCycle event)
            {
                waitForStart.release();
            }
        });
    }

    @Override public CallResult sendCallWithString(String requestMethod, URL methodURL, String body, String authenticationToken, String contentType,
            Map<String, String> additionalHeaders) throws IOException
    {
        final String rawHttpRequest = createRawHttpRequest(requestMethod, methodURL, body, authenticationToken, contentType, additionalHeaders);
        final String rawResult = doSend(rawHttpRequest);
        return parseResult(rawResult);
    }

    protected CallResult parseResult(String rawResult)
    {
        Integer responseCode;
        String body = "";
        try
        {
            final HttpTransportMetricsImpl httpTransportMetrics = new HttpTransportMetricsImpl();
            MessageConstraints constraints = null;
            CharsetDecoder charsetDecoder = Charset.forName("UTF-8").newDecoder();
            int minChunkLimit = 0;
            SessionInputBufferImpl inputBuffer = new SessionInputBufferImpl(httpTransportMetrics, 4000, minChunkLimit, constraints, charsetDecoder);
            inputBuffer.bind(new ByteArrayInputStream(rawResult.getBytes()));
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

                final byte[] bytes = readBytes(inputStream);
                body = new String(bytes);
            }
        }
        catch (Exception e)
        {
            throw new IllegalStateException("IOException thrown in String: " + e.getMessage(), e);
        }
        CallResult result = new CallResult(body, responseCode);
        return result;
    }

    public static byte[] readBytes(InputStream in) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count = 0;
        do {
            out.write(buffer, 0, count);
            count = in.read(buffer, 0, buffer.length);
        } while (count != -1);
        return out.toByteArray();
    }

    protected String createRawHttpRequest(String requestMethod, URL methodURL, String body, String authenticationToken, String contentType,
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

    // Called with reflection. Dont delete
    public void requestFinished()
    {
        waitForSemarphore.release();
    }

    // We need to make it synchronized because local connector can't handle two at once
    synchronized protected String doSend(String rawHttpRequest)
    {
        if (!connector.isRunning() && !connector.isStopped() && !connector.isStopped())
        {
            try
            {
                waitForStart.tryAcquire(60, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                throw new IllegalStateException("Server did not answer within 60 sec: " + e.getMessage(), e);
            }
        }
        //        try
        //        {
        //            final String responses = connector.getResponses(rawHttpRequest);
        //            return responses;
        //        }
        //        catch (Exception e)
        //        {
        //            throw new IllegalStateException("Server did not answer within 60 sec: " + e.getMessage(), e);
        //        }
        waitForSemarphore.drainPermits();
        LocalEndPoint endpoint = connector.executeRequest(rawHttpRequest);
        try
        {
            // Wait max 60 seconds
            waitForSemarphore.tryAcquire(60, TimeUnit.SECONDS);
            //endpoint.waitUntilClosed();
        }
        catch (InterruptedException e)
        {
            throw new IllegalStateException("Server did not answer within 60 sec: " + e.getMessage(), e);
        }
        final ByteBuffer byteBuffer = endpoint.takeOutput();
        final String s = byteBuffer == null ? null : BufferUtil.toString(byteBuffer, StandardCharsets.UTF_8);
        return s;
    }

}
