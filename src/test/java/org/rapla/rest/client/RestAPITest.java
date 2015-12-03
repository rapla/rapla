package org.rapla.rest.client;

import java.net.URL;

import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.RaplaTestCase;
import org.rapla.ServletTestBase;

import junit.framework.TestCase;
import org.rapla.framework.logger.Logger;
import org.rapla.framework.logger.RaplaBootstrapLogger;
import org.rapla.server.ServerServiceContainer;

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

   
}
