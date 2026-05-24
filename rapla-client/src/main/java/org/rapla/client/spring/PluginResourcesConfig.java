package org.rapla.client.spring;

import org.rapla.components.i18n.BundleManager;
import org.rapla.facade.RaplaFacade;
import org.rapla.plugin.autoexport.AutoExportResources;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorFactory;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorResources;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorResources;
import org.rapla.plugin.export2ical.Export2iCalResources;
import org.rapla.plugin.ical.ImportFromICalResources;
import org.rapla.plugin.notification.NotificationResources;
import org.rapla.plugin.periodcopy.PeriodCopyResources;
import org.rapla.plugin.planningstatus.PlanningStatusResources;
import org.rapla.plugin.setowner.SetOwnerResources;
import org.rapla.plugin.urlencryption.UrlEncryptionResources;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Supplier;

@Configuration
public class PluginResourcesConfig
{
    @Bean public AutoExportResources autoExportResources(BundleManager bm) { return new AutoExportResources(bm); }
    @Bean public SetOwnerResources setOwnerResources(BundleManager bm) { return new SetOwnerResources(bm); }
    @Bean public Export2iCalResources export2iCalResources(BundleManager bm) { return new Export2iCalResources(bm); }
    @Bean public ImportFromICalResources importFromICalResources(BundleManager bm) { return new ImportFromICalResources(bm); }
    @Bean public NotificationResources notificationResources(BundleManager bm) { return new NotificationResources(bm); }
    @Bean public UrlEncryptionResources urlEncryptionResources(BundleManager bm) { return new UrlEncryptionResources(bm); }
    @Bean public ExchangeConnectorResources exchangeConnectorResources(BundleManager bm) { return new ExchangeConnectorResources(bm); }
    @Bean public PeriodCopyResources periodCopyResources(BundleManager bm) { return new PeriodCopyResources(bm); }
    @Bean public EventTimeCalculatorResources eventTimeCalculatorResources(BundleManager bm) { return new EventTimeCalculatorResources(bm); }
    @Bean public PlanningStatusResources planningStatusResources(BundleManager bm) { return new PlanningStatusResources(bm); }

    @Bean
    public EventTimeCalculatorFactory eventTimeCalculatorFactory(Supplier<RaplaFacade> facade, EventTimeCalculatorResources i18n)
    {
        return new EventTimeCalculatorFactory(facade, i18n);
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public org.rapla.plugin.timeslot.TimeslotProvider timeslotProvider(org.rapla.framework.RaplaLocale raplaLocale, RaplaFacade facade) throws org.rapla.framework.RaplaInitializationException
    {
        return new org.rapla.plugin.timeslot.TimeslotProvider(raplaLocale, facade);
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public org.rapla.plugin.tableview.internal.TableConfig.TableConfigLoader tableConfigLoader(
            RaplaFacade raplaFacade, org.rapla.RaplaResources i18n, org.rapla.framework.RaplaLocale raplaLocale,
            java.util.Set<org.rapla.plugin.tableview.extensionpoints.TableColumnDefinitionExtension> extensions,
            org.rapla.plugin.tableview.internal.RaplaTableColumnFactory tableColumnCreator)
    {
        return new org.rapla.plugin.tableview.internal.TableConfig.TableConfigLoader(raplaFacade, i18n, raplaLocale, extensions, tableColumnCreator);
    }

    @Bean(name = org.rapla.entities.dynamictype.internal.StandardFunctions.NAMESPACE)
    public org.rapla.entities.dynamictype.internal.StandardFunctions standardFunctions(org.rapla.framework.RaplaLocale raplaLocale)
    {
        return new org.rapla.entities.dynamictype.internal.StandardFunctions(raplaLocale);
    }

    @Bean(name = org.rapla.plugin.appointmentnote.AppointmentNoteFunctions.NAMESPACE)
    @org.springframework.context.annotation.Lazy
    public org.rapla.plugin.appointmentnote.AppointmentNoteFunctions appointmentNoteFunctions(Supplier<RaplaFacade> facadeProvider)
    {
        return new org.rapla.plugin.appointmentnote.AppointmentNoteFunctions(facadeProvider);
    }

    @Bean(name = org.rapla.plugin.eventtimecalculator.DurationFunctions.NAMESPACE)
    @org.springframework.context.annotation.Lazy
    public org.rapla.plugin.eventtimecalculator.DurationFunctions durationFunctions(org.rapla.plugin.eventtimecalculator.EventTimeCalculatorFactory factory)
    {
        return new org.rapla.plugin.eventtimecalculator.DurationFunctions(factory);
    }
}
