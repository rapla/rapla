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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TimeTest extends TestCase {

    public TimeTest(String name) {
        super(name);
    }

    public static Test suite() {
	return new TestSuite(TimeTest.class);
    }

    protected void setUp() {
    }

    public void testTime() {
        Time time = new Time(0,1);
        assertTrue(time.getMillis() == Time.MILLISECONDS_PER_MINUTE);
        Time time1 = new Time( 2 * Time.MILLISECONDS_PER_HOUR  + 15 * Time.MILLISECONDS_PER_MINUTE);
        assertTrue(time1.getHour() == 2);
        assertTrue(time1.getMinute() == 15);
        Time time2 = new Time(23,15);
        Time time3 = new Time(2,15);
        assertTrue(time1.compareTo(time2) == -1); 
        assertTrue(time2.compareTo(time1) == 1); 
        assertTrue(time1.compareTo(time3) == 0); 
    }
}





