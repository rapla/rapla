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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Calendar;
import java.util.Locale;

@RunWith(JUnit4.class)
public class WeekdayMapperTest   {
    String[] weekdayNames;
    int[] weekday2index;
    int[] index2weekday;



    @Test
    public void testLocaleGermany() {
        WeekdayMapper mapper = new WeekdayMapper(Locale.GERMANY);
        Assert.assertEquals(6, mapper.indexForDay(Calendar.SUNDAY));
        Assert.assertEquals(0, mapper.indexForDay(Calendar.MONDAY));
        Assert.assertEquals(Calendar.SUNDAY, mapper.dayForIndex(6));
        Assert.assertEquals(Calendar.MONDAY, mapper.dayForIndex(0));
        Assert.assertEquals("Montag", mapper.getName(Calendar.MONDAY));
    }

    @Test
    public void testLocaleUS() {
        WeekdayMapper mapper = new WeekdayMapper(Locale.US);
        Assert.assertEquals(0, mapper.indexForDay(Calendar.SUNDAY));
        Assert.assertEquals(1, mapper.indexForDay(Calendar.MONDAY));
        Assert.assertEquals(Calendar.MONDAY, mapper.dayForIndex(1));
        Assert.assertEquals("Monday", mapper.getName(Calendar.MONDAY));
    }
    
}

