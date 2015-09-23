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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.extensionpoints.AppointmentTableColumn;
import org.rapla.server.extensionpoints.HTMLViewPage;

@Extension(provides = HTMLViewPage.class, id = TableViewPlugin.TABLE_APPOINTMENTS_VIEW)
public class AppointmentTableViewPage<C>  extends TableViewPage<AppointmentBlock,C>
{
    Set<AppointmentTableColumn<C>> columnSet;

    @Inject public AppointmentTableViewPage(RaplaLocale raplaLocale, Set<AppointmentTableColumn<C>> columnSet)
    {
        super(raplaLocale);
        this.columnSet = columnSet;
    }

    public String getCalendarHTML() throws RaplaException
    {
        final List<AppointmentBlock> blocks = model.getBlocks();
        List<RaplaTableColumn<AppointmentBlock,C>> appointmentColumnPlugins = new ArrayList<RaplaTableColumn<AppointmentBlock,C>>(columnSet);
        return getCalendarHTML(appointmentColumnPlugins, blocks, TableViewPlugin.BLOCKS_SORTING_STRING_OPTION);
    }

    int compareTo(AppointmentBlock object1, AppointmentBlock object2)
    {
        return object1.compareTo(object2);
    }

}

