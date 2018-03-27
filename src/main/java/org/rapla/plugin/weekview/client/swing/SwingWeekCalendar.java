
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

package org.rapla.plugin.weekview.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.internal.RaplaClipboard;
import org.rapla.client.menu.MenuFactory;
import org.rapla.components.calendar.DateChangeEvent;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.calendar.DateRenderer.RenderingInfo;
import org.rapla.components.calendar.DateRendererAdapter;
import org.rapla.components.calendarview.Builder;
import org.rapla.components.calendarview.swing.AbstractSwingCalendar;
import org.rapla.components.calendarview.swing.SwingWeekView;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.IOUtil;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.client.swing.AbstractRaplaSwingCalendar;

import javax.inject.Provider;
import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.Font;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;


public class SwingWeekCalendar extends AbstractRaplaSwingCalendar
{
    
    public SwingWeekCalendar(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarModel model, boolean editable,
            boolean printing, Set<ObjectMenuFactory> objectMenuFactories, MenuFactory menuFactory, final Provider<DateRenderer> dateRendererProvider,
            CalendarSelectionModel calendarSelectionModel, RaplaClipboard clipboard, ReservationController reservationController, InfoFactory infoFactory,
            DateRenderer dateRenderer, DialogUiFactoryInterface dialogUiFactory,
            IOInterface ioInterface, AppointmentFormater appointmentFormater, EditController editController) throws RaplaException
    {
        super(facade, i18n, raplaLocale, logger, model, editable, printing, objectMenuFactories, menuFactory, dateRendererProvider, calendarSelectionModel,
                clipboard, reservationController, infoFactory,  dateRenderer, dialogUiFactory, ioInterface,
                appointmentFormater, editController);
    }

    protected AbstractSwingCalendar createView(boolean showScrollPane) {
        final DateRendererAdapter dateRendererAdapter; 
        DateRenderer dateRenderer = dateRendererProvider.get();
        dateRendererAdapter = new DateRendererAdapter(dateRenderer, IOUtil.getTimeZone(), getRaplaLocale().getLocale());

    	final SwingWeekView wv = new SwingWeekView( showScrollPane ) {
            
            protected JComponent createSlotHeader(Integer column) {
                JLabel component = (JLabel) super.createSlotHeader(column);
                Date date = getDateFromColumn(column);
                boolean today = DateTools.isSameDay(getQuery().today().getTime(), date.getTime());
                if ( today)
                {
                    component.setFont(component.getFont().deriveFont( Font.BOLD));
                }
                if (isEditable()  ) {
                	component.setOpaque(true);
                    RenderingInfo info = dateRendererAdapter.getRenderingInfo(date);
                    if (info.getBackgroundColor() != null) {
                        component.setBackground(info.getBackgroundColor());
                    }
                    if (info.getForegroundColor() != null) {
                        component.setForeground(info.getForegroundColor());
                    }
                    if (info.getTooltipText() != null) {
                        component.setToolTipText(info.getTooltipText());
                    }
                }
                return component;
            }
            
        

            @Override
            public void rebuild(Builder b) {
                // update week
                Date startDate = getStartDate();
				weekTitle.setText(getI18n().calendarweek( startDate));
                super.rebuild(b);
            }
        };
        return wv;
    }

    public void dateChanged(DateChangeEvent evt) {
        super.dateChanged( evt );
        ((SwingWeekView)view).scrollDateVisible( evt.getDate());
    }

    @Override
    protected void configureView() {
        SwingWeekView view = (SwingWeekView) this.view;

        CalendarOptions calendarOptions = getCalendarOptions();
        int rowsPerHour = calendarOptions.getRowsPerHour();
        int startMinutes = calendarOptions.getWorktimeStartMinutes();
        int endMinutes = calendarOptions.getWorktimeEndMinutes();
        final int diffMinutes = endMinutes - startMinutes;
        int hours = Math.max(1, Math.abs(diffMinutes) / 60);
        if (diffMinutes < 0)
        {
            view.setOffsetMinutes(-diffMinutes);
        }
        view.setRowsPerHour( rowsPerHour );
        if ( rowsPerHour == 1 ) {
            if ( hours < 10)
            {
                view.setRowSize( 80);
            }
            else if ( hours < 15)
            {
                view.setRowSize( 60);
            }
            else
            {
                view.setRowSize( 30);
            }
        } else if ( rowsPerHour == 2 ) {
            if ( hours < 10)
            {
                view.setRowSize( 40);
            }
            else
            {
                view.setRowSize( 20);
            }
        } 
        else if ( rowsPerHour >= 4 )
        {
            view.setRowSize( 15);
        }
		view.setWorktimeMinutes(startMinutes, endMinutes);       
        int days = getDays( calendarOptions);
		view.setDaysInView( days);
		Set<Integer> excludeDays = calendarOptions.getExcludeDays();
		if ( days <3)
		{
			excludeDays = new HashSet<>();
		}
        view.setExcludeDays( excludeDays );
        
        view.setFirstWeekday( calendarOptions.getFirstDayOfWeek());
        view.setToDate(model.getSelectedDate());

//        if ( !view.isEditable() ) {
//            view.setSlotSize( model.getSize());
//        } else {
      //      view.setSlotSize( 135 );
 //       }
    }

    /** overide this for dayly views*/
	protected int getDays(CalendarOptions calendarOptions) {
		return calendarOptions.getDaysInWeekview();
	}


    public void scrollToStart()
    {
        ((SwingWeekView)view).scrollToStart();
    }

    @Override
    public DateTools.IncrementSize getIncrementSize() {
        return DateTools.IncrementSize.WEEK_OF_YEAR;
    }

}
