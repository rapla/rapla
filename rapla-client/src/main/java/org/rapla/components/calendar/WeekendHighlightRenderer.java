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

package org.rapla.components.calendar;



import java.awt.Color;
import java.util.Calendar;
/** Renders the weekdays (or any other day of week, if selected) in a special color. */
public class WeekendHighlightRenderer implements DateRenderer {

    Color m_weekendBackgroundColor = new Color(0xc9, 0xf3, 0xff);

    boolean[] m_highlightedDays = new boolean[10];

    public WeekendHighlightRenderer() {
        setHighlight(Calendar.SATURDAY,true);
        setHighlight(Calendar.SUNDAY,true);
    }

    /** Default color is #e2f3ff */
    public void setWeekendBackgroundColor(Color color) {
        m_weekendBackgroundColor = color;
    }
    /**
       enable/disable the highlighting for the selected day.
       Default highlighted days are saturday and sunday.
     */
    public void setHighlight(int day,boolean highlight) {
        m_highlightedDays[day] = highlight;
    }

    public boolean isHighlighted(int day) {
        return m_highlightedDays[day];
    }
    
    public RenderingInfo getRenderingInfo(int dayOfWeek, int day, int month, int year) {
        Color backgroundColor = isHighlighted(dayOfWeek) ? m_weekendBackgroundColor : null;
        Color foregroundColor = null;
        String tooltipText = null;
        RenderingInfo info = new RenderingInfo(backgroundColor, foregroundColor, tooltipText);
        return info;
    }
}
