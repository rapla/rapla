package org.rapla.plugin.mail.server;

import org.rapla.rest.client.swing.JsonRemoteConnector;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class HTTPMailConnector implements JsonRemoteConnector
{
    public CallResult sendCallWithString(String requestMethod, URL methodURL, String body, String authenticationToken,
            Map<String, String> additionalHeaders) throws IOException {
        return sendCallWithString(requestMethod, methodURL, body, authenticationToken, "application/json", additionalHeaders);
    }

    public CallResult sendCallWithString(String requestMethod, URL methodURL, String body, String authenticationToken, String accept,
            Map<String, String> additionalHeaders) throws IOException
    {
        HttpURLConnection conn = (HttpURLConnection) methodURL.openConnection();
        String[] split = authenticationToken.split(":");
        String username = split[0];
        String password = split[1];
        String userpass = username + ":" + password;
        final String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()));
        conn.setRequestProperty("Authorization", basicAuth);
        for (Map.Entry<String, String> additionalHeader : additionalHeaders.entrySet())
        {
            conn.setRequestProperty(additionalHeader.getKey(), additionalHeader.getValue());
        }
        if (!requestMethod.equals("POST") && !requestMethod.equals("GET") && !requestMethod.equals("OPTIONS"))
        {
            conn.setRequestMethod("POST");

            // we tunnel all non POST or GET requests to avoid proxy filtering (e.g. URLConnection does not allow PATCH)
            conn.setRequestProperty("X-HTTP-Method-Override", requestMethod);
        }
        else
        {
            conn.setRequestMethod(requestMethod);
        }
        conn.setUseCaches(false);
        //conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
        conn.setRequestProperty("Content-Type", "application/json" );
        conn.setRequestProperty("Accept", accept);
        if (authenticationToken != null)
        {
//            conn.setRequestProperty("Cookie", "JSESSIONID=" + authenticationToken);
          //  conn.setRequestProperty("Authorization", "Bearer " + authenticationToken);
        }
        conn.setReadTimeout(120000); //set timeout to 120 seconds
        conn.setConnectTimeout(50000); //set connect timeout to 50 seconds
        conn.setDoOutput(true);

        conn.connect();

        if (requestMethod.equals("PUT") || requestMethod.equals("POST") || requestMethod.equals("PATCH"))
        {
            OutputStream outputStream = null;
            Writer wr = null;
            try
            {
                outputStream = conn.getOutputStream();
                wr = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                if (body != null)
                {
                    wr.write(body);
                    wr.flush();
                }
                else
                {
                    wr.flush();
                }
            }
            finally
            {
                if (wr != null)
                {
                    wr.close();
                }
                if (outputStream != null)
                {
                    outputStream.close();
                }
            }
        }
        else
        {

        }
        String resultString;
        final int responseCode = conn.getResponseCode();
        {
            InputStream inputStream = null;
            try
            {
                if (responseCode != 200 && responseCode != 204)
                {
                    inputStream = conn.getErrorStream();
                }
                else
                {
                    String encoding = conn.getContentEncoding();
                    if (encoding != null && encoding.equalsIgnoreCase("gzip"))
                    {
                        inputStream = new GZIPInputStream(conn.getInputStream());
                    }
                    else if (encoding != null && encoding.equalsIgnoreCase("deflate"))
                    {
                        inputStream = new InflaterInputStream(conn.getInputStream(), new Inflater(true));
                    }
                    else
                    {
                        inputStream = conn.getInputStream();
                    }
                }
                if ( inputStream != null)
                {
                    resultString = readResultToString(inputStream);
                }
                else
                {
                    resultString = "";
                }
            }
            finally
            {
                if (inputStream != null)
                {
                    inputStream.close();
                }
            }
        }
        return new CallResult(resultString, responseCode);
    }

    private String readResultToString(InputStream input) throws IOException
    {
        InputStreamReader in = new InputStreamReader(input, StandardCharsets.UTF_8);
        char[] buf = new char[4096];
        StringBuffer buffer = new StringBuffer();
        while (true)
        {
            int len = in.read(buf);
            if (len == -1)
            {
                break;
            }
            buffer.append(buf, 0, len);
        }
        String result = buffer.toString();
        in.close();
        return result;
    }

}
