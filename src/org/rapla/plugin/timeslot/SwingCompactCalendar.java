
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

package org.rapla.plugin.timeslot;

import java.awt.Font;
import java.awt.Point;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JLabel;

import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.calendar.DateRenderer.RenderingInfo;
import org.rapla.components.calendar.DateRendererAdapter;
import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.GroupStartTimesStrategy;
import org.rapla.components.calendarview.swing.AbstractSwingCalendar;
import org.rapla.components.calendarview.swing.SwingBlock;
import org.rapla.components.calendarview.swing.SwingCompactWeekView;
import org.rapla.components.calendarview.swing.ViewListener;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.abstractcalendar.AbstractRaplaSwingCalendar;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.RaplaCalendarViewListener;

public class SwingCompactCalendar extends AbstractRaplaSwingCalendar
{
	List<Timeslot> timeslots;
	
    public SwingCompactCalendar(RaplaContext sm,CalendarModel settings, boolean editable) throws RaplaException {
        super( sm, settings, editable);
    }
  
    @Override
	protected AbstractSwingCalendar createView(boolean showScrollPane)
			throws RaplaException {
    	   final DateRendererAdapter dateRenderer = new DateRendererAdapter(getService(DateRenderer.class), getRaplaLocale().getTimeZone(), getRaplaLocale().getLocale());
           SwingCompactWeekView compactWeekView = new SwingCompactWeekView( showScrollPane ) {
               @Override
               protected JComponent createColumnHeader(Integer column) {
                   JLabel component = (JLabel) super.createColumnHeader(column);
                   if ( column != null ) {
                   	Date date = getDateFromColumn(column);
                       boolean today = DateTools.isSameDay(getQuery().today().getTime(), date.getTime());
                       if ( today)
                       {
                           component.setFont(component.getFont().deriveFont( Font.BOLD));
                       }
                       if (isEditable()  ) {
                           component.setOpaque(true);
                           RenderingInfo info = dateRenderer.getRenderingInfo(date);
                           if ( info.getBackgroundColor() != null)
                           {
                               component.setBackground(info.getBackgroundColor());
                           }
                           if ( info.getForegroundColor() != null)
                           {
                               component.setForeground(info.getForegroundColor());
                           }
                           component.setToolTipText(info.getTooltipText());
                       }
                   }
                   else 
                   {
                	   String calendarWeek = MessageFormat.format(getString("calendarweek.abbreviation"), getStartDate());
                	   component.setText( calendarWeek);
                   }
         
                   return component;
               }
               protected int getColumnCount() 
           	   {
                 	return getDaysInView();
           	   }
               @Override
               public TimeInterval normalizeBlockIntervall(SwingBlock block) 
               {
	               	Date start = block.getStart();
	   				Date end = block.getEnd();
	   				for (Timeslot slot:timeslots)
	   				{
	   					int minuteOfDay = DateTools.getMinuteOfDay( start.getTime());
	   					if ( minuteOfDay >= slot.minuteOfDay)
	   					{
	   						start = new Date(DateTools.cutDate( start).getTime() + slot.minuteOfDay);
	   						break;
	   					}
	   				}
	   				for (Timeslot slot:timeslots)
	   				{
	   					int minuteOfDay = DateTools.getMinuteOfDay( end.getTime());
	   					if ( minuteOfDay < slot.minuteOfDay)
	   					{
	   						end = new Date(DateTools.cutDate( end).getTime() + slot.minuteOfDay);
	   					}
	   					if (  slot.minuteOfDay > minuteOfDay)
	   					{
	   						break;
	   					}
	   						
	   				}
	   				return new TimeInterval(start,end);
               }

           };
   		return compactWeekView;

	}


	@Override
	public int getIncrementSize() {
		 return Calendar.WEEK_OF_YEAR;
	}
   
    
     protected ViewListener createListener() throws RaplaException {
        return  new RaplaCalendarViewListener(getContext(), model, view.getComponent()) {
        	/** override to change the allocatable to the row that is selected */
            @Override
            public void selectionChanged(Date start,Date end) 
            {
            	TimeInterval inter = getMarkedInterval(start);
        		super.selectionChanged(inter.getStart(), inter.getEnd());
            }

            public void moved(Block block, Point p, Date newStart, int slotNr) {
                int days = view.getDaysInView();

            	int columns = days;
            	int index = slotNr;
            	int rowIndex = index/columns;
            	Timeslot timeslot = timeslots.get(rowIndex);
            	int time = timeslot.minuteOfDay;
            	Calendar cal = getRaplaLocale().createCalendar();
            	int lastMinuteOfDay;
            	cal.setTime ( block.getStart() );
            	lastMinuteOfDay = cal.get( Calendar.HOUR_OF_DAY)  * 60 +   	cal.get( Calendar.MINUTE);
            	boolean sameTimeSlot = true;
            	if ( lastMinuteOfDay < time)
            	{
            		sameTimeSlot = false;
            	}
            	if ( rowIndex +1 < timeslots.size())
            	{
            		Timeslot nextTimeslot = timeslots.get(rowIndex+1);
            		if ( lastMinuteOfDay >= nextTimeslot.minuteOfDay )
            		{
            			sameTimeSlot = false;
            		}
            	}
            	
            	cal.setTime ( newStart );
            	if ( sameTimeSlot)
            	{
            		time = lastMinuteOfDay;
            	}
        		cal.set( Calendar.HOUR_OF_DAY, time /60);
        		cal.set( Calendar.MINUTE, time %60);
			      
            	newStart = cal.getTime();
            	moved(block, p, newStart);
	        }
         
            protected TimeInterval getMarkedInterval(Date start) {
				int columns =  view.getDaysInView();
				Date end;
				Integer startTime = null;
		        Integer endTime = null;
		        int slots = columns*timeslots.size();
				
		        for ( int i=0;i<slots;i++) 
		        {
		        	if ( ((SwingCompactWeekView)view).isSelected(i))
		        	{
		        		int index = i/columns;
		        		int time = timeslots.get(index).minuteOfDay;
						if ( startTime == null || time < startTime )
		        		{
		        			startTime = time;
		        		}
						
						time = index<timeslots.size()-1 ? timeslots.get(index+1).minuteOfDay : 24* 60;
						if ( endTime == null || time >= endTime )
		        		{
		        			endTime = time;
		        		}
		        	}
		        }
		        
		        CalendarOptions calendarOptions = getCalendarOptions();
				if ( startTime == null)
		        {
		        	startTime = calendarOptions.getWorktimeStartMinutes();
		        }
		        if ( endTime == null)
		        {
		        	endTime = calendarOptions.getWorktimeEndMinutes() + (calendarOptions.isWorktimeOvernight() ? 24*60:0);
		        }
		        
		        Calendar cal = getRaplaLocale().createCalendar();
		        cal.setTime ( start );
		        cal.set( Calendar.HOUR_OF_DAY, startTime/60);
		        cal.set( Calendar.MINUTE, startTime%60);
		        
		        start = cal.getTime();
		        cal.set( Calendar.HOUR_OF_DAY, endTime/60);
		        cal.set( Calendar.MINUTE, endTime%60);
			      
		        end = cal.getTime();
		        TimeInterval intervall = new TimeInterval(start,end);
				return intervall;
			}
          

        };
    }

    protected RaplaBuilder createBuilder() throws RaplaException {
    	timeslots = getService(TimeslotProvider.class).getTimeslots();
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
//Uncommont if you want to sort by resource name instead of event name
        //        strategy.setBlockComparator( new Comparator<Block>() {
//			
//			public int compare(Block b1, Block b2) {
//		        int result = b1.getStart().compareTo(b2.getStart());
//		        if (result != 0)
//		        {
//		        	return result; 
//		        }
//		        Allocatable a1 = ((AbstractRaplaBlock) b1).getGroupAllocatable();
//		        Allocatable a2 = ((AbstractRaplaBlock) b2).getGroupAllocatable();
//		        if ( a1 != null && a2 != null)
//		        {
//		        	String name1 = a1.getName( getLocale());
//					String name2 = a2.getName(getLocale());
//					
//					result = name1.compareTo(name2);
//			        if (result != 0)
//			        {
//			        	return result; 
//			        }
//
//		        }
//		        return b1.getName().compareTo(b2.getName());
//			}
//		});
    	builder.setBuildStrategy( strategy);    	
        builder.setSplitByAllocatables( false );
        String[] slotNames = new String[ timeslots.size() ];
        int maxSlotLength = 5;
        for (int i = 0; i <timeslots.size(); i++ ) {
        	String slotName = timeslots.get( i ).getName();
        	maxSlotLength = Math.max( maxSlotLength, slotName.length());
			slotNames[i] = slotName;
        }
        ((SwingCompactWeekView)view).setLeftColumnSize( 30+ maxSlotLength * 6);
        ((SwingCompactWeekView)view).setSlots( slotNames );
    	return builder;
    }

    
    @Override
	protected void configureView() throws RaplaException {
    	CalendarOptions calendarOptions = getCalendarOptions();
        Set<Integer> excludeDays = calendarOptions.getExcludeDays();
        view.setExcludeDays( excludeDays );
        view.setDaysInView( calendarOptions.getDaysInWeekview());
        int firstDayOfWeek = calendarOptions.getFirstDayOfWeek();
 		view.setFirstWeekday( firstDayOfWeek);
        view.setToDate(model.getSelectedDate());
	}

	



	

}
