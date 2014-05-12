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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

public class HTTPJsonConnector extends HTTPConnector {

    public HTTPJsonConnector() {
        super();
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
        Gson gson = new GsonBuilder().create();
        String body = gson.toJson( jsonObject);
        String resultString = sendCallWithString(requestMethod, methodURL, body, authenticationToken);
        JsonObject resultMessage = parseJson(resultString);
        return resultMessage;
    }

   
    
    public JsonObject parseJson(String resultString) throws JsonParseException {
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