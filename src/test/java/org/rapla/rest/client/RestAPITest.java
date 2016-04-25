package org.rapla.rest.client;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.ServletTestBase;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.framework.logger.Logger;
import org.rapla.rest.client.swing.HTTPConnector;
import org.rapla.rest.client.swing.HTTPConnector.HttpCallResult;
import org.rapla.rest.client.swing.JSONParserWrapper;
import org.rapla.server.ServerServiceContainer;
import org.rapla.test.util.RaplaTestCase;

import junit.framework.TestCase;

@RunWith(JUnit4.class)
public class RestAPITest  {


    int port = 8052;
    Server server;
    @Before
    public void setUp() throws Exception
    {
        Logger logger = RaplaTestCase.initLoger();
        final ServerServiceContainer servlet = RaplaTestCase.createServer(logger, "testdefault.xml");
        server = ServletTestBase.createServer(servlet, port);
    }


    @Test
    public void testSerialize()
    {
        Map<String,String> constants = new HashMap<>();
        constants.put("1","Hello");
        RaplaMapImpl map = new RaplaMapImpl(constants);
        final GsonBuilder gsonBuilder = JSONParserWrapper.defaultGsonBuilder(new Class[] { RaplaMapImpl.class });
        final Gson gson = gsonBuilder.create();
        final String s = gson.toJson(map);
        final RaplaMapImpl deserialized = gson.fromJson(s, RaplaMapImpl.class);
        TestCase.assertEquals( map, deserialized);
    }
    @After
    public void tearDown() throws Exception
    {
        server.stop();
    }

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
        URL baseUrl = new URL("http://localhost:"+port+"/rapla/");
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
            URL baseUrl = new URL("http://localhost:"+port+"/rapla/server");
            final HttpCallResult result = connector.sendCallWithString("GET", baseUrl, body, authenticationToken, "text/html", additionalHeaders);
            TestCase.assertNotNull(result);
            TestCase.assertTrue( result.getResult().contains("Server running"));
        }
        {
            URL baseUrl = new URL("http://localhost:"+port+"/rapla/calendar");
            final HttpCallResult result = connector.sendCallWithString("GET", baseUrl, body, authenticationToken, "text/html", additionalHeaders);
            TestCase.assertNotNull(result);
            TestCase.assertTrue( result.getResult().contains("<title>Rapla"));
        }
        {
            URL baseUrl = new URL("http://localhost:"+port+"/rapla/auth");
            final HttpCallResult result = connector.sendCallWithString("GET", baseUrl, body, authenticationToken, "text/html", additionalHeaders);

            TestCase.assertNotNull(result);
        }
        {
            URL baseUrl = new URL("http://localhost:"+port+"/rapla/auth?username=homer&password=duffs");
            final HttpCallResult result = connector.sendCallWithString("POST", baseUrl, body, authenticationToken, "application/json", additionalHeaders);
            TestCase.assertNotNull(result);
        }

    }





}
