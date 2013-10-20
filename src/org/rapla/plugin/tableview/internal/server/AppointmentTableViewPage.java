/*--------------------------------------------------------------------------*
 | Copyright (C) 2012 Christopher Kohlhaas                                  |
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
package org.rapla.plugin.tableview.internal.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.tableview.AppointmentTableColumn;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TableViewExtensionPoints;
import org.rapla.plugin.tableview.internal.TableViewPlugin;

public class AppointmentTableViewPage extends TableViewPage<AppointmentBlock> 
{
    public AppointmentTableViewPage( RaplaContext context, CalendarModel calendarModel ) 
    {
        super( context,calendarModel );
    }
    
    public String getCalendarHTML() throws RaplaException {
       final List<AppointmentBlock> blocks = model.getBlocks();
       Collection<AppointmentTableColumn> map2 = getContainer().lookupServicesFor(TableViewExtensionPoints.APPOINTMENT_TABLE_COLUMN);
       List<RaplaTableColumn<AppointmentBlock>> appointmentColumnPlugins = new ArrayList<RaplaTableColumn<AppointmentBlock>>(map2);
       return getCalendarHTML(appointmentColumnPlugins, blocks, TableViewPlugin.BLOCKS_SORTING_STRING_OPTION);
    }

    int compareTo(AppointmentBlock object1, AppointmentBlock object2) 
    {
        return object1.compareTo( object2);
    }
   
}

