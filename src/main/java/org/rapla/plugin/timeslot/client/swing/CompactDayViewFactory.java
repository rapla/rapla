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
package org.rapla.plugin.timeslot.client.swing;

import javax.inject.Inject;
import javax.swing.Icon;

import org.rapla.client.ReservationController;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.internal.RaplaClipboard;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.MenuFactory;
import org.rapla.client.swing.SwingCalendarView;
import org.rapla.client.swing.extensionpoints.SwingViewFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.DialogUI;
import org.rapla.inject.Extension;
import org.rapla.plugin.timeslot.TimeslotPlugin;
import org.rapla.plugin.timeslot.TimeslotProvider;

import java.awt.Component;
import java.util.Set;

@Extension(provides = SwingCalendarView.class, id = TimeslotPlugin.DAY_TIMESLOT)
public class CompactDayViewFactory extends RaplaComponent implements SwingViewFactory
{
    private final Set<ObjectMenuFactory> objectMenuFactories;
    private final MenuFactory menuFactory;
    private final CalendarSelectionModel calendarSelectionModel;
    private final RaplaClipboard clipboard;
    private final TimeslotProvider timeslotProvider;
    private final ReservationController reservationController;
    private final InfoFactory<Component, DialogUI> infoFactory;
    private final RaplaImages raplaImages;

    @Inject
    public CompactDayViewFactory(RaplaContext context, Set<ObjectMenuFactory> objectMenuFactories, MenuFactory menuFactory,
            CalendarSelectionModel calendarSelectionModel, RaplaClipboard clipboard, TimeslotProvider timeslotProvider,
            ReservationController reservationController, InfoFactory<Component, DialogUI> infoFactory, RaplaImages raplaImages)
    {
        super(context);
        this.objectMenuFactories = objectMenuFactories;
        this.menuFactory = menuFactory;
        this.calendarSelectionModel = calendarSelectionModel;
        this.clipboard = clipboard;
        this.timeslotProvider = timeslotProvider;
        this.reservationController = reservationController;
        this.infoFactory = infoFactory;
        this.raplaImages = raplaImages;
    }

    public SwingCalendarView createSwingView(RaplaContext context, CalendarModel model, boolean editable) throws RaplaException
    {
        return new SwingCompactDayCalendar(context, model, editable, objectMenuFactories, menuFactory, calendarSelectionModel, clipboard, timeslotProvider,
                reservationController, infoFactory, raplaImages);
    }

    public String getViewId()
    {
        return TimeslotPlugin.DAY_TIMESLOT;
    }

    public String getName()
    {
        return getString(TimeslotPlugin.DAY_TIMESLOT);
    }

    Icon icon;

    public Icon getIcon()
    {
        if (icon == null)
        {
            icon = RaplaImages.getIcon("/org/rapla/plugin/compactweekview/images/week_compact.png");
        }
        return icon;
    }

    public String getMenuSortKey()
    {
        return "B2";
    }

}
