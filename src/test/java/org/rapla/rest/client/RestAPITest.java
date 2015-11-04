package org.rapla.rest.client;

import java.net.URL;

import org.rapla.ServletTestBase;

import junit.framework.TestCase;

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
        URL baseUrl = new URL("http://localhost:8051/rapla/");
        example.testRestApi(baseUrl,"admin","");
    }

   
}
