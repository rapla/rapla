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

import org.rapla.RaplaResources;
import org.rapla.components.calendarview.Builder;
import org.rapla.components.calendarview.GroupStartTimesStrategy;
import org.rapla.components.calendarview.html.AbstractHTMLView;
import org.rapla.components.calendarview.html.HTMLCompactWeekView;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.xml.XMLWriter;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.server.AbstractHTMLCalendarPage;
import org.rapla.plugin.timeslot.Timeslot;
import org.rapla.plugin.timeslot.TimeslotPlugin;
import org.rapla.plugin.timeslot.TimeslotProvider;
import org.rapla.server.extensionpoints.HTMLViewPage;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Extension(provides = HTMLViewPage.class, id = TimeslotPlugin.DAY_TIMESLOT)
public class HTMLCompactDayViewPage extends AbstractHTMLCalendarPage
{
	private final TimeslotProvider timeslotProvider;
	@Inject
	public HTMLCompactDayViewPage(RaplaLocale raplaLocale, RaplaResources raplaResources, RaplaFacade facade, Logger logger,
			AppointmentFormater appointmentFormater, TimeslotProvider timeslotProvider)
	{
		super(raplaLocale, raplaResources, facade, logger, appointmentFormater);
		this.timeslotProvider = timeslotProvider;
	}

	protected AbstractHTMLView createCalendarView() {
        HTMLCompactWeekView weekView = new HTMLCompactWeekView()
        {
        	protected List<String> getHeaderNames()
        	{
	       		 List<String> headerNames = new ArrayList<>();
	       		try
	       		{
		        		 List<Allocatable> sortedAllocatables = model.getSelectedAllocatablesSorted();
		        		 for (Allocatable alloc: sortedAllocatables)
		        		 {
		        			 headerNames.add( alloc.getName( getRaplaLocale().getLocale()));
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
	       			 Collection<Allocatable> selectedAllocatables =model.getSelectedAllocatablesAsList();
	       			 return selectedAllocatables.size();
	       		 } catch (RaplaException e) {
	       			 return 0;
	       		 }
        	}
        	
        	@Override
        	public void rebuild(Builder b) {
        		setWeeknumber(getRaplaLocale().formatDateShort(getStartDate()));
        		super.rebuild(b);
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
		Set<Integer> excludeDays = new HashSet<>();
		view.setExcludeDays( excludeDays );
    }
    

    protected RaplaBuilder createBuilder() throws RaplaException 
    {
    	final List<Timeslot> timeslots = timeslotProvider.getTimeslots();
    	List<Integer> startTimes = new ArrayList<>();
    	for (Timeslot slot:timeslots) {
    		 startTimes.add( slot.getMinuteOfDay());
    	}
    	RaplaBuilder builder = super.createBuilder();
   		final List<Allocatable> allocatables = model.getSelectedAllocatablesSorted();
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

    public DateTools.IncrementSize getIncrementSize() {
        return DateTools.IncrementSize.DAY_OF_YEAR;
    }
   	
}

