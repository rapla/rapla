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
package org.rapla.server.internal;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.TimeZoneConverter;
import org.rapla.server.extensionpoints.ServerExtension;
import org.rapla.server.servletpages.ServletRequestPreprocessor;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.impl.server.LocalAbstractCachableOperator;

import dagger.MembersInjector;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;

@DefaultImplementation(of = ServerServiceContainer.class, context = InjectionContext.server, export = true) public class ServerServiceImpl
        implements ServerServiceContainer
{
    final protected CachableStorageOperator operator;
    final protected RaplaFacade facade;
    final Logger logger;

    private boolean passwordCheckDisabled;
//    private final RaplaRpcAndRestProcessor apiPage;
    private final RaplaLocale raplaLocale;
    private final CommandScheduler scheduler;

    final Set<ServletRequestPreprocessor> requestPreProcessors;
    private Map<String, MembersInjector> membersInjector;

    public Collection<ServletRequestPreprocessor> getServletRequestPreprocessors()
    {
        return requestPreProcessors;
    }

    @Inject public ServerServiceImpl(CachableStorageOperator operator, RaplaFacade facade, RaplaLocale raplaLocale, TimeZoneConverter importExportLocale,
            Logger logger, final Provider<Map<String, ServerExtension>> serverExtensions, final Provider<Set<ServletRequestPreprocessor>> requestPreProcessors,
            CommandScheduler scheduler, ServerContainerContext serverContainerContext, Map<String, MembersInjector> membersInjector) throws RaplaInitializationException
    {
        try
        {
            this.scheduler = scheduler;
            this.logger = logger;
            this.raplaLocale = raplaLocale;
            //webMethods.setList( );
            //        SimpleProvider<Object> externalMailSession = new SimpleProvider<Object>();
            //        if (containerContext.mailSession != null)
            //        {
            //            externalMailSession.setValue(containerContext.getMailSession());
            //        }
            this.operator = operator;
            this.facade = facade;
            this.membersInjector = membersInjector;
            ((FacadeImpl) facade).setOperator(operator);
//        this.apiPage = new RaplaRpcAndRestProcessor(logger, webservices.get());
            //        if ( username != null  )
            //            operator.connect( new ConnectInfo(username, password.toCharArray()));
            //        else
            
            // Start database or file connection and read data
            operator.connect();
            Preferences preferences = operator.getPreferences(null, true);
            //RaplaConfiguration encryptionConfig = preferences.getEntry(EncryptionService.CONFIG);
            //addRemoteMethodFactory( EncryptionService.class, EncryptionServiceFactory.class);
            String importExportTimeZone = TimeZone.getDefault().getID();
            // get old entries
            RaplaConfiguration entry = preferences.getEntry(RaplaComponent.PLUGIN_CONFIG);
            if (entry != null)
            {
                Configuration find = entry.find("class", Export2iCalPlugin.PLUGIN_CLASS);
                if (find != null)
                {
                    String timeZone = find.getChild("TIMEZONE").getValue(null);
                    if (timeZone != null && !timeZone.equals("Etc/UTC"))
                    {
                        importExportTimeZone = timeZone;
                    }
                }
            }
            String timezoneId = preferences.getEntryAsString(ContainerImpl.TIMEZONE, importExportTimeZone);
            //TimeZoneConverter importExportLocale = lookup(TimeZoneConverter.class);
            try
            {
                TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();
                TimeZone timeZone = registry.getTimeZone(timezoneId);
                if (timeZone == null)
                {
                    // FIXME create VTimezones for GMT+1-12 and GMT-1-12 
                    // if ( timezoneId.startsWith("GMT") )
                    String fallback = "Etc/GMT";
                    logger.error("Timezone " + timezoneId + " not found in ical registry. " + " Using " + fallback);
                    timeZone = registry.getTimeZone(fallback);
                    if (timeZone == null)
                    {
                        if (timeZone == null)
                        {
                            throw new RaplaException(fallback + " timezone not found in ical registry. ical4j maybe corrupted or not loaded correctyl");
                        }
                    }
                }
                ((RaplaLocaleImpl) raplaLocale).setImportExportTimeZone(timeZone);
                ((TimeZoneConverterImpl) importExportLocale).setImportExportTimeZone(timeZone);
                if (operator instanceof LocalAbstractCachableOperator)
                {
                    ((LocalAbstractCachableOperator) operator).setTimeZone(timeZone);
                }
            }
            catch (Exception rc)
            {
                logger.error(
                        "Timezone " + timezoneId + " not found. " + rc.getMessage() + " Using system timezone " + importExportLocale.getImportExportTimeZone());
            }
            
            /*
        {// Rest Pages
            @SuppressWarnings("rawtypes")
            final Set<Entry<String, Factory>> restPageEntries = restPageFactories.entrySet();
            for (Entry<String, Factory> restPage : restPageEntries)
            {
                final String restPagePath = restPage.getKey();
                final Factory restPageFactory = restPage.getValue();
                final Class<?> restPageClass = restPageFactory.get().getClass();
                final RaplaRestApiWrapper restWrapper = new RaplaRestApiWrapper(logger, tokenHandler, raplaAuthentificationService, restPageClass, restPageFactory);
                restPages.put(restPagePath, restWrapper);
            }
        }
             */
            
            //User user = getFirstAdmin(operator);
            //adminSession = new RemoteSessionImpl(getLogger().getChildLogger("session"), user);
            //addContainerProvidedComponentInstance(RemoteSession.class, adminSession);
            //initializePlugins(preferences, ServerServiceContainer.class);
            // start server provides
            this.requestPreProcessors = requestPreProcessors.get();
            
            final Map<String, ServerExtension> stringServerExtensionMap = serverExtensions.get();
            for (Map.Entry<String, ServerExtension> extensionEntry : stringServerExtensionMap.entrySet())
            {
                final String key = extensionEntry.getKey();
                ServerExtension extension = extensionEntry.getValue();
                if (serverContainerContext.isServiceEnabled(key))
                {
                    extension.start();
                }
            }
        }
        catch( RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }
    }
    
    public Map<String, MembersInjector> getMembersInjector()
    {
        return membersInjector;
    }

    public RaplaLocale getRaplaLocale()
    {
        return raplaLocale;
    }

    public Logger getLogger()
    {
        return logger;
    }

    public RaplaFacade getFacade()
    {
        return facade;
    }

    public void setPasswordCheckDisabled(boolean passwordCheckDisabled)
    {
        this.passwordCheckDisabled = passwordCheckDisabled;
    }

    public String getFirstAdmin() throws RaplaException
    {
        User user = getFirstAdmin(operator);
        if (user == null)
        {
            return null;
        }
        else
        {
            return user.getUsername();
        }
    }

    public User getFirstAdmin(StorageOperator operator) throws RaplaException
    {
        Set<User> sorted = new TreeSet<User>(User.USER_COMPARATOR);
        sorted.addAll(operator.getUsers());
        for (User u : sorted)
        {
            if (u.isAdmin())
            {
                return u;
            }
        }
        return null;
    }

    @Override public void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        String requestURI = request.getRequestURI();
        String raplaPrefix = "rapla/";
        String contextPath = request.getContextPath();
        String toParse;
        if (requestURI.startsWith(contextPath))
        {
            toParse = requestURI.substring(contextPath.length());
        }
        else
        {
            toParse = requestURI;
        }
        if (toParse.startsWith("/"))
        {
            toParse = toParse.substring(1);
        }
        while (toParse.toLowerCase().startsWith(raplaPrefix))
        {
            toParse = toParse.substring(raplaPrefix.length());
        }
        String path = toParse;
        final String pagename;
        final String appendix;
        int firstSeparator = path.indexOf('/');
        if (firstSeparator > 1)
        {
            pagename = path.substring(0, firstSeparator);
            appendix = path.substring(firstSeparator + 1);
        }
        else
        {
            if (path.trim().isEmpty() || path.toLowerCase().equals("rapla"))
            {
                final String pageParam = request.getParameter("page");
                if (pageParam != null && !pageParam.trim().isEmpty())
                {
                    pagename = pageParam;
                }
                else
                {
                    pagename = "index";
                }
            }
            else
            {
                pagename = path;
            }
            appendix = null;
        }
        final ServletContext servletContext = request.getServletContext();

//        final RaplaRpcAndRestProcessor.Path b = apiPage.find(pagename, appendix);
//        if (b != null)
//        {
//            apiPage.generate(servletContext, request, response, b);
//        }
//        else
//        {
            print404Response(response, pagename);
//        }

    }

    public <T> T getMockService(final Class<T> test, final String accessToken)
    {
        return null;
//        InvocationHandler invocationHandler = new InvocationHandler()
//        {
//            @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
//            {
//                if (method.getName().equals("getParameter"))
//                {
//                    String key = (String) args[0];
//                    if (key.equals("access_token"))
//                    {
//                        return accessToken;
//                    }
//                }
//                return null;
//            }
//        };
//        HttpServletRequest request = (HttpServletRequest) Proxy
//                .newProxyInstance(getClass().getClassLoader(), new Class[] { HttpServletRequest.class }, invocationHandler);
//        HttpServletResponse response = (HttpServletResponse) Proxy
//                .newProxyInstance(getClass().getClassLoader(), new Class[] { HttpServletResponse.class }, invocationHandler);
//        final T o = (T) apiPage.webserviceMap.get(test.getCanonicalName()).create(request, response);
//        return o;
    }

    private void print404Response(HttpServletResponse response, String page) throws IOException
    {
        response.setStatus(404);
        java.io.PrintWriter out = null;
        try
        {
            out = response.getWriter();
            String message = "404: Page " + page + " not found in Rapla context";
            out.print(message);
            logger.getChildLogger("server.html.404").warn(message);
        }
        finally
        {
            if (out != null)
            {
                out.close();
            }
        }
    }

    private void stop()
    {
        ((DefaultScheduler) scheduler).dispose();
        boolean wasConnected = operator.isConnected();
        Logger logger = getLogger();
        try
        {
            operator.disconnect();
        }
        catch (RaplaException e)
        {
            logger.error("Could not disconnect operator ", e);
        }
        finally
        {
        }
        if (wasConnected)
        {
            logger.info("Storage service stopped");
        }
    }

    public void dispose()
    {
        stop();
    }

    public StorageOperator getOperator()
    {
        return operator;
    }

}
