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
package org.rapla.plugin.tableview.server;

import org.rapla.framework.Configuration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.server.ServerServiceContainer;

public class TableViewServerPlugin  implements PluginDescriptor<ServerServiceContainer>
{

    public void provideServices(final ServerServiceContainer container, Configuration config)
    {
        if ( !config.getAttributeAsBoolean("enabled", TableViewPlugin.ENABLE_BY_DEFAULT) )
        	return;


        
//		addReservationTableColumns(container);
//        addAppointmentTableColumns(container);
//
//        //Summary rows
//        container.addContainerProvidedComponent(TableViewExtensionPoints.RESERVATION_TABLE_SUMMARY, EventCounter.class);
//		container.addContainerProvidedComponent(TableViewExtensionPoints.APPOINTMENT_TABLE_SUMMARY, AppointmentCounter.class);
        
        
    }

//	protected void addAppointmentTableColumns(final Container container) {
//		container.addContainerProvidedComponent(TableViewExtensionPoints.APPOINTMENT_TABLE_COLUMN, AppointmentNameColumn.class);
//        container.addContainerProvidedComponent(TableViewExtensionPoints.APPOINTMENT_TABLE_COLUMN, AppointmentStartDate.class);
//        container.addContainerProvidedComponent(TableViewExtensionPoints.APPOINTMENT_TABLE_COLUMN, AppointmentEndDate.class);
//        container.addContainerProvidedComponent(TableViewExtensionPoints.APPOINTMENT_TABLE_COLUMN, ResourceColumn.class);
//        container.addContainerProvidedComponent(TableViewExtensionPoints.APPOINTMENT_TABLE_COLUMN, PersonColumn.class);
//	}
//
//	protected void addReservationTableColumns(final Container container) {
//		container.addContainerProvidedComponent(TableViewExtensionPoints.RESERVATION_TABLE_COLUMN, ReservationNameColumn.class);
//        container.addContainerProvidedComponent(TableViewExtensionPoints.RESERVATION_TABLE_COLUMN, ReservationStartColumn.class);
//        container.addContainerProvidedComponent(TableViewExtensionPoints.RESERVATION_TABLE_COLUMN, ReservationLastChangedColumn.class);
//	}
}

