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

import java.util.Calendar;
import java.util.Locale;

public class HTMLDateComponents {
    static public String getDateSelection(String prefix,Calendar calendarview, Locale locale) {
        StringBuffer buf = new StringBuffer();
        int day = calendarview.get(Calendar.DATE);
        int month = calendarview.get(Calendar.MONTH) +1;
        int year = calendarview.get(Calendar.YEAR);
        int minYear = 2003;
        int maxYear = 2020;
        
        buf.append( getDaySelection(prefix + "day",day));
        buf.append( getMonthSelection(prefix + "month",month, locale));
        buf.append( getYearSelection(prefix + "year", year,minYear, maxYear));
        return buf.toString();
    }
        
        static public String getDaySelection(String name, int selectedValue) {
                StringBuffer buf = new StringBuffer();
                buf.append("<select name=\""); 
                buf.append( name ); 
                buf.append("\">\n"); 
                for (int i=1;i<=31;i++) { 
                    buf.append("<option ");
                    if ( i == selectedValue) {
                        buf.append("selected");
                    }   
                    buf.append(">");
                    buf.append(i);
                    buf.append("</option>");
                    buf.append("\n");
                }
                buf.append("</select>");
                return buf.toString();
        }
        
        static public String getMonthSelection(String name, int selectedValue, Locale locale) {
            StringBuffer buf = new StringBuffer();
            buf.append("<select name=\""); 
            buf.append( name ); 
            buf.append("\">\n");
            Calendar calendar = Calendar.getInstance( locale );
            java.text.SimpleDateFormat format = 
                new java.text.SimpleDateFormat("MMMMM", locale);
            calendar.set(Calendar.MONTH,Calendar.JANUARY);
            for (int i=1;i<=12;i++) { 
                buf.append("<option ");
                buf.append("value=\"");
                buf.append(i);
                buf.append("\" ");
                if ( i == selectedValue ) {
                    buf.append("selected");
                }   
                buf.append(">");
                buf.append(format.format(calendar.getTime()));
                calendar.add(Calendar.MONTH,1);
                buf.append("</option>");
                buf.append("\n");
            }
            buf.append("</select>");
            return buf.toString();
        }
            
        static public String getYearSelection(String name, int selectedValue, int minYear, int maxYear) {
            StringBuffer buf = new StringBuffer();
            buf.append("<select name=\""); 
            buf.append( name ); 
            buf.append("\">\n"); 
            for (int i=minYear;i<=maxYear;i++) { 
                buf.append("<option ");
                if ( i == selectedValue ) {
                    buf.append("selected");
                }   
                buf.append(">");
                buf.append(i);
                buf.append("</option>");
            }
            buf.append("</select>");
            return buf.toString();
        }
}


