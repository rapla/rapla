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
package org.rapla.entities.tests;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TimeTest  {

    @Test
    public void testTime() {
        Time time = new Time(0,1);
        Assert.assertTrue(time.getMillis() == Time.MILLISECONDS_PER_MINUTE);
        Time time1 = new Time( 2 * Time.MILLISECONDS_PER_HOUR  + 15 * Time.MILLISECONDS_PER_MINUTE);
        Assert.assertTrue(time1.getHour() == 2);
        Assert.assertTrue(time1.getMinute() == 15);
        Time time2 = new Time(23,15);
        Time time3 = new Time(2,15);
        Assert.assertTrue(time1.compareTo(time2) == -1);
        Assert.assertTrue(time2.compareTo(time1) == 1);
        Assert.assertTrue(time1.compareTo(time3) == 0);
    }
}





