/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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
package org.rapla;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.rapla.components.util.CommandScheduler;
import org.rapla.components.util.IOUtil;
import org.rapla.components.util.xml.RaplaNonValidatedInput;
import org.rapla.components.xmlbundle.CompoundI18n;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.components.xmlbundle.LocaleSelector;
import org.rapla.components.xmlbundle.impl.I18nBundleImpl;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.framework.Configuration;
import org.rapla.framework.Provider;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaDefaultContext;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.ServiceListCreator;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.TypedComponentRole;
import org.rapla.framework.internal.ComponentInfo;
import org.rapla.framework.internal.ConfigTools;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.framework.internal.RaplaJDKLoggingAdapter;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.framework.internal.RaplaMetaConfigInfo;
import org.rapla.framework.logger.Logger;
import org.rapla.rest.gwtjsonrpc.common.FutureResult;
import org.rapla.storage.dbrm.RaplaHTTPConnector;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.rapla.storage.dbrm.RemoteMethodStub;
import org.rapla.storage.dbrm.RemoteServer;
import org.rapla.storage.dbrm.RemoteServiceCaller;
import org.rapla.storage.dbrm.StatusUpdater;
import org.rapla.storage.dbrm.StatusUpdater.Status;
/**
The Rapla Main Container class for the basic container for Rapla specific services and the rapla plugin architecture.
The rapla container has only one instance at runtime. Configuration of the RaplaMainContainer is done in the rapla*.xconf
files. Typical configurations of the MainContainer are

 <ol>
 <li>Client: A ClientContainerService, one facade and a remote storage ( automaticaly pointing to the download server in webstart mode)</li>
 <li>Server: A ServerContainerService (providing a facade) a messaging server for handling the connections with the clients, a storage (file or db) and an extra service for importing and exporting in the db</li>
 <li>Standalone: A ClientContainerService (with preconfigured auto admin login) and a ServerContainerService that is directly connected to the client without http communication.</li>
 <li>Embedded: Configuration example follows.</li>
 </ol>
<p>
Configuration of the main container is usually done via the raplaserver.xconf 
</p>
<p>
The Main Container provides the following Services to all RaplaComponents
</p>
<ul>
<li>I18nBundle</li>
<li>AppointmentFormater</li>
<li>RaplaLocale</li>
<li>LocaleSelector</li>
<li>RaplaMainContainer.PLUGIN_LIST (A list of all available plugins)</li>
</ul>

  @see I18nBundle
  @see RaplaLocale
  @see AppointmentFormater
  @see LocaleSelector
 */
final public class RaplaMainContainer extends ContainerImpl
{
    public static final TypedComponentRole<Configuration> RAPLA_MAIN_CONFIGURATION = new TypedComponentRole<Configuration>("org.rapla.MainConfiguration");
    public static final TypedComponentRole<String> DOWNLOAD_SERVER = new TypedComponentRole<String>("download-server");
    public static final TypedComponentRole<URL> DOWNLOAD_URL = new TypedComponentRole<URL>("download-url");
    public static final TypedComponentRole<String> ENV_RAPLADATASOURCE = new TypedComponentRole<String>("env.rapladatasource");
    public static final TypedComponentRole<String> ENV_RAPLAFILE = new TypedComponentRole<String>("env.raplafile");
    public static final TypedComponentRole<Object> ENV_RAPLADB= new TypedComponentRole<Object>("env.rapladb");
    public static final TypedComponentRole<Object> ENV_RAPLAMAIL= new TypedComponentRole<Object>("env.raplamail");
    public static final TypedComponentRole<Boolean> ENV_DEVELOPMENT = new TypedComponentRole<Boolean>("env.development");
    
    public static final TypedComponentRole<Object> TIMESTAMP = new TypedComponentRole<Object>("timestamp");
    public static final TypedComponentRole<String> CONTEXT_ROOT  = new TypedComponentRole<String>("context-root");
	public final static TypedComponentRole<Set<String>> PLUGIN_LIST = new TypedComponentRole<Set<String>>("plugin-list");
	public final static TypedComponentRole<String> TITLE = new TypedComponentRole<String>("org.rapla.title");
	public final static TypedComponentRole<String> TIMEZONE = new TypedComponentRole<String>("org.rapla.timezone");
	Logger callLogger;
	
	public RaplaMainContainer() throws Exception {
        this(new RaplaStartupEnvironment());
    }

    public RaplaMainContainer(StartupEnvironment env) throws Exception {
        this( env, new RaplaDefaultContext()   );
    }
    
    public RaplaMainContainer(  StartupEnvironment env, RaplaContext context) throws Exception
    {
    	this(  env,context,createRaplaLogger());
    }
    
    RemoteConnectionInfo globalConnectInfo;
    CommandScheduler commandQueue;
    I18nBundle i18n;
    RaplaHTTPConnector connector;

    
    public RaplaMainContainer(  StartupEnvironment env, RaplaContext context,Logger logger) throws Exception{
        super( context, env.getStartupConfiguration(),logger );
        addContainerProvidedComponentInstance( StartupEnvironment.class, env);
        addContainerProvidedComponentInstance( DOWNLOAD_SERVER, env.getDownloadURL().getHost());
        addContainerProvidedComponentInstance( DOWNLOAD_URL,  env.getDownloadURL());
        commandQueue = createCommandQueue();
		addContainerProvidedComponentInstance( CommandScheduler.class, commandQueue);
		addContainerProvidedComponentInstance( RemoteServiceCaller.class, new RemoteServiceCaller() {
            
            @Override
            public <T> T getRemoteMethod(Class<T> a) throws RaplaContextException {
                return RaplaMainContainer.this.getRemoteMethod(getContext(), a);
            }
        } );

        if (env.getContextRootURL() != null)
        {
            File file = IOUtil.getFileFrom( env.getContextRootURL());
            addContainerProvidedComponentInstance( CONTEXT_ROOT, file.getPath());
        }
        addContainerProvidedComponentInstance( TIMESTAMP, new Object() {
            
            public String toString() {
                DateFormat formatter = new SimpleDateFormat("yyyyMMdd-HHmmss");
                String formatNow = formatter.format(new Date());
                return formatNow;
            }

        });
        initialize();
    }

    private static Logger createRaplaLogger() 
    {
    	Logger logger;
    	try {
			RaplaMainContainer.class.getClassLoader().loadClass("org.slf4j.Logger");
			@SuppressWarnings("unchecked")
			Provider<Logger> logManager = (Provider<Logger>) RaplaMainContainer.class.getClassLoader().loadClass("org.rapla.framework.internal.Slf4jAdapter").newInstance(); 
			logger = logManager.get();
			logger.info("Logging via SLF4J API.");
		} catch (Throwable e1) {
			Provider<Logger> logManager = new RaplaJDKLoggingAdapter( ); 
	    	logger = logManager.get();
			logger.info("Logging via java.util.logging API. " + e1.toString());
		}
        return logger;
    }

    protected Map<String,ComponentInfo> getComponentInfos() {
        return new RaplaMetaConfigInfo();
    }

    private void initialize() throws Exception {
        
        Logger logger = getLogger();
        int startupMode = getStartupEnvironment().getStartupMode();
		logger.debug("----------- Rapla startup mode = " +  startupMode);
		RaplaLocaleImpl raplaLocale = new RaplaLocaleImpl(m_config.getChild("locale"), logger);

        Configuration localeConfig = m_config.getChild("locale");
        CalendarOptions calendarOptions = new CalendarOptionsImpl(localeConfig);
        addContainerProvidedComponentInstance( CalendarOptions.class, calendarOptions );
        addContainerProvidedComponentInstance( RAPLA_MAIN_CONFIGURATION, m_config );
        addContainerProvidedComponent( RaplaNonValidatedInput.class,  ConfigTools.RaplaReaderImpl.class);
        
        // Startup mode= EMBEDDED = 0, CONSOLE = 1, WEBSTART = 2, APPLET = 3, SERVLET = 4
        addContainerProvidedComponentInstance( RaplaLocale.class,raplaLocale);
        addContainerProvidedComponentInstance( LocaleSelector.class,raplaLocale.getLocaleSelector());
        
        m_config.getChildren("rapla-client");
        
        String defaultBundleName = m_config.getChild("default-bundle").getValue( null);
        // Override the intern Resource Bundle with user provided
        Configuration parentConfig = I18nBundleImpl.createConfig( RaplaComponent.RAPLA_RESOURCES.getId() );
        if ( defaultBundleName!=null) {
        	I18nBundleImpl i18n = new I18nBundleImpl( getContext(), I18nBundleImpl.createConfig( defaultBundleName ), logger);
            String parentId = i18n.getParentId();
            if ( parentId != null && parentId.equals(RaplaComponent.RAPLA_RESOURCES.getId())) 
            {
    			I18nBundleImpl parent = new I18nBundleImpl( getContext(), parentConfig, logger);
            	I18nBundle compound = new CompoundI18n( i18n, parent);
            	addContainerProvidedComponentInstance(RaplaComponent.RAPLA_RESOURCES, compound);
            }
            else
            {
            	addContainerProvidedComponent(RaplaComponent.RAPLA_RESOURCES,I18nBundleImpl.class, parentConfig);            	
            }
        }
        else
        {
        	addContainerProvidedComponent(RaplaComponent.RAPLA_RESOURCES,I18nBundleImpl.class, parentConfig);
        }

        addContainerProvidedComponentInstance( AppointmentFormater.class, new AppointmentFormaterImpl( getContext()));

      
        // Discover and register the plugins for Rapla

        Set<String> pluginNames = new LinkedHashSet<String>();

        boolean isDevelopment = getContext().has(RaplaMainContainer.ENV_DEVELOPMENT) && getContext().lookup( RaplaMainContainer.ENV_DEVELOPMENT);
        Enumeration<URL> pluginEnum =  ConfigTools.class.getClassLoader().getResources("META-INF/rapla-plugin.list");
        if (!pluginEnum.hasMoreElements() || isDevelopment)
        { 
        	Collection<String> result = ServiceListCreator.findPluginClasses(logger);
    		pluginNames.addAll(result);
        }
        	
        while ( pluginEnum.hasMoreElements() ) {
            BufferedReader reader = new BufferedReader(new InputStreamReader((pluginEnum.nextElement()).openStream()));
            while ( true ) {
                String plugin = reader.readLine();
                if ( plugin == null)
                    break;
                pluginNames.add(plugin);
            }
        }
        
        addContainerProvidedComponentInstance( PLUGIN_LIST, pluginNames);
        logger.info("Config=" + getStartupEnvironment().getConfigURL());
        
        i18n = getContext().lookup(RaplaComponent.RAPLA_RESOURCES);
        String version = i18n.getString( "rapla.version" );
        logger.info("Rapla.Version=" + version);
        version = i18n.getString( "rapla.build" );
        logger.info("Rapla.Build=" + version);
        logger.info("Timezone " + TimeZone.getDefault().getID());
        AttributeImpl.TRUE_TRANSLATION.setName(i18n.getLang(), i18n.getString("yes"));
        AttributeImpl.FALSE_TRANSLATION.setName(i18n.getLang(), i18n.getString("no"));
        try {
            version = System.getProperty("java.version");
            logger.info("Java.Version=" + version);
        } catch (SecurityException ex) {
            version = "-";
            logger.warn("Permission to system property java.version is denied!");
        }
        callLogger =logger.getChildLogger("call");
        String errorString = i18n.format("error.connect", getStartupEnvironment().getDownloadURL()) + " ";
        connector = new RaplaHTTPConnector( commandQueue, errorString);
    }

	public void dispose() {
        getLogger().info("Shutting down rapla-container");
        if ( commandQueue != null)
        {
        	((DefaultScheduler)commandQueue).cancel();
        }
        super.dispose();
    }
	

    public <T> T getRemoteMethod(final RaplaContext context,final Class<T> a) throws RaplaContextException  
    {
        if (context.has( RemoteMethodStub.class))
        {
            RemoteMethodStub server =  context.lookup(RemoteMethodStub.class);
            return server.getWebserviceLocalStub(a);
        }  
      
        InvocationHandler proxy = new InvocationHandler() 
        {
            RemoteConnectionInfo localConnectInfo;
            
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable 
            {
                if ( method.getName().equals("setConnectInfo") && method.getParameterTypes()[0].equals(RemoteConnectionInfo.class))
                {
                    localConnectInfo = (RemoteConnectionInfo) args[0];
                    if ( globalConnectInfo == null)
                    {
                        globalConnectInfo =localConnectInfo;
                    }
                    return null;
                }

                RemoteConnectionInfo remoteConnectionInfo = localConnectInfo != null ? localConnectInfo : globalConnectInfo;
                if ( remoteConnectionInfo == null)
                {
                    throw new IllegalStateException("you need to call setConnectInfo first");
                }
                Class<?> returnType = method.getReturnType();
                String methodName = method.getName();
                final URL server;
                try 
                {
                    server = new URL( remoteConnectionInfo.getServerURL());
                } 
                catch (MalformedURLException e) 
                {
                   throw new RaplaContextException(e.getMessage());
                }
                StatusUpdater statusUpdater = remoteConnectionInfo.getStatusUpdater();
                if ( statusUpdater != null)
                {
                    statusUpdater.setStatus( Status.BUSY );
                }  
                FutureResult result;
                try
                {
                    result = call( a, methodName, args, remoteConnectionInfo);
                    if (callLogger.isDebugEnabled())
                    {
                        callLogger.debug("Calling " + server + " " + a.getName() + "."+methodName);
                    }
                }
                finally
                {
                    if ( statusUpdater != null)
                    {
                        statusUpdater.setStatus( Status.READY );
                    }
                }
                if ( !FutureResult.class.isAssignableFrom(returnType))
                {
                    return result.get();
                }
                return result;
            }
        };
        ClassLoader classLoader = a.getClassLoader();
        @SuppressWarnings("unchecked")
        Class<T>[] interfaces = new Class[] {a};
        @SuppressWarnings("unchecked")
        T proxyInstance = (T)Proxy.newProxyInstance(classLoader, interfaces, proxy);
        return proxyInstance;
    }
    

    private FutureResult call(Class<?> service, String methodName,Object[] args,RemoteConnectionInfo connectionInfo) throws NoSuchMethodException, SecurityException  {
        ConnectInfo connectInfo = connectionInfo.getConnectInfo();
        if ( connectInfo !=null)
        {
            Method method = RemoteServer.class.getMethod("login", String.class, String.class,String.class);
            connector.setReAuthentication(RemoteServer.class, method, new Object[] {connectInfo.getUsername(), new String(connectInfo.getPassword()), connectInfo.getConnectAs()});
        }
        FutureResult result =connector.call(service, methodName, args, connectionInfo);
        return result;
    }

    
     
	     
	        
 }

