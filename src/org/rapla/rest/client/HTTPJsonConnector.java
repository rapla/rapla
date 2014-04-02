package org.rapla.rest.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

public class HTTPJsonConnector {

    public HTTPJsonConnector() {
        super();
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


    public JsonObject sendPost(URL methodURL, JsonElement jsonObject, String authenticationToken) throws IOException,JsonParseException {
        return sendCall("POST", methodURL, jsonObject, authenticationToken);
    }

    public JsonObject sendGet(URL methodURL, String authenticationToken) throws IOException,JsonParseException  {
        return sendCall("GET", methodURL, null, authenticationToken);
    }

    public JsonObject sendPut(URL methodURL, JsonElement jsonObject, String authenticationToken) throws IOException,JsonParseException  {
        return sendCall("PUT", methodURL, jsonObject, authenticationToken);
    }

    public JsonObject sendPatch(URL methodURL, JsonElement jsonObject, String authenticationToken) throws IOException,JsonParseException  {
        return sendCall("PATCH", methodURL, jsonObject, authenticationToken);
    }

    public JsonObject sendDelete(URL methodURL, String authenticationToken) throws IOException,JsonParseException  {
        return sendCall("DELETE", methodURL, null, authenticationToken);
    }

    protected JsonObject sendCall(String requestMethod, URL methodURL, JsonElement jsonObject, String authenticationToken) throws  IOException,JsonParseException   {
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
        conn.setReadTimeout(20000); //set timeout to 20 seconds
        conn.setConnectTimeout(15000); //set connect timeout to 15 seconds
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
                Gson gson = new GsonBuilder().create();
        		String body = gson.toJson( jsonObject);
                wr.write( body);
                wr.flush();
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
        JsonObject resultMessage = readResult(conn);
        
        return resultMessage;
    }

    
    public JsonObject readResult(HttpURLConnection conn) throws IOException,JsonParseException {
        String resultString;
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
        JsonParser jsonParser = new JsonParser();
        JsonElement  parsed = jsonParser.parse(resultString);
        if ( !(parsed instanceof JsonObject))
        {
        	throw new JsonParseException("Invalid json result. JsonObject expected.");
        }
        JsonObject resultMessage = (JsonObject) parsed;
        return resultMessage;
        
    }

}