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

import org.rapla.entities.User;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.ReservationStartComparator;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.RaplaTableModel;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.server.extensionpoints.HTMLViewPage;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.swing.table.TableColumn;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;


public class ReservationTableViewPage implements HTMLViewPage
{
    private final TableViewPage<Reservation> tableViewPage;

    @Autowired
    public ReservationTableViewPage(RaplaLocale raplaLocale, TableConfig.TableConfigLoader tableConfigLoader)
    {
        tableViewPage = new TableViewPage<Reservation>(raplaLocale)
        {
            final Comparator<Reservation> comparator = new ReservationStartComparator(raplaLocale.getLocale());
            protected String getCalendarBody() throws RaplaException
            {
                final Collection<Reservation> reservations = ((org.rapla.facade.SyncCalendarModel) model).queryReservationsSync(model.getTimeIntervall());
                final User user = model.getUser();
                final String tableName = TableConfig.EVENTS_VIEW;
                List<RaplaTableColumn<Reservation>> columnPlugins = tableConfigLoader.loadColumns(tableName, user);
                Map<RaplaTableColumn<Reservation>, Integer> sortDirections = RaplaTableModel.getSortDirections(model,columnPlugins, tableName);
                return getCalendarBody(columnPlugins, reservations, sortDirections);
            }

            @Override
            protected Comparator<Reservation> getFallbackComparator() {
                return comparator;
            }
        };
    }

    @Override
    public void generatePage(ServletContext context, HttpServletRequest request, HttpServletResponse response, CalendarModel model)
            throws IOException, ServletException
    {
        tableViewPage.generatePage(context, request, response, model);
    }

}
