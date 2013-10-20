 /*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
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
package org.rapla.components.util;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/** Tools for manipulating dates.
 * At the moment of writing rapla internaly stores all appointments
 * in the GMT timezone.
 */
public abstract class DateTools
{
    public static final int DAYS_PER_WEEK= 7;
    public static final long MILLISECONDS_PER_MINUTE = 1000 * 60;
    public static final long MILLISECONDS_PER_HOUR = MILLISECONDS_PER_MINUTE * 60;
    public static final long MILLISECONDS_PER_DAY = 24 * MILLISECONDS_PER_HOUR;
    public static final long MILLISECONDS_PER_WEEK = 7 * MILLISECONDS_PER_DAY;
    public static TimeZone GMT = TimeZone.getTimeZone("GMT+0");
    
    public static int getHourOfDay(long date) {
        return (int) ((date % MILLISECONDS_PER_DAY)/ MILLISECONDS_PER_HOUR);
    }

    public static int getMinuteOfHour(long date) {
        return (int) ((date % MILLISECONDS_PER_HOUR)/ MILLISECONDS_PER_MINUTE);
    }
    
	public static int getMinuteOfDay(long date) {
	     return (int) ((date % MILLISECONDS_PER_DAY)/ MILLISECONDS_PER_MINUTE);
	}


    /** sets time of day to 0:00.
        @see #cutDate(Date)
     */
    public static long cutDate(long date) {
        return (date - (date % MILLISECONDS_PER_DAY));
    }

    public static boolean isMidnight(long date) {
        return cutDate( date  ) == date ;
    }

    public static boolean isMidnight(Date date) {
        return isMidnight( date.getTime());
    }

    /** sets time of day to 0:00.
        @see #cutDate(Date)
     */
    public static void cutDate( Calendar calendar ) {
        calendar.set( Calendar.HOUR_OF_DAY, 0 );
        calendar.set( Calendar.MINUTE, 0 );
        calendar.set( Calendar.SECOND, 0 );
        calendar.set( Calendar.MILLISECOND, 0 );
    }


    /** sets time of day to 0:00. */
    public static Date cutDate(Date date) {
        return new Date(cutDate(date.getTime()));
    }

    static TimeZone timeZone =TimeZone.getTimeZone("GMT");
    /** same as TimeZone.getTimeZone("GMT"). */
    public static TimeZone getTimeZone() {
        return timeZone;
    }
    /** sets time of day to 0:00 and increases day.
        @see #fillDate(Date)
     */
    public static long fillDate(long date) {
        // cut date
        long cuttedDate = (date - (date % MILLISECONDS_PER_DAY));
        return cuttedDate +  MILLISECONDS_PER_DAY;
    }

    public static Date fillDate(Date date) {
        return new Date(fillDate(date.getTime()));
    }

    /** Monday 24:00 = tuesday 0:00.
        But the first means end of monday and the second start of tuesday.
        The default DateFormat always displays tuesday.
        If you want to edit the first interpretation in calendar components.
        call addDay() to add 1 day to the given date before displaying
        and subDay() for mapping a day back after editing.
        @see #subDay
        @see #addDays
     */
    public static Date addDay(Date date) {
        return new Date(date.getTime() + MILLISECONDS_PER_DAY);
    }

    /** see #addDay*/
    public static Date addDays(Date date,long days) {
        return new Date(date.getTime() + MILLISECONDS_PER_DAY * days);
    }

    /**
        @see #addDay
        @see #subDays
    */
    public static Date subDay(Date date) {
        return new Date(date.getTime() - MILLISECONDS_PER_DAY);
    }

    /**
        @see #addDay
    */
    public static Date subDays(Date date,int days) {
        return new Date(date.getTime() - MILLISECONDS_PER_DAY * days);
    }
    /** returns if the two dates are one the same date.
     * Dates must be in GMT */
    static public boolean isSameDay( long d1, long d2) {
        return cutDate( d1 ) == cutDate ( d2 );
    }

    /** uses the calendar-object for date comparison.
    * Use this for non GMT Dates*/
    static public boolean isSameDay( Calendar calendar, Date d1, Date d2 ) {
        calendar.setTime( d1 );
        int era1 = calendar.get( Calendar.ERA );
        int year1 = calendar.get( Calendar.YEAR );
        int day_of_year1 = calendar.get( Calendar.DAY_OF_YEAR );
        calendar.setTime( d2 );
        int era2 = calendar.get( Calendar.ERA );
        int year2 = calendar.get( Calendar.YEAR );
        int day_of_year2 = calendar.get( Calendar.DAY_OF_YEAR );
        return ( era1 == era2 && year1 == year2 && day_of_year1 == day_of_year2 );
    }

    static public long countDays(Date start,Date end) {
        return (cutDate(end.getTime()) - cutDate(start.getTime())) / MILLISECONDS_PER_DAY;
    }

    static public long countMinutes(Date start, Date end) {
    	return (end.getTime()- start.getTime())/ MILLISECONDS_PER_MINUTE;
    }
    
    static public long countMinutes(long start, long end){
    	return (end-start)/ MILLISECONDS_PER_MINUTE;
    }
    static public Calendar createGMTCalendar()
    {
        return Calendar.getInstance( GMT);
    }

    /** returns the largest date null dates count as postive infinity*/
    public static Date max(Date... param) {
 		Date max = null;
 		boolean set = false;
 		for (Date d:param)
 		{
 			if ( !set)
 			{
 				max = d;
 				set = true;
 			}
 			else if ( max != null )
 			{
 				if ( d == null || max.before( d))
 				{
 					max = d;
 				}
 			}
 			
 		}
 		return max;
    }

	public static boolean isSameDay(Date date1, Date date2) {
		if (date1 == date2)
		{
			return true;
		}
		if ( date1 == null || date2 == null)
		{
			return false;
		}
		return isSameDay(date1.getTime(), date2.getTime());
	}


}







