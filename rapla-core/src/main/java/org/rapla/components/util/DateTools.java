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

import java.time.*;
import java.util.Locale;
/** Tools for manipulating dates.
 * At the moment of writing rapla internaly stores all appointments
 * in the GMT timezone.
 */
public abstract class DateTools
{
    public static String DEFAULT_GWT_LOCALE = "en_UK";
    public static LocalDateTime setWeekday(LocalDateTime dateTime, int selectedWeekday)
    {
        final int weekday = DateTools.getWeekday(dateTime);
        int diff = selectedWeekday - weekday;
        return dateTime.plusDays(diff);
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
    public static final int SUNDAY = 1, MONDAY = 2, TUESDAY = 3, WEDNESDAY = 4, THURSDAY = 5, FRIDAY = 6, SATURDAY = 7, CURRENT_WEEKDAY = 0;
    
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

    public static DayOfWeek mapRaplaToDateAPI(int weekday) {
        switch (weekday) {
            case DateTools.SUNDAY: {
                return DayOfWeek.SUNDAY;
            }
            case DateTools.MONDAY: {
                return DayOfWeek.MONDAY;
            }
            case DateTools.TUESDAY: {
                return DayOfWeek.TUESDAY;
            }
            case DateTools.WEDNESDAY: {
                return DayOfWeek.WEDNESDAY;
            }
            case DateTools.THURSDAY: {
                return  DayOfWeek.THURSDAY;
            }
            case DateTools.FRIDAY: {
                return   DayOfWeek.FRIDAY;
            }
            case  DateTools.SATURDAY: {
                return   DayOfWeek.SATURDAY;
            }
        }
        throw new IllegalArgumentException("Invalid weekday: " + weekday);
    }

    public static int mapDateAPIToRapla(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case SUNDAY: {
                return DateTools.SUNDAY;
            }
            case MONDAY: {
                return DateTools.MONDAY;
            }
            case TUESDAY: {
                return DateTools.TUESDAY;
            }
            case WEDNESDAY: {
                return DateTools.WEDNESDAY;
            }
            case THURSDAY: {
                return  DateTools.THURSDAY;
            }
            case FRIDAY: {
                return   DateTools.FRIDAY;
            }
            case  SATURDAY: {
                return   DateTools.SATURDAY;
            }
        }
        throw new IllegalArgumentException("Invalid weekday: " + dayOfWeek);
    }

    public static String formatDate(LocalDateTime dateTime)
	{
        return SerializableDateTimeFormat.INSTANCE.formatDate(dateTime);
	}

	/** {@code long}-millis variant. UTC. */
	public static String formatDate(long millis)
	{
        return formatDate(toLocalDateTime(millis));
	}

	public static String formatTime(LocalDateTime dateTime)
	{
        return SerializableDateTimeFormat.INSTANCE.formatTime(dateTime);
	}

	/** {@code long}-millis variant. UTC. */
	public static String formatTime(long timeInMillis)
	{
        return SerializableDateTimeFormat.INSTANCE.formatTime(toLocalDateTime(timeInMillis));
	}

    public static LocalDateTime toLocalDateTime(long dateTimeInMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(dateTimeInMillis), ZoneOffset.UTC);
    }

    /** Inverse of {@link #toLocalDateTime(long)}. UTC. Preserves millisecond precision. */
    public static long toMilli(LocalDateTime dateTime) {
        return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /** UTC midnight epoch milliseconds for the given local date. */
    public static long toMilli(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    public static int getHourOfDay(LocalDateTime dateTime) {
        return dateTime.getHour();
    }

    public static int getHourOfDay(LocalTime time) {
        return time.getHour();
    }

    public static int getMinuteOfHour(LocalDateTime dateTime) {
        return dateTime.getMinute();
    }

    public static int getMinuteOfHour(LocalTime time) {
        return time.getMinute();
    }

    public static int getSecondOfMinute(LocalDateTime dateTime) {
        return dateTime.getSecond();
    }

    public static int getMinuteOfDay(LocalDateTime dateTime) {
        return dateTime.getHour() * 60 + dateTime.getMinute();
    }

    /** Truncates the time-of-day component (returns midnight of the same day). */
    public static LocalDateTime cutDate(LocalDateTime dateTime) {
        return dateTime.toLocalDate().atStartOfDay();
    }

    /** Same as {@link #cutDate(LocalDateTime)} but for {@code LocalDate}: identity. */
    public static LocalDate cutDate(LocalDate date) {
        return date;
    }

    public static boolean isMidnight(LocalDateTime dateTime) {
        return dateTime.toLocalTime().equals(LocalTime.MIDNIGHT);
    }

    /** Adds {@code days} days to the date. */
    public static LocalDateTime addDays(LocalDateTime dateTime, long days) {
        return dateTime.plusDays(days);
    }

    public static LocalDate addDays(LocalDate date, long days) {
        return date.plusDays(days);
    }

    public static LocalDateTime addDay(LocalDateTime dateTime) {
        return dateTime.plusDays(1);
    }

    public static LocalDate addDay(LocalDate date) {
        return date.plusDays(1);
    }

    public static LocalDateTime subDay(LocalDateTime dateTime) {
        return dateTime.minusDays(1);
    }

    public static LocalDate subDay(LocalDate date) {
        return date.minusDays(1);
    }

    public static LocalDateTime subDays(LocalDateTime dateTime, int days) {
        return dateTime.minusDays(days);
    }

    public static LocalDate subDays(LocalDate date, int days) {
        return date.minusDays(days);
    }

    public static long countDays(LocalDate from, LocalDate to) {
        return java.time.temporal.ChronoUnit.DAYS.between(from, to);
    }

    public static long countDays(LocalDateTime from, LocalDateTime to) {
        return java.time.temporal.ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate());
    }

    public static String formatDateTime(LocalDateTime dateTime)
	{
		SerializableDateTimeFormat format = SerializableDateTimeFormat.INSTANCE;
		return format.formatDate(dateTime) + " " + format.formatTime(dateTime);
	}

	public static String formatDateTime(long millis)
	{
        return formatDateTime(toLocalDateTime(millis));
	}

	public static int getDaysInMonth(LocalDateTime dateTime)
	{
	    return getDaysInMonth(dateTime.getYear(), dateTime.getMonthValue());
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
    /** sets time of day to 0:00. */
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

    /** sets time of day to 0:00 and increases day. */
    public static long fillDate(long date) {
        // cut date
        long cuttedDate = (date - (date % MILLISECONDS_PER_DAY));
        return cuttedDate +  MILLISECONDS_PER_DAY;
    }

    /** Rounds up to next-day midnight. UTC. */
    public static LocalDateTime fillDate(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return toLocalDateTime(fillDate(toMilli(dateTime)));
    }

    public static LocalDateTime addYear(LocalDateTime dateTime) {
    	return dateTime.plusYears(1);
    }

    public static LocalDateTime addWeeks(LocalDateTime dateTime, int weeks) {
		return dateTime.plusWeeks(weeks);
    }

    public static LocalDateTime addYears(LocalDateTime dateTime, int yearModifier) {
    	return dateTime.plusYears(yearModifier);
    }

    public static LocalDateTime addMonth(LocalDateTime startDate) {
    	return startDate.plusMonths(1);
    }

    public static LocalDateTime addMonths(LocalDateTime startDate, int monthModifier) {
    	return startDate.plusMonths(monthModifier);
    }


    /** returns if the two dates are on the same date. UTC. */
    static public boolean isSameDay( LocalDateTime d1, LocalDateTime d2) {
        return d1.toLocalDate().equals(d2.toLocalDate());
    }

    /** Mixed-type overloads — common when {@code today()} returns LocalDate
     *  but the comparand is a LocalDateTime (or vice versa). */
    static public boolean isSameDay( LocalDate d1, LocalDateTime d2) {
        return d1.equals(d2.toLocalDate());
    }

    static public boolean isSameDay( LocalDateTime d1, LocalDate d2) {
        return d1.toLocalDate().equals(d2);
    }

    static public boolean isSameDay( LocalDate d1, LocalDate d2) {
        return d1.equals(d2);
    }

    /** returns if the two dates are on the same date. */
    static public boolean isSameDay( long d1, long d2) {
        return cutDate( d1 ) == cutDate ( d2 );
    }

    /** returns the day of week SUNDAY = 1, MONDAY = 2, TUESDAY = 3, WEDNESDAY = 4, THURSDAY = 5, FRIDAY = 6, SATURDAY = 7 */
    public static int getWeekday(LocalDateTime dateTime) {
        long days = countDays(0, toMilli(dateTime));
        return getWeekday( days);
    }

    /** {@code LocalDate} variant. */
    public static int getWeekday(LocalDate date) {
        long days = countDays(0, toMilli(date));
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

    public static int getYear(LocalDateTime dateTime) {
        return dateTime.getYear();
    }

    public static int getMonth(LocalDateTime dateTime) {
        return dateTime.getMonthValue();
    }

    public static int getDayOfMonth(LocalDateTime dateTime) {
        return dateTime.getDayOfMonth();
    }

    /** calculates how often the weekday of the passed date occured. e.g. if you pass a date thats on monday it returns 1 if its the first monday in the month and 3 if its the third monday*/
    public static int getDayOfWeekInMonth(LocalDate date)
    {
    	int day = date.getDayOfMonth();
    	int occurances = (day-1) / 7 + 1;
    	return occurances;
    }

    static public long countDays(long start,long end) {
        return (cutDate(end) - cutDate(start)) / MILLISECONDS_PER_DAY;
    }

    static public long countMinutes(LocalDateTime start, LocalDateTime end) {
    	return (toMilli(end) - toMilli(start)) / MILLISECONDS_PER_MINUTE;
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

   
   public static LocalDateTime toDateTime(LocalDateTime date, LocalDateTime time)
   {
       return date.toLocalDate().atTime(time.toLocalTime());
   }


    private static int getWeekInYearIso(long millis)
    {
        DateWithoutTimezone dateWithoutTimezone = toDate(millis);
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

    public static int getWeekInYear(java.time.LocalDate date, Locale locale)
    {
        if (date == null) return 0;
        long millis = toMilli(date);
        return isUsStyle(getCountry(locale)) ? getWeekInYearUs(millis) : getWeekInYearIso(millis);
    }

    public static int getWeekInYearIso(LocalDateTime dateTime)
    {
        return getWeekInYearIso(toMilli(dateTime));
    }

    public static int getWeekInYearUs(LocalDateTime dateTime)
    {
        return getWeekInYearUs(toMilli(dateTime));
    }

    public static int getWeekInYear(java.time.LocalDateTime dateTime, Locale locale)
    {
        if (dateTime == null) return 0;
        long millis = toMilli(dateTime);
        return isUsStyle(getCountry(locale)) ? getWeekInYearUs(millis) : getWeekInYearIso(millis);
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

    private static int getWeekInYearUs(long millis)
    {
        DateWithoutTimezone dateWithoutTimezone = toDate(millis);
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

    public static int getDayInYear(LocalDateTime dateTime)
    {
        return dateTime.getDayOfYear();
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
        String localeString = getLocaleString(locale);
        String[] parts = localeString.split("_");
        if (parts.length == 0) {
            throw new IllegalStateException("Locale split length can't be 0");
        }
        return parts[0];
    }

    private static String getLocaleString(Locale locale)
    {
        String localeString = locale.toString();
        if ( localeString.equals("unknown")) {
            localeString = DEFAULT_GWT_LOCALE;
        }
        return localeString;
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

    public static LocalDateTime add(LocalDateTime dateTime, DateTools.IncrementSize incrementSize, int incrementAmount)
    {
        if (dateTime == null) return null;
        switch ( incrementSize)
        {
            case DAY_OF_YEAR: return dateTime.plusDays(incrementAmount);
            case MONTH: return dateTime.plusMonths(incrementAmount);
            case WEEK_OF_YEAR: return dateTime.plusWeeks(incrementAmount);
            default: throw new IllegalArgumentException("unsupported incrementsize");
        }
    }

    public static LocalDate add(LocalDate date, DateTools.IncrementSize incrementSize, int incrementAmount)
    {
        if (date == null) return null;
        switch ( incrementSize)
        {
            case DAY_OF_YEAR: return date.plusDays(incrementAmount);
            case MONTH: return date.plusMonths(incrementAmount);
            case WEEK_OF_YEAR: return date.plusWeeks(incrementAmount);
            default: throw new IllegalArgumentException("unsupported incrementsize");
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
	       return hour * MILLISECONDS_PER_HOUR + minute * MILLISECONDS_PER_MINUTE + second * 1000L + milliseconds;
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

   /** LocalDateTime overload — drops the {@code toMilli(...)} round-trip at call sites. */
   public static TimeWithoutTimezone toTime(LocalDateTime dateTime)
   {
       return toTime(toMilli(dateTime));
   }
   
   public static long toTime(int hour, int minute, int second) {
	   return toTime(hour, minute, second, 0);
	}
   
   public static long toTime(int hour, int minute, int second, int millisecond) {
	   long millis = hour * MILLISECONDS_PER_HOUR;
	   millis += minute * MILLISECONDS_PER_MINUTE;
	   millis += second * 1000L;
	   millis += millisecond;
	   return millis;
	}


   public static LocalDate toLocalDate(long millis)
   {
       DateWithoutTimezone dateWithoutTimezone = toDate(millis);
       LocalDate result = LocalDate.of( dateWithoutTimezone.year, dateWithoutTimezone.month, dateWithoutTimezone.day);
       return result;
   }

   public static DateWithoutTimezone toDate(long millis)
   {
	   // special case for negative milliseconds as day rounding needs to get the lower day
	   int day = millis >= 0 ? (int) (millis/ MILLISECONDS_PER_DAY) : (int) (( millis + MILLISECONDS_PER_DAY -1) / MILLISECONDS_PER_DAY);
	   int julianDateAtNoon = day +  date_1970_1_1;
	   DateWithoutTimezone result = fromJulianDayNumberAtNoon( julianDateAtNoon);
	   return result;
   }

   /** LocalDateTime overload — drops the {@code toMilli(...)} round-trip at call sites. */
   public static DateWithoutTimezone toDate(LocalDateTime dateTime)
   {
       return toDate(toMilli(dateTime));
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
   /** returns the largest date; null dates count as positive infinity */
   public static LocalDateTime max(LocalDateTime... param) {
		LocalDateTime max = null;
		boolean set = false;
		for (LocalDateTime d : param)
		{
			if ( !set)
			{
				max = d;
				set = true;
			}
			else if ( max != null )
			{
				if ( d == null || max.isBefore(d))
				{
					max = d;
				}
			}
		}
		return max;
   }

   public static LocalDateTime getFirstWeekday(LocalDateTime dateTime, int firstWeekday)
   {
       int weekday = getWeekday( dateTime);
       int diff = firstWeekday - weekday;
       if ( diff > 0)
       {
           diff -= 7;
       }
       return dateTime.plusDays(diff);
   }




}







