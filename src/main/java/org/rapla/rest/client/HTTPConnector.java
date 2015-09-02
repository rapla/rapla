package org.rapla.rest.client;

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
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class HTTPConnector {

    public String sendCallWithString(String requestMethod, URL methodURL,String body, String authenticationToken) throws IOException, ProtocolException, UnsupportedEncodingException {
        HttpURLConnection conn = (HttpURLConnection)methodURL.openConnection();
        if ( !requestMethod.equals("POST") && !requestMethod.equals("GET"))
        {
            conn.setRequestMethod("POST");
            // we tunnel all non POST or GET requests to avoid proxy filtering (e.g. URLConnection does not allow PATCH)
            conn.setRequestProperty("X-HTTP-Method-Override",requestMethod);
        }
        else
        {
            conn.setRequestMethod(requestMethod);
        }
        conn.setUseCaches( false );
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
        conn.setRequestProperty("Content-Type", "application/json" + ";charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        if ( authenticationToken != null)
        {
            conn.setRequestProperty("Authorization", "Bearer "  + authenticationToken);
        }
        conn.setReadTimeout(60000); //set timeout to 60 seconds
        conn.setConnectTimeout(50000); //set connect timeout to 50 seconds
        conn.setDoOutput(true);
        conn.connect();
        
        if ( requestMethod.equals("PUT") ||requestMethod.equals("POST") ||requestMethod.equals("PATCH"))
        {
            OutputStream outputStream = null;
            Writer wr = null;
            try
            {
                outputStream= conn.getOutputStream();
                wr = new OutputStreamWriter(outputStream,"UTF-8");
                if (  body != null)
                {
                    wr.write( body);
                    wr.flush();
                }
                else
                {
                    wr.flush();
                }
            }
            finally
            {
                if ( wr != null)
                {
                    wr.close();
                }
                if ( outputStream  != null)
                {
                    outputStream.close();
                }
            }
        }
        else
        {
            
        }
        String resultString;
        {
            InputStream inputStream = null;
            try
            {
                String encoding = conn.getContentEncoding();
                if (encoding != null && encoding.equalsIgnoreCase("gzip")) 
                {
                    inputStream = new GZIPInputStream(conn.getInputStream());
                }
                else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
                     inputStream = new InflaterInputStream(conn.getInputStream(), new Inflater(true));
                }
                else {
                    inputStream = conn.getInputStream();
                }
                resultString = readResultToString( inputStream);
            }
            finally
            {
                if ( inputStream != null)
                {
                    inputStream.close();
                }
            }
        }
        return resultString;
    }

    private String readResultToString(InputStream input) throws IOException {
        InputStreamReader in = new InputStreamReader( input,"UTF-8");
        char[] buf = new char[4096];
        StringBuffer buffer = new StringBuffer();
        while ( true )
        {
            int  len = in.read(buf);
            if ( len == -1)
            {
                break;
            }
            buffer.append( buf, 0,len );
        }
        String result = buffer.toString();
        in.close();
        return result;
    }

}
