
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

package org.rapla.plugin.monthview.client.swing;

import java.awt.Color;
import java.util.Date;
import java.util.Set;

import javax.inject.Provider;
import javax.swing.JComponent;

import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.internal.RaplaClipboard;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.MenuFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.calendar.DateRendererAdapter;
import org.rapla.components.calendar.WeekendHighlightRenderer;
import org.rapla.components.calendarview.GroupStartTimesStrategy;
import org.rapla.components.calendarview.swing.AbstractSwingCalendar;
import org.rapla.components.calendarview.swing.SmallDaySlot;
import org.rapla.components.calendarview.swing.SwingMonthView;
import org.rapla.components.calendarview.swing.ViewListener;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.RaplaCalendarViewListener;
import org.rapla.plugin.abstractcalendar.client.swing.AbstractRaplaSwingCalendar;
import org.rapla.scheduler.Promise;


public class SwingMonthCalendar extends AbstractRaplaSwingCalendar
{
    public SwingMonthCalendar(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarModel settings, boolean editable, boolean printing, Set<ObjectMenuFactory> objectMenuFactories,
            MenuFactory menuFactory, Provider<DateRenderer> dateRendererProvider, CalendarSelectionModel calendarSelectionModel, RaplaClipboard clipboard,
            ReservationController reservationController, InfoFactory infoFactory, RaplaImages raplaImages, DateRenderer dateRenderer, DialogUiFactoryInterface dialogUiFactory, IOInterface ioInterface, AppointmentFormater appointmentFormater, EditController editController)
                    throws RaplaException
    {
        super(facade, i18n, raplaLocale, logger, settings, editable, printing, objectMenuFactories, menuFactory, dateRendererProvider, calendarSelectionModel, clipboard, reservationController,
                infoFactory, raplaImages, dateRenderer, dialogUiFactory, ioInterface, appointmentFormater, editController);
    }

    public static Color DATE_NUMBER_COLOR_HIGHLIGHTED = Color.black;

    protected AbstractSwingCalendar createView(boolean editable) {
        boolean showScrollPane = editable;
        final DateRenderer dateRenderer;
    	final DateRendererAdapter dateRendererAdapter; 
        dateRenderer = dateRendererProvider.get();
        dateRendererAdapter = new DateRendererAdapter(dateRenderer, getRaplaLocale().getTimeZone(), getRaplaLocale().getLocale());

        final WeekendHighlightRenderer weekdayRenderer = new WeekendHighlightRenderer();
        /** renderer for weekdays in month-view */
        SwingMonthView monthView = new SwingMonthView( showScrollPane ) {
            
            protected JComponent createSlotHeader(int weekday) {
                JComponent component = super.createSlotHeader( weekday );
                if (isEditable()) {
                    component.setOpaque(true);
                    Color color = weekdayRenderer.getRenderingInfo(weekday, 1, 1, 1).getBackgroundColor();
                    component.setBackground(color);
                }
                return component;
            }

            @Override
            protected SmallDaySlot createSmallslot(int pos, Date date) {
            	String header = "" + (pos + 1);
                DateRenderer.RenderingInfo info = dateRendererAdapter.getRenderingInfo(date);
                Color color = getNumberColor( date);
                Color backgroundColor = null;
                String tooltipText = null;
				
                if (info != null) {
                	backgroundColor = info.getBackgroundColor();
                    if (info.getForegroundColor() != null) {
                        color = info.getForegroundColor();
                    }
                    tooltipText = info.getTooltipText();
                    if ( tooltipText != null)
                    {
                    	 // commons not on client lib path
                    	//StringUtils.abbreviate(tooltipText, 15) 
//                    	header = tooltipText + " " + (pos+1);
                    }
                }
                final SmallDaySlot smallslot = super.createSmallslot(header, color, backgroundColor);
				if (tooltipText != null) {
                    smallslot.setToolTipText(tooltipText);
				}
                return smallslot;
            }

            protected Color getNumberColor( Date date )
            {
                boolean today = DateTools.isSameDay(getQuery().today().getTime(), date.getTime());
                if ( today)
                {
                    return DATE_NUMBER_COLOR_HIGHLIGHTED;
                }
                else
                {
                    return super.getNumberColor( date );
                }
            }
        };
        monthView.setDaysInView( 25);
		return monthView;
    }

    protected ViewListener createListener() throws RaplaException {
        RaplaCalendarViewListener listener = new RaplaCalendarViewListener(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), model, view.getComponent(), objectMenuFactories, menuFactory, calendarSelectionModel, clipboard, reservationController, infoFactory, raplaImages, dialogUiFactory, editController);
        listener.setKeepTime( true);
		return listener;
    }

    protected Promise<RaplaBuilder> createBuilder() 
    {
    	Promise<RaplaBuilder> builderPromise = super.createBuilder();
    	final Promise<RaplaBuilder> nextBuilderPromise = builderPromise.thenApply((builder) -> {
    	    builder.setSmallBlocks( true );
    	    GroupStartTimesStrategy strategy = new GroupStartTimesStrategy( );
    	    builder.setBuildStrategy( strategy );
    	    return builder;
    	});
    	return nextBuilderPromise;
    }

    protected void configureView() throws RaplaException {
        CalendarOptions calendarOptions = getCalendarOptions();
        Set<Integer> excludeDays = calendarOptions.getExcludeDays();

        view.setExcludeDays( excludeDays );
        view.setToDate(model.getSelectedDate());
    }

    @Override
    public DateTools.IncrementSize getIncrementSize()
    {
        return DateTools.IncrementSize.MONTH;
    }



}
