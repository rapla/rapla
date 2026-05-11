package org.rapla.client.spring;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.AbstractBufferingClientHttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * HttpURLConnection-based request factory that buffers the request body and uses
 * automatic Content-Length, instead of calling {@code setChunkedStreamingMode} or
 * {@code setFixedLengthStreamingMode}.
 *
 * <p>Why this exists: Spring 7's {@code SimpleClientHttpRequest.executeInternal}
 * unconditionally enables streaming mode on the underlying {@code HttpURLConnection}.
 * Streaming mode flips a flag in {@code sun.net.www.protocol.http.HttpURLConnection}
 * that — on a 401 response — throws {@code HttpRetryException} and returns an empty
 * error stream. The server's i18n'd error body (e.g. {@code "Login failed!"})
 * disappears, so {@code HttpClientErrorException.Unauthorized.getMessage()} comes
 * back as {@code "401 : [no body]"}. Spring 6 had
 * {@code setOutputStreaming(false)} to opt out; Spring 7 removed it.
 *
 * <p>By NOT calling either streaming-mode setter, HttpURLConnection buffers the
 * request body internally and the response error stream remains readable via
 * {@link HttpURLConnection#getErrorStream()}. Used for /auth/login so the user
 * sees the localised "Login failed!" instead of an empty exception.
 */
public class BufferingHttpUrlConnectionRequestFactory implements ClientHttpRequestFactory
{

    @Override
    public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException
    {
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod(httpMethod.name());
        boolean doOutput = !httpMethod.equals(HttpMethod.GET) && !httpMethod.equals(HttpMethod.HEAD);
        conn.setDoOutput(doOutput);
        conn.setDoInput(true);
        conn.setInstanceFollowRedirects(httpMethod.equals(HttpMethod.GET));
        return new BufferingRequest(conn, httpMethod, uri);
    }

    static class BufferingRequest extends AbstractBufferingClientHttpRequest
    {
        private final HttpURLConnection connection;
        private final HttpMethod method;
        private final URI uri;

        BufferingRequest(HttpURLConnection connection, HttpMethod method, URI uri)
        {
            this.connection = connection;
            this.method = method;
            this.uri = uri;
        }

        @Override
        public HttpMethod getMethod() { return method; }

        @Override
        public URI getURI() { return uri; }

        @Override
        protected ClientHttpResponse executeInternal(HttpHeaders headers, byte[] body) throws IOException
        {
            headers.forEach((name, values) ->
            {
                for (String v : values) connection.addRequestProperty(name, v);
            });
            connection.connect();
            if (connection.getDoOutput() && body.length > 0)
            {
                try (var out = connection.getOutputStream())
                {
                    out.write(body);
                }
            }
            return new BufferingResponse(connection);
        }
    }

    static class BufferingResponse implements ClientHttpResponse
    {
        private final HttpURLConnection connection;
        private HttpHeaders headers;
        private InputStream responseStream;

        BufferingResponse(HttpURLConnection connection) { this.connection = connection; }

        @Override
        public HttpStatusCode getStatusCode() throws IOException
        {
            return HttpStatusCode.valueOf(connection.getResponseCode());
        }

        @Override
        public String getStatusText() throws IOException
        {
            String s = connection.getResponseMessage();
            return s != null ? s : "";
        }

        @Override
        public HttpHeaders getHeaders()
        {
            if (headers == null)
            {
                HttpHeaders h = new HttpHeaders();
                Map<String, List<String>> raw = connection.getHeaderFields();
                if (raw != null)
                {
                    for (Map.Entry<String, List<String>> e : raw.entrySet())
                    {
                        if (e.getKey() != null) h.addAll(e.getKey(), e.getValue());
                    }
                }
                headers = h;
            }
            return headers;
        }

        @Override
        public InputStream getBody() throws IOException
        {
            if (responseStream == null)
            {
                int code = connection.getResponseCode();
                if (code >= 400)
                {
                    InputStream errStream = connection.getErrorStream();
                    responseStream = (errStream != null ? errStream : InputStream.nullInputStream());
                }
                else
                {
                    responseStream = connection.getInputStream();
                }
            }
            return responseStream;
        }

        @Override
        public void close()
        {
            try
            {
                if (responseStream != null) responseStream.close();
            }
            catch (IOException ignored) { }
            connection.disconnect();
        }
    }
}
