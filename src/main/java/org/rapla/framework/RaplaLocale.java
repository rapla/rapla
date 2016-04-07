package org.rapla.framework;

import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.rapla.components.i18n.I18nLocaleFormats;
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
</p>
<pre>
&lt;locale&gt;
 &lt;languages default="de"&gt;
   &lt;language&gt;de&lt;/language&gt;
   &lt;language&gt;en&lt;/language&gt;
 &lt;&gt;languages&gt;
 &lt;country&gt;US&lt;/country&gt;
&lt;/locale&gt;
</pre>
<p>
If languages default is not set, the system default wil be used.
If country code is not set, the system default will be used.
</p>

<p>
Rapla hasn't a support for different Timezones yet. 
if you look into RaplaLocale you find
3 timzones in Rapla:
</p>

<ul>
<li>{@link RaplaLocale#getTimeZone}</li>
</ul>
 */
public interface RaplaLocale
{
	TypedComponentRole<String>  LANGUAGE_ENTRY = new TypedComponentRole<String>("org.rapla.language");
    
    Collection<String> getAvailableLanguages();

    String formatTime( Date date );
    
    Date fromUTCTimestamp(Date timestamp);

    I18nLocaleFormats getFormats();

    /** sets time to 0:00:00 or 24:00:00 */
    Date toDate( Date date, boolean fillDate );

    /**
     * month is 1-12 January is 1 
     */
    Date toRaplaDate( int year, int month, int date );

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

    String formatTimestamp(Date timestamp);
    
    /** Abbreviation of locale weekday name of date. */
    String getWeekday( Date date );

     /** Monthname of date. */
    String formatMonth( Date date );

    String getCharsetNonUtf();

    /**
    This method always returns GMT+0. This is used for all internal calls. All dates and times are stored internaly with this Timezone.
    Rapla can't work with timezones but to use the Date api it needs a timezone, so GMT+0 is used, because it doesn't have DaylightSavingTimes which would confuse the conflict detection. This timezone (GMT+0) is only used internaly and never shown in Rapla. Rapla only displays the time e.g. 10:00am without the timezone.
     */
    TimeZone getTimeZone();

    Locale getLocale();

	SerializableDateTimeFormat getSerializableFormat();

    String formatDayOfWeekDateMonth(Date date);

    boolean isAmPmFormat();

    String getWeekdayName(int weekday);

    String formatTime( int minuteOfDay );

    
    String formatMonthYear(Date startDate);

    String formatHour(int i);

    Locale newLocale(String language, String country);

}