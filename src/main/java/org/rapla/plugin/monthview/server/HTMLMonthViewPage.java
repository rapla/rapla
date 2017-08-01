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

import org.rapla.RaplaResources;
import org.rapla.components.calendarview.GroupStartTimesStrategy;
import org.rapla.components.calendarview.html.AbstractHTMLView;
import org.rapla.components.calendarview.html.HTMLMonthView;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.server.AbstractHTMLCalendarPage;
import org.rapla.plugin.monthview.MonthViewPlugin;
import org.rapla.server.extensionpoints.HTMLViewPage;

import javax.inject.Inject;
import java.util.Set;

@Extension(provides = HTMLViewPage.class,id= MonthViewPlugin.MONTH_VIEW)
public class HTMLMonthViewPage extends AbstractHTMLCalendarPage
{
    @Inject
    public HTMLMonthViewPage(RaplaLocale raplaLocale, RaplaResources raplaResources, RaplaFacade facade, Logger logger,
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

    protected DateTools.IncrementSize getIncrementSize() {
        return DateTools.IncrementSize.MONTH;
    }

	@Override
	protected void configureView() throws RaplaException {
		
		CalendarOptions opt = getCalendarOptions();
		Set<Integer> excludeDays = opt.getExcludeDays();
		view.setExcludeDays( excludeDays );
		view.setDaysInView( 30);
	}

}

