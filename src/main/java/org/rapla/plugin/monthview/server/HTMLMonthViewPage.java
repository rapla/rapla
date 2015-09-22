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
package org.rapla.plugin.monthview.server;

import java.util.Calendar;
import java.util.Set;

import org.rapla.RaplaResources;
import org.rapla.components.calendarview.GroupStartTimesStrategy;
import org.rapla.components.calendarview.html.AbstractHTMLView;
import org.rapla.components.calendarview.html.HTMLMonthView;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.server.AbstractHTMLCalendarPage;
import org.rapla.plugin.monthview.MonthViewPlugin;
import org.rapla.plugin.weekview.server.HTMLWeekViewPage;
import org.rapla.server.extensionpoints.HTMLViewPage;

import javax.inject.Inject;

@Extension(provides = HTMLViewPage.class,id= MonthViewPlugin.MONTH_VIEW)
public class HTMLMonthViewPage extends AbstractHTMLCalendarPage
{
    @Inject
    public HTMLMonthViewPage(RaplaLocale raplaLocale, RaplaResources raplaResources, ClientFacade facade, Logger logger,
            AppointmentFormater appointmentFormater)
    {
        super(raplaLocale, raplaResources, facade, logger, appointmentFormater);
    }

    protected AbstractHTMLView createCalendarView() {
        HTMLMonthView monthView = new HTMLMonthView();
        return monthView;
    }

    protected RaplaBuilder createBuilder() throws RaplaException {
        RaplaBuilder builder = super.createBuilder();
        builder.setSmallBlocks( true );

        GroupStartTimesStrategy strategy = new GroupStartTimesStrategy( );
        builder.setBuildStrategy( strategy );
        return builder;
    }

    protected int getIncrementSize() {
        return Calendar.MONTH;
    }

	@Override
	protected void configureView() throws RaplaException {
		
		CalendarOptions opt = getCalendarOptions();
		Set<Integer> excludeDays = opt.getExcludeDays();
		view.setExcludeDays( excludeDays );
		view.setDaysInView( 30);
	}

}

