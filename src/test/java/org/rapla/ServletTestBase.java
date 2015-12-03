package org.rapla;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.rapla.server.HttpService;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;

@SuppressWarnings("restriction")
public abstract class ServletTestBase
{

    static public Server createServer(HttpService service, int port) throws Exception
    {
        final HttpServlet servlet = createServlet(service);
        return createServer(servlet, port);
    }

    private static HttpServlet createServlet(final HttpService service)
    {
        return new HttpServlet()
        {
            public void service( HttpServletRequest request, HttpServletResponse response )  throws IOException, ServletException
            {
                service.service(request,response);
            }
        };
    }
    static private Server createServer(HttpServlet mainServlet,int port) throws Exception
    {
        File webappFolder = new File("test");
        Server jettyServer = new Server(port);
        String contextPath = "rapla";
        WebAppContext context = new WebAppContext(jettyServer, contextPath, "/");
        context.setResourceBase(webappFolder.getAbsolutePath());
        context.setMaxFormContentSize(64000000);

        final ServletHolder servletHolder = new ServletHolder(mainServlet.getClass());
        servletHolder.setServlet(mainServlet);
        context.addServlet(servletHolder, "/*");
        jettyServer.start();
        Handler[] childHandlers = context.getChildHandlersByClass(ServletHandler.class);
        final ServletHandler childHandler = (ServletHandler) childHandlers[0];
        final ServletHolder[] servlets = childHandler.getServlets();
        ServletHolder servlet = servlets[0];

                URL server = new URL("http://127.0.0.1:"+port+"/" +contextPath + "/ping");
                HttpURLConnection connection = (HttpURLConnection)server.openConnection();
                int timeout = 10000;
                int interval = 200;
                for ( int i=0;i<timeout / interval;i++)
                {
                    try
                    {
                        connection.connect();
                    }
                    catch (ConnectException ex) {
                        Thread.sleep(interval);
                    }
                }

        return jettyServer;
    }




 }
