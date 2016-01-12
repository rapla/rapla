/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Gereon Fassbender, Christopher Kohlhaas              |
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
   For storing date-information (without daytime).
   This is only used in the test-cases.
*/

public final class Day implements Comparable<Day>
{
    int date = 0;
    int month = 0;
    int year = 0;

    public Day(int year,int month,int date) {
        this.year = year;
        this.month = month;
        this.date = date;
    }

    public int getDate() {
        return date;
    }

    public int getMonth() {
        return month;
    }

    public int getYear() {
        return year;
    }

    public String toString() {
        return getYear() + "-" + getMonth() + "-" + getDate();
    }

    public Date toDate(TimeZone zone) {
        Calendar cal = Calendar.getInstance(zone);
        cal.setTime(new Date(0));
        cal.set(Calendar.YEAR,getYear());
        cal.set(Calendar.MONTH,getMonth() - 1);
        cal.set(Calendar.DATE,getDate());
        cal.set(Calendar.HOUR_OF_DAY,0);
        cal.set(Calendar.MINUTE,0);
        cal.set(Calendar.SECOND,0);
        cal.set(Calendar.MILLISECOND,0);
        return cal.getTime();
    }
    
    public Date toGMTDate() {
        TimeZone zone = TimeZone.getTimeZone("GMT+0");
        Calendar cal = Calendar.getInstance(zone);
        cal.setTime(new Date(0));
        cal.set(Calendar.YEAR,getYear());
        cal.set(Calendar.MONTH,getMonth() - 1);
        cal.set(Calendar.DATE,getDate());
        cal.set(Calendar.HOUR_OF_DAY,0);
        cal.set(Calendar.MINUTE,0);
        cal.set(Calendar.SECOND,0);
        cal.set(Calendar.MILLISECOND,0);
        return cal.getTime();
    }
    
    public int compareTo(Day day) {
        if (getYear() < day.getYear())
            return -1;
        if (getYear() > day.getYear())
            return 1;
        if (getMonth() < day.getMonth())
            return -1;
        if (getMonth() > day.getMonth())
            return 1;
        if (getDate() < day.getDate())
            return -1;
        if (getDate() > day.getDate())
            return 1;
        return 0;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + date;
        result = prime * result + month;
        result = prime * result + year;
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
        Day other = (Day) obj;
        if (date != other.date)
            return false;
        if (month != other.month)
            return false;
        return year == other.year;
    }
}








