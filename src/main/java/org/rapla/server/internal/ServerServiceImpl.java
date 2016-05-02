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

@DefaultImplementation(of = ServerServiceContainer.class, context = InjectionContext.server, export = true)
public class ServerServiceImpl implements ServerServiceContainer
{
    final protected CachableStorageOperator operator;
    final protected RaplaFacade facade;
    final Logger logger;

    private boolean passwordCheckDisabled;
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

            // Start database or file connection and read data
            operator.connect();
            Preferences preferences = operator.getPreferences(null, true);
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
            String timezoneId = preferences.getEntryAsString(RaplaLocaleImpl.TIMEZONE, importExportTimeZone);
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

    @Override
    public void dispose()
    {
        stop();
    }

    public StorageOperator getOperator()
    {
        return operator;
    }

}
