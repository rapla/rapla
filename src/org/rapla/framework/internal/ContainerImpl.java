/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.rapla.components.util.Cancelable;
import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.framework.Configuration;
import org.rapla.framework.ConfigurationException;
import org.rapla.framework.Container;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaDefaultContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.TypedComponentRole;
import org.rapla.framework.logger.Logger;

/** Base class for the ComponentContainers in Rapla.
 * Containers are the RaplaMainContainer, the Client- and the Server-Service
 */
public class ContainerImpl implements Container
{
    protected Container m_parent;
    protected RaplaDefaultContext m_context;
    protected Configuration m_config;

    protected List<ComponentHandler> m_componentHandler = new ArrayList<ComponentHandler>();
    protected LinkedHashMap<String,RoleEntry> m_roleMap = new LinkedHashMap<String,RoleEntry>();
    Logger logger;

    public ContainerImpl(RaplaContext parentContext, Configuration config, Logger logger) throws RaplaException  {
    	m_config = config;
//    	if ( parentContext.has(Logger.class ) )
//    	{
//    		logger =  parentContext.lookup( Logger.class);
//        }
//    	else
//    	{
//    		logger = new ConsoleLogger(ConsoleLogger.LEVEL_INFO);
//    	}
    	this.logger = logger;
        if ( parentContext.has(Container.class )) {
            m_parent =  parentContext.lookup( Container.class);
        }
        m_context = new RaplaDefaultContext(parentContext) {

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
        addContainerProvidedComponentInstance(Logger.class,logger);
        init( );
    }
    
    @SuppressWarnings("unchecked")
    public <T> T lookup(Class<T> componentRole, String hint) throws RaplaContextException {
        
    	String key = componentRole.getName()+ "/" + hint;
        ComponentHandler handler = getHandler( key );
        if ( handler != null ) {
            return (T) handler.get();
        }
        if ( m_parent != null)
        {
        	return m_parent.lookup(componentRole, hint);
        }
        throw new RaplaContextException(  key );     
    }


    public Logger getLogger() 
    {
        return logger;
    }

    protected void init() throws RaplaException {
        configure( m_config );
        addContainerProvidedComponentInstance( Container.class, this );
        addContainerProvidedComponentInstance( Logger.class, getLogger());
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

    protected void configure( final Configuration config )
        throws RaplaException
    {
        Map<String,ComponentInfo> m_componentInfos = getComponentInfos();
        final Configuration[] elements = config.getChildren();
        for ( int i = 0; i < elements.length; i++ )
        {
            final Configuration element = elements[i];
            final String id = element.getAttribute( "id", null );
            if ( null == id )
            {
                // Only components with an id attribute are treated as components.
                getLogger().debug( "Ignoring configuration for component, " + element.getName()
                    + ", because the id attribute is missing." );
            }
            else
            {
                final String className;
                final String[] roles;
                if ( "component".equals( element.getName() ) )
                {
                    try {
                        className = element.getAttribute( "class" );
                        Configuration[] roleConfigs = element.getChildren("roles");
                        roles = new String[ roleConfigs.length ];
                        for ( int j=0;j< roles.length;j++) {
                            roles[j] = roleConfigs[j].getValue();
                        }
                    } catch ( ConfigurationException ex) {
                        throw new RaplaException( ex);
                    }
                }
                else
                {
                    String configName = element.getName();
                    final ComponentInfo roleEntry = m_componentInfos.get( configName );
                    if ( null == roleEntry )
                    {
                        final String message = "No class found matching configuration name " + "[name: " + element.getName()  + "]";
                        getLogger().error( message );

                        continue;
                    }
                    roles = roleEntry.getRoles();
                    className = roleEntry.getClassname();
                }
                if ( getLogger().isDebugEnabled() )
                {
                    getLogger().debug( "Configuration processed for: " + className );
                }
                Logger logger = this.logger.getChildLogger( id );
                ComponentHandler handler =new ComponentHandler( element, className, logger);
                for ( int j=0;j< roles.length;j++) {
                    String roleName = (roles[j]);
                    addHandler( roleName, id, handler );
                }
            }
        }
    }

    protected Map<String,ComponentInfo> getComponentInfos() {
        return Collections.emptyMap();
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
        Collection<T> list = new LinkedHashSet<T>();
        for (T service: getAllServicesForThisContainer(role)) {
			list.add(service);
        }
        if ( m_parent != null)
        {
        	for (T service:m_parent.lookupServicesFor(role))
        	{
        		list.add( service);
        	}
        	
        }
        return list;
    }

    public <T> Collection<T> lookupServicesFor(Class<T> role) throws RaplaContextException {
        Collection<T> list = new LinkedHashSet<T>();
        for (T service:getAllServicesForThisContainer(role)) {
			list.add( service);
        }
        if ( m_parent != null)
        {
        	for (T service:m_parent.lookupServicesFor(role))
        	{
        		list.add( service);
        	}
        	
        }
        return list;
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
    
    protected <T> Collection<T> getAllServicesForThisContainer(TypedComponentRole<T> role)  {
        RoleEntry entry = m_roleMap.get( role.getId() );
        return getAllServicesForThisContainer( entry);
    }


    protected <T> Collection<T> getAllServicesForThisContainer(Class<T> role)  {
        RoleEntry entry = m_roleMap.get( role.getName() );
        return getAllServicesForThisContainer( entry);
    }
    
    private <T> Collection<T> getAllServicesForThisContainer(RoleEntry entry)  {
        if ( entry == null)
        {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<T>();
        // make a copy, in case we have to remove a hint to avoid concurrent modification exception
        Set<String> hintSet = new LinkedHashSet<String>(entry.getHintSet());
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

    synchronized ComponentHandler getHandler( String role,Object hint) {
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

    protected final class DefaultScheduler implements CommandScheduler {
		private final ScheduledExecutorService executor;

		private DefaultScheduler() {
			final ScheduledExecutorService executor = Executors.newScheduledThreadPool(5,new ThreadFactory() {
				
				public Thread newThread(Runnable r) {
					Thread thread = new Thread(r);
					thread.setName("raplascheduler");
					thread.setDaemon(true);
					return thread;
				}
			});
			this.executor = executor;
		}

		public Cancelable schedule(Command command, long delay) 
		{
			Runnable task = createTask(command);
			return schedule(task, delay);
		}
		
		public Cancelable schedule(Runnable task, long delay) {
			if (executor.isShutdown())
			{
				RaplaException ex = new RaplaException("Can't schedule command because executer is already shutdown " + task.toString());
				getLogger().error(ex.getMessage(), ex);
				return createCancable( null);
			}
	  
			TimeUnit unit = TimeUnit.MILLISECONDS;
			ScheduledFuture<?> schedule = executor.schedule(task, delay, unit);
			return createCancable( schedule);
		}

		private Cancelable createCancable(final ScheduledFuture<?> schedule) {
			return new Cancelable() {
				public void cancel() {
					if ( schedule != null)
					{
						schedule.cancel(true);
					}
				}
			};
		}

		public Cancelable schedule(Runnable task, long delay, long period) {
			if (executor.isShutdown())
			{
				RaplaException ex = new RaplaException("Can't schedule command because executer is already shutdown " + task.toString());
				getLogger().error(ex.getMessage(), ex);
				return createCancable( null);
			}
			TimeUnit unit = TimeUnit.MILLISECONDS;
			ScheduledFuture<?> schedule = executor.scheduleAtFixedRate(task, delay, period, unit);
			return createCancable( schedule);
		}
		
		public Cancelable schedule(Command command, long delay, long period) 
		{
			Runnable task = createTask(command);
			return schedule(task, delay, period);
		}


		public void cancel() {
			try{
				getLogger().info("Scheduler thread terminated.");
				executor.shutdownNow();
			}
			catch ( Throwable ex)
			{
				getLogger().warn(ex.getMessage());
			}
			// we give the update threads some time to execute
			try
			{
				Thread.sleep( 50);
			}
			catch (InterruptedException e) 
			{
			}

		}
	}


	class RoleEntry {
        Map<String,ComponentHandler> componentMap = new LinkedHashMap<String,ComponentHandler>();
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
            componentMap.put( hint, handler);
            if (firstEntry == null)
                firstEntry = handler;
        }
        
        void remove(String hint)
        {
        	componentMap.remove( hint);
        }

        Set<String> getHintSet() {
            return componentMap.keySet();
        }

        ComponentHandler getHandler(Object hint) {
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

    /**
     * @see org.rapla.framework.Disposable#dispose()
     */
    public void dispose() {
        removeAllComponents();
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
	static Constructor findDependantConstructor(Class componentClass) {
        Constructor[] constructors= componentClass.getConstructors();
        TreeMap<Integer,Constructor> constructorMap = new TreeMap<Integer, Constructor>();
        for (Constructor constructor:constructors) {
            Class[] types = constructor.getParameterTypes();
            boolean compatibleParameters = true;
            for (int j=0; j< types.length; j++ ) {
                Class type = types[j];
                if (!( type.isAssignableFrom( RaplaContext.class) || type.isAssignableFrom( Configuration.class) || type.isAssignableFrom(Logger.class))) 
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

    /** Instantiates a class and passes the config, logger and the parent context to the object if needed by the constructor.
     * This concept is taken form pico container.*/
    @SuppressWarnings({ "rawtypes", "unchecked" })
	protected Object instanciate( String componentClassName, Configuration config, Logger logger ) throws RaplaContextException
    {
    	RaplaContext context = m_context;
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
            params = new Object[ types.length ];
            Class unknownParameter = null;
            for (int i=0; i< types.length; i++ ) {
                Class type = types[i];
                if ( type.isAssignableFrom( RaplaContext.class)) {
                    params[i] = context;
                } else  if ( type.isAssignableFrom( Configuration.class)) {
                    params[i] = config;
                } else if ( type.isAssignableFrom( Logger.class)) {
                    params[i] = logger;
                } else {
                    Class guessedRole = type.getClass();
                    if ( context.has( guessedRole )) {
                        params[i] = context.lookup( guessedRole );
                    } else {
                        unknownParameter = type;
                        break;
                    }
                }
            }
            if ( unknownParameter != null) {
                throw new RaplaContextException(componentClass, "Can't statisfy constructor dependency " + unknownParameter.getName() );
            }
        }
        try {
            Object component;
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


        Object get() throws RaplaContextException {
            if ( component == null)
                component = instanciate( componentClassName, config, logger );

            return component;
        }

        public void dispose() {
            if ( !dispose)
                return;
            try {
                if (component instanceof Disposable)
                {
                    ((Disposable) component).dispose();
                }
            } catch ( Exception ex) {
                getLogger().error("Error disposing component ", ex );
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

	protected Runnable createTask(final Command command) {
		Runnable timerTask = new Runnable() {
			public void run() {
				try {
					command.execute();
				} catch (Exception e) {
					getLogger().error( e.getMessage(), e);
				} catch (Error e) {
					getLogger().error( e.getMessage(), e);
					throw e;
				}
			}
		};
		return timerTask;
	}

    
    protected CommandScheduler createCommandQueue() {
    	
    	CommandScheduler commandQueue = new DefaultScheduler();
		return commandQueue;
	}

 }

