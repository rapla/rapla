package org.rapla.plugin.eventtimecalculator;

import org.rapla.client.ClientServiceContainer;
import org.rapla.client.RaplaClientExtensionPoints;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.Configuration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.TypedComponentRole;
import org.rapla.plugin.eventtimecalculator.client.EventTimeCalculatorAdminOption;
import org.rapla.plugin.eventtimecalculator.client.EventTimeCalculatorStatusFactory;
import org.rapla.plugin.eventtimecalculator.client.EventTimeCalculatorUserOption;
import org.rapla.plugin.tableview.TableViewExtensionPoints;

public class EventTimeCalculatorPlugin implements PluginDescriptor<ClientServiceContainer> {


    public static final TypedComponentRole<I18nBundle> RESOURCE_FILE = new TypedComponentRole<I18nBundle>( EventTimeCalculatorPlugin.class.getPackage().getName() + ".EventTimeCalculatorResources");

    public static final String PLUGIN_CLASS = EventTimeCalculatorPlugin.class.getName();
    public static final boolean ENABLE_BY_DEFAULT = false;

    // public static String PREF_LUNCHBREAK_NUMBER = "eventtimecalculator_lunchbreak_number";

    public static final TypedComponentRole<RaplaConfiguration> USER_CONFIG = new TypedComponentRole<RaplaConfiguration>("org.rapla.plugin.eventtimecalculator");

    public static final String INTERVAL_NUMBER = "interval_number";
    public static final String BREAK_NUMBER = "break_number";
    // public static final String LUNCHBREAK_NUMBER = "lunchbreak_number";
    public static final String TIME_UNIT = "time_unit";
    public static final String TIME_FORMAT = "time_format";
    public static final String USER_PREFS = "user_prefs";

    public static final int DEFAULT_intervalNumber = 60;
    public static final int DEFAULT_timeUnit = 60;
    public static final String DEFAULT_timeFormat = "{0},{1}";
    public static final int DEFAULT_breakNumber = 0;
    public static final boolean DEFAULT_userPrefs = false;
    //public static final int DEFAULT_lunchbreakNumber = 30;

    /**
     * provides the resource file of the plugin.
     * uses the extension points to provide the different services of the plugin.
     */
    public void provideServices(ClientServiceContainer container, Configuration config) {
        container.addResourceFile(RESOURCE_FILE);
        container.addContainerProvidedComponent(RaplaClientExtensionPoints.PLUGIN_OPTION_PANEL_EXTENSION, EventTimeCalculatorAdminOption.class);
        if (!config.getAttributeAsBoolean("enabled", ENABLE_BY_DEFAULT))
            return;
    	container.addContainerProvidedComponent(EventTimeCalculatorFactory.class,EventTimeCalculatorFactory.class, config);
        if ( config.getChild(USER_PREFS).getValueAsBoolean(false))
        {
        	container.addContainerProvidedComponent(RaplaClientExtensionPoints.USER_OPTION_PANEL_EXTENSION, EventTimeCalculatorUserOption.class, config);
        }
        container.addContainerProvidedComponent(RaplaClientExtensionPoints.APPOINTMENT_STATUS, EventTimeCalculatorStatusFactory.class);
        container.addContainerProvidedComponent(TableViewExtensionPoints.APPOINTMENT_TABLE_COLUMN, DurationColumnAppoimentBlock.class);
        container.addContainerProvidedComponent(TableViewExtensionPoints.RESERVATION_TABLE_COLUMN, DurationColumnReservation.class);
        container.addContainerProvidedComponent(TableViewExtensionPoints.RESERVATION_TABLE_SUMMARY, DurationCounter.class);
        container.addContainerProvidedComponent(TableViewExtensionPoints.APPOINTMENT_TABLE_SUMMARY, DurationCounter.class);
    }

}