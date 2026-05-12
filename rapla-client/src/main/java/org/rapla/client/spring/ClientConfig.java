package org.rapla.client.spring;

import org.rapla.RaplaResources;
import org.rapla.RaplaSystemInfo;
import org.rapla.client.swing.i18n.SwingBundleManager;
import org.rapla.components.i18n.BundleManager;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.logger.Logger;
import org.rapla.logger.RaplaBootstrapLogger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Client-side Spring DI bootstrap (Phase 4 — skeleton).
 *
 * <p>This config is intended to be loaded by an
 * {@link org.springframework.context.annotation.AnnotationConfigApplicationContext}
 * at Swing client startup, replacing the legacy {@code ClientCreator} +
 * {@code SimpleRaplaInjector} bootstrap.
 *
 * <p>Currently minimal — registers the locale-bundle tier needed by Swing
 * UI components. The full client migration is staged in PRD Phase 4 / 5
 * (REST proxies, EDT-aware scheduler, Swing components as @Component, etc.).
 */
@Configuration
public class ClientConfig
{
    @Bean
    public Logger raplaLogger()
    {
        return RaplaBootstrapLogger.createRaplaLogger();
    }

    /** Empty default so {@code Application}'s {@code Provider<Set<ClientExtension>>} resolves
     *  when no plugin contributes a ClientExtension. Plugins with extensions just register their
     *  own {@code @Bean Set<ClientExtension>} or individual {@code @Component ClientExtension}s. */
    @Bean
    public java.util.Set<org.rapla.client.extensionpoints.ClientExtension> clientExtensions()
    {
        return java.util.Collections.emptySet();
    }

    /** Default {@link BundleManager} for standalone (non-Swing) usage of this config.
     *  When {@code SwingClientConfig} is also loaded, its {@code @Service @Primary}
     *  {@link SwingBundleManager} wins for {@code BundleManager} injection points. */
    @Bean
    public BundleManager swingBundleManager(Logger logger)
    {
        return new SwingBundleManager(logger);
    }

    @Bean
    public RaplaResources raplaResources(BundleManager bundleManager)
    {
        return new RaplaResources(bundleManager);
    }

    @Bean
    public RaplaSystemInfo raplaSystemInfo(BundleManager bundleManager)
    {
        return new RaplaSystemInfo(bundleManager);
    }

    @Bean
    public RaplaLocale raplaLocale(BundleManager bundleManager)
    {
        return new RaplaLocaleImpl(bundleManager);
    }

    /** Default {@link org.rapla.scheduler.CommandScheduler} for standalone (non-Swing) usage.
     *  When {@code SwingClientConfig} is loaded, its {@code @Service @Primary}
     *  {@code SwingSchedulerImpl} wins. */
    @Bean
    public org.rapla.scheduler.CommandScheduler commandScheduler(Logger logger)
    {
        return new org.rapla.framework.internal.DefaultScheduler(logger);
    }

    @Bean
    public org.rapla.facade.RaplaFacade raplaFacade(RaplaResources i18n,
                                                     org.rapla.scheduler.CommandScheduler scheduler,
                                                     Logger logger)
    {
        return new org.rapla.facade.internal.FacadeImpl(i18n, scheduler, logger);
    }

    @Bean
    public org.rapla.facade.client.ClientFacade clientFacade(org.rapla.facade.RaplaFacade raplaFacade,
                                                              Logger logger,
                                                              RaplaResources i18n)
    {
        return new org.rapla.facade.internal.ClientFacadeImpl(raplaFacade, logger, i18n);
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public org.rapla.facade.CalendarSelectionModel calendarSelectionModel(org.rapla.facade.client.ClientFacade clientFacade,
                                                                          org.rapla.framework.RaplaLocale raplaLocale)
            throws org.rapla.framework.RaplaInitializationException
    {
        return new org.rapla.facade.internal.CalendarModelImpl(clientFacade, raplaLocale);
    }

    @Bean
    public java.util.Map<String, org.rapla.entities.extensionpoints.FunctionFactory> functionFactoryMap()
    {
        return new java.util.LinkedHashMap<>();
    }

    @Bean
    public java.util.Set<org.rapla.entities.domain.permission.PermissionExtension> permissionExtensions()
    {
        return java.util.Collections.singleton(new org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl());
    }

    @Bean
    public org.rapla.storage.impl.RaplaLock raplaLock(Logger logger)
    {
        return new org.rapla.storage.impl.DefaultRaplaLock(logger);
    }

    @Bean
    public org.rapla.framework.StartupEnvironment startupEnvironment(Logger bootstrapLogger)
    {
        return new org.rapla.framework.StartupEnvironment()
        {
            @Override
            public java.net.URL getDownloadURL() throws org.rapla.framework.RaplaException
            {
                String url = System.getProperty("rapla.download.url", "http://localhost:8051/");
                try
                {
                    return new java.net.URL(url);
                }
                catch (java.net.MalformedURLException e)
                {
                    throw new org.rapla.framework.RaplaException(e);
                }
            }

            @Override
            public int getStartupMode()
            {
                return CONSOLE;
            }

            @Override
            public Logger getBootstrapLogger()
            {
                return bootstrapLogger;
            }
        };
    }

    @Bean
    public org.rapla.components.iolayer.IOInterface ioInterface(Logger logger)
    {
        return new org.rapla.components.iolayer.DefaultIO(logger);
    }

    @Bean
    public org.rapla.entities.domain.AppointmentFormater appointmentFormater(RaplaResources i18n, RaplaLocale raplaLocale)
    {
        return new org.rapla.AppointmentFormaterImpl(i18n, raplaLocale);
    }

    @Bean
    public org.rapla.plugin.exchangeconnector.ShowExchangeForUser showExchangeForUser(
            org.rapla.storage.StorageOperator storageOperator)
    {
        return new org.rapla.plugin.exchangeconnector.ShowExchangeForUser(storageOperator);
    }

    /** Client-side persistent storage for the OAuth refresh token (PRD 029 Phase 2):
     *  JNLP {@code PersistenceService} when launched via OWS/IcedTea-Web, dotfile
     *  fallback otherwise. Best-effort — never throws even if storage is unavailable. */
    @Bean
    public org.rapla.storage.dbrm.TokenStore tokenStore(Logger logger)
    {
        return org.rapla.storage.dbrm.TokenStores.create(logger);
    }

    @Bean
    public org.rapla.storage.dbrm.RemoteOperator remoteOperator(Logger logger,
                                                                  org.rapla.RaplaResources i18n,
                                                                  org.rapla.framework.RaplaLocale locale,
                                                                  org.rapla.scheduler.CommandScheduler scheduler,
                                                                  java.util.Map<String, org.rapla.entities.extensionpoints.FunctionFactory> functionFactoryMap,
                                                                  org.rapla.storage.dbrm.RemoteAuthentificationService remoteAuthentificationService,
                                                                  org.rapla.storage.dbrm.RemoteStorage remoteStorage,
                                                                  org.rapla.storage.dbrm.RemoteConnectionInfo connectionInfo,
                                                                  java.util.Set<org.rapla.entities.domain.permission.PermissionExtension> permissionExtensions,
                                                                  org.rapla.storage.impl.RaplaLock lockManager)
    {
        return new org.rapla.storage.dbrm.RemoteOperator(logger, i18n, locale, scheduler,
                functionFactoryMap, remoteAuthentificationService, remoteStorage,
                connectionInfo, permissionExtensions, lockManager);
    }
}
