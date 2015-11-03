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

import java.awt.Component;
import java.util.Set;

import javax.inject.Inject;
import javax.swing.Icon;

import org.rapla.client.ReservationController;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.MenuFactory;
import org.rapla.client.swing.SwingCalendarView;
import org.rapla.client.swing.extensionpoints.SwingViewFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.DialogUI;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.inject.Extension;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.client.swing.extensionpoints.ReservationSummaryExtension;
import org.rapla.plugin.tableview.internal.TableConfig;

@Extension(provides = SwingViewFactory.class, id = TableViewPlugin.TABLE_EVENT_VIEW)
public class ReservationTableViewFactory extends RaplaComponent implements SwingViewFactory
{
    private final Set<ReservationSummaryExtension> reservationSummaryExtensions;
    private final TableConfig.TableConfigLoader tableConfigLoader;
    private final MenuFactory menuFactory;
    private final ReservationController reservationController;
    private final InfoFactory<Component, DialogUI> infoFactory;
    private final RaplaImages raplaImages;
    @Inject
    public ReservationTableViewFactory(RaplaContext context, Set<ReservationSummaryExtension> reservationSummaryExtensions,
            TableConfig.TableConfigLoader tableConfigLoader, MenuFactory menuFactory, ReservationController reservationController, InfoFactory<Component, DialogUI> infoFactory, RaplaImages raplaImages )
    {
        super( context );
        this.reservationSummaryExtensions = reservationSummaryExtensions;
        this.tableConfigLoader = tableConfigLoader;
        this.menuFactory = menuFactory;
        this.reservationController = reservationController;
        this.infoFactory = infoFactory;
        this.raplaImages = raplaImages;
    }

    public final static String TABLE_VIEW =  "table";

    public SwingCalendarView createSwingView(RaplaContext context, CalendarModel model, boolean editable) throws RaplaException
    {
        return new SwingReservationTableView( context, model, reservationSummaryExtensions, editable, tableConfigLoader, menuFactory, reservationController, infoFactory, raplaImages);
    }

    public String getViewId()
    {
        return TABLE_VIEW;
    }

    public String getName()
    {
        return getString("reservations");
    }

    Icon icon;
    public Icon getIcon()
    {
        if ( icon == null) {
            icon = RaplaImages.getIcon("/org/rapla/plugin/tableview/images/eventlist.png");
        }
        return icon;
    }

    public String getMenuSortKey() {
        return "0";
    }

}

