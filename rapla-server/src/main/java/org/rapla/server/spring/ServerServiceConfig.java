package org.rapla.server.spring;

import org.rapla.RaplaResources;
import org.rapla.RaplaSystemInfo;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.RemoteSession;
import org.rapla.server.ServerServiceContainer;
import org.rapla.framework.TimeZoneConverter;
import org.rapla.server.AuthenticationStore;
import org.rapla.server.UserProvisioner;
import org.rapla.server.extensionpoints.ServletRequestPreprocessor;
import org.rapla.server.internal.DefaultUserProvisioner;
import org.rapla.server.internal.RaplaAuthentificationService;
import org.rapla.server.internal.RaplaKeyStorageImpl;
import org.rapla.server.internal.ReloadService;
import org.rapla.server.internal.RemoteSessionImpl;
import org.rapla.server.internal.ServerServiceImpl;
import org.rapla.server.internal.ServerStorageSelector;
import org.rapla.server.internal.TokenHandler;
import org.rapla.storage.CachableStorageOperator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Configuration
@Conditional(DatasourceConfiguredCondition.class)
public class ServerServiceConfig
{
    @Bean
    public CachableStorageOperator cachableStorageOperator(ServerStorageSelector selector) throws Exception
    {
        // PRD 019 Phase 1: connect the operator here, in the @Bean factory, so by the time
        // any consumer (RaplaFacade, ServerServiceImpl, downstream beans) injects this bean
        // it is already connected to the data store. Removes the implicit "must be created
        // by ServerServiceImpl's constructor first" ordering.
        CachableStorageOperator operator = selector.get();
        operator.connect();
        // PRD 058 — one-shot startup migration of any non-GraphQL-spec keys in
        // the loaded cache. Runs before HotSwappableGraphQlSource builds the SDL.
        // Marker-guarded; a failure here is a fatal startup error.
        operator.migrateGraphqlKeysIfNeeded();
        return operator;
    }

    @Bean
    public ServerServiceContainer serverServiceContainer(
            CachableStorageOperator operator,
            RaplaFacade facade,
            RaplaLocale raplaLocale,
            TimeZoneConverter timeZoneConverter,
            ObjectProvider<Set<ServletRequestPreprocessor>> requestPreProcessorsProvider,
            CommandScheduler scheduler,
            RaplaResources i18n,
            RaplaSystemInfo systemInfo,
            ServerBundleManager bundleManager) throws RaplaInitializationException
    {
        java.util.function.Supplier<Set<ServletRequestPreprocessor>> setProvider =
                () -> requestPreProcessorsProvider.getIfAvailable(Collections::emptySet);
        // PRD 019 Phase 4: ServerExtension Map<> arg dropped. Scheduling/startup work
        // is now driven by @Scheduled / @EventListener.
        return new ServerServiceImpl(operator, facade, raplaLocale, timeZoneConverter,
                setProvider, scheduler, i18n, systemInfo, bundleManager);
    }

    @Bean
    public RaplaKeyStorage raplaKeyStorage(RaplaFacade facade) throws RaplaInitializationException
    {
        // PRD 019 Phase 1: @DependsOn("serverServiceContainer") removed — the facade
        // is now wired with a connected operator at @Bean factory time, so any consumer
        // that injects RaplaFacade gets a ready-to-use instance regardless of whether
        // ServerServiceImpl has been constructed yet.
        return new RaplaKeyStorageImpl(facade);
    }

    @Bean
    public TokenHandler tokenHandler(RaplaKeyStorage keyStorage, CachableStorageOperator operator) throws RaplaInitializationException
    {
        return new TokenHandler(keyStorage, operator);
    }

    @Bean
    public RaplaAuthentificationService raplaAuthentificationService(
            RaplaResources i18n,
            TokenHandler tokenHandler,
            CachableStorageOperator operator,
            UserProvisioner userProvisioner,
            ObjectProvider<AuthenticationStore> authenticationStoreProvider,
            @org.springframework.beans.factory.annotation.Value("${rapla.password-check-disabled:false}") boolean passwordCheckDisabled,
            @org.springframework.beans.factory.annotation.Value("${server.address:}") String serverAddress)
    {
        // B4: refuse to boot if password verification is off while the connector
        // is network-reachable — a credential-free admin login must stay loopback-only.
        PasswordCheckBindingGuard.validate(passwordCheckDisabled, serverAddress);
        // ObjectProvider.getIfAvailable(): null when no AuthenticationStore bean
        // is published, the single bean when exactly one exists, throws
        // NoUniqueBeanDefinitionException at startup when two or more do —
        // matching the design invariant that at most ONE external auth source
        // is active per server (vanilla rapla has none; dhbwrapla NTLM, rapla
        // legacy JNDI/LDAP, or a future Keycloak adapter each register one).
        // See AuthenticationStoreInjectionTest for the regression check.
        return new RaplaAuthentificationService(i18n, tokenHandler, operator,
                userProvisioner,
                authenticationStoreProvider.getIfAvailable(), passwordCheckDisabled);
    }

    /**
     * PRD 050 Phase 8 — common provisioner core. Plugins (dhbwrapla today)
     * replace this by registering their own {@link UserProvisioner} bean;
     * Spring drops the default via {@code @ConditionalOnMissingBean}.
     */
    @Bean
    @ConditionalOnMissingBean
    public UserProvisioner defaultUserProvisioner(CachableStorageOperator operator)
    {
        return new DefaultUserProvisioner(operator);
    }

    /** PRD 009: empty default so the constructor of {@code RemoteStorageController}
     *  resolves. Plugins can override by registering their own
     *  {@code @Bean Set<PrePostDispatchProcessor>}. */
    @Bean
    public Set<org.rapla.server.PrePostDispatchProcessor> prePostDispatchProcessors()
    {
        return Collections.emptySet();
    }

    /**
     * Shared JWT → rapla User resolver. One instance used by BOTH the REST
     * resource-server path ({@link org.rapla.server.spring.SpringSecurityRemoteSession})
     * and the GraphQL resolvers, so external-IdP (Keycloak/Entra/Google)
     * identity resolution can't drift between transports.
     */
    @Bean
    public org.rapla.server.spring.JwtUserResolver jwtUserResolver(
            org.rapla.storage.CachableStorageOperator operator,
            ObjectProvider<org.rapla.server.spring.oauth.external.ExternalProvidersProperties> externalProvidersProvider,
            ObjectProvider<org.rapla.server.spring.oauth.external.ExternalUserResolver> externalUserResolverProvider)
    {
        return new org.rapla.server.spring.JwtUserResolver(
                operator,
                externalProvidersProvider.getIfAvailable(),
                externalUserResolverProvider.getIfAvailable());
    }

    @Bean
    public RemoteSession remoteSession(TokenHandler tokenHandler,
                                        org.rapla.server.spring.JwtUserResolver jwtUserResolver)
    {
        // PRD 029 Phase 5 (2026-05-25): RaplaAuthentificationService no longer
        // injected here — the legacy session's username/password request-param
        // branch (which was the only consumer) is gone. The fallback now only
        // handles Bearer header / ?access_token= / raplaLoginToken cookie.
        RemoteSession legacy = new RemoteSessionImpl(tokenHandler);
        return new org.rapla.server.spring.SpringSecurityRemoteSession(legacy, jwtUserResolver);
    }

    @Bean
    public org.rapla.server.spring.oauth.external.ExternalUserResolver externalUserResolver(
            CachableStorageOperator operator)
    {
        return new org.rapla.server.spring.oauth.external.ExternalUserResolver(operator);
    }

    @Bean
    public org.rapla.server.internal.SecurityManager securityManager(org.rapla.RaplaResources i18n,
                                                                      org.rapla.entities.domain.AppointmentFormater appointmentFormater,
                                                                      org.rapla.storage.CachableStorageOperator operator,
                                                                      org.rapla.storage.SyncStorageOperator syncOperator)
    {
        return new org.rapla.server.internal.SecurityManager(i18n, appointmentFormater, operator, syncOperator);
    }



    // PRD 048: the real client-triggered restart — a logical reload of the
    // server data store (operator disconnect + reconnect), replacing the dead
    // ShutdownService stub.
    @Bean
    public ReloadService reloadService(CachableStorageOperator operator)
    {
        return new ReloadService(operator);
    }

    @Bean
    public org.rapla.server.internal.UpdateDataManager updateDataManager(org.rapla.storage.CachableStorageOperator operator,
                                                                          org.rapla.server.internal.SecurityManager securityManager)
    {
        return new org.rapla.server.internal.UpdateDataManagerImpl(operator, securityManager);
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public org.rapla.plugin.urlencryption.server.UrlEncryptor urlEncryptor(org.rapla.facade.RaplaFacade facade,
                                                                            RaplaKeyStorage keyStore,
                                                                            RemoteSession session)
    {
        return new org.rapla.plugin.urlencryption.server.UrlEncryptor(facade, keyStore, session);
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.urlencryption", matchIfMissing = true)
    public org.rapla.server.extensionpoints.ServletRequestPreprocessor urlEncryptionPreprocessor(@org.springframework.context.annotation.Lazy org.rapla.plugin.urlencryption.server.UrlEncryptor urlEncryptor,
                                                                                                  @org.springframework.context.annotation.Lazy org.rapla.facade.RaplaFacade facade)
    {
        return new org.rapla.plugin.urlencryption.server.UrlEncryptionServletRequestResponsePreprocessor(urlEncryptor, facade);
    }

    @Bean
    public org.rapla.storage.ImportExportManager importExportManager(ServerStorageSelector selector)
    {
        return selector.getImportExportManager().get();
    }

    @Bean
    @org.springframework.web.context.annotation.RequestScope
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.archiver", matchIfMissing = true)
    public org.rapla.plugin.archiver.server.ArchiverServiceImpl archiverService(jakarta.servlet.http.HttpServletRequest request,
                                                                      AutowireCapableBeanFactory beanFactory)
    {
        org.rapla.plugin.archiver.server.ArchiverServiceImpl impl = new org.rapla.plugin.archiver.server.ArchiverServiceImpl(request);
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.export2ical", matchIfMissing = true)
    public org.rapla.plugin.export2ical.server.Export2iCalConverter export2iCalConverter(org.rapla.framework.TimeZoneConverter timezoneConverter,
                                                                                          org.rapla.facade.RaplaFacade facade,
                                                                                          org.rapla.RaplaResources i18n)
    {
        return new org.rapla.plugin.export2ical.server.Export2iCalConverter(timezoneConverter, facade, i18n);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.export2ical", matchIfMissing = true)
    public org.rapla.plugin.export2ical.server.RaplaICalExport raplaICalExport(jakarta.servlet.http.HttpServletRequest request,
                                                                                AutowireCapableBeanFactory beanFactory)
    {
        org.rapla.plugin.export2ical.server.RaplaICalExport impl = new org.rapla.plugin.export2ical.server.RaplaICalExport(request);
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.eventimport", matchIfMissing = true)
    public org.rapla.plugin.eventimport.TemplateImport templateImport(AutowireCapableBeanFactory beanFactory)
    {
        org.rapla.plugin.eventimport.server.RaplaTemplateImport impl = new org.rapla.plugin.eventimport.server.RaplaTemplateImport();
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean(name = "0_spa")
    public org.rapla.server.extensionpoints.HtmlMainMenu raplaSpaEntry(org.rapla.RaplaResources i18n)
    {
        return new org.rapla.server.internal.RaplaSpaEntry(i18n);
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

    // --- ServerExtension impls registered with their @Extension id as bean name ---
    // Consumer is Map<String, ServerExtension> in ServerServiceImpl; bean name = map key.
    // PRD 070: Exchange-connector server wiring restored (dropped in the PRD 005 reactor
    // split / never ported in PRD 049). SynchronisationManager is a plain service @Bean on
    // every deployment so the GUI/connect endpoints (ExchangeConnectorController) work
    // everywhere; its @Scheduled sweeps moved to ExchangeSchedulerTrigger, gated per
    // deployment via rapla.exchange.enabled.
    @Bean
    public org.rapla.plugin.exchangeconnector.ShowExchangeForUser showExchangeForUser(CachableStorageOperator operator)
    {
        return new org.rapla.plugin.exchangeconnector.ShowExchangeForUser(operator);
    }

    @Bean
    public org.rapla.plugin.exchangeconnector.server.ExchangeAppointmentStorage exchangeAppointmentStorage(
            RaplaFacade facade, CachableStorageOperator operator,
            org.rapla.plugin.exchangeconnector.ShowExchangeForUser showExchangeForUser)
    {
        return new org.rapla.plugin.exchangeconnector.server.ExchangeAppointmentStorage(facade, operator, showExchangeForUser);
    }

    @Bean
    public org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig.ConfigReader exchangeConnectorConfigReader(CachableStorageOperator operator)
            throws RaplaInitializationException
    {
        return new org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig.ConfigReader(operator);
    }

    @Bean
    public org.rapla.plugin.exchangeconnector.ExchangeConnectorResources exchangeConnectorResources(BundleManager bundleManager)
    {
        return new org.rapla.plugin.exchangeconnector.ExchangeConnectorResources(bundleManager);
    }

    @Bean
    public org.rapla.plugin.exchangeconnector.server.SynchronisationManager synchronisationManager(
            RaplaFacade facade, RaplaResources i18nRapla,
            org.rapla.plugin.exchangeconnector.ExchangeConnectorResources i18nExchange,
            TimeZoneConverter converter, org.rapla.entities.domain.AppointmentFormater appointmentFormater,
            RaplaKeyStorage keyStorage,
            org.rapla.plugin.exchangeconnector.server.ExchangeAppointmentStorage appointmentStorage,
            org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig.ConfigReader config,
            Set<org.rapla.plugin.exchangeconnector.extensionpoints.ExchangeConfigExtensionPoint> configExtensions,
            org.rapla.plugin.mail.server.MailToUserImpl mailToUserInterface,
            org.rapla.plugin.exchangeconnector.ShowExchangeForUser showExchangeForUser)
            throws RaplaInitializationException
    {
        return new org.rapla.plugin.exchangeconnector.server.SynchronisationManager(
                facade, i18nRapla, i18nExchange, converter, appointmentFormater, keyStorage,
                appointmentStorage, config, configExtensions, mailToUserInterface, showExchangeForUser);
    }

    // PRD 070: the @Scheduled trigger. @ConditionalOnProperty binds the existing
    // rapla.exchange.enabled flag (already set true only on the dhbw sync pod, false on
    // web/test) — so the sweeps run only there; absent/false → no bean → no scheduling,
    // no idle ticks. The service above stays available on all deployments regardless.
    // This is the first real binding of rapla.exchange.enabled (was a dead property
    // referenced only in ExchangeConnectorPreferencesPanel's docstring).
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "rapla.exchange", name = "enabled", havingValue = "true")
    public org.rapla.plugin.exchangeconnector.server.ExchangeSchedulerTrigger exchangeSchedulerTrigger(
            org.rapla.plugin.exchangeconnector.server.SynchronisationManager manager)
    {
        return new org.rapla.plugin.exchangeconnector.server.ExchangeSchedulerTrigger(manager);
    }

    // PRD 019 Phase 3d: JavascriptPatcher uses @EventListener(ApplicationReadyEvent) internally.
    @Bean(name = "org.rapla.plugin.javascriptpatch.server")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "rapla.services", name = "org.rapla.plugin.javascriptpatch", matchIfMissing = true)
    public org.rapla.plugin.javasciptpatch.server.JavascriptPatcher javascriptPatcher(RaplaFacade facade,
                                                      RaplaServerProperties properties,
                                                      org.rapla.storage.CachableStorageOperator cachableStorageOperator)
    {
        return new org.rapla.plugin.javasciptpatch.server.JavascriptPatcher(
                facade, properties, cachableStorageOperator);
    }

    // PRD 019 Phase 3c: ArchiverServiceTask uses @Scheduled internally.
    @Bean(name = org.rapla.plugin.archiver.ArchiverService.PLUGIN_ID + ".server")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "rapla.services", name = "org.rapla.plugin.archiver", matchIfMissing = true)
    public org.rapla.plugin.archiver.server.ArchiverServiceTask archiverServiceTask(RaplaFacade facade,
                                                        org.rapla.storage.SyncStorageOperator syncOperator,
                                                        org.rapla.storage.ImportExportManager importExportManager)
            throws RaplaInitializationException
    {
        return new org.rapla.plugin.archiver.server.ArchiverServiceTask(
                facade, syncOperator, importExportManager);
    }

    @Bean
    public org.rapla.plugin.notification.NotificationResources notificationResources(BundleManager bundleManager)
    {
        return new org.rapla.plugin.notification.NotificationResources(bundleManager);
    }

    // PRD 019 Phase 3a: NotificationService is now a regular @Bean (not a ServerExtension)
    // and uses @Scheduled internally. The bean name (PLUGIN_ID) is kept so anything that
    // looks it up by name still resolves; ServerServiceImpl no longer iterates this type.
    @Bean(name = org.rapla.plugin.notification.NotificationPlugin.PLUGIN_ID)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "rapla.services", name = "org.rapla.plugin.notification", matchIfMissing = true)
    public org.rapla.plugin.notification.server.NotificationService notificationService(
            RaplaFacade facade, RaplaResources i18nBundle,
            org.rapla.plugin.notification.NotificationResources notificationI18n,
            org.rapla.entities.domain.AppointmentFormater appointmentFormater,
            org.springframework.beans.factory.ObjectProvider<org.rapla.plugin.mail.server.MailToUserImpl> mailToUserProvider)
            throws org.rapla.framework.RaplaException
    {
        return new org.rapla.plugin.notification.server.NotificationService(
                facade, i18nBundle, notificationI18n, appointmentFormater, mailToUserProvider::getObject);
    }
}
