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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.rapla.components.calendarview.GroupStartTimesStrategy;
import org.rapla.components.calendarview.html.AbstractHTMLView;
import org.rapla.components.calendarview.html.HTMLCompactWeekView;
import org.rapla.components.util.xml.XMLWriter;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.server.AbstractHTMLCalendarPage;
import org.rapla.plugin.timeslot.Timeslot;
import org.rapla.plugin.timeslot.TimeslotProvider;

public class HTMLCompactDayViewPage extends AbstractHTMLCalendarPage
{

    public HTMLCompactDayViewPage( RaplaContext context, CalendarModel calendarModel) 
    {
        super( context,  calendarModel);
    }
    
    protected AbstractHTMLView createCalendarView() {
        HTMLCompactWeekView weekView = new HTMLCompactWeekView()
        {
        	protected List<String> getHeaderNames()
        	{
	       		 List<String> headerNames = new ArrayList<String>();
	       		try
	       		{
		        		 List<Allocatable> sortedAllocatables = getSortedAllocatables();
		        		 for (Allocatable alloc: sortedAllocatables)
		        		 {
		        			 headerNames.add( alloc.getName( getLocale()));
		        		 }
	       		}
	       		catch (RaplaException ex)
	       		{
	       		}
	       		 return headerNames;
        	}
        	protected int getColumnCount() 
        	{
	       		 try {
	       			 Allocatable[] selectedAllocatables =model.getSelectedAllocatables();
	       			 return selectedAllocatables.length;
	       		 } catch (RaplaException e) {
	       			 return 0;
	       		 }
        	}
        	
        	@Override
        	public void rebuild() {
        		setWeeknumber(getRaplaLocale().formatDateShort(getStartDate()));
        		super.rebuild();
         	}
        };
        weekView.setLeftColumnSize(0.01);
        return weekView;
    }

    @Override
    protected void configureView() throws RaplaException {
        CalendarOptions opt = getCalendarOptions();
        int days = 1;
        view.setDaysInView( days);
        int firstDayOfWeek = opt.getFirstDayOfWeek();
		view.setFirstWeekday( firstDayOfWeek);
		Set<Integer> excludeDays = new HashSet<Integer>();
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
   		final List<Allocatable> allocatables = getSortedAllocatables();
   		builder.selectAllocatables( allocatables);

   		builder.setSmallBlocks( true );
   		GroupStartTimesStrategy strategy = new GroupStartTimesStrategy();
   		strategy.setAllocatables(allocatables);
   		strategy.setFixedSlotsEnabled( true);
   		strategy.setResolveConflictsEnabled( false );
   		strategy.setStartTimes( startTimes );
   		builder.setBuildStrategy( strategy);

        
        String[] slotNames = new String[ timeslots.size() ];
        for (int i = 0; i <timeslots.size(); i++ ) {
        	slotNames[i] = XMLWriter.encode( timeslots.get( i ).getName());
        }
    	((HTMLCompactWeekView)view).setSlots( slotNames );
    	return builder;
    }

    public int getIncrementSize() {
        return Calendar.DAY_OF_YEAR;
    }
   	
}

