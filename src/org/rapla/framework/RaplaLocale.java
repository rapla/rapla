package org.rapla.framework;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.rapla.components.util.SerializableDateTimeFormat;


/** This class contains all locale specific information for Rapla. Like
<ul>
  <li>Selected language.</li>
  <li>Selected country.</li>
  <li>Available languages (if the user has the possibility to choose a language)</li>
  <li>TimeZone for appointments (This is always GMT+0)</li>
</ul>
<p>
Also it provides basic formating information for the dates.
</p>
<p>
Configuration is done in the rapla.xconf:
<pre>
&lt;locale>
 &lt;languages default="de">
   &lt;language>de&lt;/language>
   &lt;language>en&lt;/language>
 &lt;/languages>
 &lt;country>US&lt;/country>
&lt;/locale>
</pre>
If languages default is not set, the system default wil be used.<br>
If country code is not set, the system default will be used.<br>
</p>

<p>
Rapla hasn't a support for different Timezones yet. 
if you look into RaplaLocale you find
3 timzones in Rapla:
</p>

<ul>
<li>{@link RaplaLocale#getTimeZone}</li>
<li>{@link RaplaLocale#getSystemTimeZone}</li>
<li>{@link RaplaLocale#getImportExportTimeZone}</li>
</ul>
 */
public interface RaplaLocale
{
	TypedComponentRole<String>  LANGUAGE_ENTRY = new TypedComponentRole<String>("org.rapla.language");
    
    String[] getAvailableLanguages();

    /** creates a calendar initialized with the Rapla timezone ( that is always GMT+0 for Rapla  )  and the selected locale*/
    Calendar createCalendar();

    String formatTime( Date date );

    /** sets time to 0:00:00 or 24:00:00 */
    Date toDate( Date date, boolean fillDate );

    /** sets time to 0:00:00  */
    Date toDate( int year, int month, int date );

     /** sets date to 0:00:00  */
    Date toTime( int hour, int minute, int second );

    /** Uses the first date parameter for year, month, date information and
     the second for hour, minutes, second, millisecond information.*/
    Date toDate( Date date, Date time );

    /** format long with the local NumberFormat */
    String formatNumber( long number );

    /** format without year */
    String formatDateShort( Date date );

    /** format with locale DateFormat.SHORT */
    String formatDate( Date date );

    /** format with locale DateFormat.MEDIUM */
    String formatDateLong( Date date );

    String formatTimestamp(Date timestamp, TimeZone timezone);
    
    /** Abbreviation of locale weekday name of date. */
    String getWeekday( Date date );

     /** Monthname of date. */
    String getMonth( Date date );

    String getCharsetNonUtf();

    /**
    This method always returns GMT+0. This is used for all internal calls. All dates and times are stored internaly with this Timezone.
    Rapla can't work with timezones but to use the Date api it needs a timezone, so GMT+0 is used, because it doesn't have DaylightSavingTimes which would confuse the conflict detection. This timezone (GMT+0) is only used internaly and never shown in Rapla. Rapla only displays the time e.g. 10:00am without the timezone.
     */
    TimeZone getTimeZone();
    /**
    which returns the timezone of the system where Rapla is running (java default)
    this is used on the server for storing the dates in mysql. Prior to 1.7 Rapla always switched the system time to gmt for the mysql api. But now the dates are converted from GMT+0 to system time before storing. 
    Converted meens: 10:00am GMT+0 Raplatime is converted to 10:00am Europe/Berlin, if thats the system timezone. When loading 10:00am Europe/Berlin is converted back to 10:00am GMT+0.
    This timezone is also used for the timestamps, so created-at and last-changed dates are always stored in system time.
    Also the logging api uses this timezone and now logs are in system time.
    @see RaplaLocale#toRaplaTime(TimeZone, long)
    */
    TimeZone getSystemTimeZone();

    /**
    returns the timezone configured via main options, this is per default the system timezon. This timezone is used for ical/exchange import/export
    If Rapla will support timezones in the future, than this will be the default timezone for all times. Now its only used on import and export. It works as with system time above. 10:00am GMT+0 is converted to 10:00am of the configured timezone on export and on import all times are converted to GMT+0.
    @see RaplaLocale#toRaplaTime(TimeZone, long)
    */
    TimeZone getImportExportTimeZone();
    
    long fromRaplaTime(TimeZone timeZone,long raplaTime);
	long toRaplaTime(TimeZone timeZone,long time);

	Date fromRaplaTime(TimeZone timeZone,Date raplaTime);
	/**
	 * converts a common Date object into a Date object that
	 * assumes that the user (being in the given timezone) is in the
	 * UTC-timezone by adding the offset between UTC and the given timezone.
	 * 
	 * <pre>
	 * Example: If you pass the Date "2013 Jan 15 11:00:00 UTC"
	 * and the TimeZone "GMT+1", this method will return a Date
	 * "2013 Jan 15 12:00:00 UTC" which is effectivly 11:00:00 GMT+1
	 * </pre>
	 * 
	 * @param timeZone
	 *            the orgin timezone
	 * @param time
	 *            the Date object in the passed timezone 
     * @see RaplaLocale#fromRaplaTime
	 */
	Date toRaplaTime(TimeZone timeZone,Date time);
	
    Locale getLocale();
    
    SerializableDateTimeFormat getSerializableFormat();	

}