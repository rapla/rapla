
/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copyReservations of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.plugin.timeslot.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.internal.RaplaClipboard;
import org.rapla.client.menu.MenuFactory;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.calendar.DateRenderer.RenderingInfo;
import org.rapla.components.calendar.DateRendererAdapter;
import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.GroupStartTimesStrategy;
import org.rapla.components.calendarview.swing.AbstractSwingCalendar;
import org.rapla.components.calendarview.swing.SwingBlock;
import org.rapla.components.calendarview.swing.SwingCompactWeekView;
import org.rapla.components.calendarview.swing.ViewListener;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.IOUtil;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.RaplaCalendarViewListener;
import org.rapla.plugin.abstractcalendar.client.swing.AbstractRaplaSwingCalendar;
import org.rapla.plugin.timeslot.Timeslot;
import org.rapla.plugin.timeslot.TimeslotProvider;
import org.rapla.scheduler.Promise;

import javax.inject.Provider;
import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.Font;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class SwingCompactCalendar extends AbstractRaplaSwingCalendar
{
	private List<Timeslot> timeslots;
    private final TimeslotProvider timeslotProvider;
	
    public SwingCompactCalendar(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,CalendarModel settings, boolean editable, boolean printing, Set<ObjectMenuFactory>objectMenuFactories, MenuFactory menuFactory, TimeslotProvider timeslotProvider, Provider<DateRenderer> dateRendererProvider, CalendarSelectionModel calendarSelectionModel, RaplaClipboard clipboard, ReservationController reservationController, InfoFactory infoFactory, DateRenderer dateRenderer, DialogUiFactoryInterface dialogUiFactory, IOInterface ioInterface, AppointmentFormater appointmentFormater, EditController editController) throws RaplaException {
        super( facade, i18n, raplaLocale, logger, settings, editable, printing, objectMenuFactories, menuFactory, dateRendererProvider, calendarSelectionModel, clipboard, reservationController, infoFactory, dateRenderer, dialogUiFactory, ioInterface, appointmentFormater, editController);
        this.timeslotProvider = timeslotProvider;
    }
  
    @Override
	protected AbstractSwingCalendar createView(boolean showScrollPane)
			throws RaplaException {
    	   final DateRendererAdapter dateRenderer = new DateRendererAdapter(dateRendererProvider.get(), IOUtil.getTimeZone(), getRaplaLocale().getLocale());
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
                	   String calendarWeek = getI18n().calendarweek( getStartDate());
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
						int minuteOfDay1 = slot.getMinuteOfDay();
						if ( minuteOfDay >= minuteOfDay1)
	   					{
	   						start = new Date(DateTools.cutDate( start).getTime() + minuteOfDay1);
	   						break;
	   					}
	   				}
	   				for (Timeslot slot:timeslots)
	   				{
	   					int minuteOfDay = DateTools.getMinuteOfDay( end.getTime());
						int minuteOfDay1 = slot.getMinuteOfDay();
						if ( minuteOfDay < minuteOfDay1)
	   					{
	   						end = new Date(DateTools.cutDate( end).getTime() + minuteOfDay1);
	   					}
	   					if (  minuteOfDay1 > minuteOfDay)
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
	public DateTools.IncrementSize getIncrementSize() {
		 return DateTools.IncrementSize.WEEK_OF_YEAR;
	}
   
    
     protected ViewListener createListener() throws RaplaException {
        return  new RaplaCalendarViewListener(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), model, view.getComponent(),  menuFactory,   reservationController,  dialogUiFactory, editController) {
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
            	int time = timeslot.getMinuteOfDay();
            	int lastMinuteOfDay;
				DateTools.TimeWithoutTimezone timeWithoutTimezone = DateTools.toTime(block.getStart().getTime());
				lastMinuteOfDay = timeWithoutTimezone.hour  * 60 +  timeWithoutTimezone.minute;
            	boolean sameTimeSlot = true;
            	if ( lastMinuteOfDay < time)
            	{
            		sameTimeSlot = false;
            	}
            	if ( rowIndex +1 < timeslots.size())
            	{
            		Timeslot nextTimeslot = timeslots.get(rowIndex+1);
            		if ( lastMinuteOfDay >= nextTimeslot.getMinuteOfDay() )
            		{
            			sameTimeSlot = false;
            		}
            	}
            	if ( sameTimeSlot)
            	{
            		time = lastMinuteOfDay;
            	}
				final long l = DateTools.toTime(time / 60, time % 60, 0);
            	newStart = DateTools.toDateTime( newStart, new Date(l));
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
		        	if ( view.isSelected(i))
		        	{
		        		int index = i/columns;
		        		int time = timeslots.get(index).getMinuteOfDay();
						if ( startTime == null || time < startTime )
		        		{
		        			startTime = time;
		        		}
						
						time = index<timeslots.size()-1 ? timeslots.get(index+1).getMinuteOfDay() : 24* 60;
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

		        start = DateTools.toDateTime( start,new Date(DateTools.toTime( startTime/60, startTime%60, 0)));
				end = DateTools.toDateTime( start,new Date(DateTools.toTime( endTime/60, endTime%60, 0)));
		        TimeInterval intervall = new TimeInterval(start,end);
				return intervall;
			}
          

        };
    }

    protected Promise<RaplaBuilder> createBuilder() {
		timeslots = timeslotProvider.getTimeslots();
		List<Integer> startTimes = new ArrayList<>();
		for (Timeslot slot:timeslots) {
			startTimes.add( slot.getMinuteOfDay());
		}
        Promise<RaplaBuilder> builderPromise = super.createBuilder();
        final Promise<RaplaBuilder> nextBuilderPromise = builderPromise.thenApply((builder) -> {

            builder.setSmallBlocks( true );
            GroupStartTimesStrategy strategy = new GroupStartTimesStrategy();
            strategy.setFixedSlotsEnabled( true);
            strategy.setResolveConflictsEnabled( false );
            strategy.setStartTimes( startTimes );
    //Uncommont if you want to sort by resource name instead of event name
            //        strategy.setBlockComparator( new Comparator<Block>() {
//              
//              public int compare(Block b1, Block b2) {
//                  int result = b1.getStart().compareTo(b2.getStart());
//                  if (result != 0)
//                  {
//                      return result; 
//                  }
//                  Allocatable a1 = ((RaplaBlock) b1).getGroupAllocatable();
//                  Allocatable a2 = ((RaplaBlock) b2).getGroupAllocatable();
//                  if ( a1 != null && a2 != null)
//                  {
//                      String name1 = a1.getName( getLocale());
//                      String name2 = a2.getName(getLocale());
//                      
//                      result = name1.compareTo(name2);
//                      if (result != 0)
//                      {
//                          return result; 
//                      }
    //
//                  }
//                  return b1.getName().compareTo(b2.getName());
//              }
//          });
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
        });
        return nextBuilderPromise;
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
