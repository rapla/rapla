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

import java.util.Calendar;
import java.util.Locale;

import junit.framework.TestCase;


public class WeekdayMapperTest  extends TestCase {
    String[] weekdayNames;
    int[] weekday2index;
    int[] index2weekday;

    public WeekdayMapperTest(String methodName) {
        super( methodName );
    }

    
    public void testLocaleGermany() {
        WeekdayMapper mapper = new WeekdayMapper(Locale.GERMANY);
        assertEquals(6,mapper.indexForDay(Calendar.SUNDAY));
        assertEquals(0,mapper.indexForDay(Calendar.MONDAY));
        assertEquals(Calendar.SUNDAY,mapper.dayForIndex(6));
        assertEquals(Calendar.MONDAY,mapper.dayForIndex(0));
        assertEquals("Montag", mapper.getName(Calendar.MONDAY)); 
    }

    public void testLocaleUS() {
        WeekdayMapper mapper = new WeekdayMapper(Locale.US);
        assertEquals(0,mapper.indexForDay(Calendar.SUNDAY));
        assertEquals(1,mapper.indexForDay(Calendar.MONDAY));
        assertEquals(Calendar.MONDAY,mapper.dayForIndex(1));
        assertEquals("Monday", mapper.getName(Calendar.MONDAY)); 
    }
    
}

