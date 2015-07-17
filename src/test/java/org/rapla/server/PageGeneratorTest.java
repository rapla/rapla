package org.rapla.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLEncoder;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.ServletTestBase;
import org.rapla.rest.client.HTTPConnector;
import org.rapla.servletpages.RaplaPageGenerator;

public class PageGeneratorTest extends ServletTestBase {
    
    public PageGeneratorTest(String name) {
        super(name);
    }

    final static String paramName = "param";

    @Override
    protected void setUp() throws Exception {
        WAR_SRC_FOLDER_NAME = "../rapla/war";
        super.setUp();
    }

    public void testGenerator() throws Exception
    {
        ServerServiceContainer raplaContainer = getContainer().getContext().lookup(ServerServiceContainer.class);
        String pagename = "testpage";
        raplaContainer.addWebpage(pagename, TestPage.class);
        String body = "";
        String authenticationToken = null;
        String paramValue = "world";
        URL url = new URL( "http://localhost:"+port+ "/rapla/" + pagename + "?"+ paramName +"="+URLEncoder.encode(paramValue,"UTF-8"));
        String result = new HTTPConnector().sendCallWithString("GET", url , body, authenticationToken);
        assertEquals("Hello " + paramValue,result);
    }
    
    public static class TestPage implements RaplaPageGenerator
    {
        @Override
        public void generatePage(ServletContext context, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            String parameter = request.getParameter(paramName);
            PrintWriter writer = response.getWriter();
            try
            {
                writer.print("Hello " + parameter);
            }
            finally
            {
                if (writer != null)
                {
                    writer.close();
                }
            }
        }
        
    }
}
