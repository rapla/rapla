package org.rapla.server.spring;

import org.rapla.RaplaResources;
import org.rapla.RaplaSystemInfo;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.endpoints.RemoteLogger;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.entities.dynamictype.internal.StandardFunctions;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.plugin.appointmentnote.AppointmentNoteFunctions;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.plugin.export2ical.ICalTimezones;
import org.rapla.plugin.export2ical.server.RaplaICalTimezones;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.framework.TimeZoneConverter;
import org.rapla.server.internal.RemoteLoggerImpl;
import org.rapla.server.internal.ServerContainerContext;
import org.rapla.server.internal.ServerStorageSelector;
import org.rapla.framework.internal.TimeZoneConverterImpl;

import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServerCoreConfig
{
    @Bean
    public ServerBundleManager bundleManager()
    {
        return new ServerBundleManager();
    }

    @Bean
    public TimeZoneConverter timeZoneConverter()
    {
        return new TimeZoneConverterImpl();
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

    @Bean
    public CommandScheduler commandScheduler(Logger logger, TimeZoneConverter timeZoneConverter)
    {
        return new DefaultScheduler(logger, timeZoneConverter);
    }

    @Bean
    public RemoteLogger remoteLogger(AutowireCapableBeanFactory beanFactory)
    {
        RemoteLoggerImpl impl = new RemoteLoggerImpl();
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean(name = StandardFunctions.NAMESPACE)
    public FunctionFactory standardFunctions(RaplaLocale raplaLocale)
    {
        return new StandardFunctions(raplaLocale);
    }

    @Bean(name = AppointmentNoteFunctions.NAMESPACE)
    public FunctionFactory appointmentNoteFunctions(org.springframework.beans.factory.ObjectProvider<org.rapla.facade.RaplaFacade> facadeProvider)
    {
        jakarta.inject.Provider<org.rapla.facade.RaplaFacade> provider = facadeProvider::getObject;
        return new AppointmentNoteFunctions(provider);
    }

    @Bean(name = org.rapla.server.ServerService.ENV_RAPLAMAIL_ID)
    public jakarta.inject.Provider<Object> mailSessionProvider(org.rapla.server.internal.ServerContainerContext containerContext)
    {
        return () -> containerContext.getMailSession();
    }

    @Bean
    public org.rapla.plugin.mail.server.MailInterface mailInterface(org.rapla.facade.RaplaFacade facade,
                                                                     @jakarta.inject.Named(org.rapla.server.ServerService.ENV_RAPLAMAIL_ID)
                                                                     jakarta.inject.Provider<Object> mailSessionProvider)
    {
        return new org.rapla.plugin.mail.server.MailapiClient(facade, mailSessionProvider);
    }

    @Bean
    public org.rapla.plugin.mail.server.MailToUserImpl mailToUser(org.rapla.plugin.mail.server.MailInterface mail,
                                                                   org.rapla.facade.RaplaFacade facade,
                                                                   Logger logger)
    {
        return new org.rapla.plugin.mail.server.MailToUserImpl(mail, facade, logger);
    }

    @Bean
    public org.rapla.server.internal.ResourceBundleList resourceBundleList(Set<org.rapla.components.i18n.I18nBundle> bundles,
                                                                            BundleManager bundleManager)
    {
        return new org.rapla.server.internal.ResourceBundleList(bundles, bundleManager);
    }

    @Bean
    public org.rapla.entities.domain.AppointmentFormater appointmentFormater(RaplaResources i18n, RaplaLocale raplaLocale)
    {
        return new org.rapla.AppointmentFormaterImpl(i18n, raplaLocale);
    }

    @Bean
    public java.util.Map<String, jakarta.inject.Provider<org.rapla.server.extensionpoints.HTMLViewPage>> htmlViewPageMap()
    {
        return new java.util.LinkedHashMap<>();
    }

    @Bean
    public org.rapla.plugin.autoexport.AutoExportResources autoExportResources(BundleManager bundleManager)
    {
        return new org.rapla.plugin.autoexport.AutoExportResources(bundleManager);
    }

    @Bean
    public PermissionExtension raplaDefaultPermission()
    {
        return new RaplaDefaultPermissionImpl();
    }

    @Bean
    public RaplaFacade raplaFacade(RaplaResources i18n, CommandScheduler scheduler, Logger logger)
    {
        return new FacadeImpl(i18n, scheduler, logger);
    }

    @Bean
    public ICalTimezones iCalTimezones(AutowireCapableBeanFactory beanFactory)
    {
        RaplaICalTimezones impl = new RaplaICalTimezones();
        beanFactory.autowireBean(impl);
        return impl;
    }

    @Bean
    public ServerStorageSelector serverStorageSelector(ServerContainerContext containerContext,
                                                       Logger logger,
                                                       RaplaResources i18n,
                                                       RaplaLocale raplaLocale,
                                                       CommandScheduler scheduler,
                                                       Map<String, FunctionFactory> functionFactoryMap,
                                                       Set<PermissionExtension> permissionExtensions)
    {
        return new ServerStorageSelector(containerContext, logger, i18n, raplaLocale, scheduler,
                functionFactoryMap, permissionExtensions);
    }
}
