package org.rapla.bootstrap;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class CustomJettyStarter 
{
    public static final String USAGE = new String (
            "Usage : \n"
            + "[-?|-c PATH_TO_CONFIG_FILE] [ACTION]\n"
            + "Possible actions:\n"
            + "  standalone  : Starts the rapla-gui with embedded server (this is the default)\n"
            + "  server  : Starts the rapla-server \n"
            + "  client  : Starts the rapla-client \n"
            + "  import  : Import from file into the database\n"
            + "  export  : Export from database into file\n"
            + "the config file is jetty.xml generally located in etc/jetty.xml"
            );

	
	public static void main(final String[] args) throws Exception
    {
		String property = System.getProperty("org.rapla.disableHostChecking");
		if (  Boolean.parseBoolean( property))
		{
			HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
				@Override
				public boolean verify(String arg0, SSLSession arg1) {
					return true;
				}
			});
		}
		new CustomJettyStarter().start( args);
    }
	
	Class ServerC ;
	Class ConfigurationC;
    Class ConnectorC; 
    Class ServerConnectorC;
	Class ResourceC; 
	Class EnvEntryC; 
	Class LifeCyleC; 
	Class LifeCyleListenerC; 
//	Class DeploymentManagerC;
//	Class ContextHandlerC;
//	Class WebAppContextC;
//	Class AppC; 
//	Class AppProviderC; 
//	Class ServletHandlerC; 
//	Class ServletHolderC; 
	
	ClassLoader loader;
	Method stopMethod;
	Logger logger = Logger.getLogger(getClass().getName());
	String startupMode;
	String startupUser;
   	
   	/** parse startup parameters. The parse format:
    <pre>
    [-?|-c PATH_TO_CONFIG_FILE] [ACTION]
    </pre>
    Possible map entries:
    <ul>
    <li>config: the config-file</li>
    <li>action: the start action</li>
    </ul>

    @return a map with the parameter-entries or  null if format is invalid or -? is used
    */
   public static Map<String,String> parseParams( String[] args )
   {
       boolean bInvalid = false;
       Map<String,String> map = new HashMap<String,String>();
       String config = null;
       String action = null;

       // Investigate the passed arguments
       for ( int i = 0; i < args.length; i++ )
       {
           String arg = args[i].toLowerCase();
           if ( arg.equals( "-c" ) )
           {
               if ( i + 1 == args.length )
               {
                   bInvalid = true;
                   break;
               }
               config = args[++i];
               continue;
           }
           if ( arg.equals( "-?" ) )
           {
               bInvalid = true;
               break;
           }
           if ( arg.substring( 0, 1 ).equals( "-" ) )
           {
               bInvalid = true;
               break;
           }
           if (action == null)
           {
        	   action = arg;
           }
       }

       if ( bInvalid )
       {
           return null;
       }

       if ( config != null )
           map.put( "config", config );
       if ( action != null )
           map.put( "action", action );
       return map;
   }
    
	public void start(final String[] arguments) throws Exception
	{
		Map<String, String> parseParams = parseParams(arguments);
		String configFiles = parseParams.get("config");
		
		if ( configFiles == null)
		{
			configFiles = "etc/jetty.xml";
			String property = System.getProperty("jetty.home");
			if (property != null)
			{
				if ( !property.endsWith("/"))
				{
					property += "/";
				}
				configFiles = property + configFiles;
			}
		}
		startupMode =System.getProperty("org.rapla.startupMode");
		if (startupMode == null)
		{
			startupMode = parseParams.get("action");
		}
		if (startupMode == null)
		{
			startupMode = "standalone";
		}
		startupUser =System.getProperty("org.rapla.startupUser");

		boolean isServer = startupMode.equals("server");
		final boolean removeConnectors = !isServer;
		if ( isServer)
		{
			System.setProperty( "java.awt.headless", "true" );
		}
        loader = Thread.currentThread().getContextClassLoader();
    	Class<?> LoadingProgressC= null;
		final Object progressBar;
	
        if ( startupMode.equals("standalone" ) || startupMode.equals("client" ))
		{
			LoadingProgressC = loader.loadClass("org.rapla.bootstrap.LoadingProgress");
			progressBar = LoadingProgressC.getMethod("getInstance").invoke(null);
			LoadingProgressC.getMethod("start", int.class, int.class).invoke( progressBar, 1,4);
		}
        else
        {
        	progressBar = null;
        }
		final String contextPath = System.getProperty("org.rapla.context",null);
		final String downloadUrl = System.getProperty("org.rapla.serverUrl",null);
		final String[] jettyArgs = configFiles.split(",");
		
		
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        AccessController.doPrivileged(new PrivilegedAction<Object>()
        {
        	boolean started;
        	boolean shutdownShouldRun;
			public Object run()
            {
                try
                {
                    Properties properties = new Properties();
                    // Add System Properties
                    Enumeration<?> ensysprop = System.getProperties().propertyNames();
                    while (ensysprop.hasMoreElements())
                    {
                        String name = (String)ensysprop.nextElement();
                        properties.put(name,System.getProperty(name));
                    }
                    //loader.loadClass("org.rapla.bootstrap.JNDIInit").newInstance();
                    ServerC =loader.loadClass("org.eclipse.jetty.server.Server");
                    stopMethod = ServerC.getMethod("stop");
                    ConfigurationC =loader.loadClass("org.eclipse.jetty.xml.XmlConfiguration");
                    ConnectorC = loader.loadClass("org.eclipse.jetty.server.Connector");
                    ServerConnectorC = loader.loadClass("org.eclipse.jetty.server.ServerConnector");
                	ResourceC = loader.loadClass("org.eclipse.jetty.util.resource.Resource");
                	EnvEntryC = loader.loadClass("org.eclipse.jetty.plus.jndi.EnvEntry");
                	LifeCyleC = loader.loadClass( "org.eclipse.jetty.util.component.LifeCycle");
                	LifeCyleListenerC = loader.loadClass( "org.eclipse.jetty.util.component.LifeCycle$Listener");
//                	DeploymentManagerC = loader.loadClass("org.eclipse.jetty.deploy.DeploymentManager");
//        			ContextHandlerC = loader.loadClass("org.eclipse.jetty.server.handler.ContextHandler");
//        			WebAppContextC = loader.loadClass("org.eclipse.jetty.webapp.WebAppContext");
//        			AppC = loader.loadClass("org.eclipse.jetty.deploy.App");
//        			AppProviderC = loader.loadClass("org.eclipse.jetty.deploy.AppProvider");
//        			ServletHandlerC = loader.loadClass("org.eclipse.jetty.servlet.ServletHandler");
//        			ServletHolderC = loader.loadClass("org.eclipse.jetty.servlet.ServletHolder");
                	
                	// For all arguments, load properties or parse XMLs
                	Object last = null;
                	Object[] obj = new Object[jettyArgs.length];
                    for (int i = 0; i < jettyArgs.length; i++)
                    {
                    	String configFile = jettyArgs[i];
						Object newResource = ResourceC.getMethod("newResource",String.class).invoke(null,configFile);
                    	if (configFile.toLowerCase(Locale.ENGLISH).endsWith(".properties"))
                        {
                            properties.load((InputStream)ResourceC.getMethod("getInputStream").invoke(newResource));
                        }
                        else
                        {
                        	URL url = (URL)ResourceC.getMethod("getURL").invoke(newResource);
                        	Object configuration = ConfigurationC.getConstructor(URL.class).newInstance(url);
                            if (last != null)
                                ((Map)ConfigurationC.getMethod("getIdMap").invoke(configuration)).putAll((Map)ConfigurationC.getMethod("getIdMap").invoke(last));
                            if (properties.size() > 0)
                            {
                                Map<String, String> props = new HashMap<String, String>();
                                for (Object key : properties.keySet())
                                {
                                    props.put(key.toString(),String.valueOf(properties.get(key)));
                                }
                                ((Map)ConfigurationC.getMethod("getProperties").invoke( configuration)).putAll( props);
                            }
                            Object configuredObject = ConfigurationC.getMethod("configure").invoke(configuration);
                            Integer port = null;
                            if ( ServerC.isInstance(configuredObject))
                            {
                            	final Object server =  configuredObject;
                            	Object[] connectors = (Object[]) ServerC.getMethod("getConnectors").invoke(server);
                     
                            	for (Object c: connectors)
                            	{
                            		port = (Integer) ServerConnectorC.getMethod("getPort").invoke(c);
                                    if ( removeConnectors)
                                    {
                                    	ServerC.getMethod("removeConnector", ConnectorC).invoke(server, c);
                                    }
                            	}

                            	final Method shutdownMethod = ServerC.getMethod("stop");
                            	InvocationHandler proxy = new InvocationHandler() {
                                    public Object invoke(Object proxy, Method method, Object[] args)
                                            throws Throwable 
                                    {
                                    	String name = method.getName();
										if ( name.toLowerCase(Locale.ENGLISH).indexOf("started")>=0)
                                    	{
											if ( shutdownShouldRun)
											{
												shutdownMethod.invoke( server);
											}
											else
											{
												started = true;
											}
                                    	}
                                    	return null;
                                    }
                                };
                                Class<?>[] interfaces = new Class[] {LifeCyleListenerC};
                                Object proxyInstance = Proxy.newProxyInstance(loader, interfaces, proxy);
								ServerC.getMethod("addLifeCycleListener", LifeCyleListenerC).invoke(server, proxyInstance);
								Constructor newJndi = EnvEntryC.getConstructor(String.class,Object.class);
								Method addBean = ServerC.getMethod("addBean", Object.class);
								if ( !startupMode.equals("server"))
								{
									addBean.invoke(server, newJndi.newInstance("rapla_startup_user", startupUser));
									addBean.invoke(server, newJndi.newInstance("rapla_startup_mode", startupMode));
									addBean.invoke(server, newJndi.newInstance("rapla_download_url", downloadUrl));
									addBean.invoke(server, newJndi.newInstance("rapla_instance_counter", new ArrayList<String>()));
									if ( contextPath != null)
									{
		                        		addBean.invoke(server, newJndi.newInstance("rapla_startup_context", contextPath));										
									}
								}
								addBean.invoke(server, newJndi.newInstance("rapla_startup_port", port));										
								{
									Runnable shutdownCommand = new Runnable() {
										
										public void run() {
											if ( started)
											{
												try {
													shutdownMethod.invoke(server);
												} catch (Exception ex) {
													logger.log(Level.SEVERE,ex.getMessage(),ex);
												}
											}
											else
											{
												shutdownShouldRun = true;
											}
										}
									};
	                        		addBean.invoke(server, newJndi.newInstance("rapla_shutdown_command", shutdownCommand));
								}
                            }
                            obj[i] = configuredObject;
                            last = configuration;
                        }
                    }
                    
                    // For all objects created by XmlConfigurations, start them if they are lifecycles.
                    for (int i = 0; i < obj.length; i++)
                    {
                    	if (LifeCyleC.isInstance(obj[i]))
                        {
                        	Object o =obj[i];
                        	if ( !(Boolean)LifeCyleC.getMethod("isStarted").invoke(o))
                        	{
                        		LifeCyleC.getMethod("start").invoke(o);
                        	}
                       }
                    }
//                    while (true)
//                    {
//                        Thread.sleep(1000);
//                    }
                }
                catch (Exception e)
                {
                    exception.set(e);
                }
                return null;
            }
        });
        if ( progressBar != null && LoadingProgressC != null)
        {
        	LoadingProgressC.getMethod("close").invoke( progressBar);
        }
        Throwable th = exception.get();
        if (th != null)
        {
            if (th instanceof RuntimeException)
                throw (RuntimeException)th;
            else if (th instanceof Exception)
                throw (Exception)th;
            else if (th instanceof Error)
                throw (Error)th;
            throw new Error(th);
        }
    }
 
//	void doStart(final Object server, String passedContext) 
//	{
//		try
//		{
//			Object bean = ServerC.getMethod("getBean",  Class.class).invoke(server, DeploymentManagerC);
//			Map<String, Object> raplaServletMap = new LinkedHashMap<String, Object>();
//			Collection<?> apps = (Collection<?>) DeploymentManagerC.getMethod("getApps").invoke(bean);
//			for (Object handler:apps)
//			{
//				
//				Object context_ = AppC.getMethod("getContextHandler").invoke(handler);
//				String contextPath = (String) ContextHandlerC.getMethod("getContextPath").invoke(context_);
//				Object[] servlets = (Object[]) ContextHandlerC.getMethod("getChildHandlersByClass", Class.class).invoke(context_, ServletHandlerC);
//				for (  Object childHandler : servlets)
//				{
//					Object servlet = ServletHandlerC.getMethod("getServlet",String.class).invoke( childHandler,"RaplaServer");
//					if ( servlet != null)
//					{
//						raplaServletMap.put(contextPath, servlet);
//					}
//				}
//			}
//			
//			Set<String> keySet = raplaServletMap.keySet();
//			if ( keySet.size() == 0)
//			{
//				logger.log(Level.SEVERE,"No rapla context found in jetty container.");
//				stopMethod.invoke(server);
//			}
//			else if ( keySet.size() > 1 && passedContext == null)
//			{
//				logger.log(Level.SEVERE,"Multiple context found in jetty container " + keySet +" Please specify one via -Dorg.rapla.context=REPLACE_WITH_CONTEXT");
//				stopMethod.invoke(server);
//			}
//			else
//			{
//				//Class MainServletC = loader.loadClass("org.rapla.MainServlet");
//				Object servletHolder = passedContext == null ? raplaServletMap.values().iterator().next(): raplaServletMap.get( passedContext);
//				if ( servletHolder != null)
//				{
//					
//					Object servlet = ServletHolderC.getMethod("getServlet").invoke( servletHolder);
//					PropertyChangeListener castedHack = (PropertyChangeListener)servlet;
//					castedHack.propertyChange( new PropertyChangeEvent(server, startupMode, null, shutdownCommand));
//				}
//				else
//				{
//					logger.log(Level.SEVERE,"Rapla context ' " + passedContext +"' not found.");
//					stopMethod.invoke(server);
//				}
//			}
//		} 
//		catch (Exception ex)
//		{
//			logger.log(Level.SEVERE,ex.getMessage(),ex);
//			try {
//				stopMethod.invoke(server);
//			} catch (Exception e) {
//				logger.log(Level.SEVERE,e.getMessage(),e);
//			}
//		}
//	}	

}
