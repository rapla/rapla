/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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
package org.rapla.plugin.tableview.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.client.menu.MenuFactory;
import org.rapla.client.menu.MenuItemFactory;
import org.rapla.client.swing.SwingCalendarView;
import org.rapla.client.swing.extensionpoints.SwingViewFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.RaplaMenuBarContainer;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.components.util.TimeInterval;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.abstractcalendar.client.swing.IntervalChooserPanel;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TablePage;
import org.rapla.plugin.tableview.TableQueryRequest;
import org.rapla.plugin.tableview.TableRow;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.TableViewService;
import org.rapla.plugin.tableview.client.swing.extensionpoints.ReservationSummaryExtension;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.plugin.tableview.internal.TableRowColumn;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Promise;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import javax.swing.Icon;
import javax.swing.table.TableColumn;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@Lazy

public class ReservationTableViewFactory implements SwingViewFactory
{
    private final Set<ReservationSummaryExtension> reservationSummaryExtensions;
    private final TableConfig.TableConfigLoader tableConfigLoader;
    private final MenuFactory menuFactory;
    private final ReservationController reservationController;
    private final InfoFactory infoFactory;
    private final IntervalChooserPanel dateChooser;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final RaplaLocale raplaLocale;
    private final RaplaResources i18n;
    private final ClientFacade facade;
    private final IOInterface ioInterface;
    private final RaplaMenuBarContainer menuBar;
    private final EditController editController;
    private final TableViewService tableViewService;
    private final CommandScheduler commandScheduler;

    @Autowired
    public ReservationTableViewFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale,
                                       Set<ReservationSummaryExtension> reservationSummaryExtensions, TableConfig.TableConfigLoader tableConfigLoader, MenuFactory menuFactory,
                                       ReservationController reservationController, InfoFactory infoFactory, IntervalChooserPanel dateChooser,
                                       DialogUiFactoryInterface dialogUiFactory, IOInterface ioInterface, RaplaMenuBarContainer menuBar, EditController editController,
                                       TableViewService tableViewService, CommandScheduler commandScheduler)
    {
        this.facade = facade;
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
        this.reservationSummaryExtensions = reservationSummaryExtensions;
        this.tableConfigLoader = tableConfigLoader;
        this.menuFactory = menuFactory;
        this.reservationController = reservationController;
        this.infoFactory = infoFactory;
        this.dateChooser = dateChooser;
        this.dialogUiFactory = dialogUiFactory;
        this.ioInterface = ioInterface;
        this.menuBar = menuBar;
        this.editController = editController;
        this.tableViewService = tableViewService;
        this.commandScheduler = commandScheduler;
    }
    
    @Override
    public boolean isEnabled()
    {
        return true;
    }

    public final static String TABLE_VIEW = TableViewPlugin.TABLE_EVENT_VIEW;

    public SwingCalendarView createSwingView(CalendarModel model, boolean editable, boolean printing) throws RaplaException
    {
        final String tableName = TableConfig.EVENTS_VIEW;
        // Reuse the existing column metadata (key + label + type) from the
        // legacy plugin column SPI; values come from server-projected rows
        // instead of in-process entity projection.
        List<RaplaTableColumn<Reservation>> referenceColumns = tableConfigLoader.loadColumns(tableName, facade.getUser());
        List<RaplaTableColumn<TableRow>> raplaTableColumns = referenceColumns.stream()
                .<RaplaTableColumn<TableRow>>map(TableRowColumn::new)
                .collect(Collectors.toList());
        final List<String> columnIds = referenceColumns.stream()
                .map(RaplaTableColumn::getKey)
                .collect(Collectors.toList());

        Supplier<Promise<List<TableRow>>> initFunction = () ->
        {
            TimeInterval interval = model.getTimeIntervall();
            LocalDateTime start = interval != null ? interval.getStart() : null;
            LocalDateTime end   = interval != null ? interval.getEnd()   : null;
            final String fromIso = (start != null ? start.toLocalDate() : LocalDateTime.now().toLocalDate()).toString();
            final String toIso   = (end   != null ? end.toLocalDate()   : LocalDateTime.now().toLocalDate().plusYears(1)).toString();
            return commandScheduler.supply(() ->
            {
                TablePage page = tableViewService.reservations(
                        TableQueryRequest.fromCalendarModel(model, fromIso, toIso, columnIds, null));
                return new ArrayList<>(page.rows());
            });
        };

        return new SwingTableView<TableRow>(menuBar, facade, i18n, raplaLocale, model, reservationSummaryExtensions, editable, printing, raplaTableColumns, menuFactory,
                editController, reservationController, infoFactory,  dateChooser,  dialogUiFactory, ioInterface, initFunction, tableName);
    }

    public String getViewId()
    {
        return TABLE_VIEW;
    }

    public String getName()
    {
        return i18n.getString("reservations");
    }

    Icon icon;

    public Icon getIcon()
    {
        if (icon == null)
        {
            icon = RaplaImages.getIcon("/org/rapla/plugin/tableview/images/eventlist.png");
        }
        return icon;
    }

    public String getMenuSortKey()
    {
        return "0";
    }

}
