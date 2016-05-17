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

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import org.rapla.RaplaResources;
import org.rapla.components.calendarview.Builder;
import org.rapla.components.calendarview.html.AbstractHTMLView;
import org.rapla.components.calendarview.html.HTMLWeekView;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.plugin.abstractcalendar.GroupAllocatablesStrategy;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.server.AbstractHTMLCalendarPage;
import org.rapla.plugin.weekview.WeekviewPlugin;
import org.rapla.server.extensionpoints.HTMLViewPage;

@Extension(provides = HTMLViewPage.class,id= WeekviewPlugin.WEEK_VIEW)
public class HTMLWeekViewPage extends AbstractHTMLCalendarPage
{
    @Inject
    public HTMLWeekViewPage(RaplaLocale raplaLocale, RaplaResources raplaResources, RaplaFacade facade, Logger logger, AppointmentFormater appointmentFormater)
    {
        super(raplaLocale, raplaResources, facade, logger, appointmentFormater);
    }

    protected AbstractHTMLView createCalendarView() throws RaplaException {
        HTMLWeekView weekView = new HTMLWeekView()
        {
        	public void rebuild(Builder b) {
                Date startDate = getStartDate();
                String calendarweek = getI18n().calendarweek(startDate);
                setWeeknumber(calendarweek);
        		super.rebuild(b);
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

        GroupAllocatablesStrategy strategy = new GroupAllocatablesStrategy( raplaLocale.getLocale() );
        boolean compactColumns = getCalendarOptions().isCompactColumns() ||  builder.getAllocatables().size() ==0 ;
        strategy.setFixedSlotsEnabled( !compactColumns);
        strategy.setResolveConflictsEnabled( true );
        builder.setBuildStrategy( strategy );

        return builder;
    }

    public DateTools.IncrementSize getIncrementSize() {
        return DateTools.IncrementSize.WEEK_OF_YEAR;
    }

}

