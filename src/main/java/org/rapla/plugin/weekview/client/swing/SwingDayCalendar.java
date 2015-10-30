
/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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

package org.rapla.plugin.weekview.client.swing;

import java.util.Calendar;
import java.util.Set;

import javax.inject.Provider;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.swing.MenuFactory;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;


public class SwingDayCalendar extends SwingWeekCalendar
{
    public SwingDayCalendar( RaplaContext sm, CalendarModel model, boolean editable,final Set<ObjectMenuFactory> objectMenuFactories,MenuFactory menuFactory, RaplaResources resources, Provider<DateRenderer> dateRendererProvider ) throws RaplaException
    {
        super( sm, model, editable, objectMenuFactories, menuFactory, resources, dateRendererProvider );
    }
    
    @Override
    protected int getDays( CalendarOptions calendarOptions) {
        return 1;
    }

    public int getIncrementSize() {
        return Calendar.DAY_OF_YEAR;
    }

}
