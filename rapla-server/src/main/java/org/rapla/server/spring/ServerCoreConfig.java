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

    @Bean
    public org.rapla.plugin.eventtimecalculator.EventTimeCalculatorResources eventTimeCalculatorResources(
            org.rapla.components.i18n.BundleManager bundleManager)
    {
        return new org.rapla.plugin.eventtimecalculator.EventTimeCalculatorResources(bundleManager);
    }

    @Bean
    public org.rapla.plugin.eventtimecalculator.EventTimeCalculatorFactory eventTimeCalculatorFactory(
            org.springframework.beans.factory.ObjectProvider<org.rapla.facade.RaplaFacade> facadeProvider,
            org.rapla.logger.Logger logger,
            org.rapla.plugin.eventtimecalculator.EventTimeCalculatorResources i18n)
    {
        jakarta.inject.Provider<org.rapla.facade.RaplaFacade> provider = facadeProvider::getObject;
        return new org.rapla.plugin.eventtimecalculator.EventTimeCalculatorFactory(provider, logger, i18n);
    }

    @Bean(name = org.rapla.plugin.eventtimecalculator.DurationFunctions.NAMESPACE)
    public FunctionFactory durationFunctions(org.rapla.plugin.eventtimecalculator.EventTimeCalculatorFactory factory)
    {
        return new org.rapla.plugin.eventtimecalculator.DurationFunctions(factory);
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

    /** Server-side {@code RaplaTableColumnFactory}. Required by {@link #tableConfigLoader}. */
    @Bean
    public org.rapla.plugin.tableview.internal.RaplaTableColumnFactory raplaTableColumnFactory(RaplaFacade facade)
    {
        return new org.rapla.plugin.tableview.server.ServerTableColumnFactory(facade);
    }

    /** {@link org.rapla.plugin.tableview.internal.TableConfig.TableConfigLoader} — needed by the
     *  three table-style HTMLViewPages ({@link #appointmentTableViewPage}, {@link #appointmentPerDayViewPage},
     *  {@link #reservationTableViewPage}). The {@code Set<TableColumnDefinitionExtension>} is auto-injected;
     *  empty if no plugin contributes one. */
    @Bean
    public org.rapla.plugin.tableview.internal.TableConfig.TableConfigLoader tableConfigLoader(
            RaplaFacade facade,
            RaplaResources i18n,
            RaplaLocale raplaLocale,
            Set<org.rapla.plugin.tableview.extensionpoints.TableColumnDefinitionExtension> extensions,
            org.rapla.plugin.tableview.internal.RaplaTableColumnFactory tableColumnCreator)
    {
        return new org.rapla.plugin.tableview.internal.TableConfig.TableConfigLoader(
                facade, i18n, raplaLocale, extensions, tableColumnCreator);
    }

    /** {@link org.rapla.plugin.timeslot.TimeslotProvider} — needed by the timeslot-style HTMLViewPages. */
    @Bean
    public org.rapla.plugin.timeslot.TimeslotProvider timeslotProvider(RaplaLocale raplaLocale, RaplaFacade facade)
            throws org.rapla.framework.RaplaInitializationException
    {
        return new org.rapla.plugin.timeslot.TimeslotProvider(raplaLocale, facade);
    }

    // --- HTMLViewPage extensions: prototype-scoped, named by their legacy @Extension id ---
    // The bean name is the map key in the @Autowired Map<String, Provider<HTMLViewPage>> consumer
    // (CalendarPageGenerator.factoryMap). Prototype scope matches legacy Provider<T> semantics
    // (each provider.get() returns a fresh instance) — important because AbstractHTMLCalendarPage
    // holds mutable CalendarModel state during page rendering.

    @Bean(name = org.rapla.plugin.weekview.WeekviewPlugin.DAY_VIEW)
    @org.springframework.context.annotation.Scope("prototype")
    public org.rapla.server.extensionpoints.HTMLViewPage htmlDayViewPage(
            RaplaLocale raplaLocale, RaplaResources i18n, RaplaFacade facade, Logger logger,
            org.rapla.entities.domain.AppointmentFormater appointmentFormater)
    {
        return new org.rapla.plugin.weekview.server.HTMLDayViewPage(
                raplaLocale, i18n, facade, logger, appointmentFormater);
    }

    @Bean(name = org.rapla.plugin.weekview.WeekviewPlugin.WEEK_VIEW)
    @org.springframework.context.annotation.Scope("prototype")
    public org.rapla.server.extensionpoints.HTMLViewPage htmlWeekViewPage(
            RaplaLocale raplaLocale, RaplaResources i18n, RaplaFacade facade, Logger logger,
            org.rapla.entities.domain.AppointmentFormater appointmentFormater)
    {
        return new org.rapla.plugin.weekview.server.HTMLWeekViewPage(
                raplaLocale, i18n, facade, logger, appointmentFormater);
    }

    @Bean(name = org.rapla.plugin.monthview.MonthViewPlugin.MONTH_VIEW)
    @org.springframework.context.annotation.Scope("prototype")
    public org.rapla.server.extensionpoints.HTMLViewPage htmlMonthViewPage(
            RaplaLocale raplaLocale, RaplaResources i18n, RaplaFacade facade, Logger logger,
            org.rapla.entities.domain.AppointmentFormater appointmentFormater)
    {
        return new org.rapla.plugin.monthview.server.HTMLMonthViewPage(
                raplaLocale, i18n, facade, logger, appointmentFormater);
    }

    @Bean(name = org.rapla.plugin.compactweekview.CompactWeekviewPlugin.COMPACT_WEEK_VIEW)
    @org.springframework.context.annotation.Scope("prototype")
    public org.rapla.server.extensionpoints.HTMLViewPage htmlCompactWeekViewPage(
            RaplaLocale raplaLocale, RaplaResources i18n, RaplaFacade facade, Logger logger,
            org.rapla.entities.domain.AppointmentFormater appointmentFormater)
    {
        return new org.rapla.plugin.compactweekview.server.HTMLCompactWeekViewPage(
                raplaLocale, i18n, facade, logger, appointmentFormater);
    }

    @Bean(name = org.rapla.plugin.timeslot.TimeslotPlugin.DAY_TIMESLOT)
    @org.springframework.context.annotation.Scope("prototype")
    public org.rapla.server.extensionpoints.HTMLViewPage htmlCompactDayViewPage(
            RaplaLocale raplaLocale, RaplaResources i18n, RaplaFacade facade, Logger logger,
            org.rapla.entities.domain.AppointmentFormater appointmentFormater,
            org.rapla.plugin.timeslot.TimeslotProvider timeslotProvider)
    {
        return new org.rapla.plugin.timeslot.server.HTMLCompactDayViewPage(
                raplaLocale, i18n, facade, logger, appointmentFormater, timeslotProvider);
    }

    @Bean(name = org.rapla.plugin.timeslot.TimeslotPlugin.WEEK_TIMESLOT)
    @org.springframework.context.annotation.Scope("prototype")
    public org.rapla.server.extensionpoints.HTMLViewPage htmlCompactViewPage(
            RaplaLocale raplaLocale, RaplaResources i18n, RaplaFacade facade, Logger logger,
            org.rapla.entities.domain.AppointmentFormater appointmentFormater,
            org.rapla.plugin.timeslot.TimeslotProvider timeslotProvider)
    {
        return new org.rapla.plugin.timeslot.server.HTMLCompactViewPage(
                raplaLocale, i18n, facade, logger, appointmentFormater, timeslotProvider);
    }

    @Bean(name = org.rapla.plugin.dayresource.DayResourcePlugin.DAY_RESOURCE_VIEW)
    @org.springframework.context.annotation.Scope("prototype")
    public org.rapla.server.extensionpoints.HTMLViewPage htmlDayResourcePage(
            RaplaLocale raplaLocale, RaplaResources i18n, RaplaFacade facade, Logger logger,
            org.rapla.entities.domain.AppointmentFormater appointmentFormater)
    {
        return new org.rapla.plugin.dayresource.server.HTMLDayResourcePage(
                raplaLocale, i18n, facade, logger, appointmentFormater);
    }

    @Bean(name = org.rapla.plugin.tableview.TableViewPlugin.TABLE_APPOINTMENTS_VIEW)
    @org.springframework.context.annotation.Scope("prototype")
    public org.rapla.server.extensionpoints.HTMLViewPage appointmentTableViewPage(
            RaplaLocale raplaLocale,
            org.rapla.plugin.tableview.internal.TableConfig.TableConfigLoader tableConfigLoader)
    {
        return new org.rapla.plugin.tableview.server.AppointmentTableViewPage(raplaLocale, tableConfigLoader);
    }

    @Bean(name = org.rapla.plugin.tableview.TableViewPlugin.TABLE_APPOINTMENTS_PER_DAY_VIEW)
    @org.springframework.context.annotation.Scope("prototype")
    public org.rapla.server.extensionpoints.HTMLViewPage appointmentPerDayViewPage(
            RaplaLocale raplaLocale,
            org.rapla.plugin.tableview.internal.TableConfig.TableConfigLoader tableConfigLoader)
    {
        return new org.rapla.plugin.tableview.server.AppointmentPerDayViewPage(raplaLocale, tableConfigLoader);
    }

    @Bean(name = org.rapla.plugin.tableview.TableViewPlugin.TABLE_EVENT_VIEW)
    @org.springframework.context.annotation.Scope("prototype")
    public org.rapla.server.extensionpoints.HTMLViewPage reservationTableViewPage(
            RaplaLocale raplaLocale,
            org.rapla.plugin.tableview.internal.TableConfig.TableConfigLoader tableConfigLoader)
    {
        return new org.rapla.plugin.tableview.server.ReservationTableViewPage(raplaLocale, tableConfigLoader);
    }

    /** Builds the {@code Map<String, Provider<HTMLViewPage>>} consumed by
     *  {@link org.rapla.plugin.autoexport.server.CalendarPageGenerator#factoryMap}. The values
     *  are {@link jakarta.inject.Provider}s that re-fetch from the bean factory each call so
     *  prototype-scoped HTMLViewPages get a fresh instance per page render. */
    @Bean
    public java.util.Map<String, jakarta.inject.Provider<org.rapla.server.extensionpoints.HTMLViewPage>> htmlViewPageMap(
            org.springframework.beans.factory.BeanFactory beanFactory,
            java.util.Map<String, org.rapla.server.extensionpoints.HTMLViewPage> namedPages)
    {
        java.util.Map<String, jakarta.inject.Provider<org.rapla.server.extensionpoints.HTMLViewPage>> result =
                new java.util.LinkedHashMap<>();
        for (String name : namedPages.keySet())
        {
            result.put(name, () -> beanFactory.getBean(name, org.rapla.server.extensionpoints.HTMLViewPage.class));
        }
        return result;
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
