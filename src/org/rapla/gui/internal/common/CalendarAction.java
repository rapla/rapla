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
package org.rapla.gui.internal.common;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.Date;
import java.util.List;

import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.framework.RaplaContext;
import org.rapla.gui.RaplaAction;
import org.rapla.gui.toolkit.DisposingTool;
import org.rapla.gui.toolkit.RaplaFrame;


public class CalendarAction extends RaplaAction {
    CalendarSelectionModel model;
    List<?> objects;
    Component parent;
    Date start;

    public CalendarAction(RaplaContext sm,Component parent,CalendarModel selectionModel) 
    {
        super( sm);
        this.model = (CalendarSelectionModel)selectionModel.clone();
        this.parent = parent;
        putValue(NAME,getString("calendar"));
        putValue(SMALL_ICON,getIcon("icon.calendar"));
    }


    public void changeObjects(List<?> objects) {
        this.objects = objects;

    }

    public void setStart(Date start) {
        this.start = start;
    }

    public void actionPerformed(ActionEvent evt) {
        try {
            RaplaFrame frame = new RaplaFrame(getContext());
            Dimension dimension = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            frame.setSize(new Dimension(
                                        Math.min(dimension.width,800)
                                        ,Math.min(dimension.height-10,630)
                                        )
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

            MultiCalendarView cal = new MultiCalendarView(getContext(),model, false );
            frame.setContentPane(cal.getComponent());
            frame.addWindowListener(new DisposingTool(cal));
            boolean packFrame = false;
            frame.place( true, packFrame );
            frame.setVisible(true);
            cal.getSelectedCalendar().scrollToStart();
        } catch (Exception ex) {
            showException(ex, parent);
        }
    }
}

