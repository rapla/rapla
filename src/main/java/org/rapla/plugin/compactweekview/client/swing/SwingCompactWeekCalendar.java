
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

package org.rapla.plugin.compactweekview.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
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
import org.rapla.components.calendarview.swing.AbstractSwingCalendar;
import org.rapla.components.calendarview.swing.SwingCompactWeekView;
import org.rapla.components.calendarview.swing.ViewListener;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.IOUtil;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.GroupAllocatablesStrategy;
import org.rapla.plugin.abstractcalendar.RaplaBlock;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.RaplaCalendarViewListener;
import org.rapla.plugin.abstractcalendar.client.swing.AbstractRaplaSwingCalendar;
import org.rapla.scheduler.Promise;

import javax.inject.Provider;
import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.Font;
import java.awt.Point;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class SwingCompactWeekCalendar extends AbstractRaplaSwingCalendar
{
    public SwingCompactWeekCalendar(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarModel settings, boolean editable,
            boolean printing, Set<ObjectMenuFactory> objectMenuFactories, MenuFactory menuFactory, Provider<DateRenderer> dateRendererProvider,
            CalendarSelectionModel calendarSelectionModel, RaplaClipboard clipboard, ReservationController reservationController, InfoFactory infoFactory,
            DateRenderer dateRenderer, DialogUiFactoryInterface dialogUiFactory, IOInterface ioInterface,
            AppointmentFormater appointmentFormater, EditController editController) throws RaplaException
    {
        super(facade, i18n, raplaLocale, logger, settings, editable, printing, objectMenuFactories, menuFactory, dateRendererProvider, calendarSelectionModel,
                clipboard, reservationController, infoFactory,  dateRenderer, dialogUiFactory, ioInterface, appointmentFormater, editController);
    }
    
    protected AbstractSwingCalendar createView(boolean showScrollPane) {
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
                        RenderingInfo renderingInfo = dateRenderer.getRenderingInfo( date);

                        if  ( renderingInfo.getBackgroundColor() != null)
                        {
                            component.setBackground(renderingInfo.getBackgroundColor());
                        }
                        if  ( renderingInfo.getForegroundColor() != null)
                        {
                            component.setForeground(renderingInfo.getForegroundColor());
                        }
                        component.setToolTipText(renderingInfo.getTooltipText());
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

        };
		return compactWeekView;

    }
    

    
    protected ViewListener createListener() throws RaplaException {
        RaplaCalendarViewListener listener = new RaplaCalendarViewListener(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), model, view.getComponent(),  menuFactory,  reservationController,  dialogUiFactory, editController) {
            
            @Override
            public void selectionChanged(Date start, Date end) {
                if ( end.getTime()- start.getTime() == DateTools.MILLISECONDS_PER_DAY ) {
                    int worktimeStartMinutes = getCalendarOptions().getWorktimeStartMinutes();
                    start = DateTools.toDateTime(start,new Date(DateTools.toTime(worktimeStartMinutes/60, worktimeStartMinutes%60, 0)));
                    end = new Date ( start.getTime() + 30 * DateTools.MILLISECONDS_PER_MINUTE );
                }
            	super.selectionChanged(start, end);
            }
            
            @Override
            protected Collection<Allocatable> getMarkedAllocatables() {
            	final List<Allocatable> selectedAllocatables = getSortedAllocatables();
				 
            	Set<Allocatable> allSelected = new HashSet<>();
				if ( selectedAllocatables.size() == 1 ) {
					allSelected.add(selectedAllocatables.get(0));
				}
	               
				int i= 0;
				int daysInView = view.getDaysInView();
				for ( Allocatable alloc:selectedAllocatables)
				{
					boolean add = false;
					for (int slot = i*daysInView;slot< (i+1)*daysInView;slot++)
					{
						if ( view.isSelected(slot))
						{
							add = true;
						}
					}
					if ( add )
					{
						allSelected.add(alloc);
					}
					i++;
				}
				return allSelected;
            }
            
            @Override
			 public void moved(Block block, Point p, Date newStart, int slotNr) {
				 int index= slotNr / view.getDaysInView();//getIndex( selectedAllocatables, block );
				 if ( index < 0)
				 {
					 return;
				 }
                 newStart = DateTools.toDateTime(newStart, (block.getStart()));
                 final List<Allocatable> selectedAllocatables = getSortedAllocatables();
                 Allocatable newAlloc = selectedAllocatables.get(index);
                 RaplaBlock raplaBlock = (RaplaBlock)block;
                 Allocatable oldAlloc = raplaBlock.getGroupAllocatable();
                 final Promise<Void> result;

                 if ( newAlloc != null && oldAlloc != null && !newAlloc.equals(oldAlloc))
                 {
                     AppointmentBlock appointmentBlock = raplaBlock.getAppointmentBlock();
                     PopupContext popupContext = createPopupContext(getMainComponent(),p);
                     result = reservationController.exchangeAllocatable(appointmentBlock, oldAlloc,newAlloc, newStart,popupContext);
                 }
                 else
                 {
                     result = super.moved(block, p, newStart);
                 }
                 handleException( result);
			 }

        };
        listener.setKeepTime( true);
        return listener;
    }

    protected Promise<RaplaBuilder> createBuilder() {
        Promise<RaplaBuilder> builderPromise = super.createBuilder();
        final Promise<RaplaBuilder> nextBuilderPromise = builderPromise.thenApply((builder) ->
        {
            builder.setSmallBlocks(true);
            final List<Allocatable> allocatables = getSortedAllocatables();
            GroupAllocatablesStrategy strategy = new GroupAllocatablesStrategy(getRaplaLocale().getLocale());
            strategy.setFixedSlotsEnabled(true);
            strategy.setResolveConflictsEnabled(false);
            strategy.setAllocatables(allocatables);
            builder.setBuildStrategy(strategy);
            final String[] slotNames = new String[allocatables.size()];
            for (int i = 0; i < allocatables.size(); i++)
            {
                slotNames[i] = allocatables.get(i).getName(getRaplaLocale().getLocale());
            }
            builder.setSplitByAllocatables(true);
            ((SwingCompactWeekView) view).setLeftColumnSize(150);
            ((SwingCompactWeekView) view).setSlots(slotNames);
            return builder;
        });
        return nextBuilderPromise;
    }

    protected void configureView() throws RaplaException {
        CalendarOptions calendarOptions = getCalendarOptions();
        Set<Integer> excludeDays = calendarOptions.getExcludeDays();
        view.setExcludeDays( excludeDays );
        view.setDaysInView( calendarOptions.getDaysInWeekview());
        int firstDayOfWeek = calendarOptions.getFirstDayOfWeek();
		view.setFirstWeekday( firstDayOfWeek);
        view.setToDate(model.getSelectedDate());
//        if ( !view.isEditable() ) {
//            view.setSlotSize( model.getSize());
//        } else {
//            view.setSlotSize( 200 );
//        }
    }

    public DateTools.IncrementSize getIncrementSize()
    {
        return DateTools.IncrementSize.WEEK_OF_YEAR;
    }

  
    

}
