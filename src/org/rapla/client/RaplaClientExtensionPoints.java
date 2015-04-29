package org.rapla.client;


import org.rapla.facade.CalendarSelectionModel;
import org.rapla.framework.TypedComponentRole;
import org.rapla.gui.AppointmentStatusFactory;
import org.rapla.gui.EventCheck;
import org.rapla.gui.ObjectMenuFactory;
import org.rapla.gui.OptionPanel;
import org.rapla.gui.PluginOptionPanel;
import org.rapla.gui.PublishExtensionFactory;
import org.rapla.gui.ReservationCheck;
import org.rapla.gui.SwingViewFactory;
import org.rapla.gui.toolkit.IdentifiableMenuEntry;




/** Constant Pool of basic extension points of the Rapla client.
 * You can add your extension  in the provideService Method of your PluginDescriptor
 * <pre>
 * container.addContainerProvidedComponent( REPLACE_WITH_EXTENSION_POINT_NAME, REPLACE_WITH_CLASS_IMPLEMENTING_EXTENSION, config);
 * </pre>
 * @see org.rapla.framework.PluginDescriptor
*/

public interface RaplaClientExtensionPoints
{
    /** add your own views to Rapla, by providing a org.rapla.gui.ViewFactory 
     * @see SwingViewFactory
     * */
    Class<SwingViewFactory> CALENDAR_VIEW_EXTENSION = SwingViewFactory.class;

    /** A client extension is started automaticaly when a user has successfully login into the Rapla system. A class added as service doesn't need to implement a specific interface and is instanciated automaticaly after client login. You can add a RaplaContext parameter to your constructor to get access to the services of rapla. 
     */
    Class<ClientExtension> CLIENT_EXTENSION = ClientExtension.class;
    /** You can add a specific configuration panel for your plugin.
     * Note if you add a pluginOptionPanel you need to provide the PluginClass as hint.
     * Example 
     * <code>
     *  container.addContainerProvidedComponent( RaplaExtensionPoints.PLUGIN_OPTION_PANEL_EXTENSION, AutoExportPluginOption.class, getClass().getName());
     * </code>
     * @see org.rapla.entities.configuration.Preferences
     * @see OptionPanel
     * */
    TypedComponentRole<PluginOptionPanel>  PLUGIN_OPTION_PANEL_EXTENSION = new  TypedComponentRole<PluginOptionPanel>("org.rapla.plugin.Option");
    /** You can add additional option panels for editing the user preference.
     * @see org.rapla.entities.configuration.Preferences
     * @see OptionPanel
     * */
    TypedComponentRole<OptionPanel> USER_OPTION_PANEL_EXTENSION =  new  TypedComponentRole<OptionPanel>("org.rapla.UserOptions");
    /** You can add additional option panels for the editing the system preferences
    * @see org.rapla.entities.configuration.Preferences
    * @see OptionPanel
    * */
    TypedComponentRole<OptionPanel> SYSTEM_OPTION_PANEL_EXTENSION =  new  TypedComponentRole<OptionPanel>("org.rapla.SystemOptions");

    /** add your own wizard menus to create events. Use the CalendarSelectionModel service to get access to the current calendar 
     * @see CalendarSelectionModel
     **/
    TypedComponentRole<IdentifiableMenuEntry> RESERVATION_WIZARD_EXTENSION = new TypedComponentRole<IdentifiableMenuEntry>("org.rapla.gui.ReservationWizardExtension");

    /** you can add an interactive check when the user stores a reservation 
     *@see ReservationCheck 
     **/
    Class<ReservationCheck> RESERVATION_SAVE_CHECK = ReservationCheck.class;

    /** you can add an interactive check when the user stores a reservation 
     *@see ReservationCheck 
     **/
    Class<EventCheck> EVENT_SAVE_CHECK = EventCheck.class;

    /** add your own menu entries in the context menu of an object. To do this provide
      an ObjectMenuFactory under this entry.
      @see ObjectMenuFactory
      */
    Class<ObjectMenuFactory> OBJECT_MENU_EXTENSION = ObjectMenuFactory.class;

    /** add a footer for summary of appointments in edit window
     * provide an AppointmentStatusFactory to add your own footer to the appointment edit 
       @see AppointmentStatusFactory 
     * */
    Class<AppointmentStatusFactory> APPOINTMENT_STATUS = AppointmentStatusFactory.class;
    
   
    /** add your own submenus to the admin menu. 
     */
    TypedComponentRole<IdentifiableMenuEntry>  ADMIN_MENU_EXTENSION_POINT = new TypedComponentRole<IdentifiableMenuEntry>("org.rapla.gui.AdminMenuInsert");
    /** add your own import-menu submenus
     */
    TypedComponentRole<IdentifiableMenuEntry> IMPORT_MENU_EXTENSION_POINT =new TypedComponentRole<IdentifiableMenuEntry>("org.rapla.gui.ImportMenuInsert");
    /** add your own export-menu submenus    
     */
    TypedComponentRole<IdentifiableMenuEntry> EXPORT_MENU_EXTENSION_POINT =new TypedComponentRole<IdentifiableMenuEntry>("org.rapla.gui.ExportMenuInsert");
    /** add your own view-menu submenus    
     */
    TypedComponentRole<IdentifiableMenuEntry> VIEW_MENU_EXTENSION_POINT =new TypedComponentRole<IdentifiableMenuEntry>("org.rapla.gui.ViewMenuInsert");
    /** add your own edit-menu submenus    
     */ 
    TypedComponentRole<IdentifiableMenuEntry> EDIT_MENU_EXTENSION_POINT = new TypedComponentRole<IdentifiableMenuEntry>("org.rapla.gui.EditMenuInsert");
    /** add your own help-menu submenus    
     */
    TypedComponentRole<IdentifiableMenuEntry> HELP_MENU_EXTENSION_POINT = new TypedComponentRole<IdentifiableMenuEntry>("org.rapla.gui.ExtraMenuInsert");

    /** add your own publish options for the calendars*/
    Class<PublishExtensionFactory> PUBLISH_EXTENSION_OPTION = PublishExtensionFactory.class;
}
