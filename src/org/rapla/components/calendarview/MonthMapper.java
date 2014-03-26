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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;


/** maps weekday names to Calendar.DAY_OF_WEEK.
    Example:
   <pre>
       WeekdayMapper mapper = new WeekdayMapper();
       // print name of Sunday
       System.out.println(mapper.getName(Calendar.SUNDAY));
       // Create a weekday ComboBox
       JComboBox comboBox = new JComboBox();
       comboBox.setModel(new DefaultComboBoxModel(mapper.getNames()));
       // select sunday
       comboBox.setSelectedIndex(mapper.getIndexForDay(Calendar.SUNDAY));
       // weekday == Calendar.SUNDAY
       int weekday = mapper.getDayForIndex(comboBox.getSelectedIndex());
   </pre>

*/
public class MonthMapper {
    String[] monthNames;
    
    public MonthMapper() {
        this(Locale.getDefault());
    }

    public MonthMapper(Locale locale) {
        monthNames = new String[12];
        SimpleDateFormat format = new SimpleDateFormat("MMMMMM",locale);
        Calendar calendar = Calendar.getInstance(locale);
        for (int i=0;i<12;i++) {
            calendar.set(Calendar.MONTH,i);
            monthNames[i] = format.format(calendar.getTime());
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

