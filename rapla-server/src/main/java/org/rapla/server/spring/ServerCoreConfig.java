package org.rapla.server.spring;

import org.rapla.RaplaResources;
import org.rapla.RaplaSystemInfo;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.entities.dynamictype.internal.StandardFunctions;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.plugin.appointmentnote.AppointmentNoteFunctions;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.framework.TimeZoneConverter;
import org.rapla.server.internal.ServerStorageSelector;
import org.rapla.framework.internal.TimeZoneConverterImpl;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ServerCoreConfig
{
    /** Bean name / qualifier of the primary rapla database {@link DataSource}.
     *  Consumers (notably {@code serverStorageSelector}) must qualify by this
     *  name so a deployment-private secondary {@code DataSource} &mdash; e.g.
     *  dhbwrapla's Dualis DB &mdash; is never mistaken for the rapla store. */
    public static final String RAPLA_DATASOURCE_BEAN = "raplaDataSource";

    /**
     * PRD 048: the primary database {@link DataSource}, built from
     * {@code rapla.db-datasources.rapladb} (HikariCP via Spring Boot's
     * {@code DataSourceBuilder}; driver auto-derived from the JDBC URL).
     * Absent when running file-backed &mdash; {@code ServerStorageSelector}
     * then selects the {@code FileOperator}.
     */
    @Bean(name = RAPLA_DATASOURCE_BEAN)
    @Primary
    @ConditionalOnProperty(prefix = "rapla.db-datasources", name = RaplaServerProperties.MAIN_DB_DATASOURCE + ".url")
    public DataSource raplaDataSource(RaplaServerProperties properties)
    {
        DataSourceProperties dbProps = properties.getDbDatasources().get(RaplaServerProperties.MAIN_DB_DATASOURCE);
        return dbProps.initializeDataSourceBuilder().build();
    }

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
    public CommandScheduler commandScheduler(TimeZoneConverter timeZoneConverter)
    {
        return new DefaultScheduler(timeZoneConverter);
    }

    @Bean(name = StandardFunctions.NAMESPACE)
    public FunctionFactory standardFunctions(RaplaLocale raplaLocale)
    {
        return new StandardFunctions(raplaLocale);
    }

    @Bean(name = AppointmentNoteFunctions.NAMESPACE)
    public FunctionFactory appointmentNoteFunctions(org.springframework.beans.factory.ObjectProvider<org.rapla.facade.RaplaFacade> facadeProvider)
    {
        return new AppointmentNoteFunctions(facadeProvider::getObject);
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
            org.rapla.plugin.eventtimecalculator.EventTimeCalculatorResources i18n)
    {
        return new org.rapla.plugin.eventtimecalculator.EventTimeCalculatorFactory(facadeProvider::getObject, i18n);
    }

    @Bean(name = org.rapla.plugin.eventtimecalculator.DurationFunctions.NAMESPACE)
    public FunctionFactory durationFunctions(org.rapla.plugin.eventtimecalculator.EventTimeCalculatorFactory factory)
    {
        return new org.rapla.plugin.eventtimecalculator.DurationFunctions(factory);
    }

    // PRD 048: the mail session is dead today (nothing ever set it). Kept as a
    // null-returning supplier — MailapiClient falls back to JNDI / system mail.
    @Bean(name = org.rapla.server.ServerService.ENV_RAPLAMAIL_ID)
    public java.util.function.Supplier<Object> mailSessionProvider()
    {
        return () -> null;
    }

    @Bean
    public org.rapla.plugin.mail.server.MailInterface mailInterface(org.rapla.facade.RaplaFacade facade,
                                                                     @org.springframework.beans.factory.annotation.Qualifier(org.rapla.server.ServerService.ENV_RAPLAMAIL_ID)
                                                                     java.util.function.Supplier<Object> mailSessionProvider)
    {
        return new org.rapla.plugin.mail.server.MailapiClient(facade, mailSessionProvider);
    }

    @Bean
    public org.rapla.plugin.mail.server.MailToUserImpl mailToUser(org.rapla.plugin.mail.server.MailInterface mail,
                                                                   org.rapla.facade.RaplaFacade facade)
    {
        return new org.rapla.plugin.mail.server.MailToUserImpl(mail, facade);
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
            RaplaLocale raplaLocale, RaplaResources i18n, RaplaFacade facade,
            org.rapla.entities.domain.AppointmentFormater appointmentFormater)
    {
        return new org.rapla.plugin.weekview.server.HTMLDayViewPage(
                raplaLocale, i18n, facade, appointmentFormater);
    }

    @Bean(name = org.rapla.plugin.weekview.WeekviewPlugin.WEEK_VIEW)
    @org.springframework.context.annotation.Scope("prototype")
    public org.rapla.server.extensionpoints.HTMLViewPage htmlWeekViewPage(
            RaplaLocale raplaLocale, RaplaResources i18n, RaplaFacade facade,
            org.rapla.entities.domain.AppointmentFormater appointmentFormater)
    {
        return new org.rapla.plugin.weekview.server.HTMLWeekViewPage(
                raplaLocale, i18n, facade, appointmentFormater);
    }

    @Bean(name = org.rapla.plugin.monthview.MonthViewPlugin.MONTH_VIEW)
    @org.springframework.context.annotation.Scope("prototype")
    public org.rapla.server.extensionpoints.HTMLViewPage htmlMonthViewPage(
            RaplaLocale raplaLocale, RaplaResources i18n, RaplaFacade facade,
            org.rapla.entities.domain.AppointmentFormater appointmentFormater)
    {
        return new org.rapla.plugin.monthview.server.HTMLMonthViewPage(
                raplaLocale, i18n, facade, appointmentFormater);
    }

    @Bean(name = org.rapla.plugin.compactweekview.CompactWeekviewPlugin.COMPACT_WEEK_VIEW)
    @org.springframework.context.annotation.Scope("prototype")
    public org.rapla.server.extensionpoints.HTMLViewPage htmlCompactWeekViewPage(
            RaplaLocale raplaLocale, RaplaResources i18n, RaplaFacade facade,
            org.rapla.entities.domain.AppointmentFormater appointmentFormater)
    {
        return new org.rapla.plugin.compactweekview.server.HTMLCompactWeekViewPage(
                raplaLocale, i18n, facade, appointmentFormater);
    }

    @Bean(name = org.rapla.plugin.timeslot.TimeslotPlugin.DAY_TIMESLOT)
    @org.springframework.context.annotation.Scope("prototype")
    public org.rapla.server.extensionpoints.HTMLViewPage htmlCompactDayViewPage(
            RaplaLocale raplaLocale, RaplaResources i18n, RaplaFacade facade,
            org.rapla.entities.domain.AppointmentFormater appointmentFormater,
            org.rapla.plugin.timeslot.TimeslotProvider timeslotProvider)
    {
        return new org.rapla.plugin.timeslot.server.HTMLCompactDayViewPage(
                raplaLocale, i18n, facade, appointmentFormater, timeslotProvider);
    }

    @Bean(name = org.rapla.plugin.timeslot.TimeslotPlugin.WEEK_TIMESLOT)
    @org.springframework.context.annotation.Scope("prototype")
    public org.rapla.server.extensionpoints.HTMLViewPage htmlCompactViewPage(
            RaplaLocale raplaLocale, RaplaResources i18n, RaplaFacade facade,
            org.rapla.entities.domain.AppointmentFormater appointmentFormater,
            org.rapla.plugin.timeslot.TimeslotProvider timeslotProvider)
    {
        return new org.rapla.plugin.timeslot.server.HTMLCompactViewPage(
                raplaLocale, i18n, facade, appointmentFormater, timeslotProvider);
    }

    @Bean(name = org.rapla.plugin.dayresource.DayResourcePlugin.DAY_RESOURCE_VIEW)
    @org.springframework.context.annotation.Scope("prototype")
    public org.rapla.server.extensionpoints.HTMLViewPage htmlDayResourcePage(
            RaplaLocale raplaLocale, RaplaResources i18n, RaplaFacade facade,
            org.rapla.entities.domain.AppointmentFormater appointmentFormater)
    {
        return new org.rapla.plugin.dayresource.server.HTMLDayResourcePage(
                raplaLocale, i18n, facade, appointmentFormater);
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

    /** Builds the {@code Map<String, Supplier<HTMLViewPage>>} consumed by
     *  {@link org.rapla.plugin.autoexport.server.CalendarPageGenerator#factoryMap}. The values
     *  are {@link java.util.function.Supplier}s that re-fetch from the bean factory each call so
     *  prototype-scoped HTMLViewPages get a fresh instance per page render. */
    @Bean
    public java.util.Map<String, java.util.function.Supplier<org.rapla.server.extensionpoints.HTMLViewPage>> htmlViewPageMap(
            org.springframework.beans.factory.BeanFactory beanFactory,
            java.util.Map<String, org.rapla.server.extensionpoints.HTMLViewPage> namedPages)
    {
        java.util.Map<String, java.util.function.Supplier<org.rapla.server.extensionpoints.HTMLViewPage>> result =
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
    public RaplaFacade raplaFacade(RaplaResources i18n, CommandScheduler scheduler,
            org.rapla.storage.CachableStorageOperator operator)
    {
        // PRD 019 Phase 1: wire the operator into the facade up-front so consumers
        // injecting RaplaFacade get a fully-configured (storage-attached) facade.
        // Removes the historical "ServerServiceImpl constructor calls setOperator"
        // dependency, which forced @DependsOn("serverServiceContainer") on every
        // bean that wanted to use the facade.
        FacadeImpl facade = new FacadeImpl(i18n, scheduler);
        facade.setOperator(operator);
        return facade;
    }

    @Bean
    public ServerStorageSelector serverStorageSelector(
                                                       @org.springframework.beans.factory.annotation.Qualifier(RAPLA_DATASOURCE_BEAN) ObjectProvider<DataSource> raplaDataSourceProvider,
                                                       RaplaResources i18n,
                                                       RaplaLocale raplaLocale,
                                                       CommandScheduler scheduler,
                                                       Map<String, FunctionFactory> functionFactoryMap,
                                                       Set<PermissionExtension> permissionExtensions,
                                                       RaplaServerProperties properties)
    {
        // PRD 048: the @Qualifier narrows resolution to the raplaDataSource bean —
        // getIfAvailable() yields it when db-backed, or null when file-backed (no
        // such bean). Qualifying is mandatory: a deployment may register other
        // DataSource beans (dhbwrapla's Dualis DB) that must NOT be picked here.
        return new ServerStorageSelector(raplaDataSourceProvider.getIfAvailable(), i18n, raplaLocale, scheduler,
                functionFactoryMap, permissionExtensions, properties);
    }
}
