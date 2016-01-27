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

import org.rapla.components.util.IOUtil;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.framework.logger.RaplaBootstrapLogger;
import org.rapla.server.internal.ServerContainerContext;
import org.rapla.server.internal.ServerStarter;
import org.rapla.server.internal.console.ClientStarter;
import org.rapla.server.internal.console.ImportExportManagerContainer;
import org.rapla.server.internal.console.StandaloneStarter;
import org.rapla.server.servletpages.ServletRequestPreprocessor;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

public class MainServlet extends HttpServlet
{

    private static final long serialVersionUID = 1L;
    private Logger logger = null;
    ServerStarter serverStarter;

    public static ServerContainerContext createBackendContext(Logger logger, RaplaJNDIContext jndi)
    {
        String env_raplafile;
        DataSource env_rapladb = null;
        Object env_raplamail;
        String env_rapladatasource = jndi.lookupEnvString("rapladatasource", true);
        ServerContainerContext backendContext = new ServerContainerContext();

        final String[] split = env_rapladatasource != null ? env_rapladatasource.split(",") : new String[] {};
        for (String key : split)
        {
            String message = null;

            if (key.startsWith("jdbc") || key.equals("rapladb"))
            {
                if ( key.equals("rapladb"))
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
                throw new RaplaException(message);
            }
        }
        if (split.length == 0)
        {
            final Object database = jndi.lookupResource("jdbc/rapladb", true);
            if ( database != null)
            {
                backendContext.addDbDatasource("jdbc/rapladb",(DataSource)database);
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
        env_raplamail = jndi.lookupResource("mail/Session", false);
        if (env_raplamail != null)
        {
            logger.info("Configured mail service via JNDI");
        }
        backendContext.setMailSession(env_raplamail);
        Runnable runnable = (Runnable) jndi.lookup("rapla_shutdown_command", false);
        backendContext.setShutdownCommand( runnable);
        return backendContext;
    }

    synchronized public void init() throws ServletException
    {
        logger = RaplaBootstrapLogger.createRaplaLogger();
        logger.info("Init RaplaServlet");
        String startupMode;
        RaplaJNDIContext jndi = new RaplaJNDIContext(logger, getInitParameters());
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
                String realPath = getServletConfig().getServletContext().getRealPath("/WEB-INF");
                URL downloadUrl = new File(realPath).toURI().toURL();
                StandaloneStarter guiStarter = new StandaloneStarter(logger, backendContext, downloadUrl, startupUser);
                guiStarter.startStandalone();
            }
            else if (startupMode.equals("client"))
            {
                Collection<String> instanceCounter = null;
                String selectedContextPath = null;
                @SuppressWarnings("unchecked") Collection<String> instanceCounterLookup = (Collection<String>) jndi.lookup("rapla_instance_counter", false);
                instanceCounter = instanceCounterLookup;
                selectedContextPath = jndi.lookupEnvString("rapla_startup_context", false);
                String contextPath = getServletContext().getContextPath();
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
        }
        catch (Exception ex)
        {
            logger.error(ex.getMessage(), ex);
            throw new ServletException(ex.getMessage(), ex);
        }

        if (startupMode.equals("import") || startupMode.equals("export"))
        {
            ImportExportManagerContainer manager = null;
            try
            {
                manager = new ImportExportManagerContainer(logger, backendContext);
                if (startupMode.equals("import"))
                {
                    manager.doImport();
                }
                else
                {
                    manager.doExport();
                }
            }
            catch (RaplaException ex)
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
            serverStarter.getServer().service(request, response);
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
    private Map<String, String> getInitParameters()
    {
        ServletContext context = getServletContext();
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

    private static class RaplaJNDIContext
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

