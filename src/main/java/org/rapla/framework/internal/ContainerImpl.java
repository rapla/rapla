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

import org.jetbrains.annotations.Nullable;
import org.rapla.AppointmentFormaterImpl;
import org.rapla.RaplaResources;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.components.util.CommandScheduler;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.framework.*;
import org.rapla.framework.logger.Logger;
import org.rapla.gwtjsonrpc.RemoteJsonMethod;
import org.rapla.gwtjsonrpc.common.RemoteJsonService;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.Extension;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;
import org.rapla.storage.dbrm.RemoteServiceCaller;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

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
    protected Map<String, RoleEntry> m_roleMap = Collections.synchronizedMap(new LinkedHashMap<String, RoleEntry>());
    Logger logger;
    Class webserviceAnnotation;
    protected CommandScheduler commandQueue;
    protected final Provider<RemoteServiceCaller> remoteServiceCaller;
    //public static boolean DEVELOPMENT_RESSOLVING = false;
    Map<String, Object> singletonMap = new ConcurrentHashMap<>();

    public ContainerImpl(Logger logger, final Provider<RemoteServiceCaller> remoteServiceCaller)
    {
        this.logger = logger;
        this.remoteServiceCaller = remoteServiceCaller;
        try
        {
            webserviceAnnotation = Class.forName("javax.jws.WebService");
        }
        catch (Exception ex)
        {
            logger.warn("javax.jws.WebService class not found. Assuming Android env");
        }
        m_context = new RaplaDefaultContext()
        {

            @Override public boolean has(Class<?> componentRole)
            {
                boolean has = super.has(componentRole);
                if (!has && isWebservice(componentRole))
                {
                    return true;
                }
                return has;
            }

            @Override public <T> T lookup(Class<T> componentRole) throws RaplaContextException
            {
                if (isWebservice(componentRole))
                {
                    T proxy = (T) remoteServiceCaller.get().getRemoteMethod(componentRole);
                    return proxy;
                }
                return super.lookup(componentRole);
            }

            @Override protected Object lookup(String role) throws RaplaContextException
            {
                return lookupPrivate(role, 0);
            }

            @Override protected boolean has(String role)
            {
                if (getHandler(role) != null)
                    return true;
                return false;
            }
        };
        addContainerProvidedComponentInstance(Container.class, this);
        addContainerProvidedComponentInstance(Logger.class, logger);
        commandQueue = createCommandQueue();
        addContainerProvidedComponentInstance(CommandScheduler.class, commandQueue);
        addContainerProvidedComponent(BundleManager.class, ServerBundleManager.class);
        addContainerProvidedComponent(RaplaLocale.class, RaplaLocaleImpl.class);
        addContainerProvidedComponent(RaplaResources.class, RaplaResources.class);
    }

    public <T> T inject(Class<T> component, Object... params) throws RaplaContextException
    {
        T result = (T) instanciate(component, 0, params);
        return result;
    }

    protected <T> T getInstance(Class<T> componentRole, Object... params) throws RaplaContextException
    {
        String key = componentRole.getName();//+ "/" + hint;
        ComponentHandler<T> handler = getHandler(key);
        if (handler != null)
        {
            if (handler instanceof RequestComponentHandler)
            {
                RequestComponentHandler<T> handler1 = (RequestComponentHandler) handler;
                T o = handler1.get(0, params);
                return o;
            }
            else
            {
                return handler.get(0);
            }
        }
        throw new RaplaContextException(key);
    }

    @SuppressWarnings("unchecked") @Deprecated public <T> T lookupDeprecated(Class<T> componentRole, String hint) throws RaplaContextException
    {

        String key = componentRole.getName() + "/" + hint;
        ComponentHandler<T> handler = getHandler(key);
        if (handler != null)
        {
            return handler.get(0);
        }
        throw new RaplaContextException(key);
    }

    private Object lookupPrivate(String role, int depth) throws RaplaContextException
    {
        ComponentHandler handler = getHandler(role);
        if (handler != null)
        {
            return handler.get(depth);
        }
        return null;
    }

    protected boolean has(Class componentRole, String hint)
    {

        String key = componentRole.getName() + "/" + hint;
        ComponentHandler handler = getHandler(key);

        if (handler != null)
        {
            return true;
        }
        //        Constructor injectableConstructor = findInjectableConstructor(componentRole);
        //        if ( injectableConstructor != null)
        //        {
        //            return true;
        //        }
        return false;
    }

    public Logger getLogger()
    {
        return logger;
    }

    public StartupEnvironment getStartupEnvironment()
    {
        try
        {
            return getContext().lookup(StartupEnvironment.class);
        }
        catch (RaplaContextException e)
        {
            throw new IllegalStateException(" Container not initialized with a startup environment");
        }
    }

    public <T, I extends T> void addContainerProvidedComponent(Class<T> roleInterface, Class<I> implementingClass)
    {
        addContainerProvidedComponent(roleInterface, implementingClass, null, (Configuration) null);
    }

    public <T, I extends T> void addContainerProvidedComponent(Class<T> roleInterface, Class<I> implementingClass, Configuration config)
    {
        addContainerProvidedComponent(roleInterface, implementingClass, null, config);
    }

    public <T, I extends T> void addContainerProvidedComponent(TypedComponentRole<T> roleInterface, Class<I> implementingClass)
    {
        addContainerProvidedComponent(roleInterface, implementingClass, (Configuration) null);
    }

    public <T, I extends T> void addContainerProvidedComponent(TypedComponentRole<T> roleInterface, Class<I> implementingClass, Configuration config)
    {
        addContainerProvidedComponentPrivate(roleInterface.getId(), implementingClass, null);
    }

    public <T, I extends T> void addContainerProvidedComponentInstance(TypedComponentRole<T> roleInterface, I implementingInstance)
    {
        addContainerProvidedComponentInstance(roleInterface.getId(), implementingInstance, null);
    }

    public <T, I extends T> void addContainerProvidedComponentInstance(Class<T> roleInterface, I implementingInstance)
    {
        addContainerProvidedComponentInstance(roleInterface, implementingInstance, implementingInstance.toString());
    }

    public <T> Collection<T> lookupServicesFor(TypedComponentRole<T> role) throws RaplaContextException
    {
        String id = role.getId();
        return lookupServicesFor(id, 0);
    }

    public <T> Set<T> lookupServicesFor(Class<T> role) throws RaplaContextException
    {
        String id = role.getName();
        return lookupServicesFor(id, 0);
    }

    protected <T, I extends T> void addContainerProvidedComponentInstance(Class<T> roleInterface, I implementingInstance, String hint)
    {
        addContainerProvidedComponentInstance(roleInterface.getName(), implementingInstance, hint);
    }

    protected <T, I extends T> void addContainerProvidedComponent(Class<T> roleInterface, Class<I> implementingClass, String hint, Configuration config)
    {
        addContainerProvidedComponentPrivate(roleInterface.getName(), implementingClass, hint);
    }

    synchronized private void addContainerProvidedComponentPrivate(String role, Class implementingClass, String hint)
    {
        if (implementingClass.getAnnotation(Singleton.class) != null)
        {
            ComponentHandler handler = new SingletonComponentHandler(implementingClass);
            addHandler(role, hint, handler);
        }
        else
        {
            ComponentHandler handler = new RequestComponentHandler(implementingClass);
            addHandler(role, hint, handler);
        }

    }

    synchronized private void addContainerProvidedComponentInstance(String role, Object componentInstance, String hint)
    {
        addHandler(role, hint, new SingletonComponentHandler(componentInstance));
    }

    synchronized private <T> void addRequestComponent(Class<T> role, Class<? extends T> implementingClass, String hint)
    {
        ComponentHandler handler = new RequestComponentHandler(implementingClass);
        addHandler(role.getCanonicalName(), hint, handler);
    }

    private <T> Map<String, T> lookupServiceMapFor(String name, int depth)
    {
        RoleEntry entry = m_roleMap.get(name);
        if (entry == null)
        {
            return Collections.emptyMap();
        }
        Map<String, T> result = new LinkedHashMap<String, T>();
        Set<String> hintSet = entry.getHintSet();
        for (String hint : hintSet)
        {
            ComponentHandler handler = entry.getHandler(hint);
            try
            {
                Object service = handler.get(depth);
                // we can safely cast here because the method is only called from checked methods
                @SuppressWarnings("unchecked") T casted = (T) service;
                result.put(hint, casted);
            }
            catch (Exception e)
            {
                Throwable ex = e;
                while (ex.getCause() != null)
                {
                    ex = ex.getCause();
                }
                getLogger().error("Could not initialize component " + handler + " due to " + ex.getMessage() + " removing from service list", e);
                entry.remove(hint);
            }
        }
        return result;
    }

    private <T> Set< Provider<T>> lookupServiceSetProviderFor(ParameterizedType paramType, String componentClassName)
    {
        final Map<String, Provider<T>> stringProviderMap = lookupServiceMapProviderFor(paramType, componentClassName);
        final Collection<Provider<T>> values = stringProviderMap.values();
        return new LinkedHashSet<>(values);
    }

    private <T> Map<String,Provider<T>> lookupServiceMapProviderFor(ParameterizedType paramType, String componentClassName)
    {
        if(!paramType.getRawType().getTypeName().equals("javax.inject.Provider"))
        {
            throw new IllegalStateException("Can't statisfy constructor dependency  for " + componentClassName + ". Provider is the only generic that is currently supported by rapla injection");
        }
        Type[] actualTypeArguments = paramType.getActualTypeArguments();
        Class<? extends Type> interfaceClass = null;
        if (actualTypeArguments.length > 0)
        {
            final Type param = actualTypeArguments[0];
            if (param instanceof Class)
            {
                interfaceClass = (Class)param;
            }
            else
            {
                throw new IllegalStateException("Provider for " + componentClassName + " can't be created. " + param + " is not a class " );
            }
        }
        else
        {
            throw new IllegalStateException("Provider for " + componentClassName + " can't be created not type specified.");
        }

        RoleEntry entry = m_roleMap.get(interfaceClass.getName());
        if (entry == null)
        {
            return Collections.emptyMap();
        }
        Map<String,Provider<T>> result = new LinkedHashMap<String,Provider<T>>();
        Set<String> hintSet = entry.getHintSet();
        for (String hint : hintSet)
        {
            final ComponentHandler handler = entry.getHandler(hint);
                Provider<T> p = new Provider<T>(){
                    @Override public T get()
                    {
                        try
                        {
                            Object service = handler.get( 0);
                            @SuppressWarnings("unchecked") T casted = (T) service;
                            return casted;
                        }
                        catch (Exception e)
                        {
                            Throwable ex = e;
                            while (ex.getCause() != null)
                            {
                                ex = ex.getCause();
                            }
                            final String message = "Could not initialize component " + handler + " due to " + ex.getMessage() + " removing from service list";
                            getLogger().error(message, e);
                            throw new IllegalStateException(message, e);
                        }
                    }
                };

                // we can safely cast here because the method is only called from checked methods
                result.put(hint, p);
        }
        return result;
    }

    private <T> Set<T> lookupServicesFor(String name, int depth)
    {
        Map<String, T> map = lookupServiceMapFor(name, depth);
        Set<T> result = new LinkedHashSet<T>(map.values());
        return result;
    }

    /**
     * @param roleName
     * @param hint
     * @param handler
     */
    private void addHandler(String roleName, String hint, ComponentHandler handler)
    {
        m_componentHandler.add(handler);
        RoleEntry entry = m_roleMap.get(roleName);
        if (entry == null)
            entry = new RoleEntry(roleName);
        entry.put(hint, handler);
        m_roleMap.put(roleName, entry);
    }

    protected ComponentHandler getHandler(String role)
    {
        int hintSeperator = role.indexOf('/');
        String roleName = role;
        String hint = null;
        if (hintSeperator > 0)
        {
            roleName = role.substring(0, hintSeperator);
            hint = role.substring(hintSeperator + 1);
        }
        RoleEntry entry = m_roleMap.get(roleName);
        if (entry == null)
        {
            return null;
        }

        ComponentHandler handler = entry.getHandler(hint);
        if (handler != null)
        {
            return handler;
        }
        if (hint == null || hint.equals("*"))
            return entry.getFirstHandler();
        // Try the first accessible handler
        return null;

    }


    class RoleEntry
    {
        Map<String, ComponentHandler> componentMap = Collections.synchronizedMap(new LinkedHashMap<String, ComponentHandler>());
        ComponentHandler firstEntry;
        int generatedHintCounter = 0;
        String roleName;

        RoleEntry(String roleName)
        {
            this.roleName = roleName;
        }

        String generateHint()
        {
            return roleName + "_" + generatedHintCounter++;
        }

        void put(String hint, ComponentHandler handler)
        {
            if (hint == null)
            {
                hint = generateHint();
            }
            synchronized (this)
            {
                componentMap.put(hint, handler);
            }
            if (firstEntry == null)
                firstEntry = handler;
        }

        void remove(String hint)
        {
            componentMap.remove(hint);
        }

        Set<String> getHintSet()
        {
            // we return a clone to avoid concurrent modification exception
            synchronized (this)
            {
                LinkedHashSet<String> result = new LinkedHashSet<String>(componentMap.keySet());
                return result;
            }
        }

        ComponentHandler getHandler(String hint)
        {
            return componentMap.get(hint);
        }

        ComponentHandler getFirstHandler()
        {
            return firstEntry;
        }

        public String toString()
        {
            return componentMap.toString();
        }

    }

    public RaplaContext getContext()
    {
        return m_context;
    }

    boolean disposing;

    public void dispose()
    {
        getLogger().info("Shutting down rapla-container");
        // prevent reentrence in dispose
        synchronized (this)
        {
            if (disposing)
            {
                getLogger().warn("Disposing is called twice", new RaplaException(""));
                return;
            }
            disposing = true;
        }
        try
        {
            if (commandQueue != null)
            {
                ((DefaultScheduler) commandQueue).cancel();
            }
            removeAllComponents();
        }
        finally
        {
            disposing = false;
        }
    }

    protected void removeAllComponents()
    {

        ArrayList<ComponentHandler> componentHandlers = new ArrayList<ComponentHandler>(m_componentHandler);
        for (ComponentHandler comp : componentHandlers)
        {
            if (comp instanceof Disposable)
            {
                ((Disposable) comp).dispose();
            }
        }
        m_componentHandler.clear();
        m_roleMap.clear();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" }) Constructor findInjectableConstructor(Class componentClass)
    {
        Constructor[] constructors = componentClass.getConstructors();
        Constructor emptyPublic = null;
        for (Constructor constructor : constructors)
        {
            Class[] types = constructor.getParameterTypes();
            boolean compatibleParameters = true;
            if (constructor.getAnnotation(Inject.class) != null)
            {
                return constructor;
            }
            if ( types.length == 0  )
            {
                if (Modifier.isPublic(constructor.getModifiers()))
                {
                    emptyPublic = constructor;
                }
            }
        }
        return emptyPublic;
    }

    @SuppressWarnings("unchecked") protected boolean isWebservice(Class type)
    {
        if (webserviceAnnotation != null)
        {
            if (type.isAnnotationPresent(webserviceAnnotation))
            {
                return true;
            }
        }
        //boolean assignableFrom = type.isAssignableFrom( RemoteJsonService.class );
        boolean assignableFrom = RemoteJsonService.class.isAssignableFrom(type);
        return assignableFrom;
        //return type.isAnnotationPresent(WebService.class);
    }

    /** Instantiates a class and passes the config, logger and the parent context to the object if needed by the constructor.
     * This concept is taken form pico container.*/
    Map<Class, Semaphore> instanciating = new ConcurrentHashMap<>();
    @SuppressWarnings({ "rawtypes", "unchecked" }) private Object instanciate(Class componentClass, int depth, final Object... additionalParamObject) throws RaplaContextException
    {
        String componentClassName = componentClass.getName();
        final boolean isSingleton = componentClass.getAnnotation(Singleton.class) != null;

        if ( depth > 50)
        {
            throw new RaplaContextException("Dependency cycle while injection " + componentClassName + " aborting!");
        }
        if (isSingleton)
        {
            Object singleton = singletonMap.get(componentClassName);
            if (singleton != null)
            {
                return singleton;
            }
            else
            {
                try
                {
                    final Semaphore result = instanciating.putIfAbsent(componentClass, new Semaphore(0));
                    if(result != null)
                    {
                        result.acquire();
                        result.release();
                        return singletonMap.get( componentClassName);
                    }
                }
                catch (InterruptedException e)
                {
                    throw new RaplaContextException("Timeout while waiting for instanciation of " + componentClass.getName());
                }
            }
        }
        final List<Object> additionalParams = new ArrayList<Object>(Arrays.asList(additionalParamObject));
        final RaplaContext context = m_context;
        Constructor c = findInjectableConstructor(componentClass);
        if ( c == null)
        {
            throw new RaplaContextException("No javax.inject.Inject Annotation or public default contructor found in class " +componentClass);
        }
        Object[] params = null;
        Class[] types = c.getParameterTypes();
        Annotation[][] parameterAnnotations = c.getParameterAnnotations();
        Type[] genericParameterTypes = c.getGenericParameterTypes();
        params = new Object[types.length];
        for (int i = 0; i < types.length; i++)
        {
            final Class type = types[i];
            Object p = null;
            Annotation[] annotations = parameterAnnotations[i];
            for (Annotation annotation : annotations)
            {
                if (annotation.annotationType().equals(Named.class))
                {
                    String value = ((Named) annotation).value();
                    Object lookup = lookupPrivate(new TypedComponentRole(value).getId(), depth+1);
                    p = lookup;
                }
            }
            if (p != null)
            {
                params[i] = p;
                continue;
            }
            String typeName = type.getName();
            final Type type2 = genericParameterTypes[i];
            if (typeName.equals("javax.inject.Provider") && type2 instanceof ParameterizedType)
            {
                Object result = createProvider((ParameterizedType) type2);
                p = result;
            }
            if (typeName.equals("java.util.Set") && type2 instanceof ParameterizedType)
            {
                Type[] actualTypeArguments = ((ParameterizedType) type2).getActualTypeArguments();
                if (actualTypeArguments.length > 0)
                {
                    final Type param = actualTypeArguments[0];
                    if (param instanceof Class)
                    {
                        final Class<? extends Type> class1 = (Class<? extends Type>) param;
                        p = lookupServicesFor(class1);
                    }
                    else if ( param instanceof ParameterizedType)
                    {
                        p = lookupServiceSetProviderFor((ParameterizedType) param, componentClassName);
                    }
                    else
                    {
                        throw new IllegalStateException("Can't statisfy constructor dependency  for " + componentClassName + " unknown type " + param);
                    }
                } else {
                    throw new IllegalStateException("Can't statisfy constructor dependency untyped java.util.Set is not supported for " + componentClassName);
                }
            }
            if (typeName.equals("java.util.Map") && type2 instanceof ParameterizedType)
            {
                Type[] actualTypeArguments = ((ParameterizedType) type2).getActualTypeArguments();
                if (actualTypeArguments.length > 1)
                {
                    {
                        final Type param = actualTypeArguments[0];
                        if (!(param.equals(String.class)))
                        {
                            throw new IllegalStateException("Can't statisfy constructor dependency java.util.Map is only supported for String keys. Error in " + componentClassName);
                        }
                    }
                    {
                        final Type param = actualTypeArguments[1];
                        if (param instanceof Class)
                        {
                            final Class<? extends Type> class1 = (Class<? extends Type>) param;
                            String class1Name = class1.getName();
                            p = lookupServiceMapFor(class1Name, depth);
                        }
                        else if ( param instanceof ParameterizedType)
                        {
                            p = lookupServiceMapProviderFor((ParameterizedType)param, componentClassName);
                        }
                        else
                        {
                            throw new IllegalStateException("Can't statisfy constructor dependency  for " + componentClassName + " unknown type " + param);
                        }
                    }
                } else {
                    throw new IllegalStateException("Can't statisfy constructor dependency untyped java.util.Map is not supported for " + componentClassName);
                }

            }
            if (RaplaContext.class.isAssignableFrom(type))
            {
                p = context;
            }
            if (p != null)
            {
                params[i] = p;
                continue;
            }
            {
                Class guessedRole = type;
                if (context.has(guessedRole))
                {
                    p = context.lookup(guessedRole);
                }
                else
                {
                    for (int j =0;j< additionalParams.size();j++)
                    {
                        Object additional = additionalParams.get(j);
                        Class<?> aClass = additional.getClass();
                        if (guessedRole.isAssignableFrom(aClass))
                        {
                            p = additional;
                            // we need to remove the additional params we used, to prevent to inject the same param twice
                            // e.g. MyClass(Date startDate, Date endDate)
                            additionalParams.remove(j);
                            break;
                        }
                    }
                }
                if ( p == null)
                {
                    Constructor injectableConstructor = findInjectableConstructor(guessedRole);
                    if (injectableConstructor != null)
                    {
                        p = instanciate( guessedRole, depth+1);
                    }
                }
                if (p == null)
                {
                    throw new RaplaContextException(componentClass, "Can't statisfy constructor dependency " + type.getName());
                }

            }
            params[i] = p;
        }
        try
        {
            final Object component = c.newInstance(params);
            if (isSingleton)
            {
                instanciating.get(componentClass).release();
                singletonMap.put(componentClassName, component);
            }
            return component;
        }
        catch (Exception e)
        {
            throw new RaplaContextException(componentClassName + " could not be initialized due to " + e.getMessage(), e);
        }
    }

    @Nullable private Provider createProvider(ParameterizedType type)
    {
        Type[] actualTypeArguments = type.getActualTypeArguments();
        if (actualTypeArguments.length > 0)
        {
            final Type param = actualTypeArguments[0];
            if (param instanceof Class)
            {
                final Class<? extends Type> class1 = (Class<? extends Type>) param;
                Provider result = new Provider()
                {
                    @Override public Object get()
                    {
                        try
                        {
                            return getContext().lookup( class1);
                        }
                        catch (RaplaContextException e)
                        {
                            throw new IllegalStateException(e.getMessage(), e);
                        }
                    }

                };
                return result;
            }
            else
            {
                throw new IllegalStateException("Provider for " + type + " can't be created. " + param + " is not a class " );
            }
        }
        else
        {
            throw new IllegalStateException("Provider for " + type + " can't be created not type specified.");
        }
    }

    abstract protected class ComponentHandler<T>
    {
        protected Class componentClass;

        abstract T get(int depth) throws RaplaContextException;
    }

    protected class RequestComponentHandler<T> extends ComponentHandler<T>
    {
        protected RequestComponentHandler(Class<T> componentClass)
        {
            this.componentClass = componentClass;

        }

        @Override T get(int depth) throws RaplaContextException
        {
            Object component = instanciate(componentClass, depth);
            return (T)component;
        }

        T get(int depth,Object... params) throws RaplaContextException
        {
            Object component = instanciate(componentClass, depth,params);
            return (T)component;
        }
    }

    protected class SingletonComponentHandler extends ComponentHandler implements Disposable
    {
        protected Object component;
        boolean dispose = true;

        protected SingletonComponentHandler(Object component)
        {
            this.component = component;
            this.dispose = false;
        }

        protected SingletonComponentHandler( Class componentClass)
        {
            this.componentClass = componentClass;
        }

        Object get(int depth) throws RaplaContextException
        {
            if (component != null)
            {
                return component;
            }
            component = instanciate(componentClass, depth);
            return component;
        }

        boolean disposing;

        public void dispose()
        {
            // prevent reentrence in dispose
            synchronized (this)
            {
                if (disposing)
                {
                    getLogger().warn("Disposing is called twice", new RaplaException(""));
                    return;
                }
                disposing = true;
            }
            try
            {
                if (component instanceof Disposable)
                {
                    if (component == ContainerImpl.this)
                    {
                        return;
                    }
                    ((Disposable) component).dispose();
                }
            }
            catch (Exception ex)
            {
                getLogger().error("Error disposing component ", ex);
            }
            finally
            {
                disposing = false;
            }
        }

        public String toString()
        {
            if (component != null)
            {
                return component.toString();
            }
            if (componentClass != null)
            {
                return componentClass.getName();
            }
            return super.toString();
        }
    }

    protected CommandScheduler createCommandQueue()
    {
        CommandScheduler commandQueue = new DefaultScheduler(getLogger(), 6);
        return commandQueue;
    }

    protected void loadFromServiceList() throws Exception
    {
        String folder = org.rapla.inject.generator.AnnotationInjectionProcessor.GWT_MODULE_LIST;

        Set<String> interfaces = new TreeSet<String>();
        final Collection<URL> resources = find(folder);
        for (URL url : resources)
        {
            final InputStream modules = url.openStream();
            final BufferedReader br = new BufferedReader(new InputStreamReader(modules, "UTF-8"));
            String module = null;
            while ((module = br.readLine()) != null)
            {
                interfaces.add(module);
            }
            br.close();
        }
        for (String module : interfaces)
        {
            Class<?> interfaceClass;
            try
            {
                interfaceClass = Class.forName(module);
            }
            catch (ClassNotFoundException e1)
            {
                logger.warn("Found interfaceName definition but no class for " + module);
                continue;
            }
            addImplementations(interfaceClass);
        }
    }

    private Collection<URL> find(String fileWithfolder) throws IOException
    {

        List<URL> result = new ArrayList<URL>();
        Enumeration<URL> resources = this.getClass().getClassLoader().getResources(fileWithfolder);
        while (resources.hasMoreElements())
        {
            result.add(resources.nextElement());
        }
        return result;
    }

    private static Collection<String> getImplementingIds(Class interfaceClass, Extension... clazzAnnot)
    {
        Set<String> ids = new LinkedHashSet<>();
        for (Extension ext : clazzAnnot)
        {
            final Class provides = ext.provides();
            if (provides.equals(interfaceClass))
            {
                String id = ext.id();
                ids.add(id);
            }
        }
        return ids;
    }

    protected boolean isSupported(InjectionContext... context)
    {
        return true;
    }


    private boolean isImplementing(Class interfaceClass, DefaultImplementation... clazzAnnot)
    {
        for (DefaultImplementation ext : clazzAnnot)
        {
            final Class provides = ext.of();
            final InjectionContext[] context = ext.context();
            if (provides.equals(interfaceClass) && isSupported(context))
            {
                return true;
            }
        }
        return false;
    }

    private <T> void addImplementations(Class<T> interfaceClass) throws IOException
    {
        final ExtensionPoint extensionPointAnnotation = interfaceClass.getAnnotation(ExtensionPoint.class);
        final RemoteJsonMethod remoteJsonMethodAnnotation = interfaceClass.getAnnotation(RemoteJsonMethod.class);
        final boolean isExtensionPoint = extensionPointAnnotation != null;
        final boolean isRemoteMethod = remoteJsonMethodAnnotation != null;
        if (isExtensionPoint)
        {
            final InjectionContext[] context = extensionPointAnnotation.context();
            if (!isSupported(context))
            {
                return;
            }
        }

        final String folder = "META-INF/services/";
        boolean foundExtension = false;
        boolean foundDefaultImpl = false;
        // load all implementations or extensions from service list file
        Set<String> implemantations = new LinkedHashSet<String>();
        final String interfaceName = interfaceClass.getCanonicalName();
        final Collection<URL> resources = find(folder + interfaceName);
        for (URL url : resources)
        {
            //final URL def = moduleDefinition.nextElement();
            final InputStream in = url.openStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String implementationClassName = null;
            boolean implOrExtensionFound = false;
            while ((implementationClassName = reader.readLine()) != null)
            {
                try
                {
                    if (implemantations.contains(implementationClassName))
                    {
                        continue;
                    }
                    else
                    {
                        implemantations.add(implementationClassName);
                    }
                    // load class for implementation or extension
                    final Class<T> clazz = (Class<T>) Class.forName(implementationClassName);
                    final Extension[] extensions = clazz.getAnnotationsByType(Extension.class);

                    Collection<String> idList = getImplementingIds(interfaceClass, extensions);
                    // add extension implmentations
                    if (idList.size() > 0)
                    {

                        for (String id : idList)
                        {
                            foundExtension = true;
                            addContainerProvidedComponent(interfaceClass, clazz, id, null);
                            getLogger().info("Found extension for " + interfaceName + " : " +implementationClassName );
                        }
                    }
                    else
                    {
                        if (isExtensionPoint)
                        {
                            logger.warn(clazz + " provides no extension for " + interfaceName + " but is in the service list of " + interfaceName
                                    + ". You may need run a clean build.");
                        }
                    }
                    // add default implmentations
                    final DefaultImplementation[] defaultImplementations = clazz.getAnnotationsByType(DefaultImplementation.class);
                    final boolean implementing = isImplementing(interfaceClass, defaultImplementations);
                    if (implementing)
                    {
                        if (isRemoteMethod)
                        {
                            String id  = remoteJsonMethodAnnotation.path();
                            if ( id == null || id.isEmpty())
                            {
                                id = interfaceName;
                            }
                            addRequestComponent(interfaceClass, clazz, id);
                        }
                        else
                        {
                            addContainerProvidedComponent(interfaceClass, clazz, (Configuration) null);
                        }
                        getLogger().info("Found implementation for " + interfaceName + " : " +implementationClassName );

                        foundDefaultImpl = true;
                        // not necessary in current impl
                        //src.println("binder.bind(" + interfaceName + ".class).to(" + implementationClassName + ".class).in(Singleton.class);");
                    }

                }
                catch (ClassNotFoundException e)
                {
                    logger.warn("Error loading implementationClassName (" + implementationClassName + ") for " + interfaceName);
                }

            }
            reader.close();
        }

        if (isExtensionPoint)
        {
            if (!foundExtension)
            {
                // not necessary to create a default Binding
            }
        }
        else
        {
            if (!foundDefaultImpl)
            {
                //logger.warn( "No DefaultImplemenation found for " + interfaceName + " Interface will not be available in the supported Contexts " + supportedContexts  + " ");
            }
        }
    }

    protected void initialize() throws Exception
    {
        //Logger logger = getLogger();
        CalendarOptions calendarOptions = new CalendarOptionsImpl(new DefaultConfiguration());
        addContainerProvidedComponentInstance(CalendarOptions.class, calendarOptions);
        addContainerProvidedComponent(AppointmentFormater.class, AppointmentFormaterImpl.class);

        // Discover and register the plugins for Rapla
        RaplaResources i18n = getContext().lookup(RaplaResources.class);
        String version = i18n.getString("rapla.version");
        logger.info("Rapla.Version=" + version);
        version = i18n.getString("rapla.build");
        logger.info("Rapla.Build=" + version);
        AttributeImpl.TRUE_TRANSLATION.setName(i18n.getLang(), i18n.getString("yes"));
        AttributeImpl.FALSE_TRANSLATION.setName(i18n.getLang(), i18n.getString("no"));
        try
        {
            version = System.getProperty("java.version");
            logger.info("Java.Version=" + version);
        }
        catch (SecurityException ex)
        {
            version = "-";
            logger.warn("Permission to system property java.version is denied!");
        }
    }

    /*
    protected Set<String> discoverPluginClassnames() throws RaplaException
    {
        try
        {
            Set<String> pluginNames = new LinkedHashSet<String>();

            //            boolean isDevelopment = getContext().has(ContainerImpl.ENV_DEVELOPMENT) ? getContext().lookupDeprecated( ContainerImpl.ENV_DEVELOPMENT) : DEVELOPMENT_RESSOLVING;
            Enumeration<URL> pluginEnum = ConfigTools.class.getClassLoader().getResources("META-INF/rapla-plugin.list");
            //            if (!pluginEnum.hasMoreElements() || isDevelopment)
            //            {
            //                Collection<String> result = ServiceListCreator.findPluginClasses(logger);
            //                pluginNames.addAll(result);
            //            }

            while (pluginEnum.hasMoreElements())
            {
                BufferedReader reader = new BufferedReader(new InputStreamReader((pluginEnum.nextElement()).openStream()));
                while (true)
                {
                    String plugin = reader.readLine();
                    if (plugin == null)
                        break;
                    pluginNames.add(plugin);
                }
            }
            return pluginNames;
        }
        catch (Exception ex)
        {
            throw new RaplaException(ex.getMessage(), ex);
        }
    }

    private <T extends Container> List<PluginDescriptor<T>> getPluginList(Class<T> containerType) throws RaplaException
    {
        Logger logger = getLogger().getChildLogger("plugin");
        @SuppressWarnings("unchecked") List<PluginDescriptor<T>> pluginList = new ArrayList();
        Set<String> pluginNames = discoverPluginClassnames();
        for (String plugin : pluginNames)
        {
            try
            {
                boolean found = false;
                try
                {
                    Class<?> componentClass = ContainerImpl.class.getClassLoader().loadClass(plugin);
                    Method[] methods = componentClass.getMethods();
                    for (Method method : methods)
                    {
                        if (method.getName().equals("provideServices"))
                        {
                            Class<?> type = method.getParameterTypes()[0];
                            if (containerType.isAssignableFrom(type))
                            {
                                found = true;
                            }
                        }
                    }
                }
                catch (ClassNotFoundException ex)
                {
                    continue;
                }
                catch (NoClassDefFoundError ex)
                {
                    getLogger().error("Error loading plugin " + plugin + " " + ex.getMessage());
                    continue;
                }
                catch (Exception e1)
                {
                    getLogger().error("Error loading plugin " + plugin + " " + e1.getMessage());
                    continue;
                }
                if (found)
                {
                    @SuppressWarnings("unchecked") PluginDescriptor<T> descriptor = (PluginDescriptor<T>) instanciate(plugin, null, logger);
                    pluginList.add(descriptor);
                    logger.info("Installed plugin " + plugin);
                }
            }
            catch (RaplaContextException e)
            {
                if (e.getCause() instanceof ClassNotFoundException)
                {
                    logger.error("Could not instanciate plugin " + plugin, e);
                }
            }
        }
        return pluginList;
    }

    private Configuration findPluginConfig(RaplaConfiguration raplaConfig, final String pluginClassname)
    {
        Configuration pluginConfig;
        // TODO should be replaced with a more descriptive approach instead of looking for the config by guessing from the package name
        pluginConfig = raplaConfig.find("class", pluginClassname);
        // If no plugin config for server is found look for plugin config for client plugin
        if (pluginConfig == null)
        {
            String newClassname = pluginClassname.replaceAll("ServerPlugin", "Plugin");
            newClassname = newClassname.replaceAll(".server.", ".client.");
            pluginConfig = raplaConfig.find("class", newClassname);
            if (pluginConfig == null)
            {
                newClassname = newClassname.replaceAll(".client.", ".");
                pluginConfig = raplaConfig.find("class", newClassname);
            }
        }
        return pluginConfig;
    }


    protected <T extends Container> List<PluginDescriptor<T>> initializePlugins(Preferences preferences, Class<T> pluginContainerClass) throws RaplaException
    {
        List<PluginDescriptor<T>> pluginList = getPluginList(pluginContainerClass);
        RaplaConfiguration raplaConfig = preferences.getEntry(RaplaComponent.PLUGIN_CONFIG);
        // Add plugin configs
        for (Iterator<PluginDescriptor<T>> it = pluginList.iterator(); it.hasNext(); )
        {
            PluginDescriptor<T> pluginDescriptor = it.next();
            String pluginClassname = pluginDescriptor.getClass().getName();
            Configuration pluginConfig = null;
            if (raplaConfig != null)
            {
                pluginConfig = findPluginConfig(raplaConfig, pluginClassname);
            }
            if (pluginConfig == null)
            {
                pluginConfig = new DefaultConfiguration("plugin");
            }
            @SuppressWarnings("unchecked") T container = (T) this;
            pluginDescriptor.provideServices(container, pluginConfig);
        }
        return pluginList;
    }
*/

}

