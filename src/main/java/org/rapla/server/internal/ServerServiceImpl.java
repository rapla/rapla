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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import javax.ws.rs.Path;

import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.SimpleProvider;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.rest.server.RaplaRestApiWrapper;
import org.rapla.server.RemoteSession;
import org.rapla.server.ServerService;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.TimeZoneConverter;
import org.rapla.server.extensionpoints.RaplaPageExtension;
import org.rapla.server.extensionpoints.ServerExtension;
import org.rapla.server.servletpages.RaplaPageGenerator;
import org.rapla.server.servletpages.ServletRequestPreprocessor;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.StorageUpdateListener;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.dbrm.RemoteAuthentificationService;
import org.rapla.storage.dbrm.RemoteServiceCaller;
import org.rapla.storage.dbsql.DBOperator;
import org.rapla.storage.impl.server.ImportExportManagerImpl;
import org.rapla.storage.impl.server.LocalAbstractCachableOperator;

import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;


public class ServerServiceImpl extends ContainerImpl implements StorageUpdateListener, ServerService, ShutdownService, ServerServiceContainer
{
    protected CachableStorageOperator operator;


    ShutdownService shutdownService;


    private boolean passwordCheckDisabled;

    RemoteSessionImpl adminSession;

    public Collection<ServletRequestPreprocessor> getServletRequestPreprocessors() throws RaplaContextException
    {
        return lookupServicesFor(ServletRequestPreprocessor.class, 0);
    }

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

    public ServerServiceImpl(Logger logger, ServerContainerContext containerContext, String selectedStorage) throws Exception
    {
        super(logger, new SimpleProvider<RemoteServiceCaller>());
        ((SimpleProvider<RemoteServiceCaller>) remoteServiceCaller).setValue(new RemoteServiceCaller()
        {

            @Override public <T> T getRemoteMethod(Class<T> a) throws RaplaContextException
            {
                RemoteSession remoteSession = adminSession;
                T service = getInstance(a, remoteSession);
                return service;
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
        SimpleProvider<Object> externalMailSession = new SimpleProvider<Object>();
        if (containerContext.mailSession != null)
        {
            externalMailSession.setValue(containerContext.getMailSession());
        }
        addContainerProvidedComponentInstance(ServerService.ENV_RAPLAMAIL, externalMailSession);
        loadFromServiceList();
        initialize();
        //addContainerProvidedComponent(TimeZoneConverter.class, TimeZoneConverterImpl.class);
        if (selectedStorage == null || "raplafile".equals( selectedStorage ))
        {
            operator = lookup(FileOperator.class);
        }
        else if (selectedStorage.equals("rapladb"))
        {
            operator = lookup(DBOperator.class);
        }
        else
        {
            throw new RaplaException("Unknown datasource " + selectedStorage);
        }
        addContainerProvidedComponentInstance(StorageOperator.class, operator);
        addContainerProvidedComponentInstance(CachableStorageOperator.class, operator);

        addContainerProvidedComponentInstance(ServerServiceContainer.class, this);
        addContainerProvidedComponentInstance(ShutdownService.class, this);


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
        RaplaLocale raplaLocale = lookup(RaplaLocale.class);
        TimeZoneConverter importExportLocale = lookup(TimeZoneConverter.class);
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

        {// Rest Pages
            // Scan for services 
            try
            {
                final String name = "META-INF/services/" + Path.class.getCanonicalName();
                final Enumeration<URL> restPagesDefinitions = getClass().getClassLoader().getResources(name);
                Set<String> restPagesSet = new LinkedHashSet<String>();
                while (restPagesDefinitions.hasMoreElements())
                {
                    final URL url = restPagesDefinitions.nextElement();
                    final InputStream modules = url.openStream();
                    final BufferedReader br = new BufferedReader(new InputStreamReader(modules, "UTF-8"));
                    String module = null;
                    while ((module = br.readLine()) != null)
                    {
                        restPagesSet.add(module);
                    }
                    br.close();
                }
                for (String restPage : restPagesSet)
                {
                    try
                    {
                        final Class<?> restPageClass = Class.forName(restPage);
                        final String restUri = restPageClass.getAnnotation(Path.class).value();
                        final RaplaRestApiWrapper restWrapper = new RaplaRestApiWrapper(this, getLogger(), restPageClass);
                        addContainerProvidedComponentInstance(RaplaPageExtension.class, restWrapper, restUri);
                    }
                    catch (Exception e)
                    {
                        logger.error(restPage + " could not be used as REST page: " + e.getMessage(), e);
                    }
                }
            }
            catch (Exception e)
            {
                logger.error("Rest pages not available due to " + e.getMessage(), e);
            }
        }
        
        User user = getFirstAdmin(operator);
        adminSession = new RemoteSessionImpl(getLogger().getChildLogger("session"), user);
        //initializePlugins(preferences, ServerServiceContainer.class);
        // start server provides
        final Set<ServerExtension> serverExtensions = lookupServicesFor(ServerExtension.class, 0);
        for (ServerExtension extension : serverExtensions)
        {
            extension.start();
        }
    }

    @Override protected boolean isSupported(InjectionContext... contexts)
    {
        return InjectionContext.isInjectableOnServer(contexts);
    }

    public void setPasswordCheckDisabled(boolean passwordCheckDisabled)
    {
        this.passwordCheckDisabled = passwordCheckDisabled;
    }

    public <T> T getRemoteMethod(Class<T> a, RemoteSessionImpl standaloneSession) throws RaplaContextException
    {
        T service = getInstance(a, standaloneSession);
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
        return super.hasRole( interfaceName);
    }


    public RaplaPageGenerator getWebpage(String page)
    {
        try
        {
            String lowerCase = page.toLowerCase();
            @SuppressWarnings("deprecation")
            RaplaPageGenerator factory = lookupDeprecated(RaplaPageExtension.class, lowerCase);
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
                RemoteAuthentificationService service = lookup(RemoteAuthentificationService.class);
                // TODO remove HACK
                user = ((RemoteAuthentificationServiceImpl)service).getUserWithPassword(username, password);
            }
        }
        if (user == null)
        {
            user = lookup(TokenHandler.class).getUserWithAccessToken(token);
        }
        return user;
    }



}
