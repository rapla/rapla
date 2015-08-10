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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.ConnectInfo;
import org.rapla.RaplaMainContainer;
import org.rapla.RaplaStartupEnvironment;
import org.rapla.client.ClientService;
import org.rapla.client.ClientServiceContainer;
import org.rapla.client.RaplaClientListenerAdapter;
import org.rapla.components.util.IOUtil;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.User;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Container;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaDefaultContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.ServiceListCreator;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.framework.internal.RaplaJDKLoggingAdapter;
import org.rapla.framework.logger.Logger;
import org.rapla.server.internal.ServerServiceImpl;
import org.rapla.server.internal.ShutdownService;
import org.rapla.servletpages.RaplaPageGenerator;
import org.rapla.servletpages.ServletRequestPreprocessor;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbrm.RemoteMethodStub;
import org.rapla.storage.dbrm.WrongRaplaVersionException;
public class MainServlet extends HttpServlet {
    
    private static final String RAPLA_RPC_PATH = "/rapla/rpc/";

	private static final long serialVersionUID = 1L;

    /** The default config filename is raplaserver.xconf*/
    private ContainerImpl raplaContainer;
    public final static String DEFAULT_CONFIG_NAME = "raplaserver.xconf";

    private Logger logger = null;
	private String startupMode =null;
   	private String startupUser = null;
   	private Integer port;
   	private String contextPath;
   	private String env_rapladatasource;
   	private String env_raplafile;
   	private Object env_rapladb;
   	private Object env_raplamail;
   	private Boolean env_development;
   	private String downloadUrl;
   	private String serverVersion;
   	
   	private ServerServiceImpl server;
    
   	private Runnable shutdownCommand;
   	private Collection<ServletRequestPreprocessor> processors;
   	private ReadWriteLock restartLock = new ReentrantReadWriteLock();

   	// the following variables are only for non server startup
   	private Semaphore guiMutex = new Semaphore(1);
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
    		env_rapladatasource = lookupEnvString(env,  "rapladatasource", true);
    		env_raplafile = lookupEnvString(env,"raplafile", true);
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
    		startupMode = lookupEnvString(env,"rapla_startup_mode", false);
    		env_development = (Boolean) lookupEnvVariable(env, "rapla_development", false);
    		@SuppressWarnings("unchecked")
    		Collection<String> instanceCounterLookup = (Collection<String>)  lookup(env,"rapla_instance_counter", false);
    		instanceCounter = instanceCounterLookup;
    		
    		selectedContextPath = lookupEnvString(env,"rapla_startup_context", false);
    		startupUser = lookupEnvString( env, "rapla_startup_user", false);
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
	    		guiMutex.acquire();
	    	} catch (InterruptedException e) {
			}
			try
			{
			    startGUI(startupMode);
			} 
			catch (Exception ex)
			{
			    exit();
			    throw new ServletException(ex);
			}
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

	private String lookupEnvString(Context env, String lookupname, boolean log) {
		Object result = lookupEnvVariable(env, lookupname, log);
		return (String) result;
	
	}			
	
	private Object lookupEnvVariable(Context env, String lookupname, boolean log) {
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
			Object result = lookup(env,lookupname, log);
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
				guiMutex.acquire();
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
	                     guiMutex.acquire();
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

	protected String getFirstAdmin() throws RaplaContextException,	RaplaException {
		String username = null;
		StorageOperator operator = server.getContext().lookup(StorageOperator.class);
		Set<User> sorted = new TreeSet<User>( User.USER_COMPARATOR);
		sorted.addAll( operator.getUsers());
		for (User u:sorted)
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
	                                guiMutex.release();
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
			    String hint = serverContainerHint != null ? serverContainerHint :"*";
                // Start the server via lookup
			    // We start the standalone server before the client to prevent jndi lookup failures 
			    server = (ServerServiceImpl) raplaContainer.lookup( ServerServiceContainer.class, hint);
			    processors = server.lookupServicesFor(RaplaServerExtensionPoints.SERVLET_REQUEST_RESPONSE_PREPROCESSING_POINT);
			    final Logger logger = getLogger();
		        logger.info("Rapla server started");
				if ( startupMode.equals("server"))
    		    {
				    // if 
	    		    setShutdownService(startupMode);
    		    }
				else
				{
					raplaContainer.addContainerProvidedComponentInstance(RemoteMethodStub.class, server);
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
	
	protected void setShutdownService(final String startupMode)	 {
		server.setShutdownService( new ShutdownService() {
		        public void shutdown(final boolean restart) {
		        	Lock writeLock;
		        	try
		        	{
		        		try
		        		{
		        			RaplaComponent.unlock( restartLock.readLock());
		        		}
		        		catch (IllegalMonitorStateException ex)
		        		{
		        			getLogger().error("Error unlocking read for restart " + ex.getMessage());
		        		}
		        		writeLock = RaplaComponent.lock( restartLock.writeLock(), 60);
		        	}
		        	catch (RaplaException ex)
		        	{ 
		        		getLogger().error("Can't restart server " + ex.getMessage());
		        		return;
		        	}
		        	try
		        	{
		        		//acquired = requestCount.tryAcquire(maxRequests -1,10, TimeUnit.SECONDS);
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
		        	finally
		        	{
		        		RaplaComponent.unlock(writeLock);
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
		if ( env_development != null && env_development)
		{
			context.put(RaplaMainContainer.ENV_DEVELOPMENT, Boolean.TRUE);
		}
		raplaContainer = new RaplaMainContainer( env, context );
		logger = raplaContainer.getContext().lookup(Logger.class);
		if ( env_development != null && env_development)
		{
			addDevelopmentWarFolders();
		}
		serverVersion = raplaContainer.getContext().lookup(RaplaComponent.RAPLA_RESOURCES).getString("rapla.version");
	}

	// add the war folders of the plugins to jetty resource handler so that the files inside the war 
	// folders can be served from within jetty, even when they are not located in the same folder.
	// The method will search the class path for plugin classes and then add the look for a war folder entry in the file hierarchy
	// so a plugin allways needs a plugin class for this to work
	@SuppressWarnings("unchecked")
	private void addDevelopmentWarFolders()
	{
		  Thread currentThread = Thread.currentThread();
		  ClassLoader classLoader = currentThread.getContextClassLoader();
		  ClassLoader parent = null;
		  try
		  {
			  Collection<File> webappFolders = ServiceListCreator.findPluginWebappfolders(logger);
			  if ( webappFolders.size() < 1)
			  {
				  return;
			  }
			  parent = classLoader.getParent();
			  if ( parent != null)
			  {
				  currentThread.setContextClassLoader( parent);
			  }
			  
			  
			  // first we need to access the necessary classes via reflection (are all loaded, because webapplication is already initialized) 
			  final Class WebAppClassLoaderC = Class.forName("org.eclipse.jetty.webapp.WebAppClassLoader",false, parent);
			  final Class WebAppContextC = Class.forName("org.eclipse.jetty.webapp.WebAppContext",false, parent);
			  final Class ResourceCollectionC = Class.forName("org.eclipse.jetty.util.resource.ResourceCollection",false, parent);
			  final Class FileResourceC = Class.forName("org.eclipse.jetty.util.resource.FileResource",false, parent);
			  final Object webappContext = WebAppClassLoaderC.getMethod("getContext").invoke(classLoader);
			  if  (webappContext == null)
			  {
				  return;
			  }
			  final Object baseResource = WebAppContextC.getMethod("getBaseResource").invoke( webappContext);
			  if ( baseResource != null && ResourceCollectionC.isInstance( baseResource) )
			  {
				  
				  //Resource[] resources = ((ResourceCollection) baseResource).getResources();
				  final Object[] resources = (Object[])ResourceCollectionC.getMethod("getResources").invoke( baseResource);
				  Set list = new HashSet( Arrays.asList( resources));
				  for (File folder:webappFolders)
				  {
					  Object fileResource = FileResourceC.getConstructor( URL.class).newInstance(  folder.toURI().toURL());
					  if ( !list.contains( fileResource))
					  {
						  list.add( fileResource);
						  getLogger().info("Adding " + fileResource + " to webapp folder");
					  }
					  
				  }
				  Object[] array = list.toArray( resources);
				  //((ResourceCollection) baseResource).setResources( array);
				  ResourceCollectionC.getMethod("setResources", resources.getClass()).invoke( baseResource, new Object[] {array});
				  //ResourceCollectionC.getMethod(", parameterTypes)  
			  }
		  }
		  catch (ClassNotFoundException ex)
		  {
			  getLogger().info("Development mode not in jetty so war finder will be disabled");
		  }
		  catch (Exception ex)
		  {
			  getLogger().error(ex.getMessage(), ex);
		  }
		  finally
		  {
			  if ( parent != null)
			  {
				  currentThread.setContextClassLoader( classLoader);
			  }
		  }
	}
	
	 private void exit() {
	     MainServlet.this.reconnect = null;
		 guiMutex.release();
		 if ( shutdownCommand != null)
		 {
			 shutdownCommand.run();
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
        	    readLock = RaplaComponent.lock( restartLock.readLock(), 25);
            	RaplaContext context = server.getContext();
            	
    		    for (ServletRequestPreprocessor preprocessor: processors)
    			{
    	            final HttpServletRequest newRequest = preprocessor.handleRequest(context, getServletContext(), request, response);
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
                    getLogger().error("Error writing exception back to client " + e.getMessage());
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
	        //String servletPath = request.getServletPath();
	        if ( requestURI.indexOf(RAPLA_RPC_PATH) >= 0)  {
	        	handleOldRPCCall( request, response );
	            return;
	        }
//	        if ( requestURI.indexOf(RAPLA_JSON_PATH)>= 0)  {
//	            handleJSONCall( request, response, requestURI );
//	            return;
//	        }
	        if ( page == null || page.trim().length() == 0) {
	            page = "index";
	        }
	
            servletPage = server.getWebpage( page);

            if ( servletPage == null)
            {
            	response.setStatus( 404 );
                java.io.PrintWriter out = null;
                try
                {
                	out =	response.getWriter();
                	String message = "404: Page " + page + " not found in Rapla context";
        			out.print(message);
        			getLogger().getChildLogger("server.html.404").warn( message);
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
    
	/** serverContainerHint is useful when you have multiple server configurations in one config file e.g. in a test environment*/
    public static String serverContainerHint = null;
	

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
    
    
    // only for old rapla versions, will be removed in 2.0
    private  void handleOldRPCCall( HttpServletRequest request, HttpServletResponse response ) throws IOException
    {
    	
		String clientVersion = request.getParameter("v");
		if ( clientVersion != null )
		{
			String message = getVersionErrorText(request,  clientVersion);
			response.addHeader("X-Error-Classname",  WrongRaplaVersionException.class.getName());
			response.addHeader("X-Error-Stacktrace", message );
			response.setStatus( 500);
		}
		else
		{
			//if ( !serverVersion.equals( clientVersion ) )
			String message = getVersionErrorText(request,  "");
			response.addHeader("X-Error-Stacktrace", message );
			RaplaException e1= new RaplaException( message );   
			ByteArrayOutputStream outStream = new ByteArrayOutputStream();
			ObjectOutputStream exout = new ObjectOutputStream( outStream);
			exout.writeObject( e1);
			exout.flush();
			exout.close();
			byte[] out = outStream.toByteArray();
			ServletOutputStream outputStream = null;
			try
			{
				outputStream = response.getOutputStream();
				outputStream.write( out );
			}
			catch (Exception ex)
			{
			  
				getLogger().error( " Error writing exception back to client " + ex.getMessage());
			}
			finally
			{
			    if (outputStream != null)
                {
                    outputStream.close();
                }
			}
			response.setStatus( 500);
		}
    	
    }
    // only for old rapla versions, will be removed in 2.0
	private String getVersionErrorText(HttpServletRequest request,String clientVersion) 
	{
		String requestUrl = request.getRequestURL().toString();
		int indexOf = requestUrl.indexOf( "rpc/");
		if (indexOf>=0 ) 
		{
			requestUrl = requestUrl.substring( 0, indexOf) ;
		}
		String message;
		try {
			I18nBundle i18n = getContext().lookup(RaplaComponent.RAPLA_RESOURCES);
			message = i18n.format("error.wrong_rapla_version", clientVersion, serverVersion, requestUrl);
		} catch (Exception e) {
			message = "Update client from " + clientVersion + " to " + serverVersion + " on " + requestUrl + ". Click on the webstart or applet to update.";
		}
		return message;
	}


	//  private boolean isClientVersionSupported(String clientVersion) {
//		// add/remove supported client versions here 
//		return clientVersion.equals(serverVersion) || clientVersion.equals("@doc.version@")   ; 
//	}
//


	
}

