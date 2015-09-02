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
package org.rapla.framework.internal;

import org.rapla.AppointmentFormaterImpl;
import org.rapla.RaplaResources;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.components.xmlbundle.impl.I18nBundleImpl;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.framework.*;
import org.rapla.framework.logger.Logger;
import org.rapla.gwtjsonrpc.common.RemoteJsonService;
import org.rapla.storage.dbrm.RemoteServiceCaller;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Base class for the ComponentContainers in Rapla.
 * Containers are the RaplaMainContainer, the Client- and the Server-Service
 */
public class ContainerImpl implements Container
{
    protected RaplaDefaultContext m_context;
    public final static TypedComponentRole<String> TIMEZONE = new TypedComponentRole<String>("org.rapla.timezone");
    public final static TypedComponentRole<String> LOCALE = new TypedComponentRole<String>("org.rapla.locale");
    public final static TypedComponentRole<String> TITLE = new TypedComponentRole<String>("org.rapla.title");
    public static final TypedComponentRole<Boolean> ENV_DEVELOPMENT = new TypedComponentRole<Boolean>("env.development");
    
    protected List<ComponentHandler> m_componentHandler = Collections.synchronizedList(new ArrayList<ComponentHandler>());
    protected Map<String,RoleEntry> m_roleMap = Collections.synchronizedMap(new LinkedHashMap<String, RoleEntry>());
    Logger logger;
    Class webserviceAnnotation;
    protected CommandScheduler commandQueue;
    protected final Provider<RemoteServiceCaller> remoteServiceCaller;
    //public static boolean DEVELOPMENT_RESSOLVING = false;
    protected I18nBundle i18n;
    protected RaplaLocaleImpl raplaLocale;

    public ContainerImpl( Logger logger, final Provider<RemoteServiceCaller> remoteServiceCaller)  {
    	this.logger = logger;
    	this.remoteServiceCaller = remoteServiceCaller;
        try
        {
            webserviceAnnotation = Class.forName("javax.jws.WebService");
        } catch (Exception ex)
        {
            logger.warn("javax.jws.WebService class not found. Assuming Android env");
        }
        m_context = new RaplaDefaultContext() {

            @Override
            public boolean has(Class<?> componentRole) {
                boolean has = super.has(componentRole);
                if (!has && isWebservice(componentRole))
                {
                    return true;
                }
                return has;
            }
            
            @Override
            public <T> T lookup(Class<T> componentRole) throws RaplaContextException 
            {   
                if ( isWebservice( componentRole ) )
                {
                    T proxy = (T) remoteServiceCaller.get().getRemoteMethod( componentRole);
                    return proxy;
                }
                return super.lookup(componentRole);
            }
            
            @Override
            protected Object lookup(String role) throws RaplaContextException {
                ComponentHandler handler = getHandler( role );
                if ( handler != null ) {
                    return handler.get();
                }
                return null;
            }
            
            @Override
            protected boolean has(String role) {
                if (getHandler( role ) != null)
                    return true;
                return false;
            }
        };
        addContainerProvidedComponentInstance(Container.class, this);
        addContainerProvidedComponentInstance(Logger.class,logger);
        commandQueue = createCommandQueue();
        addContainerProvidedComponentInstance(CommandScheduler.class, commandQueue);
        addContainerProvidedComponent(BundleManager.class, ServerBundleManager.class);
        addContainerProvidedComponent(RaplaLocale.class, RaplaLocaleImpl.class);
        addResourceFile(RaplaResources.class);
        try {
            raplaLocale = (RaplaLocaleImpl)getContext().lookup(RaplaLocale.class);
        } catch (RaplaContextException e) {
           throw new IllegalStateException(e);
        }
    }
    
    @SuppressWarnings("unchecked")
    @Deprecated
    public <T> T lookup(Class<T> componentRole, String hint) throws RaplaContextException {
        
    	String key = componentRole.getName()+ "/" + hint;
        ComponentHandler handler = getHandler( key );
        if ( handler != null ) {
            return (T) handler.get();
        }
        throw new RaplaContextException(  key );     
    }

    protected boolean has(Class componentRole, String hint) {
        
        String key = componentRole.getName()+ "/" + hint;
        ComponentHandler handler = getHandler( key );
        return handler != null;     
    }
    public Logger getLogger() 
    {
        return logger;
    }

	
    public StartupEnvironment getStartupEnvironment() {
        try
        {
            return getContext().lookup( StartupEnvironment.class);
        }
        catch ( RaplaContextException e )
        {
            throw new IllegalStateException(" Container not initialized with a startup environment");
        }
    }

    /**
     *
     * @Deprecated use BundleClasses instead of interfaces
     */
    @Deprecated
    public void addResourceFile(TypedComponentRole<I18nBundle> file)
    {
        BundleManager localeSelector;
        try
        {
            localeSelector = getContext().lookup(BundleManager.class);
        }
        catch (RaplaContextException e)
        {
            throw new IllegalStateException("LocaleSelector not found: "+e.getMessage(), e);
        }
        addContainerProvidedComponentInstance(file, new I18nBundleImpl(getLogger(), file.getId(), localeSelector));
    }

    public Iterable<Class> getResourceBundles() {
        return resourceBundles;
    }

    Set<Class>  resourceBundles = new LinkedHashSet<Class>();

    public <T extends AbstractBundle> void  addResourceFile(Class<T> abstractBundle )
    {
        addContainerProvidedComponent(abstractBundle, abstractBundle);
        resourceBundles.add( abstractBundle);
    }



    public <T, I extends T> void addContainerProvidedComponent(Class<T> roleInterface, Class<I> implementingClass) {
        addContainerProvidedComponent(roleInterface, implementingClass, null, (Configuration)null);
    }

    public <T, I extends T> void addContainerProvidedComponent(Class<T> roleInterface, Class<I> implementingClass, Configuration config) {
        addContainerProvidedComponent(roleInterface, implementingClass, null,config);
    }

    public <T, I extends T> void addContainerProvidedComponent(TypedComponentRole<T> roleInterface, Class<I> implementingClass) {
        addContainerProvidedComponent(roleInterface, implementingClass, (Configuration) null);
    }

    public <T, I extends T> void addContainerProvidedComponent(TypedComponentRole<T> roleInterface, Class<I> implementingClass, Configuration config) {
        addContainerProvidedComponent( roleInterface, implementingClass, null, config);
    }
    
    public <T, I extends T> void addContainerProvidedComponentInstance(TypedComponentRole<T> roleInterface, I implementingInstance) {
        addContainerProvidedComponentInstance(roleInterface.getId(), implementingInstance, null);
    }
    
    public <T, I extends T> void addContainerProvidedComponentInstance(Class<T> roleInterface, I implementingInstance) {
        addContainerProvidedComponentInstance(roleInterface, implementingInstance, implementingInstance.toString());
    }

    public <T> Collection< T> lookupServicesFor(TypedComponentRole<T> role) throws RaplaContextException {
        String id = role.getId();
        return lookupServicesFor( id);
    }

    public <T> Collection<T> lookupServicesFor(Class<T> role) throws RaplaContextException {
        String id = role.getName();
        return lookupServicesFor( id );
    }

    protected <T, I extends T> void addContainerProvidedComponentInstance(Class<T> roleInterface, I implementingInstance, String hint) {
        addContainerProvidedComponentInstance(roleInterface.getName(), implementingInstance, hint);
    }
    
    protected <T, I extends T> void addContainerProvidedComponent(Class<T> roleInterface, Class<I> implementingClass, String hint, Configuration config) {
        addContainerProvidedComponent( roleInterface.getName(), implementingClass.getName(), hint, config);
    }

    private <T, I extends T> void addContainerProvidedComponent(TypedComponentRole<T> roleInterface, Class<I> implementingClass, String hint, Configuration config)
    {
        addContainerProvidedComponent( roleInterface.getId(), implementingClass.getName(), hint, config);
    }

    synchronized private void addContainerProvidedComponent(String role,String classname, String hint,Configuration config) {
        addContainerProvidedComponent( new String[] {role}, classname, hint, config);
    }

    synchronized private void addContainerProvidedComponentInstance(String role,Object componentInstance,String hint) {
        addHandler( role, hint, new ComponentHandler(componentInstance));
    }

    synchronized private void addContainerProvidedComponent(String[] roles,String classname,String hint, Configuration config) {
        ComponentHandler handler = new ComponentHandler( config, classname, getLogger() );
        for ( int i=0;i<roles.length;i++) {
            addHandler( roles[i], hint, handler);
        }
    }
    
   
    private <T> Collection<T> lookupServicesFor(String name) {
        RoleEntry entry = m_roleMap.get( name );
        if ( entry == null)
        {
            return Collections.emptyList();
        }
        Collection<T> result = new LinkedHashSet<T>();
        Set<String> hintSet = entry.getHintSet();
		for (String hint: hintSet)
        {
        	ComponentHandler handler = entry.getHandler(hint);
        	try
        	{
        		Object service = handler.get();
        		// we can safely cast here because the method is only called from checked methods
        		@SuppressWarnings("unchecked")
				T casted = (T)service;
				result.add(casted);
        	}
        	catch (Exception e)
        	{
            	Throwable ex = e;
            	while( ex.getCause() != null)
            	{
            		ex = ex.getCause();
            	}
        		getLogger().error("Could not initialize component " + handler + " due to " + ex.getMessage() + " removing from service list" , e);
        		entry.remove(hint);
        	}
        }
        return result;
    }
    
    /**
     * @param roleName
     * @param hint
     * @param handler
     */
    private void addHandler(String roleName, String hint, ComponentHandler handler) {
        m_componentHandler.add(  handler);
        RoleEntry entry = m_roleMap.get( roleName );
        if ( entry == null)
            entry = new RoleEntry(roleName);
        entry.put( hint , handler);
        m_roleMap.put( roleName, entry);
    }

    ComponentHandler getHandler( String role) {
        int hintSeperator = role.indexOf('/');
        String roleName = role;
        String hint = null;
        if ( hintSeperator > 0 ) {
            roleName = role.substring( 0, hintSeperator   );
            hint = role.substring( hintSeperator + 1 );
        }
        return getHandler( roleName, hint );
    }

    ComponentHandler getHandler( String role,String hint) {
        RoleEntry entry = m_roleMap.get( role );
        if ( entry == null)
        {
            return null;
        }

        ComponentHandler handler = entry.getHandler( hint );
        if ( handler != null)
        {
            return handler;
        }
        if ( hint == null || hint.equals("*" ) )
            return entry.getFirstHandler();
        // Try the first accessible handler
        return null;
    }
    

    class RoleEntry {
        Map<String,ComponentHandler> componentMap = Collections.synchronizedMap(new LinkedHashMap<String,ComponentHandler>());
        ComponentHandler firstEntry;
        int generatedHintCounter = 0;
        String roleName;
        
        RoleEntry(String roleName) {
        	this.roleName = roleName;
        }
        
        String generateHint()
        {
        	return roleName + "_" +generatedHintCounter++;
        }

        void put( String hint, ComponentHandler handler ){
        	if ( hint == null)
        	{
        		hint = generateHint();
        	}
        	synchronized (this) {
        		componentMap.put( hint, handler);
			}
            if (firstEntry == null)
                firstEntry = handler;
        }
        
        void remove(String hint)
        {
        	componentMap.remove( hint);
        }

        Set<String> getHintSet() {
        	// we return a clone to avoid concurrent modification exception
        	synchronized (this) {
        		LinkedHashSet<String> result = new LinkedHashSet<String>(componentMap.keySet());
        		return result;
        	}
        }

        ComponentHandler getHandler(String hint) {
            return  componentMap.get( hint );
        }

        ComponentHandler getFirstHandler() {
            return firstEntry;
        }
        public String toString()
        {
        	return componentMap.toString();
        }

    }

    public RaplaContext getContext() {
        return m_context;
    }

    boolean disposing;
    public void dispose() {
        getLogger().info("Shutting down rapla-container");
    	// prevent reentrence in dispose
    	synchronized ( this)
    	{
	    	if ( disposing)
	    	{
	    		getLogger().warn("Disposing is called twice",new RaplaException(""));
	    	   	return;
	    	}
	    	disposing = true;
    	}
    	try
    	{
            if ( commandQueue != null)
            {
                ((DefaultScheduler)commandQueue).cancel();
            }
    	    removeAllComponents();
    	}
    	finally
    	{
    		disposing = false;
    	}
    }

    protected void removeAllComponents() {
        Iterator<ComponentHandler> it = new ArrayList<ComponentHandler>(m_componentHandler).iterator();
        while ( it.hasNext() ) {
            it.next().dispose();
        }
        m_componentHandler.clear();
        m_roleMap.clear();

    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    Constructor findDependantConstructor(Class componentClass) {
        Constructor[] constructors= componentClass.getConstructors();
        TreeMap<Integer,Constructor> constructorMap = new TreeMap<Integer, Constructor>();
        for (Constructor constructor:constructors) {
            Class[] types = constructor.getParameterTypes();
            boolean compatibleParameters = true;
            if (constructor.getAnnotation( Inject.class) != null)
            {
                return constructor;
            }
            for (int j=0; j< types.length; j++ ) {
                Class type = types[j];
                if (!( type.isAssignableFrom( RaplaContext.class) || type.isAssignableFrom( Configuration.class) ||  /*isWebservice(type) ||*/ getContext().has( type))) 
                {
                    compatibleParameters = false;
                }
            }
            if ( compatibleParameters )
            {
            	//return constructor;
            	constructorMap.put( types.length, constructor);
            }
        }
        // return the constructor with the most paramters
        if (!constructorMap.isEmpty())
        {
        	return constructorMap.lastEntry().getValue();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    protected boolean isWebservice(Class type) {
        if ( webserviceAnnotation != null)
        {
            if ( type.isAnnotationPresent(webserviceAnnotation))
            {
                return true;
            }
        }
        //boolean assignableFrom = type.isAssignableFrom( RemoteJsonService.class );
        boolean assignableFrom = RemoteJsonService.class.isAssignableFrom( type );
        return assignableFrom;
        //return type.isAnnotationPresent(WebService.class);
    }
    
    /** Instantiates a class and passes the config, logger and the parent context to the object if needed by the constructor.
     * This concept is taken form pico container.*/
    @SuppressWarnings({ "rawtypes", "unchecked" })
	protected Object instanciate( String componentClassName, Configuration config, Logger logger ) throws RaplaContextException
    {
        final RaplaContext context = m_context;
		Class componentClass;
        try {
            componentClass = Class.forName( componentClassName );
        } catch (ClassNotFoundException e1) {
            throw new RaplaContextException("Component class " + componentClassName + " not found." , e1);
        }
		Constructor c = findDependantConstructor( componentClass );
        Object[] params = null;
        if ( c != null) {
            Class[] types = c.getParameterTypes();
            Annotation[][] parameterAnnotations = c.getParameterAnnotations();
            Type[] genericParameterTypes = c.getGenericParameterTypes();
            params = new Object[ types.length ];
            for (int i=0; i< types.length; i++ ) {
                final Class type = types[i];
                Object p = null;
                Annotation[] annotations = parameterAnnotations[i];
                for ( Annotation annotation: annotations)
                {
                    if ( annotation.annotationType().equals( Named.class))
                    {
                        String value = ((Named)annotation).value();
                        Object lookup = getContext().lookup( new TypedComponentRole( value));
                        p = lookup;
                    }
                }
                if ( p!= null)
                {
                    params[i] = p;
                    continue;
                }
                String typeName = type.getName();
                final Type type2 = genericParameterTypes[i];
                if (typeName.equals("javax.inject.Provider") && type2 instanceof ParameterizedType)
                {
                    Type[] actualTypeArguments = ((ParameterizedType)type2).getActualTypeArguments();
                    if ( actualTypeArguments.length > 0)
                    {
                        final Type param = actualTypeArguments[0];
                        if ( param instanceof Class)
                        {
                            final Class<? extends Type> class1 = (Class<? extends Type>) param;
                            p = new Provider()
                            {
                                @Override
                                public Object get() {
                                    try {
                                        return context.lookup(class1);
                                    } catch (RaplaContextException e) {
                                        throw new IllegalStateException( e.getMessage(),e);
                                    }
                                }
                                
                            };
                        }
                    }
                }
                if ( p!= null)
                {
                    params[i] = p;
                    continue;
                }
                if ( RaplaContext.class.isAssignableFrom( type)) {
                    p = context;
                } else  if ( Configuration.class.isAssignableFrom( type)) {
                    p = config;
                } else if ( Logger.class.isAssignableFrom( type)) {
                    p = logger;
//                } else if ( isWebservice(type)) {
//					RemoteServiceCaller lookup = context.lookup(RemoteServiceCaller.class);
//                    p = lookup.getRemoteMethod( type); 
                } else {
                    Class guessedRole = type;
                    if ( context.has( guessedRole )) {
                        p = context.lookup( guessedRole );
                    } else {
                        throw new RaplaContextException(componentClass, "Can't statisfy constructor dependency " + type.getName() );
                    }
                }
                params[i] = p;
            }
        }
        try {
        	final Object component;
            if ( c!= null) {
                component = c.newInstance( params);
            } else {
                component = componentClass.newInstance();
            }
            return component;
        } 
        catch (Exception e)
        {
            throw new RaplaContextException(componentClassName + " could not be initialized due to " + e.getMessage(), e);
        }
    }
   
    protected class ComponentHandler implements Disposable {
        protected Configuration config;
        protected Logger logger;
        protected Object component;
        protected String componentClassName;
        boolean dispose = true;
        protected ComponentHandler( Object component) {
            this.component = component;
            this.dispose = false;
        }

        protected ComponentHandler( Configuration config, String componentClass, Logger logger) {
            this.config = config;
            this.componentClassName = componentClass;
            this.logger = logger;
        }

        Semaphore instanciating = new Semaphore(1);
        Object get() throws RaplaContextException {
        	 if ( component != null)
	         {
        		 return component;
	         }
        	 boolean acquired;
        	 try {
        		 acquired = instanciating.tryAcquire(60,TimeUnit.SECONDS);
        	 } catch (InterruptedException e) {
        		 throw new RaplaContextException("Timeout while waiting for instanciation of " + componentClassName );
        	 }
        	 if ( !acquired)
        	 {
        		 throw new RaplaContextException("Instanciating component " + componentClassName + " twice. Possible a cyclic dependency.",new RaplaException(""));
        	 }
        	 else
        	 {
        		 try
        		 {
	        		 // test again, maybe instanciated by another thread
	        		 if ( component != null)
	        		 {
	        			 return component;
	        		 }
	        		 component = instanciate( componentClassName, config, logger );
	        		 return component;
        		 }
        		 finally
        		 {
        			 instanciating.release();
        		 }
        	 }
        }

        boolean disposing;
        public void dispose() {
        	// prevent reentrence in dispose
        	synchronized ( this)
        	{
    	    	if ( disposing)
    	    	{
    	    		getLogger().warn("Disposing is called twice",new RaplaException(""));
    	    		return;
    	    	}
    	    	disposing = true;
        	}
        	try
        	{
                if (component instanceof Disposable)
                {
                	if ( component == ContainerImpl.this)
                	{
                		return;
                	}
                    ((Disposable) component).dispose();
                }
            } catch ( Exception ex) {
                getLogger().error("Error disposing component ", ex );
            }
        	finally
        	{
        		disposing = false;
        	}
        }

        
        public String toString()
        {
        	if ( component != null)
        	{
        		return component.toString();
        	}
        	if ( componentClassName != null)
        	{
        		return componentClassName.toString();
        	}
        	return super.toString();
        }
    }

    protected CommandScheduler createCommandQueue() {
    	CommandScheduler commandQueue = new DefaultScheduler(getLogger(),6);
		return commandQueue;
	}

    protected void initialize() throws Exception {
        //Logger logger = getLogger();
        CalendarOptions calendarOptions = new CalendarOptionsImpl(new DefaultConfiguration());
        addContainerProvidedComponentInstance( CalendarOptions.class, calendarOptions );
        addContainerProvidedComponent(AppointmentFormater.class, AppointmentFormaterImpl.class);
      
        // Discover and register the plugins for Rapla
        i18n = getContext().lookup(RaplaComponent.RAPLA_RESOURCES);
        String version = i18n.getString( "rapla.version" );
        logger.info("Rapla.Version=" + version);
        version = i18n.getString( "rapla.build" );
        logger.info("Rapla.Build=" + version);
        AttributeImpl.TRUE_TRANSLATION.setName(i18n.getLang(), i18n.getString("yes"));
        AttributeImpl.FALSE_TRANSLATION.setName(i18n.getLang(), i18n.getString("no"));
        try {
            version = System.getProperty("java.version");
            logger.info("Java.Version=" + version);
        } catch (SecurityException ex) {
            version = "-";
            logger.warn("Permission to system property java.version is denied!");
        }        
    }

    protected Set<String> discoverPluginClassnames() throws RaplaException 
    {
        try
        {
            Set<String> pluginNames = new LinkedHashSet<String>();
    
//            boolean isDevelopment = getContext().has(ContainerImpl.ENV_DEVELOPMENT) ? getContext().lookup( ContainerImpl.ENV_DEVELOPMENT) : DEVELOPMENT_RESSOLVING;
            Enumeration<URL> pluginEnum =  ConfigTools.class.getClassLoader().getResources("META-INF/rapla-plugin.list");
//            if (!pluginEnum.hasMoreElements() || isDevelopment)
//            { 
//                Collection<String> result = ServiceListCreator.findPluginClasses(logger);
//                pluginNames.addAll(result);
//            }
                
            while ( pluginEnum.hasMoreElements() ) {
                BufferedReader reader = new BufferedReader(new InputStreamReader((pluginEnum.nextElement()).openStream()));
                while ( true ) {
                    String plugin = reader.readLine();
                    if ( plugin == null)
                        break;
                    pluginNames.add(plugin);
                }
            }
            return pluginNames;
        } 
        catch (Exception ex)
        {
            throw new RaplaException( ex.getMessage(), ex);
        }        
    }
    
    private <T extends Container> List<PluginDescriptor<T>> getPluginList(Class<T> containerType) throws RaplaException {
        Logger logger = getLogger().getChildLogger("plugin");
        @SuppressWarnings("unchecked")
        List<PluginDescriptor<T>> pluginList = new ArrayList( );
        Set<String> pluginNames = discoverPluginClassnames();
        for ( String plugin: pluginNames)
        {
            try {
                boolean found = false;
                try {
                    Class<?> componentClass = ContainerImpl.class.getClassLoader().loadClass( plugin );
                    Method[] methods = componentClass.getMethods();
                    for ( Method method:methods)
                    {
                        if ( method.getName().equals("provideServices"))
                        {
                            Class<?> type = method.getParameterTypes()[0];
                            if (containerType.isAssignableFrom(type))
                            {
                                found = true;
                            }
                        }
                    }
                } catch (ClassNotFoundException ex) {
                    continue;
                } catch (NoClassDefFoundError ex) {
                    getLogger().error("Error loading plugin " + plugin + " " +ex.getMessage());
                    continue;
                } catch (Exception e1) {
                    getLogger().error("Error loading plugin " + plugin + " " +e1.getMessage());
                    continue;
                }
                if ( found )
                {
                    @SuppressWarnings("unchecked")
                    PluginDescriptor<T> descriptor = (PluginDescriptor<T>) instanciate(plugin, null, logger);
                    pluginList.add(descriptor);
                    logger.info("Installed plugin "+plugin);
                }
            } catch (RaplaContextException e) {
                if (e.getCause() instanceof ClassNotFoundException) {
                    logger.error("Could not instanciate plugin "+ plugin, e);
                }
            }
        }
        return pluginList;
    }
    
    private Configuration findPluginConfig(RaplaConfiguration raplaConfig, final String pluginClassname) {
        Configuration pluginConfig;
        // TODO should be replaced with a more descriptive approach instead of looking for the config by guessing from the package name
        pluginConfig = raplaConfig.find( "class", pluginClassname );
        // If no plugin config for server is found look for plugin config for client plugin
        if ( pluginConfig == null )
        {
            String newClassname = pluginClassname.replaceAll("ServerPlugin", "Plugin");
            newClassname = newClassname.replaceAll(".server.", ".client.");
            pluginConfig = raplaConfig.find( "class", newClassname );
            if ( pluginConfig == null)
            {
                newClassname = newClassname.replaceAll(".client.", ".");
                pluginConfig = raplaConfig.find( "class", newClassname );
            }
        }
        return pluginConfig;
    }
    
    protected <T extends Container> List<PluginDescriptor<T>> initializePlugins(Preferences preferences, Class<T> pluginContainerClass) throws RaplaException {
        List<PluginDescriptor<T>> pluginList = getPluginList(pluginContainerClass);
        RaplaConfiguration raplaConfig = preferences.getEntry( RaplaComponent.PLUGIN_CONFIG);
        // Add plugin configs
        for ( Iterator<PluginDescriptor<T>> it = pluginList.iterator(); it.hasNext(); )
        {
            PluginDescriptor<T> pluginDescriptor = it.next();
            String pluginClassname = pluginDescriptor.getClass().getName();
            Configuration pluginConfig = null;
            if ( raplaConfig != null )
            {
                pluginConfig = findPluginConfig(raplaConfig, pluginClassname);
            }
            if ( pluginConfig == null )
            {
                pluginConfig = new DefaultConfiguration( "plugin" );
            }
            @SuppressWarnings("unchecked")
            T container = (T)this;
            pluginDescriptor.provideServices( container, pluginConfig );
        }
        return pluginList;
    }



 }

