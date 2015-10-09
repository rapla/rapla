/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.plugin.tableview.internal;



import org.rapla.client.ClientServiceContainer;
import org.rapla.client.RaplaClientExtensionPoints;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.Configuration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.TypedComponentRole;
import org.rapla.gui.AnnotationEditExtension;
import org.rapla.plugin.tableview.TableViewExtensionPoints;

public class TableViewPlugin  implements PluginDescriptor<ClientServiceContainer>
{
	public static final String PLUGIN_CLASS = TableViewPlugin.class.getName();

    public static final String EVENTS_SORTING_STRING_OPTION = "org.rapla.plugin.tableview.events.sortingstring";
    public static final String BLOCKS_SORTING_STRING_OPTION = "org.rapla.plugin.tableview.blocks.sortingstring";

	public final static boolean ENABLE_BY_DEFAULT = true;

    public static final TypedComponentRole<RaplaConfiguration> CONFIG = new TypedComponentRole<RaplaConfiguration>("org.rapla.plugin.tableview.config");

    public void provideServices(final ClientServiceContainer container, Configuration config)
    {
        container.addContainerProvidedComponent( AnnotationEditExtension.DYNAMICTYPE_ANNOTATION_EDIT, TableColumnAnnotationEdit.class);
        if ( !config.getAttributeAsBoolean("enabled", ENABLE_BY_DEFAULT) )
        	return;

        //container.addContainerProvidedComponent( RaplaClientExtensionPoints.PLUGIN_OPTION_PANEL_EXTENSION, TablePluginOption.class);
        container.addContainerProvidedComponent( RaplaClientExtensionPoints.EXPORT_MENU_EXTENSION_POINT, CSVExportMenu.class);
        container.addContainerProvidedComponent( RaplaClientExtensionPoints.CALENDAR_VIEW_EXTENSION,ReservationTableViewFactory.class);
        container.addContainerProvidedComponent( RaplaClientExtensionPoints.CALENDAR_VIEW_EXTENSION,AppointmentTableViewFactory.class);
        container.addContainerProvidedComponent( RaplaClientExtensionPoints.PLUGIN_OPTION_PANEL_EXTENSION, TableviewOption.class);

        //Summary rows
        container.addContainerProvidedComponent(TableViewExtensionPoints.RESERVATION_TABLE_SUMMARY, EventCounter.class);
		container.addContainerProvidedComponent(TableViewExtensionPoints.APPOINTMENT_TABLE_SUMMARY, AppointmentCounter.class);
    }
}

