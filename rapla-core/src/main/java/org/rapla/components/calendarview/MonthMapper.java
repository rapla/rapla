/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas, Bettina Lademann                |
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
package org.rapla.components.calendarview;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;


/** Maps month index (0-11) to its localised display name. */
public class MonthMapper {
    String[] monthNames;

    public MonthMapper() {
        this(Locale.getDefault());
    }

    public MonthMapper(Locale locale) {
        monthNames = new String[12];
        for (int i = 0; i < 12; i++) {
            // Month enum is 1-based (JANUARY = 1); legacy MonthMapper API is 0-based.
            monthNames[i] = Month.of(i + 1).getDisplayName(TextStyle.FULL, locale);
        }
    }

    public String[] getNames() {
        return monthNames;
    }

    /** month are 0 based */
    public String getName(int month) {
        return getNames()[month];
    }


}
