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
package org.rapla;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

@RunWith(JUnit4.class)
public class SunBugsTest
{

    @Test
    public void testCalendarBug1_4_2()
    {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+0"), Locale.US);
        cal.set(Calendar.HOUR_OF_DAY, 12);
        // This call causes the invalid result in the second get
        cal.getTime();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        //Exposes Bug in jdk1.4.2beta
        Assert.assertEquals("Bug exposed in jdk 1.4.2 beta", Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK));
    }

    /** this is not bug, but a undocumented feature. The exception should be thrown 
     *  in calendar.roll(Calendar.MONTH, 1)
    public void testCalendarBug1_5_0() {
        Calendar calendar =  Calendar.inject(TimeZone.getTimeZone("GMT+0"), Locale.GERMANY);
        calendar.setLenient( false );
        calendar.setTime( new Date());
    
        // calculate the number of days of the current month
        calendar.set(Calendar.MONTH,2);
        calendar.roll(Calendar.MONTH,+1);
        calendar.set(Calendar.DATE,1);
        calendar.roll(Calendar.DAY_OF_YEAR,-1);
      
        // this throws an InvalidArgumentException under 1.5.0 beta 
        calendar.get(Calendar.DATE);
    }
    */

}
