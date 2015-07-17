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

import org.rapla.facade.CalendarModel;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.plugin.abstractcalendar.server.HTMLViewFactory;
import org.rapla.servletpages.RaplaPageGenerator;

public class CompactHTMLDayViewFactory extends RaplaComponent implements HTMLViewFactory
{
    public final static String DAY_TIMESLOT = "day_timeslot";

    public CompactHTMLDayViewFactory( RaplaContext context ) 
    {
        super( context );
    }

    public RaplaPageGenerator createHTMLView(RaplaContext context, CalendarModel model) 
    {
        return new HTMLCompactDayViewPage( context, model);
    }

    public String getViewId()
    {
        return DAY_TIMESLOT;
    }

    public String getName()
    {
        return getString(DAY_TIMESLOT);
    }


}

