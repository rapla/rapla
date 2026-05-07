package org.rapla.framework;

import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.util.SerializableDateTimeFormat;

import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;


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

    Date fromUTCTimestamp(Date timestamp);

    I18nLocaleFormats getFormats();

    /** sets time to 0:00:00 or 24:00:00 */
    Date toDate( Date date, boolean fillDate );

    /** Uses the first date parameter for year, month, date information and
     the second for hour, minutes, second, millisecond information.*/
    Date toDate( Date date, Date time );

    /**
     * month is 1-12 January is 1 
     */
    Date toRaplaDate( int year, int month, int date );

    /** {@code LocalDate} variant. UTC. */
    default java.time.LocalDate toRaplaLocalDate(int year, int month, int date) {
        return java.time.LocalDate.of(year, month, date);
    }

    /** sets date to 0:00:00  */
    Date toTime( int hour, int minute, int second );

    /** format long with the local NumberFormat */
    String formatNumber( Long number );

    /** format without year */
    String formatDateShort( Date date );

    /** format with locale DateFormat.SHORT */
    String formatDate( Date date );

    /** format with locale DateFormat.MEDIUM */
    String formatDateLong( Date date );

    String formatTimestamp(Date timestamp);

    /** {@code java.time} variants. UTC. */
    default String formatDate( java.time.LocalDate date ) {
        return date == null ? "" : formatDate(org.rapla.components.util.DateTools.toDate(date));
    }
    default String formatDate( java.time.LocalDateTime dateTime ) {
        return dateTime == null ? "" : formatDate(org.rapla.components.util.DateTools.toDate(dateTime));
    }
    default String formatTimestamp(java.time.LocalDateTime timestamp) {
        return timestamp == null ? "" : formatTimestamp(org.rapla.components.util.DateTools.toDate(timestamp));
    }
    default String formatDateLong(java.time.LocalDateTime dateTime) {
        return dateTime == null ? "" : formatDateLong(org.rapla.components.util.DateTools.toDate(dateTime));
    }
    
    /** Abbreviation of locale weekday name of date. */
    String getWeekday( Date date );

    /** {@code LocalDate}/{@code LocalDateTime} variants. */
    default String getWeekday( java.time.LocalDate date ) {
        return date == null ? "" : getWeekday(org.rapla.components.util.DateTools.toDate(date));
    }
    default String getWeekday( java.time.LocalDateTime dateTime ) {
        return dateTime == null ? "" : getWeekday(org.rapla.components.util.DateTools.toDate(dateTime));
    }

     /** Monthname of date. */
    String formatMonth( Date date );

    String getCharsetForHtml();

    String getCharsetForCsv();

    Locale getLocale();

	SerializableDateTimeFormat getSerializableFormat();

    String formatDayOfWeekDateMonth(Date date);

    String formatDayOfWeekLongDateMonth(Date date);

    int getWeekInYear(Date date);

    boolean isAmPmFormat();

    String getWeekdayNameShort(int weekday);

    String getWeekdayName(int weekday);

    String formatTime( Date date );

    /** {@code LocalTime}/{@code LocalDateTime} variants. */
    default String formatTime( java.time.LocalTime time ) {
        if (time == null) return "";
        return formatTime(new Date(time.getHour() * 3600_000L + time.getMinute() * 60_000L + time.getSecond() * 1000L));
    }
    default String formatTime( java.time.LocalDateTime dateTime ) {
        return dateTime == null ? "" : formatTime(org.rapla.components.util.DateTools.toDate(dateTime));
    }

    String formatMinuteOfDay( int minuteOfDay );
    
    String formatMonthYear(Date startDate);

    String formatHour(int i);

    Locale newLocale(String language, String country);

    Comparator<String> getCollator();
}