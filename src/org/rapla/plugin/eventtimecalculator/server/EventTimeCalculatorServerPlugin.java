package org.rapla.plugin.eventtimecalculator.server;

import org.rapla.components.xmlbundle.impl.I18nBundleImpl;
import org.rapla.framework.Configuration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.plugin.eventtimecalculator.DurationColumnAppoimentBlock;
import org.rapla.plugin.eventtimecalculator.DurationColumnReservation;
import org.rapla.plugin.eventtimecalculator.DurationCounter;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorFactory;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;
import org.rapla.plugin.tableview.TableViewExtensionPoints;
import org.rapla.server.ServerServiceContainer;

public class EventTimeCalculatorServerPlugin implements PluginDescriptor<ServerServiceContainer> {

    /**
     * provides the resource file of the plugin.
     * uses the extension points to provide the different services of the plugin.
     */
    public void provideServices(ServerServiceContainer container, Configuration config) {
        container.addContainerProvidedComponent(EventTimeCalculatorPlugin.RESOURCE_FILE, I18nBundleImpl.class,  I18nBundleImpl.createConfig(EventTimeCalculatorPlugin.RESOURCE_FILE.getId()));
        if (!config.getAttributeAsBoolean("enabled", EventTimeCalculatorPlugin.ENABLE_BY_DEFAULT))
            return;
    	container.addContainerProvidedComponent(EventTimeCalculatorFactory.class,EventTimeCalculatorFactory.class, config);
        container.addContainerProvidedComponent(TableViewExtensionPoints.APPOINTMENT_TABLE_COLUMN, DurationColumnAppoimentBlock.class, config);
        container.addContainerProvidedComponent(TableViewExtensionPoints.RESERVATION_TABLE_COLUMN, DurationColumnReservation.class, config);
        container.addContainerProvidedComponent(TableViewExtensionPoints.RESERVATION_TABLE_SUMMARY, DurationCounter.class, config);
        container.addContainerProvidedComponent(TableViewExtensionPoints.APPOINTMENT_TABLE_SUMMARY, DurationCounter.class, config);
    }

}