/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas, Bettina Lademann                |
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
package org.rapla.client.swing.internal.common;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.RaplaAction;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.MultiCalendarPresenter;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.FrameControllerList;
import org.rapla.client.swing.toolkit.RaplaFrame;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

import javax.inject.Provider;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.Date;
import java.util.List;

public class CalendarAction extends RaplaAction {
    CalendarSelectionModel model;
    List<?> objects;
    Component parent;
    Date start;
    private final Provider<MultiCalendarPresenter> multiCalendarViewFactory;
    private final DialogUiFactoryInterface dialogUiFactory;

    public CalendarAction(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, Component parent, CalendarModel selectionModel, RaplaImages raplaImages, Provider<MultiCalendarPresenter> multiCalendarViewFactory, DialogUiFactoryInterface dialogUiFactory)
    {
        super(facade, i18n, raplaLocale, logger);
        this.multiCalendarViewFactory = multiCalendarViewFactory;
        this.dialogUiFactory = dialogUiFactory;
        this.model = (CalendarSelectionModel)selectionModel.clone();
        this.parent = parent;
        putValue(NAME,getString("calendar"));
        putValue(SMALL_ICON,raplaImages.getIconFromKey("icon.calendar"));
    }


    public void changeObjects(List<?> objects) {
        this.objects = objects;

    }

    public void setStart(Date start) {
        this.start = start;
    }

    public void actionPerformed() {
        try {
            MultiCalendarPresenter cal = multiCalendarViewFactory.get();
            final PopupContext popupContext = dialogUiFactory.createPopupContext(() -> parent);
            DialogInterface frame = dialogUiFactory.create(popupContext, false, cal.provideContent().getComponent(), new String[]{});
            Dimension dimension = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            frame.setSize(
                                        Math.min(dimension.width,800)
                                        ,Math.min(dimension.height-10,630)
                          );
            if (start != null)
                model.setSelectedDate(start);
            if (objects != null && objects.size() > 0)
                model.setSelectedObjects( objects );

            if ( model.getViewId( ).equals("table")) {
                model.setViewId("week");
            }
            model.setOption( CalendarModel.ONLY_MY_EVENTS, "false");
            model.setAllocatableFilter( null);
            model.setReservationFilter( null);
            frame.setTitle("Rapla "  + getString("calendar"));

            cal.init( false,()->{ getLogger().debug("CalendarChangeCallback");});
            boolean packFrame = false;
            frame.start(  packFrame );
            cal.scrollToStart();
        } catch (Exception ex) {
            dialogUiFactory.showException(ex, new SwingPopupContext(parent, null));
        }
    }
}

