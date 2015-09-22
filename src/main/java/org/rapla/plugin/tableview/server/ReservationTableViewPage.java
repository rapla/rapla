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
package org.rapla.plugin.tableview.server;

import java.util.*;

import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.extensionpoints.ReservationTableColumn;
import org.rapla.server.extensionpoints.HTMLViewPage;

import javax.inject.Inject;

@Extension(provides = HTMLViewPage.class,id=TableViewPlugin.TABLE_EVENT_VIEW)
public class ReservationTableViewPage extends TableViewPage<Reservation>
{
    Set<ReservationTableColumn> columnSet;
    @Inject
    public ReservationTableViewPage(RaplaLocale raplaLocale,Set<ReservationTableColumn> columnSet)
    {
        super(raplaLocale);
        this.columnSet = columnSet;
    }

    String getCalendarHTML() throws RaplaException {
        final Date startDate = model.getStartDate();
        final Date endDate = model.getEndDate();
        final List<Reservation> reservations = Arrays.asList(model.getReservations(startDate, endDate));           
        List< RaplaTableColumn<Reservation>> columPluigns =  new ArrayList<RaplaTableColumn<Reservation>>( columnSet);
        return getCalendarHTML( columPluigns, reservations,TableViewPlugin.EVENTS_SORTING_STRING_OPTION );
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

