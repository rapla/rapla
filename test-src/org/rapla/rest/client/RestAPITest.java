package org.rapla.rest.client;

import java.net.URL;

import junit.framework.TestCase;

import org.rapla.ServletTestBase;

public class RestAPITest extends ServletTestBase {

    public RestAPITest(String name) {
        super(name);
    }

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
        URL baseUrl = new URL("http://localhost:8052/rapla/");
        example.testRestApi(baseUrl,"homer","duffs");
    }

   
}
