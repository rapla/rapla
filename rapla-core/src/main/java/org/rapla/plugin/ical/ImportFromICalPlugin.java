package org.rapla.plugin.ical;

public class ImportFromICalPlugin {


	public static final boolean ENABLE_BY_DEFAULT = false;
    public static final String PLUGIN_ID ="org.rapla.plugin.ical";

//	//FIXME maybe this is no longer needed with signed applets
//	boolean isApplet;
//    public ImportFromICalPlugin(StartupEnvironment env)
//    {
//        isApplet = env.getStartupMode() == StartupEnvironment.APPLET;
//    }

//	public void provideServices(ClientServiceContainer container, Configuration config) throws RaplaXMLContextException {
//		if (!config.getAttributeAsBoolean("enabled", ENABLE_BY_DEFAULT))
//			return;
//
//	    if ( !isApplet )
//        {
//        	container.addContainerProvidedComponent(RaplaClientExtensionPoints.IMPORT_MENU_EXTENSION_POINT, ImportFromICalMenu.class);
//        }
//	}

}