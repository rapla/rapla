package org.rapla.rest.client.swing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

@Deprecated
public class HTTPWithJsonConnector extends HTTPConnector
{
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(com.fasterxml.jackson.core.json.JsonWriteFeature.ESCAPE_NON_ASCII)
            .build();

    public HTTPWithJsonConnector() {
        super();
    }

    public ObjectNode sendPost(URL methodURL, JsonNode jsonObject) throws IOException {
        return sendPost( methodURL, jsonObject, null);
    }

    public ObjectNode sendPost(URL methodURL, JsonNode jsonObject, String authenticationToken) throws IOException {
        return sendPost( methodURL, jsonObject, authenticationToken, Collections.emptyMap());
    }

    public ObjectNode sendPost(URL methodURL, JsonNode jsonObject, String authenticationToken,Map<String, String>additionalHeaders) throws IOException {
        return sendCall("POST", methodURL, jsonObject, authenticationToken, additionalHeaders);
    }

    public ObjectNode sendGet(URL methodURL) throws IOException {
        return sendGet( methodURL, null);
    }

    public ObjectNode sendGet(URL methodURL, String authenticationToken) throws IOException {
        return sendGet( methodURL, authenticationToken, Collections.emptyMap());
    }

    public ObjectNode sendGet(URL methodURL, String authenticationToken, Map<String, String>additionalHeaders) throws IOException {
        return sendCall("GET", methodURL, null, authenticationToken, additionalHeaders);
    }

    public ObjectNode sendPut(URL methodURL, JsonNode jsonObject, String authenticationToken) throws IOException {
        return sendPut( methodURL, jsonObject, authenticationToken, Collections.emptyMap());
    }

    public ObjectNode sendPut(URL methodURL, JsonNode jsonObject, String authenticationToken, Map<String, String>additionalHeaders) throws IOException {
        return sendCall("PUT", methodURL, jsonObject, authenticationToken, additionalHeaders);
    }

    public ObjectNode sendPatch(URL methodURL, JsonNode jsonObject) throws IOException
    {
        return sendPatch( methodURL,jsonObject,null);
    }

    public ObjectNode sendPatch(URL methodURL, JsonNode jsonObject,String authenticationToken) throws IOException
    {
        return sendPatch( methodURL,jsonObject, authenticationToken, Collections.emptyMap());
    }
    public ObjectNode sendPatch(URL methodURL, JsonNode jsonObject, String authenticationToken,Map<String, String>additionalHeaders) throws IOException {
        return sendCall("PATCH", methodURL, jsonObject, authenticationToken, additionalHeaders);
    }

    public ObjectNode sendDelete(URL methodURL, String authenticationToken) throws IOException {
        return sendCall("DELETE", methodURL, null, authenticationToken, Collections.emptyMap());
    }

    public ObjectNode sendDelete(URL methodURL, String authenticationToken,Map<String, String>additionalHeaders) throws IOException {
        return sendCall("DELETE", methodURL, null, authenticationToken, additionalHeaders);
    }

    protected ObjectNode sendCall(String requestMethod, URL methodURL, JsonNode jsonObject, String authenticationToken,Map<String, String>additionalHeaders) throws IOException {
        final String body = parseJson(jsonObject);
        CallResult callResult = sendCallWithString(requestMethod, methodURL, body, authenticationToken, additionalHeaders);
        final int responseCode = callResult.getResponseCode();
        ObjectNode response = MAPPER.createObjectNode();
        final String json = callResult.getResult();
        if ( responseCode == 200 )
        {
            final JsonNode parse = MAPPER.readTree(json);
            response.set("result", parse);
        }
        else if ( responseCode != 204)
        {
            final JsonNode parse = MAPPER.readTree(json);
            response.set("error", parse);
        }
        return response;
    }

    public String parseJson(JsonNode jsonObject)
    {
        final String body;
        if(jsonObject != null)
        {
            try
            {
                body = MAPPER.writeValueAsString(jsonObject);
            }
            catch (JsonProcessingException e)
            {
                throw new RuntimeException(e);
            }
        }
        else
        {
            body = "";
        }
        return body;
    }

}
