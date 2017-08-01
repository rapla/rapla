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
package org.rapla.plugin.weekview.server;

import org.rapla.RaplaResources;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.weekview.WeekviewPlugin;
import org.rapla.server.extensionpoints.HTMLViewPage;

import javax.inject.Inject;

@Extension(provides = HTMLViewPage.class,id = WeekviewPlugin.DAY_VIEW)
public class HTMLDayViewPage extends HTMLWeekViewPage
{
    @Inject
    public HTMLDayViewPage(RaplaLocale raplaLocale, RaplaResources raplaResources, RaplaFacade facade, Logger logger, AppointmentFormater appointmentFormater)
    {
        super(raplaLocale, raplaResources, facade, logger, appointmentFormater);
    }

    @Override
    protected int getDays(CalendarOptions calendarOptions)
    {
    	return 1;
    }

    public DateTools.IncrementSize getIncrementSize() {
        return DateTools.IncrementSize.DAY_OF_YEAR;
    }

}

