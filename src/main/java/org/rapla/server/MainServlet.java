/*--------------------------------------------------------------------------*
 | Copyright (C) 2015 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.server;

import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.rapla.components.util.IOUtil;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaException;
import org.rapla.inject.Injector;
import org.rapla.logger.Logger;
import org.rapla.logger.RaplaBootstrapLogger;
import org.rapla.server.internal.ServerContainerContext;
import org.rapla.server.internal.ServerStarter;
import org.rapla.server.internal.console.ClientStarter;
import org.rapla.server.internal.console.ImportExportManagerContainer;
import org.rapla.server.internal.console.StandaloneStarter;
import org.rapla.server.internal.rest.RestApplication;
import org.rapla.server.servletpages.ServletRequestPreprocessor;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

public class MainServlet extends HttpServlet
{

    private static final long serialVersionUID = 1L;
    private Logger logger = null;
    ServerStarter serverStarter;
    private final HttpServletDispatcher dispatcher;
    private StandaloneStarter standaloneStarter = null;

    public MainServlet()
    {
        dispatcher = new HttpServletDispatcher();
    }

    public static ServerContainerContext createBackendContext(Logger logger, RaplaJNDIContext jndi) throws ServletException
    {
        Object env_raplamail;
        String env_rapladatasource = jndi.lookupEnvString("rapladatasource", true);
        ServerContainerContext backendContext = new ServerContainerContext();

        final String[] split = env_rapladatasource != null ? env_rapladatasource.split(",") : new String[] {};
        for (String key : split)
        {
            String message = null;

            if (key.startsWith("jdbc") || key.equals("rapladb"))
            {
                if (key.equals("rapladb"))
                {
                    key = "jdbc/rapladb";
                }
                Object lookupResource = jndi.lookupResource(key, true);
                if (lookupResource != null)
                {
                    backendContext.addDbDatasource(key, (DataSource) lookupResource);
                }
                else
                {
                    message = "configured datasource " + key + " is not initialized or can't be found.";
                }
            }
            else
            {
                String file = jndi.lookupEnvString(key, true);
                if (file != null)
                {
                    backendContext.addFileDatasource(key, file);
                }
                else
                {
                    message = "configured datasource " + key + " is not initialized or can't be found.";
                }
            }
            if (message != null)
            {
                logger.error(message);
                throw new ServletException(message);
            }
        }
        if (split.length == 0)
        {
            final Object database = jndi.lookupResource("jdbc/rapladb", true);
            if (database != null)
            {
                backendContext.addDbDatasource("jdbc/rapladb", (DataSource) database);
            }
            else
            {
                String file = jndi.lookupEnvString("raplafile", true);
                if (file != null)
                {
                    backendContext.addFileDatasource("raplafile", file);
                }
                else
                {
                    logger.warn("Neither file nor database setup configured.");
                }
            }
        }
        {
            String services = jndi.lookupEnvString("raplaservices", true);
            if (services != null)
            {
                String[] splits = services.split(",");
                for (String key : splits)
                {
                    String[] split2 = key.split("=");
                    String service = split2[0].trim();
                    boolean disabled = split2.length > 1 && split2[1].trim().toLowerCase().equals("false");
                    backendContext.putServiceState(service, !disabled);
                }
            }
        }
        env_raplamail = jndi.lookupResource("mail/Session", false);
        if (env_raplamail != null)
        {
            logger.info("Configured mail service via JNDI");
        }
        backendContext.setMailSession(env_raplamail);
        Runnable runnable = (Runnable) jndi.lookup("rapla_shutdown_command", false);
        backendContext.setShutdownCommand(runnable);
        return backendContext;
    }

    synchronized public void init() throws ServletException
    {
        logger = RaplaBootstrapLogger.createRaplaLogger();
        logger.info("Init RaplaServlet");
        ServletContext context = getServletContext();
        String startupMode;
        RaplaJNDIContext jndi = new RaplaJNDIContext(logger, getInitParameters(context));
        String startupUser = jndi.lookupEnvString("rapla_startup_user", false);
        ServerContainerContext backendContext = createBackendContext(logger, jndi);
        if (jndi.hasContext())
        {
            startupMode = jndi.lookupEnvString("rapla_startup_mode", false);
        }
        else
        {
            startupMode = null;
        }
        if (startupMode == null)
        {
            startupMode = "server";
        }
        try
        {
            // this is the default purpose of the servlet to start rapla server as http servlet
            if (startupMode.equals("server"))
            {
                serverStarter = new ServerStarter(logger, backendContext);
                serverStarter.startServer();
            }
            else if (startupMode.equals("standalone"))
            {
                String realPath = context.getRealPath("/WEB-INF");
                URL downloadUrl = new URL("http://localhost:8051/");//new File(realPath).toURI().toURL();
                final Object localConnector;
                if (jndi.hasContext())
                {
                    localConnector = jndi.lookup("rapla_localconnector", true);
                }
                else
                {
                    throw new RaplaException("Localconnector not set! Can't start standalone");
                }
                serverStarter = new ServerStarter(logger, backendContext);
                standaloneStarter = new StandaloneStarter(logger, backendContext, serverStarter, downloadUrl, startupUser, localConnector);
                serverStarter.startServer();
                standaloneStarter.startClient();
            }
            else if (startupMode.equals("client"))
            {
                Collection<String> instanceCounter = null;
                String selectedContextPath = null;
                @SuppressWarnings("unchecked") Collection<String> instanceCounterLookup = (Collection<String>) jndi.lookup("rapla_instance_counter", false);
                instanceCounter = instanceCounterLookup;
                selectedContextPath = jndi.lookupEnvString("rapla_startup_context", false);
                String contextPath = context.getContextPath();
                if (!contextPath.startsWith("/"))
                {
                    contextPath = "/" + contextPath;
                }
                // don't startup server if contextPath is not selected
                if (selectedContextPath != null)
                {
                    if (!contextPath.equals(selectedContextPath))
                        return;
                }
                else if (instanceCounter != null)
                {
                    instanceCounter.add(contextPath);
                    if (instanceCounter.size() > 1)
                    {
                        String msg = ("Ignoring webapp [" + contextPath + "]. Multiple context found in jetty container " + instanceCounter
                                + " You can specify one via -Dorg.rapla.context=REPLACE_WITH_CONTEXT");
                        logger.error(msg);
                        return;
                    }
                }

                Integer port = null;
                String downloadUrl = null;
                final URL downloadUrl_;
                if (jndi.hasContext())
                {
                    port = (Integer) jndi.lookup("rapla_startup_port", false);
                    downloadUrl = (String) jndi.lookup("rapla_download_url", false);
                }
                if (port == null && downloadUrl == null)
                {
                    throw new RaplaException("Neither port nor download url specified in enviroment! Can't start client");
                }
                if (downloadUrl == null)
                {
                    String url = "http://localhost:" + port + contextPath;
                    if (!url.endsWith("/"))
                    {
                        url += "/";
                    }
                    downloadUrl_ = new URL(url);
                }
                else
                {
                    downloadUrl_ = new URL(downloadUrl);
                }

                ClientStarter guiStarter = new ClientStarter(logger, startupUser, backendContext.getShutdownCommand(), downloadUrl_);
                guiStarter.startClient();
            }
            else if (startupMode.equals("import") || startupMode.equals("export"))
            {
                ServerStarter serverStarter = new ServerStarter(logger, backendContext);
                ImportExportManagerContainer manager = null;
                try
                {
                    manager = serverStarter.createManager();
                    if (startupMode.equals("import"))
                    {
                        manager.doImport();
                    }
                    else
                    {
                        manager.doExport();
                    }
                }
                catch (Exception ex)
                {
                    logger.error(ex.getMessage(), ex);
                }
                finally
                {
                    if (manager != null)
                    {
                        manager.dispose();
                    }
                }
            }
        }
        catch (Exception ex)
        {
            logger.error(ex.getMessage(), ex);
            throw new ServletException(ex.getMessage(), ex);
        }
    }

    @Override public void init(ServletConfig config) throws ServletException
    {
        dispatcher.init(new ServletConfig()
        {
            @Override public String getServletName()
            {
                return config.getServletName();
            }

            @Override public ServletContext getServletContext()
            {
                return config.getServletContext();
            }

            @Override public String getInitParameter(String name)
            {
                switch (name)
                {
                    case "resteasy.servlet.mapping.prefix":
                        return "rapla/";
                    case "resteasy.use.builtin.providers":
                        return "true";
                    case "javax.ws.rs.Application":
                        return RestApplication.class.getCanonicalName();
                }
                return config.getInitParameter(name);
            }

            @Override public Enumeration<String> getInitParameterNames()
            {
                return config.getInitParameterNames();
            }
        });
        super.init(config);
    }

    public void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        Lock readLock = null;
        try
        {
            try
            {
                // we need to get the restart look to avoid serving pages in a restart
                ReadWriteLock restartLock = serverStarter.getRestartLock();
                readLock = RaplaComponent.lock(restartLock.readLock(), 25);
                for (ServletRequestPreprocessor preprocessor : serverStarter.getServletRequestPreprocessors())
                {
                    final HttpServletRequest newRequest = preprocessor.handleRequest(getServletContext(), request, response);
                    if (newRequest != null)
                        request = newRequest;
                    if (response.isCommitted())
                        return;
                }
            }
            catch (RaplaException e)
            {
                java.io.PrintWriter out = null;
                try
                {
                    response.setStatus(500);
                    out = response.getWriter();
                    out.println(IOUtil.getStackTraceAsString(e));
                }
                catch (Exception ex)
                {
                    logger.error("Error writing exception back to client " + e.getMessage());
                }
                finally
                {
                    if (out != null)
                    {
                        out.close();
                    }
                }

                return;
            }
            Injector membersInjector = serverStarter.getMembersInjector();
            request.setAttribute(Injector.class.getCanonicalName(), new Injector()
            {
                @Override public void injectMembers(Object instance) throws Exception
                {
                    try
                    {
                        membersInjector.injectMembers( instance);
                    }
                    catch (Exception ex)
                    {
                        logger.error(ex.getMessage(),ex);
                        throw  ex;
                    }
                }
            });
            dispatcher.service(request, response);
        }
        finally
        {
            try
            {
                RaplaComponent.unlock(readLock);
            }
            catch (IllegalMonitorStateException ex)
            {
                // Released by the restarter
            }
            try
            {
                ServletOutputStream outputStream = response.getOutputStream();
                outputStream.close();
            }
            catch (Exception ex)
            {

            }
            try
            {
                if (standaloneStarter != null)
                {
                    standaloneStarter.requestFinished();
                }
            }
            catch (Exception ex)
            {

            }
        }

    }

    /**
     * Disposes of container manager and container instance.
     */
    public void destroy()
    {
        if (serverStarter != null)
        {
            serverStarter.stopServer();
        }
    }

    //serverVersion = raplaContainer.getContext().lookupDeprecated(RaplaComponent.RAPLA_RESOURCES).getString("rapla.version");
    //  private boolean isClientVersionSupported(String clientVersion) {
    //		// add/remove supported client versions here
    //		return clientVersion.equals(serverVersion) || clientVersion.equals("@doc.version@")   ;
    //	}
    //
    public static Map<String, String> getInitParameters(ServletContext context)
    {
        Map<String, String> initParameters = new HashMap<String, String>();
        Enumeration<String> initParameterNames = context.getInitParameterNames();
        while (initParameterNames.hasMoreElements())
        {
            String key = initParameterNames.nextElement();
            String value = context.getInitParameter(key);
            initParameters.put(key, value);
        }
        return initParameters;
    }

    public static class RaplaJNDIContext
    {
        Logger logger;
        Context env;
        Map<String, String> initParameters;

        public RaplaJNDIContext(Logger logger, Map<String, String> initParameters)
        {
            this.logger = logger;
            this.initParameters = initParameters;
            Context env;
            try
            {
                Context initContext = new InitialContext();
                Context envContext = (Context) initContext.lookup("java:comp");
                env = (Context) envContext.lookup("env");
            }
            catch (Exception e)
            {
                env = null;
                getLogger().warn("No JNDI Enivronment found under java:comp or java:/comp");
            }
            this.env = env;
        }

        public boolean hasContext()
        {
            return env != null;
        }

        public Logger getLogger()
        {
            return logger;
        }

        public Object lookupResource(String lookupname, boolean log)
        {
            String newLookupname = initParameters.get(lookupname);
            if (newLookupname != null && newLookupname.length() > 0)
            {
                lookupname = newLookupname;
            }
            Object result = lookup(lookupname, log);
            return result;
        }

        public String lookupEnvString(String lookupname, boolean log)
        {
            Object result = lookupEnvVariable(lookupname, log);
            return (String) result;

        }

        public Object lookupEnvVariable(String lookupname, boolean log)
        {
            String newEnvname = initParameters.get(lookupname);
            if (newEnvname != null)
            {
                getLogger().info("Using contextparam for " + lookupname + ": " + newEnvname);
            }

            if (newEnvname != null && newEnvname.length() > 0)
            {
                return newEnvname;
            }
            else
            {
                Object result = lookup(lookupname, log);
                return result;
            }
        }

        public Object lookup(String string, boolean warn)
        {
            try
            {
                Object result = env.lookup(string);
                if (result == null && warn)
                {
                    getLogger().warn("JNDI Entry " + string + " not found");
                }

                return result;
            }
            catch (Exception e)
            {
                if (warn)
                {
                    getLogger().warn("JNDI Entry " + string + " not found");
                }
                return null;
            }
        }
    }

}

