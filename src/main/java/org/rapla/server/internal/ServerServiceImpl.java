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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

import org.rapla.RaplaResources;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.SimpleProvider;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.rest.RemoteLogger;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.RaplaServerExtensionPoints;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;
import org.rapla.server.ServerService;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.TimeZoneConverter;
import org.rapla.server.servletpages.*;
import org.rapla.storage.*;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.dbrm.*;
import org.rapla.storage.dbrm.RemoteAuthentificationService;
import org.rapla.storage.dbsql.DBOperator;
import org.rapla.storage.impl.server.ImportExportManagerImpl;
import org.rapla.storage.impl.server.LocalAbstractCachableOperator;

import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;


public class ServerServiceImpl extends ContainerImpl implements StorageUpdateListener, ServerServiceContainer, ServerService, ShutdownService
{

    @SuppressWarnings("rawtypes")
    public static Class<RemoteMethodFactory> REMOTE_METHOD_FACTORY = RemoteMethodFactory.class;
    static Class<RaplaPageGenerator> SERVLET_PAGE_EXTENSION = RaplaPageGenerator.class;

    protected CachableStorageOperator operator;


    ShutdownService shutdownService;


    private boolean passwordCheckDisabled;

    RemoteSessionImpl adminSession;

    public static class ServerContainerContext
    {
        DataSource dbDatasource;
        String fileDatasource;
        Object mailSession;

        public String getFileDatasource()
        {
            return fileDatasource;
        }

        public Object getMailSession()
        {
            return mailSession;
        }

        public DataSource getDbDatasource()
        {
            return dbDatasource;
        }
    }

    @Inject
    public ServerServiceImpl(Logger logger, ServerContainerContext containerContext, String selectedStorage) throws Exception
    {
        super(logger, new SimpleProvider<RemoteServiceCaller>());
        ((SimpleProvider<RemoteServiceCaller>) remoteServiceCaller).setValue(new RemoteServiceCaller()
        {

            @Override
            public <T> T getRemoteMethod(Class<T> a) throws RaplaContextException
            {
                RemoteSession remoteSession = adminSession;
                T service = inject(a, remoteSession);
                return service;
            }
        });


        //        URL downloadURL = env.getDownloadURL();
        //        if (downloadURL != null)
        //        {
        //            File file = IOUtil.getFileFrom( downloadURL);
        //            addContainerProvidedComponentInstance( ServerService.CONTEXT_ROOT, file.getPath());
        //        }
        addContainerProvidedComponentInstance(ServerService.TIMESTAMP, new Object()
        {

            public String toString()
            {
                DateFormat formatter = new SimpleDateFormat("yyyyMMdd-HHmmss");
                String formatNow = formatter.format(new Date());
                return formatNow;
            }

        });
        if (containerContext.fileDatasource != null)
        {
            addContainerProvidedComponentInstance(ServerService.ENV_RAPLAFILE, containerContext.fileDatasource);
            addContainerProvidedComponent(FileOperator.class, FileOperator.class);
        }
        if ( selectedStorage == null)
        {
            addContainerProvidedComponentInstance(ServerService.ENV_RAPLAFILE, "data/data.xml");
            addContainerProvidedComponent(FileOperator.class, FileOperator.class);
        }
        if (containerContext.dbDatasource != null)
        {
            addContainerProvidedComponentInstance(DataSource.class, containerContext.dbDatasource);
            addContainerProvidedComponent(DBOperator.class, DBOperator.class);
        }
        if (containerContext.fileDatasource != null && containerContext.dbDatasource != null)
        {
            addContainerProvidedComponent(ImportExportManager.class, ImportExportManagerImpl.class);
        }
        if (containerContext.mailSession != null)
        {
            addContainerProvidedComponentInstance(ServerService.ENV_RAPLAMAIL, containerContext.mailSession);
        }
        loadFromServiceList();
        initialize();
        //addContainerProvidedComponent(TimeZoneConverter.class, TimeZoneConverterImpl.class);
        if (selectedStorage == null || "raplafile".equals( selectedStorage ))
        {
            operator = getContext().lookup(FileOperator.class);
        }
        else if (selectedStorage.equals("rapladb"))
        {
            operator = getContext().lookup(DBOperator.class);
        }
        else
        {
            throw new RaplaException("Unknown datasource " + selectedStorage);
        }
        addContainerProvidedComponentInstance(StorageOperator.class, operator);
        //addContainerProvidedComponent(ClientFacade.class, FacadeImpl.class);
        RaplaContext context = getContext();

        addContainerProvidedComponentInstance(ServerService.class, this);
        addContainerProvidedComponentInstance(ServerServiceContainer.class, this);

        addContainerProvidedComponentInstance(ShutdownService.class, this);
        addContainerProvidedComponentInstance(ServerServiceContainer.class, this);
        addContainerProvidedComponentInstance(CachableStorageOperator.class, operator);

        // adds 5 basic pages to the webapplication
        addWebpage("raplaapplet", RaplaAppletPageGenerator.class);
        //addWebpage("store", RaplaStorePage.class);

        {
            RaplaResources i18n = context.lookup(RaplaResources.class);

            // Index page menu
            addContainerProvidedComponentInstance(RaplaServerExtensionPoints.HTML_MAIN_MENU_EXTENSION_POINT, new DefaultHTMLMenuEntry(context, i18n.getString("start_rapla_with_webstart"), "rapla/raplaclient.jnlp"));
            addContainerProvidedComponentInstance(RaplaServerExtensionPoints.HTML_MAIN_MENU_EXTENSION_POINT, new DefaultHTMLMenuEntry(context, i18n.getString("start_rapla_with_applet"), "rapla?page=raplaapplet"));
            addContainerProvidedComponentInstance(RaplaServerExtensionPoints.HTML_MAIN_MENU_EXTENSION_POINT, new DefaultHTMLMenuEntry(context, i18n.getString("server_status"), "rapla?page=server"));
        }

        operator.addStorageUpdateListener(this);
        //        if ( username != null  )
        //            operator.connect( new ConnectInfo(username, password.toCharArray()));
        //        else
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
        RaplaLocale raplaLocale = context.lookup(RaplaLocale.class);
        TimeZoneConverter importExportLocale = context.lookup(TimeZoneConverter.class);
        try
        {
            TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();
            TimeZone timeZone = registry.getTimeZone(timezoneId);
            if (timeZone == null)
            {
                // FIXME create VTimezones for GMT+1-12 and GMT-1-12 
                // if ( timezoneId.startsWith("GMT") )
                String fallback = "Etc/GMT";
                getLogger().error("Timezone " + timezoneId + " not found in ical registry. " + " Using " + fallback);
                timeZone = registry.getTimeZone(fallback);
                if (timeZone == null)
                {
                    if (timeZone == null)
                    {
                        throw new RaplaContextException(fallback + " timezone not found in ical registry. ical4j maybe corrupted or not loaded correctyl");
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
            getLogger().error(
                    "Timezone " + timezoneId + " not found. " + rc.getMessage() + " Using system timezone " + importExportLocale.getImportExportTimeZone());
        }

        User user = getFirstAdmin(operator);
        adminSession = new RemoteSessionImpl(getLogger().getChildLogger("session"), user);
        //initializePlugins(preferences, ServerServiceContainer.class);

        Set<I18nBundle> i18nBundles = lookupServicesFor(I18nBundle.class);
        Set<String> i18nBundleIds = new LinkedHashSet<String>();
        for ( I18nBundle i18n:i18nBundles)
        {
            String packageId = i18n.getPackageId();
            i18nBundleIds.add( packageId);
        }
        ResourceBundleList implementingInstance = new ResourceBundleList(i18nBundleIds);
        addContainerProvidedComponentInstance(ResourceBundleList.class, implementingInstance);
        // start server provides
        lookupServicesFor(RaplaServerExtensionPoints.SERVER_EXTENSION);


    }

    protected Collection<InjectionContext> getSupportedContexts()
    {
        return Arrays.asList(InjectionContext.server);
    }

    public void setPasswordCheckDisabled(boolean passwordCheckDisabled)
    {
        this.passwordCheckDisabled = passwordCheckDisabled;
    }

    public <T> T getRemoteMethod(Class<T> a, RemoteSessionImpl standaloneSession) throws RaplaContextException
    {
        T service = inject(a, standaloneSession);
        return service;
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

    public <T> void addRemoteMethodFactory(Class<T> role, Class<? extends RemoteMethodFactory<T>> factory)
    {
        addRemoteMethodFactory(role, factory, null);
    }

    public <T> void addRemoteMethodFactory(Class<T> role, Class<? extends RemoteMethodFactory<T>> factory, Configuration configuration)
    {
        addContainerProvidedComponent(REMOTE_METHOD_FACTORY, factory, role.getCanonicalName(), configuration);
    }

    @Override
    public <T> T createWebservice(Class<T> role, HttpServletRequest request) throws RaplaException
    {
        RemoteSession remoteSession = getRemoteSession(request);
        T service = getInstance(role, remoteSession);
        return service;
    }

    @Override
    public boolean hasWebservice(String interfaceName)
    {
        boolean found = has(REMOTE_METHOD_FACTORY, interfaceName);
        if ( ! found)
        {
            ComponentHandler handler = getHandler(interfaceName);
            return handler != null;
        }
        return found;
    }

    public <T extends RaplaPageGenerator> void addWebpage(String pagename, Class<T> pageClass)
    {

        String lowerCase = pagename.toLowerCase();
        addContainerProvidedComponent(SERVLET_PAGE_EXTENSION, pageClass, lowerCase, null);
    }



    public RaplaPageGenerator getWebpage(String page)
    {
        try
        {
            String lowerCase = page.toLowerCase();
            @SuppressWarnings("deprecation")
            RaplaPageGenerator factory = lookup(RaplaPageExtension.class, lowerCase);
            return factory;
        }
        catch (RaplaContextException ex)
        {
            Throwable cause = ex.getCause();
            if (cause != null)
            {
                getLogger().error(cause.getMessage(), cause);
            }
            else
            {
                getLogger().error(ex.getMessage(), ex);
            }
            return null;
        }
    }

    public void updateError(RaplaException ex)
    {
        if (getLogger() != null)
            getLogger().error(ex.getMessage(), ex);
        try
        {
            stop();
        }
        catch (Exception e)
        {
            if (getLogger() != null)
                getLogger().error(e.getMessage());
        }
    }

    public void objectsUpdated(UpdateResult evt)
    {
    }

    private void stop()
    {
        boolean wasConnected = operator.isConnected();
        operator.removeStorageUpdateListener(this);
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
        super.dispose();
    }

    public StorageOperator getOperator()
    {
        return operator;
    }

    public void storageDisconnected(String message)
    {
        try
        {
            stop();
        }
        catch (Exception e)
        {
            if (getLogger() != null)
                getLogger().error(e.getMessage());
        }
    }

    public void setShutdownService(ShutdownService shutdownService)
    {
        this.shutdownService = shutdownService;
    }

    public void shutdown(boolean restart)
    {
        if (shutdownService != null)
        {
            shutdownService.shutdown(restart);
        }
        else
        {
            getLogger().error("Shutdown service not set");
        }
    }

    public RemoteSession getRemoteSession(HttpServletRequest request) throws RaplaException
    {
        User user = getUser(request);
        Logger childLogger = getLogger().getChildLogger(user != null ? user.getUsername() : "anonymous");
        RemoteSessionImpl remoteSession = new RemoteSessionImpl(childLogger, user);
        return remoteSession;
    }


    public User getUser(HttpServletRequest request) throws RaplaException
    {
        String token = request.getHeader("Authorization");
        if (token != null)
        {
            String bearerStr = "bearer";
            int bearer = token.toLowerCase().indexOf(bearerStr);
            if (bearer >= 0)
            {
                token = token.substring(bearer + bearerStr.length()).trim();
            }
        }
        else
        {
            token = request.getParameter("access_token");
        }
        User user = null;
        if (token == null)
        {
            String username = request.getParameter("username");
            String password = request.getParameter("password");
            if (username != null && password != null)
            {
                RemoteAuthentificationService service = getContext().lookup(RemoteAuthentificationService.class);
                // TODO remove HACK
                user = ((RemoteAuthentificationServiceImpl)service).getUserWithPassword(username, password);
            }
        }
        if (user == null)
        {
            user = getContext().lookup(TokenHandler.class).getUserWithAccessToken(token);
        }
        return user;
    }



}
