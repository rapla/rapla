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

import org.rapla.components.util.DateTools;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.TimeZone;

/** Maps the DateRenderer methods to the appropriate date method.
    @see DateRenderer
 */
public class DateRendererAdapter implements DateRenderer {
    DateRenderer m_renderer = null;
    /** use this constructor if you want to implement a custom getBackgroundColor(LocalDateTime)
        or getToolTipText(LocalDateTime) method.
        TimeZone + Locale parameters are kept for API compatibility but are no longer used —
        the day-of-week / day-of-month / month / year fields are derived directly from the
        LocalDateTime via DateTools.
    */
    public DateRendererAdapter(TimeZone timeZone, Locale locale) {
    }

    /** use this constructor if you want to make an existing {@link DateRenderer}
        listen to the methods getBackgroundColor(LocalDateTime) and getToolTipText(LocalDateTime).
    */
    public DateRendererAdapter(DateRenderer renderer, TimeZone timeZone, Locale locale) {
        m_renderer = renderer;
    }

    /** override this method for a custom renderiungInfo
        @return null.*/
    public RenderingInfo getRenderingInfo(LocalDateTime date) {
        if (m_renderer == null)
            return null;
        return m_renderer.getRenderingInfo(
                DateTools.getWeekday(date)        // Rapla weekday: SUNDAY=1..SATURDAY=7 (matches Calendar)
                , date.getDayOfMonth()
                , date.getMonthValue()
                , date.getYear()
        );
    }


    /* calls {@link #getRenderingInfo(LocalDateTime)} */
    public RenderingInfo getRenderingInfo(int dayOfWeek, int day, int month, int year) {
        LocalDateTime date = LocalDate.of(year, month, day).atStartOfDay();
        return getRenderingInfo(date);
    }

}
