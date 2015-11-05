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

import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.TimeZoneConverter;
import org.rapla.server.extensionpoints.RaplaPageExtension;
import org.rapla.server.extensionpoints.ServerExtension;
import org.rapla.server.internal.dagger.WebMethodProvider;
import org.rapla.server.servletpages.RaplaPageGenerator;
import org.rapla.server.servletpages.ServletRequestPreprocessor;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.StorageUpdateListener;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.impl.server.LocalAbstractCachableOperator;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.sql.DataSource;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;


public class ServerServiceImpl implements StorageUpdateListener, ServerServiceContainer
{
    final protected CachableStorageOperator operator;
    final private WebMethodProvider methodProvider;

    final Logger logger;

    private final Map<String,RaplaPageExtension> pageMap;
    private boolean passwordCheckDisabled;

    final Set<ServletRequestPreprocessor> requestPreProcessors;

    public Collection<ServletRequestPreprocessor> getServletRequestPreprocessors()
    {
        return requestPreProcessors;
    }

    public static class ServerContainerContext
    {
        DataSource dbDatasource;
        String fileDatasource;
        Object mailSession;
        boolean isDbDatasource;
        ShutdownService shutdownService;

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

        public boolean isDbDatasource() { return isDbDatasource;}

        public ShutdownService getShutdownService()
        {
            return shutdownService;
        }
    }

    public WebMethodProvider getMethodProvider()
    {
        return methodProvider;
    }

    public Logger getLogger()
    {
        return logger;
    }

    @Inject
    public ServerServiceImpl(CachableStorageOperator operator,ClientFacade facade, RaplaLocale raplaLocale, TimeZoneConverter importExportLocale, Logger logger, final Provider<Set<ServerExtension>> serverExtensions, final Provider<Set<ServletRequestPreprocessor>> requestPreProcessors, final Provider<Map<String,RaplaPageExtension>> pageMap,WebMethodProvider methodProvider)
    {
        this.logger = logger;
        this.methodProvider = methodProvider;
        //webMethods.setList( );
//        SimpleProvider<Object> externalMailSession = new SimpleProvider<Object>();
//        if (containerContext.mailSession != null)
//        {
//            externalMailSession.setValue(containerContext.getMailSession());
//        }
        this.operator = operator;
        ((FacadeImpl)facade).setOperator( operator);
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
            logger.error("Timezone " + timezoneId + " not found. " + rc.getMessage() + " Using system timezone " + importExportLocale.getImportExportTimeZone());
        }

        {// Rest Pages
            // Scan for services
            /**
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
                        final RaplaRestApiWrapper restWrapper = new RaplaRestApiWrapper(this, logger, restPageClass);
                        addContainerProvidedComponentInstance(RaplaPageExtension.class, restWrapper, restUri);
                    }
                    catch (Exception e)
                    {
                        getLogger().error(restPage + " could not be used as REST page: " + e.getMessage(), e);
                    }
                }
            }
            catch (Exception e)
            {
                getLogger().error("Rest pages not available due to " + e.getMessage(), e);
            }
             */
        }
        
        //User user = getFirstAdmin(operator);
        //adminSession = new RemoteSessionImpl(getLogger().getChildLogger("session"), user);
        //addContainerProvidedComponentInstance(RemoteSession.class, adminSession);
        //initializePlugins(preferences, ServerServiceContainer.class);
        // start server provides
        this.requestPreProcessors = requestPreProcessors.get();

        this.pageMap = pageMap.get();

        for (ServerExtension extension : serverExtensions.get())
        {
            extension.start();
        }
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

    public RaplaPageGenerator getWebpage(String page)
    {
        String lowerCase = page.toLowerCase();
        @SuppressWarnings("deprecation")
        RaplaPageGenerator factory = pageMap.get(lowerCase);
        return factory;
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

}
