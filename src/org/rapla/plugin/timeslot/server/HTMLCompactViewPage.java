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
package org.rapla.plugin.timeslot.server;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;

import org.rapla.components.calendarview.GroupStartTimesStrategy;
import org.rapla.components.calendarview.html.AbstractHTMLView;
import org.rapla.components.calendarview.html.HTMLCompactWeekView;
import org.rapla.components.util.xml.XMLWriter;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.server.AbstractHTMLCalendarPage;
import org.rapla.plugin.timeslot.Timeslot;
import org.rapla.plugin.timeslot.TimeslotProvider;

public class HTMLCompactViewPage extends AbstractHTMLCalendarPage
{

    public HTMLCompactViewPage( RaplaContext context, CalendarModel calendarModel) 
    {
        super( context,  calendarModel);
    }
    
    protected AbstractHTMLView createCalendarView() {
        HTMLCompactWeekView weekView = new HTMLCompactWeekView()
        {
          	@Override
        	public void rebuild() {
        		 String weeknumberString = MessageFormat.format(getString("calendarweek.abbreviation"), getStartDate());
        		 setWeeknumber(weeknumberString);
        		 super.rebuild();
        	}
        };
        weekView.setLeftColumnSize(0.01);
        return weekView;
    }
   
    

    @Override
    protected void configureView() throws RaplaException {
        CalendarOptions opt = getCalendarOptions();
        Set<Integer> excludeDays = opt.getExcludeDays();
        view.setExcludeDays( excludeDays );
        view.setDaysInView( opt.getDaysInWeekview());
        int firstDayOfWeek = opt.getFirstDayOfWeek();
		view.setFirstWeekday( firstDayOfWeek);
        view.setExcludeDays( excludeDays );
    }

    protected RaplaBuilder createBuilder() throws RaplaException 
    {
    	List<Timeslot> timeslots = getService(TimeslotProvider.class).getTimeslots();
    	List<Integer> startTimes = new ArrayList<Integer>();
    	for (Timeslot slot:timeslots) {
    		 startTimes.add( slot.getMinuteOfDay());
    	}
    	RaplaBuilder builder = super.createBuilder();
    	builder.setSmallBlocks( true );
    	GroupStartTimesStrategy strategy = new GroupStartTimesStrategy();
    	strategy.setFixedSlotsEnabled( true);
    	strategy.setResolveConflictsEnabled( false );
        strategy.setStartTimes( startTimes );
    	builder.setBuildStrategy( strategy);    	
        builder.setSplitByAllocatables( false );
        String[] slotNames = new String[ timeslots.size() ];
        for (int i = 0; i <timeslots.size(); i++ ) {
        	slotNames[i] = XMLWriter.encode( timeslots.get( i ).getName());
        }
    	((HTMLCompactWeekView)view).setSlots( slotNames );
    	return builder;
    }

    protected int getIncrementSize() {
        return Calendar.WEEK_OF_YEAR;
    }

}

