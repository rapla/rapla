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

import java.util.List;

import javax.inject.Inject;
import javax.swing.table.TableColumn;

import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.server.extensionpoints.HTMLViewPage;

@Extension(provides = HTMLViewPage.class, id = TableViewPlugin.TABLE_APPOINTMENTS_VIEW) public class AppointmentTableViewPage
        extends TableViewPage<AppointmentBlock, TableColumn>
{
    private final TableConfig.TableConfigLoader tableConfigLoader;

    @Inject public AppointmentTableViewPage(RaplaLocale raplaLocale, TableConfig.TableConfigLoader tableConfigLoader)
    {
        super(raplaLocale);
        this.tableConfigLoader = tableConfigLoader;
    }

    public String getCalendarHTML() throws RaplaException
    {
        List<RaplaTableColumn<AppointmentBlock, TableColumn>> appointmentColumnPlugins = tableConfigLoader.loadColumns("appointments");
        final List<AppointmentBlock> blocks = model.getBlocks();
        return getCalendarHTML(appointmentColumnPlugins, blocks, TableViewPlugin.BLOCKS_SORTING_STRING_OPTION);
    }

    int compareTo(AppointmentBlock object1, AppointmentBlock object2)
    {
        return object1.compareTo(object2);
    }

}

