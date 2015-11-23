package org.rapla;

import java.io.File;
import java.io.IOException;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.rapla.framework.RaplaLocale;
import org.rapla.server.HttpService;
import org.rapla.server.MainServlet;
import org.rapla.server.internal.ServerServiceImpl;

import junit.framework.TestCase;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("restriction")
public abstract class ServletTestBase extends TestCase
{
	MainServlet mainServlet;
    //private Server jettyServer;
    public static String TEST_SRC_FOLDER_NAME="test-src";
    public static String WAR_SRC_FOLDER_NAME="war";
    protected int port = 8052;
    
    final public static String WEBAPP_FOLDER_NAME = RaplaTestCase.TEST_FOLDER_NAME  + "/webapp";
    final public static String WEBAPP_INF_FOLDER_NAME = WEBAPP_FOLDER_NAME + "/WEB-INF";
  
    public ServletTestBase( String name )
    {
        super( name );
        new File("temp").mkdir();
        File testFolder =new File(RaplaTestCase.TEST_FOLDER_NAME);
        testFolder.mkdir();
    }

    static public Server createServer(HttpServlet mainServlet,int port) throws Exception
    {

        File webappFolder = new File("test");
        Server jettyServer = new Server(port);
        WebAppContext context = new WebAppContext(jettyServer, "rapla", "/");
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
        return jettyServer;
    }

    static public Server createServer(HttpService service, int port) throws Exception
    {
        final HttpServlet servlet = createServlet(service);
        return createServer(servlet, port);
    }

    static HttpServlet createServlet(final HttpService service)
    {
        return new HttpServlet()
        {
            public void service( HttpServletRequest request, HttpServletResponse response )  throws IOException, ServletException
            {
                service.service(request,response);
            }
        };
    }



    protected void setUp() throws Exception
    {
//        File webappFolder = new File(WEBAPP_FOLDER_NAME);
//		IOUtil.deleteAll( webappFolder );
//        new File(WEBAPP_INF_FOLDER_NAME).mkdirs();
//        
//        IOUtil.copy( TEST_SRC_FOLDER_NAME + "/test.xconf", WEBAPP_INF_FOLDER_NAME + "/raplaserver.xconf" );
//        //IOUtil.copy( "test-src/test.xlog", WEBAPP_INF_FOLDER_NAME + "/raplaserver.xlog" );
//        IOUtil.copy( TEST_SRC_FOLDER_NAME + "/testdefault.xml", WEBAPP_INF_FOLDER_NAME + "/test.xml" );
//        IOUtil.copy( WAR_SRC_FOLDER_NAME + "/WEB-INF/web.xml", WEBAPP_INF_FOLDER_NAME + "/web.xml" );
//        
//        
//        jettyServer =new Server(port);
//        WebAppContext context = new WebAppContext( jettyServer,"rapla","/" );
//        context.setResourceBase(  webappFolder.getAbsolutePath() );
//        context.setMaxFormContentSize(64000000);
//        //MainServlet.serverContainerHint=getStorageName();
//        
//      //  context.addServlet( new ServletHolder(mainServlet), "/*" );
//        jettyServer.start();
//        Handler[] childHandlers = context.getChildHandlersByClass(ServletHandler.class);
//        ServletHolder servlet = ((ServletHandler)childHandlers[0]).getServlet("RaplaServer");
//        mainServlet = (MainServlet) servlet.getServlet();
//        URL server = new URL("http://127.0.0.1:8052/rapla/ping");
//        HttpURLConnection connection = (HttpURLConnection)server.openConnection();
//        int timeout = 10000;
//        int interval = 200;
//        for ( int i=0;i<timeout / interval;i++)
//        {
//            try
//            {
//                connection.connect();
//            } 
//            catch (ConnectException ex) {
//                Thread.sleep(interval);
//            }
//        }
    }
    

    protected ServerServiceImpl getContainer()
    {
        throw new IllegalStateException();
        //return mainServlet.getContainer();
    }

	protected RaplaLocale getRaplaLocale() throws Exception {
        throw new IllegalStateException();
    }

    
    protected void tearDown() throws Exception
    {
        //jettyServer.stop();
        super.tearDown();
    }
    
    protected String getStorageName() {
        return "storage-file";
    }
 }
