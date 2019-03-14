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
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.RaplaTableModel;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.server.PromiseWait;
import org.rapla.server.extensionpoints.HTMLViewPage;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.table.TableColumn;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Extension(provides = HTMLViewPage.class, id = TableViewPlugin.TABLE_EVENT_VIEW)
public class ReservationTableViewPage implements HTMLViewPage
{
    private TableViewPage<Reservation, TableColumn> tableViewPage;

    @Inject
    public ReservationTableViewPage(PromiseWait waiter,RaplaLocale raplaLocale, TableConfig.TableConfigLoader tableConfigLoader)
    {
        tableViewPage = new TableViewPage<Reservation, TableColumn>(raplaLocale)
        {
            String getCalendarHTML() throws RaplaException
            {
                final Collection<Reservation> reservations = waiter.waitForWithRaplaException(model.queryReservations(model.getTimeIntervall()),
                        10000);
                final User user = model.getUser();
                final String tableName = TableConfig.EVENTS_VIEW;
                List<RaplaTableColumn<Reservation, TableColumn>> columnPlugins = tableConfigLoader.loadColumns(tableName, user);
                Map<RaplaTableColumn, Integer> sortDirections = RaplaTableModel.getSortDirections(model,columnPlugins, tableName);
                return getCalendarHTML(columnPlugins, reservations, sortDirections);
            }

            @Override
            int compareTo(Reservation r1, Reservation r2)
            {
                if (r1.equals(r2))
                {
                    return 0;
                }
                int compareTo = r1.getFirstDate().compareTo(r2.getFirstDate());
                return compareTo;
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
