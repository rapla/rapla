/*--------------------------------------------------------------------------*
 | Copyright (C) 2013 Christopher Kohlhaas                                  |
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.rapla.ConnectInfo;
import org.rapla.RaplaMainContainer;
import org.rapla.RaplaStartupEnvironment;
import org.rapla.client.ClientService;
import org.rapla.client.ClientServiceContainer;
import org.rapla.client.RaplaClientListenerAdapter;
import org.rapla.components.util.IOUtil;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.SimpleIdentifier;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Container;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaDefaultContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.framework.internal.RaplaJDKLoggingAdapter;
import org.rapla.framework.logger.Logger;
import org.rapla.server.internal.RemoteServiceDispatcher;
import org.rapla.server.internal.RemoteSessionImpl;
import org.rapla.server.internal.ServerServiceImpl;
import org.rapla.server.internal.ShutdownService;
import org.rapla.servletpages.RaplaPageGenerator;
import org.rapla.servletpages.ServletRequestPreprocessor;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.LocalCache;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbrm.RaplaConnectException;
import org.rapla.storage.dbrm.RemoteMethodSerialization;
import org.rapla.storage.dbrm.RemoteMethodStub;
import org.rapla.storage.xml.WrongVersionException;
public class MainServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /** The default config filename is raplaserver.xconf*/
    private ContainerImpl raplaContainer;
    public final static String DEFAULT_CONFIG_NAME = "raplaserver.xconf";

    private long serverStartTime;
    private Logger logger = null;
	private String startupMode =null;
   	private String startupUser = null;
   	private Integer port;
   	private String contextPath;
   	private String env_rapladatasource;
   	private String env_raplafile;
   	private Object env_rapladb;
   	private Object env_raplamail;
   	private String downloadUrl;
   	private String serverVersion;
   	
   	// the following variables are only for non server startup
	Runnable shutdownCommand;
	Semaphore mutex = new Semaphore(1);
	{
    	
	}
	private ConnectInfo reconnect;

    private URL getConfigFile(String entryName, String defaultName) throws ServletException,IOException {
        String configName = getServletConfig().getInitParameter(entryName);
        if (configName == null)
            configName = defaultName;
        if (configName == null)
            throw new ServletException("Must specify " + entryName + " entry in web.xml !");

        String realPath = getServletConfig().getServletContext().getRealPath("/WEB-INF/" + configName);
        if (realPath != null)
        {
        	File configFile = new File(realPath);
        	if (configFile.exists()) {
        		URL configURL = configFile.toURI().toURL();
            	return configURL;
        	}
        }		
        
        URL configURL = getClass().getResource("/raplaserver.xconf");
        if ( configURL == null)
        {
			String message = "ERROR: Config file not found " + configName;
    		throw new ServletException(message);
    	}
        else
        {
        	return configURL;
        }
    }

    /**
     * Initializes Servlet and creates a <code>RaplaMainContainer</code> instance
     *
     * @exception ServletException if an error occurs
     */
    synchronized public void init()
        throws ServletException
    {
    	getLogger().info("Init RaplaServlet");
    	Collection<String> instanceCounter = null;
    	String selectedContextPath = null;
    	Context env;
    	try
    	{
    		Context initContext = new InitialContext();
    		Context envContext = (Context)initContext.lookup("java:comp");
    		env = (Context)envContext.lookup("env");
		} catch (Exception e) {
			env = null;
			getLogger().warn("No JNDI Enivronment found under java:comp or java:/comp");
		}

    	if ( env != null)
    	{
    		env_rapladatasource = lookupEnvVariable(env,  "rapladatasource", true);
    		env_raplafile = lookupEnvVariable(env,"raplafile", true);
    		env_rapladb =   lookupResource(env, "jdbc/rapladb", true);
    		getLogger().info("Passed JNDI Environment rapladatasource=" + env_rapladatasource + " env_rapladb="+env_rapladb + " env_raplafile="+ env_raplafile);
   		
    		if ( env_rapladatasource == null || env_rapladatasource.trim().length() == 0  || env_rapladatasource.startsWith( "${"))
    		{
    			if ( env_rapladb != null)
    			{
    				env_rapladatasource = "rapladb";
    			}
    			else if ( env_raplafile != null)
    			{
    				env_rapladatasource = "raplafile";
    			}
    			else
    			{
    				getLogger().warn("Neither file nor database setup configured.");
    			}
    		}
    		
    		env_raplamail =   lookupResource(env, "mail/Session", false);
        		
    		startupMode = lookupEnvVariable(env,"rapla_startup_mode", false);
    		
    		@SuppressWarnings("unchecked")
    		Collection<String> instanceCounterLookup = (Collection<String>)  lookup(env,"rapla_instance_counter", false);
    		instanceCounter = instanceCounterLookup;
    		
    		selectedContextPath = lookupEnvVariable(env,"rapla_startup_context", false);
    		startupUser = lookupEnvVariable( env, "rapla_startup_user", false);
			shutdownCommand = (Runnable) lookup(env,"rapla_shutdown_command", false);
			port = (Integer) lookup(env,"rapla_startup_port", false);
			downloadUrl = (String) lookup(env,"rapla_download_url", false);

    	}
    	if ( startupMode == null)
		{
			startupMode = "server";
		}
		contextPath = getServletContext().getContextPath();
		if ( !contextPath.startsWith("/"))
		{
			contextPath = "/" + contextPath;
		}
		// don't startup server if contextPath is not selected
		if ( selectedContextPath != null)
		{
			if( !contextPath.equals(selectedContextPath))
				return;
		}
		else if ( instanceCounter != null)
		{
			instanceCounter.add( contextPath);
			if ( instanceCounter.size() > 1)
			{
				String msg = ("Ignoring webapp ["+ contextPath +"]. Multiple context found in jetty container " + instanceCounter + " You can specify one via -Dorg.rapla.context=REPLACE_WITH_CONTEXT");
				getLogger().error(msg);
				return;
			}
		}
		startServer(startupMode);
		if ( startupMode.equals("standalone") || startupMode.equals("client"))
		{
			try {
	    		mutex.acquire();
	    	} catch (InterruptedException e) {
			}
			startGUI(startupMode);
		}
    }

	private Object lookupResource(Context env, String lookupname, boolean log) {
		String newLookupname = getServletContext().getInitParameter(lookupname);
		if (newLookupname != null && newLookupname.length() > 0)
		{
			lookupname = newLookupname;
		}
		Object result = lookup(env,lookupname, log);
		return result;
	}

	private String lookupEnvVariable(Context env, String lookupname, boolean log) {
		String newEnvname = getServletContext().getInitParameter(lookupname);
		if ( newEnvname != null)
		{
			getLogger().info("Using contextparam for " + lookupname + ": " + newEnvname);
		}

		if (newEnvname != null && newEnvname.length() > 0 )
		{
			return newEnvname;
		}
		else
		{
			String result = (String) lookup(env,lookupname, log);
			return result;
		}
	}
    
    private Object lookup(Context env, String string, boolean warn) {
    	try {
    		Object result = env.lookup( string);
     		if ( result == null && warn)
    		{
    			getLogger().warn("JNDI Entry "+ string + " not found");
    		}
  
    		return result;
    	} catch (Exception e) {
    		if ( warn )
    		{
    			getLogger().warn("JNDI Entry "+ string + " not found");
    		}
    		return null;
		}
	}

	private void startGUI(final String startupMode) throws ServletException {
		ConnectInfo connectInfo = null;
		if (startupMode.equals("standalone"))
		{
			try
			{
				String username = startupUser;
				if ( username == null )
				{
					username = getFirstAdmin();
				}
				if ( username != null)
				{
					connectInfo = new ConnectInfo(username, "".toCharArray());
				}
			}
			catch (RaplaException ex)
			{
				getLogger().error(ex.getMessage(),ex);
			}
			
		}
		startGUI(  startupMode, connectInfo);
		if ( startupMode.equals("standalone") ||  startupMode.equals("client"))
        {
        	try {
				mutex.acquire();
				while ( reconnect != null )
				{
					
					 raplaContainer.dispose();
	                 try {
	                	
	                	 if ( startupMode.equals("client"))
	                	 {
	                		 initContainer(startupMode);
	                	 }
	                	 else if ( startupMode.equals("standalone"))
	                	 {
	                		 startServer("standalone");
	                	 }
	                	 if ( startupMode.equals("standalone") && reconnect.getUsername() == null)
	 					 {
	 						String username = getFirstAdmin();
	 						if ( username != null)
	 						{
	 							reconnect= new ConnectInfo(username, "".toCharArray());
	 						}
	 					 }
	                     startGUI(startupMode, reconnect);
	                     mutex.acquire();
	                 } catch (Exception ex) {
	                     getLogger().error("Error restarting client",ex);
	                     exit();
	                     return;
	                 }
				}
			} catch (InterruptedException e) {
				
			}
        }		
	}

	protected String getFirstAdmin() throws RaplaContextException,
			RaplaException {
		String username = null;
		StorageOperator operator = getServer().getContext().lookup(StorageOperator.class);
		for (User u:operator.getObjects( User.class))
		{
		    if ( u.isAdmin())
		    {
		        username = u.getUsername();
			    break;
		    }
		}
		return username;
	}
	
	public void startGUI( final String startupMode, ConnectInfo connectInfo) throws ServletException {
        try
        {
            if ( startupMode.equals("standalone") || startupMode.equals("client"))
            {
                ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            	try
            	{
            		Thread.currentThread().setContextClassLoader( ClassLoader.getSystemClassLoader());
            		ClientServiceContainer clientContainer = raplaContainer.getContext().lookup(ClientServiceContainer.class );
            		ClientService client =  clientContainer.getContext().lookup( ClientService.class);
	            	client.addRaplaClientListener(new RaplaClientListenerAdapter() {
	                         public void clientClosed(ConnectInfo reconnect) {
	                             MainServlet.this.reconnect = reconnect;
	                             if ( reconnect != null) {
	                                mutex.release();
	                             } else {
	                                 exit();
	                             }
	                         }
	                        
							public void clientAborted()
							{
								exit();
							}
	                     });
					clientContainer.start(connectInfo);
            	}
            	finally
            	{
            		Thread.currentThread().setContextClassLoader( contextClassLoader);
            	}
            } 
            else if (!startupMode.equals("server"))
            {
            	exit();
            }
        }
        catch( Exception e )
        {
        	getLogger().error("Could not start server", e);
        	if ( raplaContainer != null)
        	{
        		raplaContainer.dispose();
        	}
            throw new ServletException( "Error during initialization see logs for details: " + e.getMessage(), e );
        }
       // log("Rapla Servlet started");

    }

	protected void startServer(final String startupMode)
			throws ServletException {

        try
        {
        	initContainer(startupMode);
			if ( startupMode.equals("import"))
			{
				ImportExportManager manager = raplaContainer.getContext().lookup(ImportExportManager.class);
				manager.doImport();
				exit();
			}
			else if (startupMode.equals("export"))
			{
				ImportExportManager manager = raplaContainer.getContext().lookup(ImportExportManager.class);
				manager.doExport();
				exit();
			}
			else if ( startupMode.equals("server") || startupMode.equals("standalone") )
    		{
				serverStartTime = System.currentTimeMillis();
    		    //lookup shutdownService
				if ( startupMode.equals("server"))
    		    {
	    		    startServer_(startupMode);
    		    }
				else
				{
					// We start the standalone server before the client to prevent jndi lookup failures 
					ServerServiceImpl server = (ServerServiceImpl)getServer();
					raplaContainer.addContainerProvidedComponentInstance(RemoteMethodStub.class, server);
					RemoteSessionImpl standaloneSession = new RemoteSessionImpl(server.getContext(), "session") {
						public void logout() throws RaplaException {
						}
						
					};
					server.setStandalonSession( standaloneSession);
				
				}
    		}
        }
        catch( Exception e )
        {
    		getLogger().error(e.getMessage(), e);
        	String message = "Error during initialization see logs for details: " + e.getMessage();
    		if ( raplaContainer != null)
        	{
        		raplaContainer.dispose();
        	}
        	if ( shutdownCommand != null)
        	{
        		shutdownCommand.run();
        	}
        	
        	throw new ServletException( message,e);
        }
	}
	
	protected void startServer_(final String startupMode)
			throws RaplaContextException {
		final Logger logger = getLogger();
		logger.info("Rapla server started");
		ServerServiceImpl server = (ServerServiceImpl)getServer();
		server.setShutdownService( new ShutdownService() {
		        public void shutdown(final boolean restart) {
		       	 	logger.info( "Stopping  Server");
		       	 	stopServer();
		       	 	if ( restart)
		       	 	{
		       	 		try {
		       	 			logger.info( "Restarting Server");
		       	 			MainServlet.this.startServer(startupMode);
		       	 		} catch (Exception e) {
		       	 			logger.error( "Error while restarting Server", e );
		       	 		}
		       	 	}
		        }
		    });
	}

	protected void initContainer(String startupMode) throws ServletException, IOException,
			MalformedURLException, Exception, RaplaContextException {
		URL configURL = getConfigFile("config-file",DEFAULT_CONFIG_NAME);
		//URL logConfigURL = getConfigFile("log-config-file","raplaserver.xlog").toURI().toURL();

		RaplaStartupEnvironment env = new RaplaStartupEnvironment();
		env.setStartupMode( StartupEnvironment.CONSOLE);
		env.setConfigURL( configURL );
		if ( startupMode.equals( "client"))
		{
			if ( port != null)
			{
				String url = downloadUrl;
				if ( url == null)
				{
					url = "http://localhost:" + port+ contextPath;
					if (! url.endsWith("/"))
					{
						url += "/";
					}
				}
				env.setDownloadURL( new URL(url));
			}
		}
         // env.setContextRootURL( contextRootURL );
		//env.setLogConfigURL( logConfigURL );
    	
		RaplaDefaultContext context = new RaplaDefaultContext();
		if ( env_rapladatasource != null)
		{
			context.put(RaplaMainContainer.ENV_RAPLADATASOURCE, env_rapladatasource);
		}
		if ( env_raplafile != null)
		{
			context.put(RaplaMainContainer.ENV_RAPLAFILE, env_raplafile);
		}
		if ( env_rapladb != null)
		{
			context.put(RaplaMainContainer.ENV_RAPLADB, env_rapladb);
		}
		if ( env_raplamail != null)
		{
			context.put(RaplaMainContainer.ENV_RAPLAMAIL, env_raplamail);
			getLogger().info("Configured mail service via JNDI");
		}

		raplaContainer = new RaplaMainContainer( env, context );
		logger = raplaContainer.getContext().lookup(Logger.class);
		serverVersion = raplaContainer.getContext().lookup(RaplaComponent.RAPLA_RESOURCES).getString("rapla.version");
	}
	
	 private void exit() {
		MainServlet.this.reconnect = null;
		 mutex.release();
		 if ( shutdownCommand != null)
		 {
			 shutdownCommand.run();
		 }
		
	 }

    
    public void service( HttpServletRequest request, HttpServletResponse response )  throws IOException, ServletException
    {
        RaplaPageGenerator  servletPage;
        try {
        	RaplaContext context = getServer().getContext();
			Collection<ServletRequestPreprocessor> processors = getServer().lookupServicesFor(RaplaServerExtensionPoints.SERVLET_REQUEST_RESPONSE_PREPROCESSING_POINT);
		    for (ServletRequestPreprocessor preprocessor: processors)
			{
	            final HttpServletRequest newRequest = preprocessor.handleRequest(context, getServletContext(), request, response);
	            if (newRequest != null)
	                request = newRequest;
	            if (response.isCommitted())
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
	            }
	        }
	        //String servletPath = request.getServletPath();
	        int rpcIndex=requestURI.indexOf("/rapla/rpc/") ;
	        if ( rpcIndex>= 0)  {
	            handleRPCCall( request, response, requestURI );
	            return;
	        }
	        if ( page == null || page.trim().length() == 0) {
	            page = "index";
	        }
	
            servletPage = getServer().getWebpage( page);
            if ( servletPage == null)
            {
            	response.setStatus( 404 );
              	java.io.PrintWriter out = response.getWriter();
            	String message = "404: Page " + page + " not found in Rapla context";
    			out.print(message);
    			getLogger().getChildLogger("server.html.404").warn( message);
    			out.close();
    			return;
            }
            ServletContext servletContext = getServletContext();
            servletPage.generatePage( servletContext, request, response);
        } 
        catch (RaplaException e) 
        {
        	try
        	{
	        	response.setStatus( 500 );
	        	
	          	java.io.PrintWriter out = response.getWriter();
	        	out.println(IOUtil.getStackTraceAsString( e));
	        	out.close();
        	}
        	catch (Exception ex)
        	{
        		getLogger().error("Error writing exception back to client " + e.getMessage());
        	}
            return;
        }
        finally
        {
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
    
    private  void handleRPCCall( HttpServletRequest request, HttpServletResponse response, String requestURI ) 
    {
    	
    	int rpcIndex=requestURI.indexOf("/rapla/rpc/") ;
        int sessionParamIndex = requestURI.indexOf(";");
        int endIndex = sessionParamIndex >= 0 ? sessionParamIndex : requestURI.length(); 
        String methodName = requestURI.substring(rpcIndex + "/rapla/rpc/".length(),endIndex);
        final HttpSession session = request.getSession( true );
        String sessionId = session.getId();
        response.addCookie(new Cookie("JSESSIONID", sessionId));
        boolean dispatcherExceptionThrown = false;
        try
        {
			final Map<String,String[]> originalMap = request.getParameterMap();
			final Map<String,String> parameterMap = makeSinglesAndRemoveVersion(originalMap);
            final ServerServiceContainer serverContainer = getServer();
            final RaplaContext context = serverContainer.getContext();
			final RemoteSession  remoteSession = new RemoteSessionImpl(context, session.getId()){

                public void logout() throws RaplaException {
                    setUser( null ); 
                }
                
                @Override
                public void setUser(User user) {
                	super.setUser(user);
                	if (user == null )
                	{
                        session.removeAttribute("userid");
                	}
                	else
                	{
                		session.setAttribute("userid", "" + ((SimpleIdentifier)((RefEntity<?>)user).getId()).getKey());
                	}
                }
            };
           
            String clientVersion = request.getParameter("v");
            if ( clientVersion != null )
            {
            	if ( !isClientVersionSupported(clientVersion))
                {
                	String message = getVersionErrorText(request, methodName, clientVersion);
                	response.addHeader("X-Error-Classname",  WrongVersionException.class.getName());
                	response.addHeader("X-Error-Stacktrace", message );
                	response.setStatus( 500);
                	return;
                }
            }
            else
            {
            	//if ( !serverVersion.equals( clientVersion ) )
	             String message = getVersionErrorText(request, methodName, "");
	           	 response.addHeader("X-Error-Stacktrace", message );
	             RaplaException e1= new RaplaException( message );   
	           	 ByteArrayOutputStream outStream = new ByteArrayOutputStream();
	           	 ObjectOutputStream exout = new ObjectOutputStream( outStream);
	           	 exout.writeObject( e1);
	           	 exout.flush();
	           	 exout.close();
	           	 byte[] out = outStream.toByteArray();
	           	 try
	           	 {
	           		 ServletOutputStream outputStream = response.getOutputStream();
	           		 outputStream.write( out );
	           		 outputStream.close();
	           	 }
	           	 catch (Exception ex)
	           	 {
	           		 getLogger().error( " Error writing exception back to client " + ex.getMessage());
	           	 }
                 response.setStatus( 500);
                 return;
            }
            Object attribute = session.getAttribute("userid");
			final Comparable userId = attribute != null ? LocalCache.getId(User.TYPE,(String) attribute) : null;

            if ( userId != null)
            {
            	StorageOperator operator= context.lookup( ServerService.class).getFacade().getOperator();
            	if ( operator.isConnected())
            	{
	            	try 
	            	{
		            	User user = (User) ((CachableStorageOperator)operator).resolveId( userId); 
		                ((RemoteSessionImpl)remoteSession).setUser( user);
	            	}
	            	catch (EntityNotFoundException ex)
	            	{
	            		throw ex;
	            	}
            	}
            	else
            	{
            	    response.addHeader("X-Error-Classname",  RaplaConnectException.class.getName());
                  	response.addHeader("X-Error-Stacktrace", "RaplaServer is shutting down" );
                    response.setStatus( 500);
            	}
            }
            Long sessionstart = (Long)session.getAttribute("serverstarttime");
            if ( sessionstart != null)
            {
                // We have to reset the client because the server restarted
                if ( sessionstart < serverStartTime )
                {
                    // this will cause most statefull methods to fail because the user is not set
                    ((RemoteSessionImpl)remoteSession).setUser( null);
                }
            }
            if ( methodName.endsWith("login"))
            {
                session.setAttribute("serverstarttime",  new Long(this.serverStartTime));
            }
            	
        	
        	RemoteServiceDispatcher serviceDispater= context.lookup( RemoteServiceDispatcher.class);
            byte[] out;
            try
            {
            		out = serviceDispater.dispatch(remoteSession, methodName, parameterMap);
            }
            catch (Exception ex)
            {
            	dispatcherExceptionThrown = true;
            	throw ex;
            }
            //String test = new String( out);
            response.setContentType( "text/html; charset=utf-8");
            try
        	{
            	response.getOutputStream().write( out);
            	response.flushBuffer();
            	response.getOutputStream().close();
            }
        	catch (Exception ex)
            {
            	getLogger().error( " Error writing exception back to client " + ex.getMessage());
            }	
        }
        catch (Exception e)
        {
        	if ( !dispatcherExceptionThrown)
        	{
        		getLogger().error(e.getMessage(), e);
        	}
        	try
        	{
        		String message = e.getMessage();
	            String name = e.getClass().getName();
	            if ( message == null )
	            {
					message = name;
	            }
	            response.addHeader("X-Error-Stacktrace", message );
	            response.addHeader("X-Error-Classname",  name);
	            String param = RemoteMethodSerialization.serializeExceptionParam( e);
	            if ( param != null)
	            {
	            	response.addHeader("X-Error-Param",  param);
	            }
	            response.setStatus( 500);
	        }
	        catch (Exception ex)
	        {
	        	getLogger().error( " Error writing exception back to client " + e.getMessage(), ex);
	        }
        }

    }
    
    private boolean isClientVersionSupported(String clientVersion) {
		// add/remove supported client versions here 
		return clientVersion.equals(serverVersion) || clientVersion.equals("@doc.version@")  || clientVersion == "1.7.4RC2"  || clientVersion == "1.7.4";
	}

	private String getVersionErrorText(HttpServletRequest request, String methodName, String clientVersion) 
	{
		String requestUrl = request.getRequestURL().toString();
		int indexOf = requestUrl.indexOf( "rpc/"+methodName);
		if (indexOf>=0 ) 
		{
			requestUrl = requestUrl.substring( 0, indexOf) ;
		}
		String message = "Incompatible client version " + clientVersion + ". Expected " + serverVersion + " Open " + requestUrl + " in your browser and click on the webstart or applet link to update your client.";
		return message;
	}
    
    public static String serverContainerHint = null;
	protected ServerServiceContainer getServer() throws RaplaContextException {
    	String hint = serverContainerHint;
    	if  (hint == null)
    	{
    		//hint = startupMode;
    		return raplaContainer.getContext().lookup( ServerServiceContainer.class);
    	}
    	return raplaContainer.lookup( ServerServiceContainer.class, hint);
    }

    private Map<String,String> makeSinglesAndRemoveVersion( Map<String, String[]> parameterMap )
    {
        TreeMap<String,String> singlesMap = new TreeMap<String,String>();
        for (Iterator<String> it = parameterMap.keySet().iterator();it.hasNext();)
        {
            String key = it.next();
            if ( key.toLowerCase().equals("v"))
            {
            	continue;
            }
            String[] values =  parameterMap.get( key);
            if ( values != null && values.length > 0 )
            {
                singlesMap.put( key,values[0]);
            }
            else
            {
                singlesMap.put( key,null);
            }
        }

        return singlesMap;

    }

    private void stopServer() {
    	if ( raplaContainer == null)
    	{
    		return;
    	}
        try {
            raplaContainer.dispose();
        } catch (Exception ex) {
        	String message = "Error while stopping server ";
        	getLogger().error(message + ex.getMessage());
        }
    }

    /**
     * Disposes of container manager and container instance.
     */
    public void destroy()
    {
        stopServer();
    }

    public RaplaContext getContext()
    {
        return raplaContainer.getContext();
    }
    
    public Container getContainer()
    {
        return raplaContainer;
    }

	public void doImport() throws RaplaException {
		ImportExportManager manager = raplaContainer.getContext().lookup(ImportExportManager.class);
		manager.doImport();
	}
	
	public void doExport() throws RaplaException {
		ImportExportManager manager = raplaContainer.getContext().lookup(ImportExportManager.class);
		manager.doExport();
	}

    public Logger getLogger() 
    {
    	if ( logger == null)
    	{
    		return new RaplaJDKLoggingAdapter().get(); 
    	}
    	return logger;
	}
    
  
	
}

