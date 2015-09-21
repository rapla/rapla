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
import org.rapla.facade.CalendarModel;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.inject.Extension;
import org.rapla.plugin.abstractcalendar.server.HTMLViewFactory;
import org.rapla.server.servletpages.RaplaPageGenerator;

import javax.inject.Inject;

@Extension(provides = HTMLViewFactory.class,id=HTMLWeekViewFactory.WEEK_VIEW)
public class HTMLWeekViewFactory implements HTMLViewFactory
{
    RaplaResources i18n;
    @Inject
    public HTMLWeekViewFactory( RaplaResources i18n )
    {
        this.i18n = i18n ;
    }

    public final static String WEEK_VIEW = "week";

    public RaplaPageGenerator createHTMLView(RaplaContext context, CalendarModel model) throws RaplaException
    {
        return new HTMLWeekViewPage( context,  model);
    }

    public String getViewId()
    {
        return WEEK_VIEW;
    }

    public String getName()
    {
        return i18n.getString(WEEK_VIEW);
    }


}

