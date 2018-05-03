 /*--------------------------------------------------------------------------*
 | Copyright (C) 2017 Christopher Kohlhaas               |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copyReservations of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.components.util;

import java.util.Date;
import java.util.Locale;

/** Tools for manipulating dates.
 * At the moment of writing rapla internaly stores all appointments
 * in the GMT timezone.
 */
public abstract class DateTools
{
    public static Date setWeekday(Date date, int selectedWeekday)
    {
        final int weekday = DateTools.getWeekday(date);
        int diff = selectedWeekday -weekday;
        final Date result = DateTools.addDays(date, diff);
        return result;
    }

    public enum IncrementSize
    {
        MONTH(2),
        DAY_OF_YEAR(6),
        WEEK_OF_YEAR(3);
        IncrementSize(int numValue)
        {
            this.numValue = numValue;
        }
        final int numValue;
    }
    public static final int DAYS_PER_WEEK= 7;
    public static final long MILLISECONDS_PER_MINUTE = 1000 * 60;
    public static final long MILLISECONDS_PER_HOUR = MILLISECONDS_PER_MINUTE * 60;
    public static final long MILLISECONDS_PER_DAY = 24 * MILLISECONDS_PER_HOUR;
    public static final long MILLISECONDS_PER_WEEK = 7 * MILLISECONDS_PER_DAY;
    public static final int SUNDAY = 1, MONDAY = 2, TUESDAY = 3, WEDNESDAY = 4, THURSDAY = 5, FRIDAY = 6, SATURDAY = 7;
    
    private static final String[] US_WEEKDAY_COUNTRY_CODES = new String[] { "CA", "US", "MX" };
    
    public static int getHourOfDay(long date) {
        return (int) ((date % MILLISECONDS_PER_DAY)/ MILLISECONDS_PER_HOUR);
    }

    public static int getMinuteOfHour(long date) {
        return (int) ((date % MILLISECONDS_PER_HOUR)/ MILLISECONDS_PER_MINUTE);
    }

    public static int getSecondOfMinute(long date) {
        return (int) ((date % MILLISECONDS_PER_MINUTE)/ 1000);
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
	
	public static String formatTime(Date date)
	{
        String string = SerializableDateTimeFormat.INSTANCE.formatTime( date);
        return string;
	}
	
	public static String formatDateTime(Date date)
	{
        return formatDateTime(date, null);
	}
	
	public static String formatDateTime(Date date, Locale locale)
	{
		SerializableDateTimeFormat format = SerializableDateTimeFormat.INSTANCE;
		String dateString = format.formatDate( date);
		String timeString = format.formatTime( date);
        String string = dateString + " " + timeString; 
        return string;
	}

	public static int getDaysInMonth(Date date)
	{
	    DateWithoutTimezone date2 = toDate(date.getTime());
	    return getDaysInMonth( date2.year, date2.month);
	}
	
	public static int getDaysInMonth(final int year, final int month)
    {
        if ( month == 2)
        {
            if ( isLeapYear(year))
            {
            	return 29;
            }
        	return 28;
        }
        else if ( month  == 4 || month == 6 || month == 9 || month == 11 )
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
        return addDays(date, 1);
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
    	return getWeekday( days);
    }
    
    public static int getWeekday(DateWithoutTimezone date) {
        long days = calculateJulianDayNumberAtNoon( date) - date_1970_1_1;
        return getWeekday( days);
    }

    private static int getWeekday(long daysSince19700101) {
        int weekday_zero = THURSDAY;
        int alt = (int) daysSince19700101%7;
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

    public static int getYear(Date date) {
        DateWithoutTimezone date2 = toDate( date.getTime());
        return date2.year;
    }

    public static int getMonth(Date date) {
        DateWithoutTimezone date2 = toDate( date.getTime());
        return date2.month;
    }

    public static int getDayOfMonth(Date date) {
        DateWithoutTimezone date2 = toDate( date.getTime());
        int result = date2.day;
        return result;
    }

    /** calculates how often the weekday of the passed date occured. e.g. if you pass a date thats on monday it returns 1 if its the first monday in the month and 3 if its the third monday*/
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

    private static int calculateJulianDayNumberAtNoon(DateWithoutTimezone dateWithoutTimezone) {
        int day = calculateJulianDayNumberAtNoon(dateWithoutTimezone.year,dateWithoutTimezone.month,dateWithoutTimezone.day);
        return day;
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


    public static int getWeekInYearIso(Date date)
    {
        DateWithoutTimezone dateWithoutTimezone = toDate(date.getTime());
        DateWithoutTimezone thursdayInWeek = thursdayInWeekISO(dateWithoutTimezone);
        int calendarweekInYear = thursdayInWeek.year;
        DateWithoutTimezone fourthOfJanuary = new DateWithoutTimezone();
        fourthOfJanuary.year = calendarweekInYear;
        fourthOfJanuary.month = 1;
        fourthOfJanuary.day = 4;
        DateWithoutTimezone firstThursdayInYear = thursdayInWeekISO(fourthOfJanuary);
        int calendarweek = (calculateJulianDayNumberAtNoon(thursdayInWeek)- calculateJulianDayNumberAtNoon(firstThursdayInYear))/7+1;
        return calendarweek;
    }

    public static int getWeekInYear(Date date, Locale locale)
    {
        if(isUsStyle(DateTools.getCountry(locale)))
        {
            return getWeekInYearUs(date);
        }
        return getWeekInYearIso(date);
    }

    private static boolean isUsStyle(String country)
    {
        for (String countryCode : US_WEEKDAY_COUNTRY_CODES)
        {
            if (countryCode.equalsIgnoreCase(country))
            {
                return true;
            }
        }
        return false;
    }

    public static int getWeekInYearUs(Date date)
    {
        DateWithoutTimezone dateWithoutTimezone = toDate(date.getTime());
        DateWithoutTimezone sundayInWeek = sundayInWeekUs(dateWithoutTimezone);
        DateWithoutTimezone sixthOfJanuary = new DateWithoutTimezone();
        sixthOfJanuary.year = dateWithoutTimezone.year;
        sixthOfJanuary.month = 1;
        sixthOfJanuary.day = 6;
        DateWithoutTimezone firstSundayInYear = sundayInWeekUs(sixthOfJanuary);
        int calendarweek = (calculateJulianDayNumberAtNoon(sundayInWeek) - calculateJulianDayNumberAtNoon(firstSundayInYear)) / 7 + 1;
        DateWithoutTimezone firstOfJanuary = new DateWithoutTimezone();
        firstOfJanuary.year = dateWithoutTimezone.year;
        firstOfJanuary.month = 1;
        firstOfJanuary.day = 1;
        if (getWeekday(firstOfJanuary) != SUNDAY)
        {
            calendarweek++;
        }
        return calendarweek;
    }

    public static int getDayInYear(Date date)
    {
        DateWithoutTimezone dateWithoutTimezone = toDate(date.getTime());
        DateWithoutTimezone firstOfJanuary = new DateWithoutTimezone();
        firstOfJanuary.year = dateWithoutTimezone.year;
        firstOfJanuary.month = 1;
        firstOfJanuary.day = 1;
        int dayOfYear = calculateJulianDayNumberAtNoon( dateWithoutTimezone)- calculateJulianDayNumberAtNoon(firstOfJanuary) + 1;
        return dayOfYear;
    }

    private static DateWithoutTimezone thursdayInWeekISO(DateWithoutTimezone dateWithoutTimezone) {
        int newJulienDate = mondayInWeekJulianDay(dateWithoutTimezone) + 3;
        return fromJulianDayNumberAtNoon( newJulienDate);
    }

    private static DateWithoutTimezone mondayInWeekISO(DateWithoutTimezone dateWithoutTimezone) {
        int newJulienDate = mondayInWeekJulianDay(dateWithoutTimezone) ;
        return fromJulianDayNumberAtNoon( newJulienDate);
    }

    private static DateWithoutTimezone sundayInWeekUs(DateWithoutTimezone dateWithoutTimezone) {
        int julianDate = calculateJulianDayNumberAtNoon(dateWithoutTimezone.year, dateWithoutTimezone.month, dateWithoutTimezone.day);
        int day = getWeekday(dateWithoutTimezone) - 1;
        int newJulienDate = julianDate - day;
        return fromJulianDayNumberAtNoon(newJulienDate);
    }

    private static int mondayInWeekJulianDay(DateWithoutTimezone dateWithoutTimezone) {

        int julianDate = calculateJulianDayNumberAtNoon(dateWithoutTimezone.year, dateWithoutTimezone.month, dateWithoutTimezone.day);
        // convert from weekday format SUNDAY =1, ... to MONDAY = 0, ...;
        int day = getWeekday( dateWithoutTimezone) -2;
        if ( day <0)
        {
            day +=7;
        }
        int newJulienDate = julianDate - day;
        return newJulienDate;
    }

    public static String getLang(Locale locale) {
        String localeString = locale.toString();
        String[] parts = localeString.split("_");
        if (parts.length == 0) {
            throw new IllegalStateException("Locale split length can't be 0");
        }
        return parts[0];
    }

    public static String getCountry(Locale locale)
    {
        String localeString = locale.toString();
        String[] parts = localeString.split("_");
        if ( parts.length < 2)
        {
            return "";
        }
        return parts[1];
    }

    public static Date add(Date date, DateTools.IncrementSize incrementSize, int incrementAmount)
    {
        switch ( incrementSize)
        {
            case DAY_OF_YEAR:return addDays( date, incrementAmount);
            case MONTH:return addMonths( date, incrementAmount);
            case WEEK_OF_YEAR:return addWeeks( date, incrementAmount);
            default:throw new IllegalArgumentException("unssuported incrementsize");
        }
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
	   
	   public long getMilliseconds()
	   {
	       return hour * MILLISECONDS_PER_HOUR + minute * MILLISECONDS_PER_MINUTE + second * 1000 + milliseconds;
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



   public static Date getFirstWeekday(Date date, int firstWeekday)
   {
       int weekday = getWeekday( date);
       int diff =  firstWeekday- weekday;
       if ( diff >0)
       {
           diff -= 7;
       }
       Date result = DateTools.addDays( date, diff);
//       
//       calendar.setTime( startDate );
//       calendar.set( Calendar.DAY_OF_WEEK, firstWeekday );
//       getWeekday(date)
       return result;
       
   }




}







