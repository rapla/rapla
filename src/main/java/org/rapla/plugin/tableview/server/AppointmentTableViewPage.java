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
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.swing.table.TableColumn;

import org.rapla.RaplaResources;
import org.rapla.components.i18n.BundleManager;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.extensionpoints.AppointmentTableColumn;
import org.rapla.plugin.tableview.internal.MyAppoitmentTableColumn;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;
import org.rapla.server.extensionpoints.HTMLViewPage;

@Extension(provides = HTMLViewPage.class, id = TableViewPlugin.TABLE_APPOINTMENTS_VIEW)
public class AppointmentTableViewPage  extends TableViewPage<AppointmentBlock,TableColumn>
{
    Set<AppointmentTableColumn> columnSet;
    private final ClientFacade clientFacade;
    private final RaplaResources i18n;
    private final RaplaLocale raplaLocale;

    @Inject public AppointmentTableViewPage(RaplaLocale raplaLocale, Set<AppointmentTableColumn> columnSet, ClientFacade clientFacade, RaplaResources i18n)
    {
        super(raplaLocale);
        this.columnSet = columnSet;
        this.clientFacade = clientFacade;
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
    }

    public String getCalendarHTML() throws RaplaException
    {
        List<RaplaTableColumn<AppointmentBlock, TableColumn>> appointmentColumnPlugins = loadAppointmentColumns();
        final List<AppointmentBlock> blocks = model.getBlocks();
        return getCalendarHTML(appointmentColumnPlugins, blocks, TableViewPlugin.BLOCKS_SORTING_STRING_OPTION);
    }

    private List<RaplaTableColumn<AppointmentBlock, TableColumn>> loadAppointmentColumns() throws RaplaException, RaplaContextException
    {
        List<RaplaTableColumn<AppointmentBlock, TableColumn>> columnPlugins = new ArrayList<RaplaTableColumn<AppointmentBlock, TableColumn>>();
        final Preferences preferences = clientFacade.getSystemPreferences();
        TableConfig config = TableConfig.read( preferences, i18n);
        final Collection<TableColumnConfig> columns = config.getColumns("appointments");
        for ( final TableColumnConfig column: columns)
        {
            columnPlugins.add( new MyAppoitmentTableColumn(column, raplaLocale));
        }
        columnPlugins.addAll( columnSet);
        return columnPlugins;
    }

    int compareTo(AppointmentBlock object1, AppointmentBlock object2)
    {
        return object1.compareTo(object2);
    }

}

