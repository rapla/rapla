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

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.rapla.entities.User;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TableViewExtensionPoints;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.plugin.tableview.internal.TableViewPlugin;

public class ReservationTableViewPage extends TableViewPage<Reservation> 
{
    public ReservationTableViewPage( RaplaContext context, CalendarModel calendarModel ) 
    {
        super( context, calendarModel );
    }

    String getCalendarHTML() throws RaplaException {
        final Date startDate = model.getStartDate();
        final Date endDate = model.getEndDate();
        User user = model.getUser();
        final List<Reservation> reservations = Arrays.asList(model.getReservations(startDate, endDate));           
        List<RaplaTableColumn<Reservation>> columnPlugins = TableConfig.loadColumns(getContainer(), "events", TableViewExtensionPoints.RESERVATION_TABLE_COLUMN, user);
        return getCalendarHTML( columnPlugins, reservations,TableViewPlugin.EVENTS_SORTING_STRING_OPTION );
    }

    @Override
    int compareTo(Reservation r1, Reservation r2) {
        if ( r1.equals( r2))
        {
            return 0;
        }
        int compareTo = r1.getFirstDate().compareTo( r2.getFirstDate());
        return compareTo;
    }
    
    
        
   
}

