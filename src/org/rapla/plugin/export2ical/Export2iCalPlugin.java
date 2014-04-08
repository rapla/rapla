package org.rapla.plugin.export2ical;

import org.rapla.client.ClientServiceContainer;
import org.rapla.client.RaplaClientExtensionPoints;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.components.xmlbundle.impl.I18nBundleImpl;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.Configuration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.TypedComponentRole;


public class Export2iCalPlugin implements PluginDescriptor<ClientServiceContainer>{

	public static final TypedComponentRole<I18nBundle> RESOURCE_FILE = new TypedComponentRole<I18nBundle>(Export2iCalPlugin.class.getPackage().getName() + ".Export2iCalResources");

	public static final String PLUGIN_CLASS = Export2iCalPlugin.class.getName();
	public static final TypedComponentRole<RaplaConfiguration> ICAL_CONFIG =  new TypedComponentRole<RaplaConfiguration>("org.rapla.plugin.export2ical.server.Config");
    
	public static final String ICAL_EXPORT = "org.rapla.plugin.export2ical.selected";
	public static final String DAYS_BEFORE = "days_before";
	public static final String DAYS_AFTER = "days_after";

	public static final TypedComponentRole<Integer> PREF_BEFORE_DAYS = new TypedComponentRole<Integer>("export2iCal_before_days");
	public static final TypedComponentRole<Integer> PREF_AFTER_DAYS	= new TypedComponentRole<Integer>("export2iCal_after_days");
	
	public static final String GLOBAL_INTERVAL = "global_interval";
	public static final String EXPORT_ATTENDEES = "export_attendees";
	public static final TypedComponentRole<Boolean> EXPORT_ATTENDEES_PREFERENCE = new TypedComponentRole<Boolean>("export_attendees"); 
	public static final String EXPORT_ATTENDEES_EMAIL_ATTRIBUTE = "export_attendees_email_attribute";
	public static final String LAST_MODIFIED_INTERVALL = "last_modified_intervall";
	
	public static final int DEFAULT_daysBefore = 30;
	public static final int DEFAULT_daysAfter = 30;
	public static final boolean DEFAULT_basedOnAutoExport = true;
	public static final boolean DEFAULT_globalIntervall = true;
	public static final int DEFAULT_lastModifiedIntervall = 5;
    public static final String DEFAULT_attendee_resource_attribute = "email";
    public static final String DEFAULT_attendee_participation_status= "TENTATIVE";
    public static final String GENERATOR = "ical";

	public static final boolean ENABLE_BY_DEFAULT = true;
    public static final boolean DEFAULT_exportAttendees = false;
    public static final String EXPORT_ATTENDEES_PARTICIPATION_STATUS = "export_attendees_participation_status";

    public static final TypedComponentRole<String> EXPORT_ATTENDEES_PARTICIPATION_STATUS_PREFERENCE = new TypedComponentRole<String>("export_attendees_participation_status");

	public void provideServices(ClientServiceContainer container, Configuration config) throws RaplaContextException {
		container.addContainerProvidedComponent(RaplaClientExtensionPoints.PLUGIN_OPTION_PANEL_EXTENSION, Export2iCalAdminOption.class);
		if (!config.getAttributeAsBoolean("enabled", ENABLE_BY_DEFAULT))
			return;

		container.addContainerProvidedComponent(RESOURCE_FILE, I18nBundleImpl.class, I18nBundleImpl.createConfig(RESOURCE_FILE.getId()));
	    container.addContainerProvidedComponent( RaplaClientExtensionPoints.PUBLISH_EXTENSION_OPTION, IcalPublicExtensionFactory.class);
	    
	    final int startupMode = container.getStartupEnvironment().getStartupMode();
        if ( startupMode != StartupEnvironment.APPLET)
        {
        	container.addContainerProvidedComponent(RaplaClientExtensionPoints.EXPORT_MENU_EXTENSION_POINT, Export2iCalMenu.class);
        }
	    container.addContainerProvidedComponent(RaplaClientExtensionPoints.USER_OPTION_PANEL_EXTENSION, Export2iCalUserOption.class, config);
	}

}