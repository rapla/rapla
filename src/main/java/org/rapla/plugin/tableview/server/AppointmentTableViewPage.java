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

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.table.TableColumn;

import org.rapla.entities.User;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.server.PromiseSynchroniser;
import org.rapla.server.extensionpoints.HTMLViewPage;

@Extension(provides = HTMLViewPage.class, id = TableViewPlugin.TABLE_APPOINTMENTS_VIEW) public class AppointmentTableViewPage
        implements HTMLViewPage
{
    private TableViewPage<AppointmentBlock, TableColumn> tableViewPage; 

    @Inject public AppointmentTableViewPage(RaplaLocale raplaLocale, final TableConfig.TableConfigLoader tableConfigLoader)
    {
        tableViewPage = new TableViewPage<AppointmentBlock, TableColumn>(raplaLocale) {

            @Override
            public String getCalendarHTML() throws RaplaException
            {
                User user = model.getUser();
                List<RaplaTableColumn<AppointmentBlock, TableColumn>> appointmentColumnPlugins = tableConfigLoader.loadColumns("appointments", user);
                final List<AppointmentBlock> blocks = PromiseSynchroniser.waitForWithRaplaException(model.getBlocks(), 10000);
                return getCalendarHTML(appointmentColumnPlugins, blocks, TableViewPlugin.BLOCKS_SORTING_STRING_OPTION);
            }

            @Override
            public int compareTo(AppointmentBlock object1, AppointmentBlock object2)
            {
                return object1.compareTo(object2);
            }
            
        };
    }
    
    @Override
    public void generatePage( ServletContext context, HttpServletRequest request, HttpServletResponse response, CalendarModel model ) throws IOException, ServletException
    {
        tableViewPage.generatePage(context, request, response, model);
    }
    

}

