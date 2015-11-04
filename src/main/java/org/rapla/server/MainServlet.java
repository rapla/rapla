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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.components.util.IOUtil;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.framework.logger.RaplaBootstrapLogger;
import org.rapla.server.internal.RaplaJNDIContext;
import org.rapla.server.internal.ServerStarter;
import org.rapla.server.internal.console.ClientStarter;
import org.rapla.server.internal.console.ImportExportManagerContainer;
import org.rapla.server.internal.console.StandaloneStarter;
import org.rapla.server.servletpages.RaplaPageGenerator;
import org.rapla.server.servletpages.ServletRequestPreprocessor;
public class MainServlet extends HttpServlet {
    
    private static final long serialVersionUID = 1L;
    private Logger logger = null;
    ServerStarter serverStarter;
	
    synchronized public void init() throws ServletException
    {
        logger = RaplaBootstrapLogger.createRaplaLogger();
    	logger.info("Init RaplaServlet");
    	String startupMode;
        Boolean env_development;
		RaplaJNDIContext jndi = new RaplaJNDIContext(logger, getInitParameters());
    	if ( jndi.hasContext())
    	{
    		startupMode = jndi.lookupEnvString("rapla_startup_mode", false);
    		env_development = (Boolean) jndi.lookupEnvVariable( "rapla_development", false);
//    		if ( env_development != null && env_development)
//    		{
//                 ContainerImpl.DEVELOPMENT_RESSOLVING = true;
//    		}
    	}
    	else
    	{
    	    startupMode = null;
            env_development = null;
    	}
    	if ( startupMode == null)
		{
			startupMode = "server";
		}
        try
        {
            // this is the default purpose of the servlet to start rapla server as http servlet
            if ( startupMode.equals("server"))
    		{
				// Does not work with maven yet
//    	        if ( env_development != null && env_development )
//    	        {
//    	            JettyDevelopment.addDevelopmentWarFolders(logger);
//    	        }
    		    serverStarter  = new ServerStarter(logger, jndi);
    		    serverStarter.startServer();
    		}
            else if ( startupMode.equals("standalone"))
    		{
                String realPath = getServletConfig().getServletContext().getRealPath("/WEB-INF");
                URL downloadUrl = new File(realPath).toURI().toURL();
                StandaloneStarter guiStarter = new StandaloneStarter(logger,jndi,downloadUrl);
                guiStarter.startStandalone(  );
            }
            else if (  startupMode.equals("client"))
    		{
                String contextPath = getServletContext().getContextPath();
                ClientStarter guiStarter = new ClientStarter(logger,jndi, contextPath);
                guiStarter.startClient();
    		}
        }
        catch (Exception ex)
        {
            logger.error(ex.getMessage(),ex);
            throw new ServletException(ex.getMessage(),ex);
        }
		
		if ( startupMode.equals("import") || startupMode.equals("export"))
		{
		    ImportExportManagerContainer manager = null;
            try {
                manager = new ImportExportManagerContainer(logger, jndi);
    		    if ( startupMode.equals("import"))
    		    {
    		        manager.doImport();
    		    }
    		    else
    		    {
    		        manager.doExport();
    		    }
            } catch (RaplaException ex) {
                logger.error(ex.getMessage(),ex);
            } 
            finally
            {
                if ( manager != null)
                {
                    manager.dispose();
                }
            }
		}
    }
		
	public void service( HttpServletRequest request, HttpServletResponse response )  throws IOException, ServletException
    {
    	RaplaPageGenerator  servletPage;
    	Lock readLock = null;
    	try
    	{
    	    try
    	    {
    	        // we need to get the restart look to avoid serving pages in a restart
    	        ReadWriteLock restartLock = serverStarter.getRestartLock();
    	        readLock = RaplaComponent.lock( restartLock.readLock(), 25);
            	for (ServletRequestPreprocessor preprocessor: serverStarter.getServletRequestPreprocessors())
    			{
    	            final HttpServletRequest newRequest = preprocessor.handleRequest( getServletContext(), request, response);
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
	                response.setStatus( 500 );
	                out = response.getWriter();
	                out.println(IOUtil.getStackTraceAsString( e));
	            }
	            catch (Exception ex)
                {
                    logger.error("Error writing exception back to client " + e.getMessage());
                }
	            finally
	            {
	                if ( out != null)
	                {
	                    out.close();
	                }
	            }
	           
	            return;
	        }

	        String page =  request.getParameter("page");
	        String requestURI =request.getRequestURI();
	        if ( page == null)
	        {
	            String raplaPrefix = "rapla/";
	            String contextPath = request.getContextPath();
	            String toParse; 
	            if (requestURI.startsWith( contextPath))
	            {
	            	toParse = requestURI.substring( contextPath.length());
	            }
	            else
	            {
	            	toParse = requestURI;
	            }
	            int pageContextIndex = toParse.lastIndexOf(raplaPrefix);
	            if ( pageContextIndex>= 0)
	            {
	                page = toParse.substring( pageContextIndex + raplaPrefix.length());
	                int firstSeparator = page.indexOf('/');
	                if ( firstSeparator>1)
	                {
	                    page = page.substring(0,firstSeparator );
	                }
	            }
	        }
	        if ( page == null || page.trim().length() == 0) {
	            page = "index";
	        }
            servletPage = serverStarter.getServer().getWebpage( page);
            if ( servletPage == null)
            {
            	response.setStatus( 404 );
                java.io.PrintWriter out = null;
                try
                {
                	out =	response.getWriter();
                	String message = "404: Page " + page + " not found in Rapla context";
        			out.print(message);
        			logger.getChildLogger("server.html.404").warn( message);
                } finally 
                {
                    if ( out != null)
                    {
                        out.close();
                    }
                }
                
    			return;
            }
            ServletContext servletContext = getServletContext();
            servletPage.generatePage( servletContext, request, response);
        } 

        finally
        {
        	try
        	{
        		RaplaComponent.unlock( readLock );
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
    private Map<String, String> getInitParameters() {
        ServletContext context = getServletContext();
        Map<String,String> initParameters = new HashMap<String, String>();
        Enumeration<String> initParameterNames = context.getInitParameterNames();
        while (initParameterNames.hasMoreElements())
        {
            String key = initParameterNames.nextElement();
            String value = context.getInitParameter( key);
            initParameters.put(key, value);
        }
        return initParameters;
    }
}

