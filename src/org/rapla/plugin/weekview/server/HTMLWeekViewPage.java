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
package org.rapla.plugin.weekview.server;

import java.text.MessageFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import org.rapla.components.calendarview.html.AbstractHTMLView;
import org.rapla.components.calendarview.html.HTMLWeekView;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.abstractcalendar.GroupAllocatablesStrategy;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.server.AbstractHTMLCalendarPage;

public class HTMLWeekViewPage extends AbstractHTMLCalendarPage
{
    public HTMLWeekViewPage( RaplaContext context, CalendarModel calendarModel ) 
    {
        super( context,  calendarModel );
    }

    protected AbstractHTMLView createCalendarView() {
        HTMLWeekView weekView = new HTMLWeekView()
        {
        	public void rebuild() {
                setWeeknumber(MessageFormat.format(getString("calendarweek.abbreviation"), getStartDate()));
        		super.rebuild();
        	}
        };
        return weekView;
    }

	protected void configureView() {
		HTMLWeekView weekView = (HTMLWeekView) view;
		CalendarOptions opt = getCalendarOptions();
        weekView.setRowsPerHour( opt.getRowsPerHour() );
        weekView.setWorktimeMinutes(opt.getWorktimeStartMinutes(), opt.getWorktimeEndMinutes() );
        weekView.setFirstWeekday( opt.getFirstDayOfWeek());
        int days = getDays(opt);
		weekView.setDaysInView( days);
		Set<Integer> excludeDays = opt.getExcludeDays();
        if ( days <3)
		{
			excludeDays = new HashSet<Integer>();
		}
        weekView.setExcludeDays( excludeDays );
       
	}
    
    
    
    /** overide this for daily views*/
   	protected int getDays(CalendarOptions calendarOptions) {
   		return calendarOptions.getDaysInWeekview();
   	}

    protected RaplaBuilder createBuilder() throws RaplaException {
        RaplaBuilder builder = super.createBuilder();

        GroupAllocatablesStrategy strategy = new GroupAllocatablesStrategy( getRaplaLocale().getLocale() );
        boolean compactColumns = getCalendarOptions().isCompactColumns() ||  builder.getAllocatables().size() ==0 ;
        strategy.setFixedSlotsEnabled( !compactColumns);
        strategy.setResolveConflictsEnabled( true );
        builder.setBuildStrategy( strategy );

        return builder;
    }

    public int getIncrementSize() {
        return Calendar.WEEK_OF_YEAR;
    }

}

