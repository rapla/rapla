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

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.User;
import org.rapla.entities.domain.AppointmentBlock;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Extension(provides = HTMLViewPage.class, id = TableViewPlugin.TABLE_APPOINTMENTS_VIEW) public class AppointmentTableViewPage
        implements HTMLViewPage
{
    private final TableViewPage<AppointmentBlock> tableViewPage;

    @Inject public AppointmentTableViewPage(PromiseWait waiter,RaplaLocale raplaLocale, final TableConfig.TableConfigLoader tableConfigLoader)
    {
        tableViewPage = new TableViewPage<AppointmentBlock>(raplaLocale) {

            @Override
            protected String getCalendarBody() throws RaplaException
            {
                User user = model.getUser();
                final String tableViewName = TableConfig.APPOINTMENTS_VIEW;
                List<RaplaTableColumn<AppointmentBlock>> columnPlugins = tableConfigLoader.loadColumns(tableViewName, user);
                final TimeInterval timeIntervall = model.getTimeIntervall();
                final List<AppointmentBlock> blocks = waiter.waitForWithRaplaException(model.queryBlocks(timeIntervall), 10000);
                Map<RaplaTableColumn<AppointmentBlock>, Integer> sortDirections = RaplaTableModel.getSortDirections(model,columnPlugins, tableViewName);
                return getCalendarBody(columnPlugins, blocks, sortDirections);
            }

            @Override
            protected Comparator<AppointmentBlock> getFallbackComparator() {
                return Comparator.naturalOrder();
            }
            
        };
    }
    
    @Override
    public void generatePage( ServletContext context, HttpServletRequest request, HttpServletResponse response, CalendarModel model ) throws IOException, ServletException
    {
        tableViewPage.generatePage(context, request, response, model);
    }
    

}

