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
package org.rapla.entities.tests;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
/**
   For storing time-information (without dates).
   This is only used in the test-cases.
*/
public final class Time implements Comparable<Time>
{
    public final static int MILLISECONDS_PER_SECOND = 1000;
    public final static int MILLISECONDS_PER_MINUTE = 60 * 1000;
    public final static int MILLISECONDS_PER_HOUR =  60 * 60 * 1000  ;
    public final static int MILLISECONDS_PER_DAY = 24 * 60 * 60 * 1000 ;
    int millis = 0;

    public Time(int millis) {
        this.millis = millis;
    }

    public Time(Calendar cal) {
        this.millis = calculateMillis(cal.get(Calendar.HOUR_OF_DAY)
                                      ,cal.get(Calendar.MINUTE)
                                      ,cal.get(Calendar.SECOND)
                                      ,cal.get(Calendar.MILLISECOND));
    }

    public Time(int hour,int minute) {
        int second = 0;
        int millis = 0;
        this.millis = calculateMillis(hour,minute,second,millis);
    }

    public static Calendar time2calendar(long time,TimeZone zone)
    {
        Calendar c = Calendar.getInstance(zone);
        c.setTime(new Date(time));
        return c;
    }


    public static int date2daytime(Date d,TimeZone zone)
    {
        Calendar c = Calendar.getInstance(zone);
        c.setTime(d);
        return calendar2daytime(c);
    }


    //* @return Time in milliseconds
    public static int calendar2daytime(Calendar cal)
    {
        return Time.calculateMillis(cal.get(Calendar.HOUR_OF_DAY)
                                    ,cal.get(Calendar.MINUTE)
                                    ,cal.get(Calendar.SECOND)
                                    ,cal.get(Calendar.MILLISECOND));
    }

    private static int calculateMillis(int hour,int minute,int second,int millis) {
        return ((hour * 60 + minute) * 60  + second) * 1000 + millis;
    }

    public int getHour() {
        return millis / MILLISECONDS_PER_HOUR;
    }

    public int getMillis() {
        return millis;
    }

    public int getMinute() {
       return (millis % MILLISECONDS_PER_HOUR) / MILLISECONDS_PER_MINUTE;
    }

    public int getSecond() {
       return (millis % MILLISECONDS_PER_MINUTE) / MILLISECONDS_PER_SECOND;
    }

    public int getMillisecond() {
       return (millis % MILLISECONDS_PER_SECOND);
    }

    public String toString() {
        return getHour() + ":" + getMinute();
    }

    public Date toDate(TimeZone zone) {
        Calendar cal = Calendar.getInstance(zone);
        cal.setTime(new Date(0));
        cal.set(Calendar.HOUR_OF_DAY,getHour());
        cal.set(Calendar.MINUTE,getMinute());
        cal.set(Calendar.SECOND,getSecond());
        return cal.getTime();
    }

    public int compareTo(Time time2) {
        if (getMillis() < time2.getMillis())
            return -1;
        if (getMillis() > time2.getMillis())
            return 1;
        return 0;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + millis;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Time other = (Time) obj;
        if (millis != other.millis)
            return false;
        return true;
    }
}








