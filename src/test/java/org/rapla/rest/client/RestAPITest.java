package org.rapla.rest.client;

import junit.framework.TestCase;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.AbstractTestWithServer;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.rest.JsonParserWrapper;
import org.rapla.rest.client.swing.HTTPConnector;
import org.rapla.rest.client.swing.JsonRemoteConnector;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4.class)
public class RestAPITest extends AbstractTestWithServer {

    @Test
    public void testSerialize()
    {
        Map<String,String> constants = new HashMap<>();
        constants.put("1","Hello");
        RaplaMapImpl map = new RaplaMapImpl(constants);
        final JsonParserWrapper.JsonParser gson = JsonParserWrapper.defaultJson().get();
        final String s = gson.toJson(map);
        final RaplaMapImpl deserialized = gson.fromJson(s, RaplaMapImpl.class);
        TestCase.assertEquals( map, deserialized);
    }

    @Ignore
    @Test
    public void testRestApi() throws Exception
    {
        RestAPIExample example = new RestAPIExample()
        {
            protected void assertTrue( boolean condition)
            {
                TestCase.assertTrue(condition);
            }
            
            protected void assertEquals( Object o1, Object o2)
            {
                TestCase.assertEquals(o1, o2);
            }
        };
        URL baseUrl = new URL("http://localhost:"+getPort()+"/rapla/");
        example.testRestApi(baseUrl,"homer","duffs");
    }


    @Test
    public void testWebpages() throws Exception
    {
        HTTPConnector connector = new HTTPConnector();
        String body = null;
        String authenticationToken = null;
        Map<String, String> additionalHeaders = new HashMap<>();
        {
            URL baseUrl = new URL("http://localhost:"+getPort()+"/rapla/server");
            final JsonRemoteConnector.CallResult result = connector.sendCallWithString("GET", baseUrl, body, authenticationToken, "text/html", additionalHeaders);
            TestCase.assertNotNull(result);
            TestCase.assertTrue( result.getResult().contains("Server running"));
        }
        {
            URL baseUrl = new URL("http://localhost:"+getPort()+"/rapla/calendar");
            final JsonRemoteConnector.CallResult  result = connector.sendCallWithString("GET", baseUrl, body, authenticationToken, "text/html", additionalHeaders);
            TestCase.assertNotNull(result);
            TestCase.assertTrue( result.getResult().contains("<title>Rapla"));
        }
        {
            URL baseUrl = new URL("http://localhost:"+getPort()+"/rapla/auth");
            final JsonRemoteConnector.CallResult  result = connector.sendCallWithString("GET", baseUrl, body, authenticationToken, "text/html", additionalHeaders);

            TestCase.assertNotNull(result);
        }
        {
            URL baseUrl = new URL("http://localhost:"+getPort()+"/rapla/auth?username=homer&password=duffs");
            final JsonRemoteConnector.CallResult  result = connector.sendCallWithString("POST", baseUrl, body, authenticationToken, "application/json", additionalHeaders);
            TestCase.assertNotNull(result);
        }

    }





}
