package org.rapla.framework;

import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.SerializableDateTimeFormat;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;


import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
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

 */
public interface RaplaLocale
{
	TypedComponentRole<String>  LANGUAGE_ENTRY = new TypedComponentRole<>("org.rapla.language");
    
    Collection<String> getAvailableLanguages();

    LocalDateTime fromUTCTimestamp(LocalDateTime timestamp);

    I18nLocaleFormats getFormats();

    /** sets time to 0:00:00 or 24:00:00 */
    LocalDateTime toDate( LocalDateTime date, boolean fillDate );

    /** Uses the first date parameter for year, month, date information and
     the second for hour, minutes, second, millisecond information.*/
    LocalDateTime toDate( LocalDateTime date, LocalDateTime time );

    /**
     * month is 1-12 January is 1 
     */
    LocalDateTime toRaplaDate( int year, int month, int date );

    /** sets date to 0:00:00  */
    LocalDateTime toTime( int hour, int minute, int second );

    /** format long with the local NumberFormat */
    String formatNumber( Long number );

    /** format without year */
    String formatDateShort( LocalDateTime date );

    /** format with locale DateFormat.SHORT */
    String formatDate( LocalDateTime date );

    /** format with locale DateFormat.MEDIUM */
    String formatDateLong( LocalDateTime date );

    String formatTimestamp(LocalDateTime timestamp);

    /** {@code java.time} variants. UTC. */
    default String formatDate( java.time.LocalDate date ) {
        return date == null ? "" : formatDate(date.atStartOfDay());
    }

    /** Abbreviation of locale weekday name of date. */
    String getWeekday( LocalDateTime date );

    /** {@code LocalDate}/{@code LocalDateTime} variants. */
    default String getWeekday( java.time.LocalDate date ) {
        return date == null ? "" : getWeekday(date.atStartOfDay());
    }

     /** Monthname of date. */
    String formatMonth( LocalDateTime date );

    String getCharsetForHtml();

    String getCharsetForCsv();

    Locale getLocale();

	SerializableDateTimeFormat getSerializableFormat();

    String formatDayOfWeekDateMonth(LocalDateTime date);

    String formatDayOfWeekLongDateMonth(LocalDateTime date);

    int getWeekInYear(LocalDateTime date);

    boolean isAmPmFormat();

    String getWeekdayNameShort(int weekday);

    String getWeekdayName(int weekday);

    String formatTime( LocalDateTime date );

    /** {@code LocalTime}/{@code LocalDateTime} variants. */
    default String formatTime( java.time.LocalTime time ) {
        if (time == null) return "";
        return formatTime(DateTools.toLocalDateTime(time.getHour() * 3600_000L + time.getMinute() * 60_000L + time.getSecond() * 1000L));
    }

    String formatMinuteOfDay( int minuteOfDay );
    
    String formatMonthYear(LocalDateTime startDate);

    String formatHour(int i);

    Locale newLocale(String language, String country);

    Comparator<String> getCollator();
}