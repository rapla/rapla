package org.rapla.server.spring;

import org.rapla.RaplaResources;
import org.rapla.RaplaSystemInfo;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.RemoteSession;
import org.rapla.server.ServerServiceContainer;
import org.rapla.framework.TimeZoneConverter;
import org.rapla.server.AuthenticationStore;
import org.rapla.server.extensionpoints.ServerExtension;
import org.rapla.server.extensionpoints.ServletRequestPreprocessor;
import org.rapla.server.internal.RaplaAuthentificationService;
import org.rapla.server.internal.RaplaKeyStorageImpl;
import org.rapla.server.internal.RemoteSessionImpl;
import org.rapla.server.internal.ServerContainerContext;
import org.rapla.server.internal.ServerServiceImpl;
import org.rapla.server.internal.ServerStorageSelector;
import org.rapla.server.internal.TokenHandler;
import org.rapla.storage.CachableStorageOperator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Configuration
@ConditionalOnProperty(prefix = "rapla.file-datasources", name = "raplafile")
public class ServerServiceConfig
{
    @Bean
    public CachableStorageOperator cachableStorageOperator(ServerStorageSelector selector)
    {
        return selector.get();
    }

    @Bean
    public ServerServiceContainer serverServiceContainer(
            CachableStorageOperator operator,
            RaplaFacade facade,
            RaplaLocale raplaLocale,
            TimeZoneConverter timeZoneConverter,
            Logger logger,
            ObjectProvider<Map<String, ServerExtension>> serverExtensionsProvider,
            ObjectProvider<Set<ServletRequestPreprocessor>> requestPreProcessorsProvider,
            CommandScheduler scheduler,
            ServerContainerContext containerContext,
            RaplaResources i18n,
            RaplaSystemInfo systemInfo,
            ServerBundleManager bundleManager) throws RaplaInitializationException
    {
        jakarta.inject.Provider<Map<String, ServerExtension>> mapProvider =
                () -> serverExtensionsProvider.getIfAvailable(Collections::emptyMap);
        jakarta.inject.Provider<Set<ServletRequestPreprocessor>> setProvider =
                () -> requestPreProcessorsProvider.getIfAvailable(Collections::emptySet);
        return new ServerServiceImpl(operator, facade, raplaLocale, timeZoneConverter, logger,
                mapProvider, setProvider, scheduler, containerContext, i18n, systemInfo, bundleManager);
    }

    @Bean
    @DependsOn("serverServiceContainer")
    public RaplaKeyStorage raplaKeyStorage(RaplaFacade facade, Logger logger) throws RaplaInitializationException
    {
        return new RaplaKeyStorageImpl(facade, logger);
    }

    @Bean
    public TokenHandler tokenHandler(RaplaKeyStorage keyStorage, CachableStorageOperator operator) throws RaplaInitializationException
    {
        return new TokenHandler(keyStorage, operator);
    }

    @Bean
    public RaplaAuthentificationService raplaAuthentificationService(AutowireCapableBeanFactory beanFactory)
    {
        RaplaAuthentificationService impl = new RaplaAuthentificationService();
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean
    public Set<AuthenticationStore> authenticationStores()
    {
        return Collections.emptySet();
    }

    @Bean
    public RemoteSession remoteSession(Logger logger,
                                        TokenHandler tokenHandler,
                                        RaplaAuthentificationService authService,
                                        org.rapla.storage.CachableStorageOperator operator)
    {
        RemoteSession legacy = new RemoteSessionImpl(logger, tokenHandler, authService);
        return new org.rapla.server.spring.SpringSecurityRemoteSession(legacy, operator, logger);
    }

    @Bean
    @org.springframework.web.context.annotation.RequestScope
    public org.rapla.storage.RemoteLocaleService remoteLocaleService(jakarta.servlet.http.HttpServletRequest request,
                                                                      AutowireCapableBeanFactory beanFactory)
    {
        org.rapla.server.internal.RemoteLocaleServiceImpl impl = new org.rapla.server.internal.RemoteLocaleServiceImpl(request);
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean
    public org.rapla.server.internal.SecurityManager securityManager(Logger logger,
                                                                      org.rapla.RaplaResources i18n,
                                                                      org.rapla.entities.domain.AppointmentFormater appointmentFormater,
                                                                      org.rapla.storage.CachableStorageOperator operator)
    {
        return new org.rapla.server.internal.SecurityManager(logger, i18n, appointmentFormater, operator);
    }

    @Bean
    @org.springframework.web.context.annotation.RequestScope
    public org.rapla.enpoints.server.RaplaResourcesRestPage raplaResourcesRestPage(jakarta.servlet.http.HttpServletRequest request,
                                                                                    AutowireCapableBeanFactory beanFactory)
    {
        org.rapla.enpoints.server.RaplaResourcesRestPage impl = new org.rapla.enpoints.server.RaplaResourcesRestPage(request);
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean
    @org.springframework.web.context.annotation.RequestScope
    public org.rapla.enpoints.server.RaplaDynamicTypesRestPage raplaDynamicTypesRestPage(jakarta.servlet.http.HttpServletRequest request,
                                                                                          AutowireCapableBeanFactory beanFactory)
    {
        org.rapla.enpoints.server.RaplaDynamicTypesRestPage impl = new org.rapla.enpoints.server.RaplaDynamicTypesRestPage(request);
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean
    @org.springframework.web.context.annotation.RequestScope
    public org.rapla.enpoints.server.RaplaEventsRestPage raplaEventsRestPage(jakarta.servlet.http.HttpServletRequest request,
                                                                              AutowireCapableBeanFactory beanFactory)
    {
        org.rapla.enpoints.server.RaplaEventsRestPage impl = new org.rapla.enpoints.server.RaplaEventsRestPage(request);
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean
    public org.rapla.server.internal.ShutdownService shutdownService(ServerContainerContext containerContext)
    {
        return containerContext.getShutdownService();
    }

    @Bean
    public org.rapla.server.internal.UpdateDataManager updateDataManager(Logger logger,
                                                                          org.rapla.storage.CachableStorageOperator operator,
                                                                          org.rapla.server.internal.SecurityManager securityManager)
    {
        return new org.rapla.server.internal.UpdateDataManagerImpl(logger, operator, securityManager);
    }

    @Bean
    @org.springframework.web.context.annotation.RequestScope
    public org.rapla.storage.dbrm.RemoteStorage remoteStorage(jakarta.servlet.http.HttpServletRequest request,
                                                               AutowireCapableBeanFactory beanFactory)
    {
        org.rapla.server.internal.RemoteStorageImpl impl = new org.rapla.server.internal.RemoteStorageImpl(request);
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean
    @org.springframework.web.context.annotation.RequestScope
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.jndi", matchIfMissing = true)
    public org.rapla.plugin.jndi.internal.JNDIConfig jndiConfig(jakarta.servlet.http.HttpServletRequest request,
                                                                 AutowireCapableBeanFactory beanFactory)
    {
        org.rapla.plugin.jndi.server.RaplaJNDITestOnLocalhost impl = new org.rapla.plugin.jndi.server.RaplaJNDITestOnLocalhost(request);
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public org.rapla.plugin.urlencryption.server.UrlEncryptor urlEncryptor(org.rapla.facade.RaplaFacade facade,
                                                                            Logger logger,
                                                                            RaplaKeyStorage keyStore,
                                                                            RemoteSession session)
    {
        return new org.rapla.plugin.urlencryption.server.UrlEncryptor(facade, logger, keyStore, session);
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.urlencryption", matchIfMissing = true)
    public org.rapla.server.extensionpoints.ServletRequestPreprocessor urlEncryptionPreprocessor(@org.springframework.context.annotation.Lazy org.rapla.plugin.urlencryption.server.UrlEncryptor urlEncryptor,
                                                                                                  @org.springframework.context.annotation.Lazy org.rapla.facade.RaplaFacade facade,
                                                                                                  Logger logger)
    {
        return new org.rapla.plugin.urlencryption.server.UrlEncryptionServletRequestResponsePreprocessor(urlEncryptor, facade, logger);
    }

    @Bean
    @org.springframework.web.context.annotation.RequestScope
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.urlencryption", matchIfMissing = true)
    public org.rapla.plugin.urlencryption.UrlEncryption urlEncryption(jakarta.servlet.http.HttpServletRequest request,
                                                                       AutowireCapableBeanFactory beanFactory) throws org.rapla.framework.RaplaInitializationException
    {
        org.rapla.plugin.urlencryption.server.UrlEncryptionService impl = new org.rapla.plugin.urlencryption.server.UrlEncryptionService(request);
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean
    public org.rapla.storage.ImportExportManager importExportManager(ServerStorageSelector selector)
    {
        return selector.getImportExportManager().get();
    }

    @Bean
    @org.springframework.web.context.annotation.RequestScope
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.archiver", matchIfMissing = true)
    public org.rapla.plugin.archiver.ArchiverService archiverService(jakarta.servlet.http.HttpServletRequest request,
                                                                      AutowireCapableBeanFactory beanFactory)
    {
        org.rapla.plugin.archiver.server.ArchiverServiceImpl impl = new org.rapla.plugin.archiver.server.ArchiverServiceImpl(request);
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean
    @org.springframework.web.context.annotation.RequestScope
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.mail", matchIfMissing = true)
    public org.rapla.plugin.mail.MailConfigService mailConfigService(jakarta.servlet.http.HttpServletRequest request,
                                                                      AutowireCapableBeanFactory beanFactory)
    {
        org.rapla.plugin.mail.server.RaplaConfigServiceImpl impl = new org.rapla.plugin.mail.server.RaplaConfigServiceImpl(request);
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean
    @org.springframework.web.context.annotation.RequestScope
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.export2ical", matchIfMissing = true)
    public org.rapla.plugin.export2ical.ICalConfigService iCalConfigService(jakarta.servlet.http.HttpServletRequest request,
                                                                             AutowireCapableBeanFactory beanFactory)
    {
        org.rapla.plugin.export2ical.server.ICalConfigServiceImpl impl = new org.rapla.plugin.export2ical.server.ICalConfigServiceImpl(request);
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.export2ical", matchIfMissing = true)
    public org.rapla.plugin.export2ical.server.Export2iCalConverter export2iCalConverter(org.rapla.framework.TimeZoneConverter timezoneConverter,
                                                                                          Logger logger,
                                                                                          org.rapla.facade.RaplaFacade facade,
                                                                                          org.rapla.RaplaResources i18n)
    {
        return new org.rapla.plugin.export2ical.server.Export2iCalConverter(timezoneConverter, logger, facade, i18n);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.export2ical", matchIfMissing = true)
    public org.rapla.plugin.export2ical.server.Export2iCalServlet export2iCalServlet(AutowireCapableBeanFactory beanFactory)
    {
        org.rapla.plugin.export2ical.server.Export2iCalServlet impl = new org.rapla.plugin.export2ical.server.Export2iCalServlet();
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean
    public org.rapla.server.servletpages.RaplaJNLPPageGenerator raplaJNLPPageGenerator(AutowireCapableBeanFactory beanFactory)
    {
        org.rapla.server.servletpages.RaplaJNLPPageGenerator impl = new org.rapla.server.servletpages.RaplaJNLPPageGenerator();
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.autoexport", matchIfMissing = true)
    public org.rapla.plugin.autoexport.server.CalendarPageGenerator calendarPageGenerator(AutowireCapableBeanFactory beanFactory)
    {
        org.rapla.plugin.autoexport.server.CalendarPageGenerator impl = new org.rapla.plugin.autoexport.server.CalendarPageGenerator();
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean
    @org.springframework.web.context.annotation.RequestScope
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.ical", matchIfMissing = true)
    public org.rapla.plugin.ical.ICalImport iCalImport(jakarta.servlet.http.HttpServletRequest request,
                                                        AutowireCapableBeanFactory beanFactory)
    {
        org.rapla.plugin.ical.server.RaplaICalImport impl = new org.rapla.plugin.ical.server.RaplaICalImport(request);
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean(name = "1_jnlp")
    public org.rapla.server.extensionpoints.HtmlMainMenu raplaJnlpEntry(org.rapla.RaplaResources i18n)
    {
        return new org.rapla.server.internal.RaplaJnlpEntry(i18n);
    }

    @Bean(name = "3_status")
    public org.rapla.server.extensionpoints.HtmlMainMenu raplaStatusEntry(org.rapla.RaplaResources i18n)
    {
        return new org.rapla.server.internal.RaplaStatusEntry(i18n);
    }

    @Bean(name = "exportedcalendars")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.autoexport", matchIfMissing = true)
    public org.rapla.server.extensionpoints.HtmlMainMenu exportMenuEntry(
            org.rapla.plugin.autoexport.AutoExportResources i18n,
            org.rapla.facade.RaplaFacade facade)
    {
        return new org.rapla.plugin.autoexport.server.ExportMenuEntry(i18n, facade);
    }

    @Bean
    public org.rapla.server.servletpages.RaplaIndexPageGenerator raplaIndexPageGenerator(AutowireCapableBeanFactory beanFactory)
    {
        org.rapla.server.servletpages.RaplaIndexPageGenerator impl = new org.rapla.server.servletpages.RaplaIndexPageGenerator();
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean
    public org.rapla.server.servletpages.RaplaStatusPageGenerator raplaStatusPageGenerator(AutowireCapableBeanFactory beanFactory)
    {
        org.rapla.server.servletpages.RaplaStatusPageGenerator impl = new org.rapla.server.servletpages.RaplaStatusPageGenerator();
        beanFactory.autowireBean(impl);
        return impl;
    }
}
