/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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

package org.rapla.components.calendar;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Maps the DateRenderer methods to the appropriate date method.
    @see DateRenderer
 */
public class DateRendererAdapter implements DateRenderer {
    Calendar m_calendar;
    DateRenderer m_renderer = null;
    /** use this constructor if you want to implement a custom getBackgroundColor(Date)
        or getToolTipText(Date) method.
    */
    public DateRendererAdapter(TimeZone timeZone,Locale locale) {
        m_calendar = Calendar.getInstance(timeZone,locale);
    }

    /** use this constructor if you want to make an existing {@link DateRenderer}
        listen to the methods getBackgroundColor(Date) and getToolTipText(Date).
    */
    public DateRendererAdapter(DateRenderer renderer,TimeZone timeZone,Locale locale) {
        m_calendar = Calendar.getInstance(timeZone,locale);
        m_renderer = renderer;
    }

    /** override this method for a custom renderiungInfo
        @return null.*/
    public RenderingInfo getRenderingInfo(Date date) {
        if (m_renderer == null)
            return null;
        m_calendar.setTime(date);
        return m_renderer.getRenderingInfo(
                                           m_calendar.get(Calendar.DAY_OF_WEEK)
                                           ,m_calendar.get(Calendar.DATE)
                                           ,m_calendar.get(Calendar.MONTH) + 1
                                           ,m_calendar.get(Calendar.YEAR)
                                           );
    }


    /* calls {@link #getBackgroundColor(Date)} */
    public RenderingInfo getRenderingInfo(int dayOfWeek,int day,int month, int year) {
        m_calendar.set(Calendar.DATE,day);
        m_calendar.set(Calendar.MONTH,month -1 );
        m_calendar.set(Calendar.YEAR,year);
        m_calendar.set(Calendar.HOUR_OF_DAY,0);
        m_calendar.set(Calendar.MINUTE,0);
        m_calendar.set(Calendar.SECOND,0);
        m_calendar.set(Calendar.MILLISECOND,0);
        return getRenderingInfo(m_calendar.getTime());
    }

}
