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

import java.util.Date;
import java.util.Locale;
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
    public static final int SUNDAY = 1, MONDAY = 2, TUESDAY = 3, WEDNESDAY = 4, THURSDAY = 5, FRIDAY = 6, SATURDAY = 7;
    
    public static int getHourOfDay(long date) {
        return (int) ((date % MILLISECONDS_PER_DAY)/ MILLISECONDS_PER_HOUR);
    }

    public static int getMinuteOfHour(long date) {
        return (int) ((date % MILLISECONDS_PER_HOUR)/ MILLISECONDS_PER_MINUTE);
    }
    
	public static int getMinuteOfDay(long date) {
	     return (int) ((date % MILLISECONDS_PER_DAY)/ MILLISECONDS_PER_MINUTE);
	}
	
	public static String formatDate(Date date)
	{
		SerializableDateTimeFormat format = new SerializableDateTimeFormat();
        String string = format.formatDate( date);
        return string;
	}
	
	public static String formatDate(Date date, @SuppressWarnings("unused") Locale locale) {
		// FIXME has to be replaced with locale implementation
//		DateFormat format = DateFormat.getDateInstance(DateFormat.SHORT,locale);
//        format.setTimeZone(DateTools.getTimeZone());
//        String string = format.format( date);
//        return string;
		return formatDate(date);
	}
	
	public static String formatTime(Date date)
	{
		SerializableDateTimeFormat format = new SerializableDateTimeFormat();
        String string = format.formatTime( date);
        return string;
	}
	
	public static String formatDateTime(Date date)
	{
		SerializableDateTimeFormat format = new SerializableDateTimeFormat();
		String dateString = format.formatDate( date);
		String timeString = format.formatTime( date);
        String string = dateString + " " + timeString; 
        return string;
	}

	public static int getDaysInMonth(int year, int month)
    {
		int _month = month+1; 
        if ( _month == 2)
        {
            if ( isLeapYear(year))
            {
            	return 29;
            }
        	return 28;
        }
        else if ( _month  == 4 || _month == 6 || _month == 9 || _month == 11 )
        {
        	return 30;
        }
        else
        {
        	return 31;
        }
    }

	public static boolean isLeapYear(int year) 
	{
		return year % 4 == 0 && ((year % 100) != 0 || (year % 400) == 0);
	} 
	
    /** sets time of day to 0:00.
        @see #cutDate(Date)
     */
    public static long cutDate(long date) {
    	long dateModMillis = date % MILLISECONDS_PER_DAY;
    	if ( dateModMillis == 0)
    	{
    		return date;
    	}
		if ( date >= 0)
    	{
    		return (date - dateModMillis);
    	}
    	else
    	{
    		return (date - (MILLISECONDS_PER_DAY + dateModMillis));
    	    		
    	}
    }

    public static boolean isMidnight(long date) {
        return cutDate( date  ) == date ;
    }

    public static boolean isMidnight(Date date) {
        return isMidnight( date.getTime());
    }

    /** sets time of day to 0:00. */
    public static Date cutDate(Date date) {
        long time = date.getTime();
        if ( time %MILLISECONDS_PER_DAY == 0)
        {
        	return date;
        }
		return new Date(cutDate(time));
    }

    private static TimeZone timeZone =TimeZone.getTimeZone("GMT");
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
        return addDays( date, 1);
    }
    
    public static Date addYear(Date date) {
    	return addYears(date, 1);
    }
    
    public static Date addWeeks(Date date, int weeks) {
    	Date result = new Date(date.getTime() + MILLISECONDS_PER_WEEK * weeks);
		return result;
    }
    
    public static Date addYears(Date date, int yearModifier) {
    
    	int monthModifier = 0;
    	return modifyDate(date, yearModifier, monthModifier);
    }
    
    public static Date addMonth(Date startDate) {
    	return addMonths(startDate, 1);
    }
    
    public static Date addMonths(Date startDate, int monthModifier) {
    	int yearModifier = 0;
    	return modifyDate(startDate, yearModifier, monthModifier);
    }

	private static Date modifyDate(Date startDate, int yearModifier,
			int monthModifier) {
		long original = startDate.getTime();
    	long millis = original  - DateTools.cutDate( original );
		DateWithoutTimezone date = toDate( original);
    	int year = date.year + yearModifier;
    	int month = date.month + monthModifier;
    	if ( month < 1 )
    	{
    		year += month/ 12 -1 ;
    		month = ((month +11) % 12) + 1;
    	    
    	}
    	if ( month >12 )
    	{
    		year += month/ 12;
    		month = ((month -1) % 12) + 1;
    	}
    	int day = date.day ;
		long newDate = toDate(year, month, day);
    	Date result = new Date( newDate + millis);
		return result;
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
    static public boolean isSameDay( Date d1, Date d2) {
        return cutDate( d1 ).equals( cutDate ( d2 ));
    }

    /** returns if the two dates are one the same date.
     * Dates must be in GMT */
    static public boolean isSameDay( long d1, long d2) {
        return cutDate( d1 ) == cutDate ( d2 );
    }

    /** returns the day of week SUNDAY = 1, MONDAY = 2, TUESDAY = 3, WEDNESDAY = 4, THURSDAY = 5, FRIDAY = 6, SATURDAY = 7 */
    public static int getWeekday(Date date) {
    	long days = countDays(0,date.getTime());
    	int weekday_zero = THURSDAY;
    	int alt = (int) days%7;
    	int weekday = weekday_zero + alt;
    	if ( weekday > 7)
    	{
    		weekday -=7;
    	}
    	else if ( weekday <=0 )
    	{
    		weekday += 7 ;
    	}
    	return weekday;
    }

    public static int getDayOfWeekInMonth(Date date) 
    {
    	DateWithoutTimezone date2 = toDate( date.getTime());
    	int day = date2.day;
    	int occurances = (day-1) / 7 + 1;
    	return occurances;
    }

//    public static int getWeekOfYear(Date date) 
//    {
//    	// 1970/1/1 is a thursday
//    	long millis = date.getTime();
//		long daysSince1970 = millis >= 0 ? millis/ MILLISECONDS_PER_DAY : ((millis + MILLISECONDS_PER_DAY - 1)/ MILLISECONDS_PER_DAY  + 1) ;
//        int weekday = daysSince1970 + 4;	
//    	
//    }

    
    public static int getDayOfMonth(Date date) {
    	DateWithoutTimezone date2 = toDate( date.getTime());
    	int result = date2.day;
    	return result;
    }

//    /** uses the calendar-object for date comparison.
//    * Use this for non GMT Dates*/
//    static public boolean isSameDay( Calendar calendar, Date d1, Date d2 ) {
//        calendar.setTime( d1 );
//        int era1 = calendar.get( Calendar.ERA );
//        int year1 = calendar.get( Calendar.YEAR );
//        int day_of_year1 = calendar.get( Calendar.DAY_OF_YEAR );
//        calendar.setTime( d2 );
//        int era2 = calendar.get( Calendar.ERA );
//        int year2 = calendar.get( Calendar.YEAR );
//        int day_of_year2 = calendar.get( Calendar.DAY_OF_YEAR );
//        return ( era1 == era2 && year1 == year2 && day_of_year1 == day_of_year2 );
//    }

    static public long countDays(Date start,Date end) {
        return countDays(start.getTime(), end.getTime());
    }
    
    static public long countDays(long start,long end) {
        return (cutDate(end) - cutDate(start)) / MILLISECONDS_PER_DAY;
    }

    static public long countMinutes(Date start, Date end) {
    	return (end.getTime()- start.getTime())/ MILLISECONDS_PER_MINUTE;
    }
    
    static public long countMinutes(long start, long end){
    	return (end-start)/ MILLISECONDS_PER_MINUTE;
    }
//    static public Calendar createGMTCalendar()
//    {
//        return Calendar.getInstance( GMT);
//    }

    static int date_1970_1_1 = calculateJulianDayNumberAtNoon(1970, 1, 1); 
    /**
    Return a the whole number, with no fraction.
    The JD at noon is 1 more than the JD at midnight. 
    */
   private static int calculateJulianDayNumberAtNoon(int y, int m, int d) {
     //http://www.hermetic.ch/cal_stud/jdn.htm
     int result = (1461 * (y + 4800 + (m - 14) / 12)) / 4 + (367 * (m - 2 - 12 * ((m - 14) / 12))) / 12 - (3 * ((y + 4900 + (m - 14) / 12) / 100)) / 4 + d - 32075;
     return result;
   }
   
   /**
    * 
    * @param year
    * @param month ranges from 1-12
    * @param day
    * @return
    */
   public static long toDate(int year, int month, int day )
   {
	   int days = calculateJulianDayNumberAtNoon(year, month, day);
	   int diff = days - date_1970_1_1;
	   long millis = diff * MILLISECONDS_PER_DAY;
	   return millis;
   }
   
   public static Date toDateTime(Date date, Date time )
   {
       long millisTime = time.getTime() - DateTools.cutDate( time.getTime());
       Date result = new Date( DateTools.cutDate(date.getTime()) + millisTime);
       return result;
   }
   
   public static class DateWithoutTimezone
   {
	   public int year;
	   public int month;
	   public int day;
	   public String toString()
	   {
		   return year+"-" +month + "-" + day;
	   }
   }
   
   public static class TimeWithoutTimezone
   {
	   public int hour;
	   public int minute;
	   public int second;
	   public int milliseconds;
	   public String toString()
	   {
		   return hour+":" +minute + ":" + second + "." + milliseconds;
	   }
   }
   
   public static TimeWithoutTimezone toTime(long millis)
   {
	   long millisInDay = millis - DateTools.cutDate( millis);
	   TimeWithoutTimezone result = new TimeWithoutTimezone();
	   result.hour = (int) (millisInDay / MILLISECONDS_PER_HOUR); 
	   result.minute = (int) ((millisInDay % MILLISECONDS_PER_HOUR) / MILLISECONDS_PER_MINUTE); 
	   result.second = (int) ((millisInDay % MILLISECONDS_PER_MINUTE) / 1000);
	   result.milliseconds = (int) (millisInDay % 1000 );
	   return result;
   }
   
   public static long toTime(int hour, int minute, int second) {
	   return toTime(hour, minute, second, 0);
	}
   
   public static long toTime(int hour, int minute, int second, int millisecond) {
	   long millis = hour * MILLISECONDS_PER_HOUR;
	   millis += minute * MILLISECONDS_PER_MINUTE;
	   millis += second * 1000;
	   millis += millisecond;
	   return millis;
	}
   
   public static int toHour(long millisecond) {
       long result = (millisecond % MILLISECONDS_PER_DAY - millisecond % MILLISECONDS_PER_HOUR)/MILLISECONDS_PER_HOUR;
       return (int) result;
   }

   
   public static DateWithoutTimezone toDate(long millis)
   {
	   // special case for negative milliseconds as day rounding needs to get the lower day
	   int day = millis >= 0 ? (int) (millis/ MILLISECONDS_PER_DAY) : (int) (( millis + MILLISECONDS_PER_DAY -1) / MILLISECONDS_PER_DAY); 
	   int julianDateAtNoon = day +  date_1970_1_1;
	   DateWithoutTimezone result = fromJulianDayNumberAtNoon( julianDateAtNoon);
	   return result;
   }
   
   private static DateWithoutTimezone fromJulianDayNumberAtNoon(int julianDateAtNoon) 
   {
	    //http://www.hermetic.ch/cal_stud/jdn.htm
	    int l = julianDateAtNoon + 68569;
	    int n = (4 * l) / 146097;
	    l = l - (146097 * n + 3) / 4;
	    int i = (4000 * (l + 1)) / 1461001;
	    l = l - (1461 * i) / 4 + 31;
	    int j = (80 * l) / 2447;
	    int d = l - (2447 * j) / 80;
	    l = j / 11;
	    int m = j + 2 - (12 * l);
	    int y = 100 * (n - 49) + i + l;
	    DateWithoutTimezone dt = new DateWithoutTimezone();
	    dt.year = y;
	    dt.month = m;
	    dt.day = d;
	    return dt;
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




}







