package org.rapla;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.EnumSet;
import java.util.Map;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.jboss.resteasy.plugins.server.servlet.ResteasyBootstrap;
import org.rapla.rest.server.RestApplication;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.internal.ServerServiceImpl;
import org.rapla.server.internal.rest.validator.RaplaRestDaggerContextProvider;

import dagger.MembersInjector;

@SuppressWarnings("restriction")
public abstract class ServletTestBase
{

    static public Server createServer(final ServerServiceContainer serverService,int port) throws Exception
    {
        final ServerServiceImpl serverServiceImpl = (ServerServiceImpl) serverService;
        File webappFolder = new File("test");
        Server jettyServer = new Server(port);
        String contextPath = "rapla";
        WebAppContext context = new WebAppContext(jettyServer, contextPath, "/");
//        context.addFilter(org.rapla.server.HTTPMethodOverrideFilter.class, "/rapla/*", null);
        context.addEventListener(new ResteasyBootstrap());
        final Filter filter = new Filter()
        {
            @Override
            public void init(FilterConfig filterConfig) throws ServletException
            {
                // do not init context as given from outside
            }

            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
            {
                request.setAttribute(RaplaRestDaggerContextProvider.RAPLA_CONTEXT, serverServiceImpl.getMembersInjector());
                chain.doFilter(request, response);
            }

            @Override
            public void destroy()
            {

            }
        };
        final FilterHolder holder = new FilterHolder(filter);
        context.addFilter(holder, "/*", EnumSet.allOf(DispatcherType.class));
        context.setInitParameter("resteasy.servlet.mapping.prefix", "/rapla");
        context.setInitParameter("resteasy.use.builtin.providers", "false");
        context.setInitParameter("javax.ws.rs.Application", RestApplication.class.getCanonicalName());
        context.setResourceBase(webappFolder.getAbsolutePath());
        context.setMaxFormContentSize(64000000);

        final ServletHolder servletHolder = new ServletHolder(HttpServletDispatcher.class);
        servletHolder.setServlet(new HttpServletDispatcher());
        context.addServlet(servletHolder, "/rapla/*");
        jettyServer.start();
        Handler[] childHandlers = context.getChildHandlersByClass(ServletHandler.class);
        final ServletHandler childHandler = (ServletHandler) childHandlers[0];
        final ServletHolder[] servlets = childHandler.getServlets();
        ServletHolder servlet = servlets[0];

                URL server = new URL("http://127.0.0.1:"+port+"/rapla/auth");
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
