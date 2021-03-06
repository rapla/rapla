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

package org.rapla.plugin.abstractcalendar.server;

import org.jetbrains.annotations.NotNull;
import org.rapla.components.util.DateTools;
import org.rapla.framework.RaplaLocale;

import java.util.Date;

public class HTMLDateComponents {
    static public String getDateSelection(String prefix, Date calendarview, RaplaLocale raplaLocale) {
        StringBuffer buf = new StringBuffer();
        final DateTools.DateWithoutTimezone dateWithoutTimezone = DateTools.toDate(calendarview.getTime());
        int day = dateWithoutTimezone.day;
        int month = dateWithoutTimezone.month;
        int year = dateWithoutTimezone.year;
        int currentYear = DateTools.getYear(new Date());
        int minYear = currentYear - 8;
        int maxYear = currentYear + 8;

        buf.append(getDaySelection(prefix + "day", day));
        buf.append(getMonthSelection(prefix + "month", month, raplaLocale));
        buf.append(getYearSelection(prefix + "year", year, minYear, maxYear));
        return buf.toString();
    }

    static public String getDaySelection(String name, int selectedValue) {
        return getCountSelect( name, selectedValue, 1, 31, false );
    }

    static public String getYearSelection(String name, int selectedValue, int minYear, int maxYear) {
        return getCountSelect(name, selectedValue, minYear, maxYear, false);
    }

    @NotNull
    static public String getCountSelect(String name, int selectedValue, int minValue, int maxValue, boolean onSubmit) {
        StringBuffer buf = new StringBuffer();
        buf.append("<select name=\"");
        buf.append(name);
        buf.append("\"");
        if ( onSubmit ) {
            buf.append(" onchange=\"this.form.submit()\"");
        }
        buf.append(">\n");
        for (int i = minValue; i <= maxValue; i++) {
            buf.append("<option ");
            if (i == selectedValue) {
                buf.append("selected");
            }
            buf.append(">");
            buf.append(i);
            buf.append("</option>");
        }
        buf.append("</select>");
        return buf.toString();
    }

    static public String getMonthSelection(String name, int selectedValue, RaplaLocale locale) {
        StringBuffer buf = new StringBuffer();
        buf.append("<select name=\"");
        buf.append(name);
        buf.append("\">\n");
        Date date = new Date(DateTools.toDate(2000, 1, 1));
        for (int i = 1; i <= 12; i++) {
            buf.append("<option ");
            buf.append("value=\"");
            buf.append(i);
            buf.append("\" ");
            if (i == selectedValue) {
                buf.append("selected");
            }
            buf.append(">");
            final String str = locale.formatMonth(date);
            buf.append(str);
            date = DateTools.addMonth(date);
            buf.append("</option>");
            buf.append("\n");
        }
        buf.append("</select>");
        return buf.toString();
    }


}


