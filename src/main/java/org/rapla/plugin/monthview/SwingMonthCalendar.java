
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

package org.rapla.plugin.monthview;

import java.awt.Color;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;

import javax.swing.JComponent;

import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.calendar.DateRendererAdapter;
import org.rapla.components.calendar.WeekendHighlightRenderer;
import org.rapla.components.calendarview.GroupStartTimesStrategy;
import org.rapla.components.calendarview.swing.AbstractSwingCalendar;
import org.rapla.components.calendarview.swing.SmallDaySlot;
import org.rapla.components.calendarview.swing.SwingMonthView;
import org.rapla.components.calendarview.swing.ViewListener;
import org.rapla.components.util.DateTools;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.abstractcalendar.AbstractRaplaSwingCalendar;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.RaplaCalendarViewListener;


public class SwingMonthCalendar extends AbstractRaplaSwingCalendar
{
	public SwingMonthCalendar(RaplaContext context,CalendarModel settings, boolean editable) throws RaplaException {
        super( context, settings, editable);
    }

    public static Color DATE_NUMBER_COLOR_HIGHLIGHTED = Color.black;

    protected AbstractSwingCalendar createView(boolean editable) {
        boolean showScrollPane = editable;
        final DateRenderer dateRenderer;
    	final DateRendererAdapter dateRendererAdapter; 
        dateRenderer = getService(DateRenderer.class);
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
        RaplaCalendarViewListener listener = new RaplaCalendarViewListener(getContext(), model, view.getComponent());
        listener.setKeepTime( true);
		return listener;
    }

    protected RaplaBuilder createBuilder() throws RaplaException
    {
    	RaplaBuilder builder = super.createBuilder();
    	builder.setSmallBlocks( true );
        GroupStartTimesStrategy strategy = new GroupStartTimesStrategy( );
        builder.setBuildStrategy( strategy );
        return builder;
    }

    protected void configureView() throws RaplaException {
        CalendarOptions calendarOptions = getCalendarOptions();
        Set<Integer> excludeDays = calendarOptions.getExcludeDays();

        view.setExcludeDays( excludeDays );
        view.setToDate(model.getSelectedDate());
    }

    public int getIncrementSize()
    {
        return Calendar.MONTH;
    }



}
