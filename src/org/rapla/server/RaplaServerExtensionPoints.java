package org.rapla.server;

import org.rapla.framework.TypedComponentRole;
import org.rapla.gui.SwingViewFactory;
import org.rapla.plugin.abstractcalendar.server.HTMLViewFactory;
import org.rapla.servletpages.RaplaMenuGenerator;
import org.rapla.servletpages.ServletRequestPreprocessor;

/** Constant Pool of basic extension points of the Rapla server.
 * You can add your extension  in the provideService Method of your PluginDescriptor
 * <pre>
 * container.addContainerProvidedComponent( REPLACE_WITH_EXTENSION_POINT_NAME, REPLACE_WITH_CLASS_IMPLEMENTING_EXTENSION, config);
 * </pre>
 * @see org.rapla.framework.PluginDescriptor
*/

public class RaplaServerExtensionPoints {

	/** add your own views to Rapla, by providing a org.rapla.gui.ViewFactory 
	 * @see SwingViewFactory
	 * */
	public static final Class<HTMLViewFactory> HTML_CALENDAR_VIEW_EXTENSION = HTMLViewFactory.class;
	/** A server extension is started automaticaly when the server is up and running and connected to a data store. A class added as service doesn't need to implement a specific interface and is instanciated automaticaly after server start. You can add a RaplaContext parameter to your constructor to get access to the services of rapla.
	 * */
	public static final Class<ServerExtension> SERVER_EXTENSION = ServerExtension.class;
	/** you can add servlet pre processer to manipulate request and response before standard processing is
	 * done by rapla
	 */
	public static final Class<ServletRequestPreprocessor> SERVLET_REQUEST_RESPONSE_PREPROCESSING_POINT = ServletRequestPreprocessor.class;
	/** you can add your own entries on the index page Just add a HTMLMenuEntry to the list
	     @see RaplaMenuGenerator
	     * */
	public static final TypedComponentRole<RaplaMenuGenerator> HTML_MAIN_MENU_EXTENSION_POINT = new TypedComponentRole<RaplaMenuGenerator>("org.rapla.servletpages");

	//public final static TypedComponentRole<List<PluginDescriptor<ServerServiceContainer>>> SERVER_PLUGIN_LIST = new TypedComponentRole<List<PluginDescriptor<ServerServiceContainer>>>("server-plugin-list");
	
}