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

import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.Icon;

import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.MenuFactory;
import org.rapla.client.swing.SwingCalendarView;
import org.rapla.client.swing.extensionpoints.SwingViewFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.RaplaMenuBarContainer;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.plugin.abstractcalendar.client.swing.IntervalChooserPanel;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.client.swing.extensionpoints.ReservationSummaryExtension;
import org.rapla.plugin.tableview.internal.TableConfig;

@Singleton
@Extension(provides = SwingViewFactory.class, id = TableViewPlugin.TABLE_EVENT_VIEW)
public class ReservationTableViewFactory implements SwingViewFactory
{
    private final Set<ReservationSummaryExtension> reservationSummaryExtensions;
    private final TableConfig.TableConfigLoader tableConfigLoader;
    private final MenuFactory menuFactory;
    private final ReservationController reservationController;
    private final InfoFactory infoFactory;
    private final RaplaImages raplaImages;
    private final IntervalChooserPanel dateChooser;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final Logger logger;
    private final RaplaLocale raplaLocale;
    private final RaplaResources i18n;
    private final ClientFacade facade;
    private final IOInterface ioInterface;
    private final RaplaMenuBarContainer menuBar;
    private final EditController editController;

    @Inject
    public ReservationTableViewFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,
            Set<ReservationSummaryExtension> reservationSummaryExtensions, TableConfig.TableConfigLoader tableConfigLoader, MenuFactory menuFactory,
            ReservationController reservationController, InfoFactory infoFactory, RaplaImages raplaImages, IntervalChooserPanel dateChooser,
            DialogUiFactoryInterface dialogUiFactory, IOInterface ioInterface, RaplaMenuBarContainer menuBar, EditController editController)
    {
        this.facade = facade;
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
        this.logger = logger;
        this.reservationSummaryExtensions = reservationSummaryExtensions;
        this.tableConfigLoader = tableConfigLoader;
        this.menuFactory = menuFactory;
        this.reservationController = reservationController;
        this.infoFactory = infoFactory;
        this.raplaImages = raplaImages;
        this.dateChooser = dateChooser;
        this.dialogUiFactory = dialogUiFactory;
        this.ioInterface = ioInterface;
        this.menuBar = menuBar;
        this.editController = editController;
    }
    
    @Override
    public boolean isEnabled()
    {
        return true;
    }

    public final static String TABLE_VIEW = "table";

    public SwingCalendarView createSwingView(CalendarModel model, boolean editable, boolean printing) throws RaplaException
    {
        return new SwingReservationTableView(menuBar,facade, i18n, raplaLocale, logger, model, reservationSummaryExtensions, editable, printing, tableConfigLoader, menuFactory,
                editController, reservationController, infoFactory, raplaImages, dateChooser, dialogUiFactory, ioInterface);
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
