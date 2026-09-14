package org.rapla.rest.client.swing;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

public interface JsonRemoteConnector
{
    CallResult sendCallWithString(String requestMethod, URL methodURL, String body, String authenticationToken, String contentType,
            Map<String, String> additionalHeaders) throws IOException;


    class CallResult
    {
        private final String result;
        private final int responseCode;

        public CallResult(String result, int responseCode)
        {
            this.result = result;
            this.responseCode = responseCode;
        }

        public String getResult()
        {
            return result;
        }

        public int getResponseCode()
        {
            return responseCode;
        }

        @Override public String toString()
        {
            return responseCode + ":" + result;
        }
    }
}
