package org.rapla.framework;

import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsType;
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
@JsType
public interface RaplaLocale
{
	TypedComponentRole<String>  LANGUAGE_ENTRY = new TypedComponentRole<>("org.rapla.language");
    
    Collection<String> getAvailableLanguages();

    Date fromUTCTimestamp(Date timestamp);

    I18nLocaleFormats getFormats();

    /** sets time to 0:00:00 or 24:00:00 */
    @JsMethod(name = "toDate_")
    Date toDate( Date date, boolean fillDate );

    /** Uses the first date parameter for year, month, date information and
     the second for hour, minutes, second, millisecond information.*/
    Date toDate( Date date, Date time );

    /**
     * month is 1-12 January is 1 
     */
    Date toRaplaDate( int year, int month, int date );

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
    
    /** Abbreviation of locale weekday name of date. */
    String getWeekday( Date date );

     /** Monthname of date. */
    String formatMonth( Date date );

    String getCharsetNonUtf();

    Locale getLocale();

	SerializableDateTimeFormat getSerializableFormat();

    String formatDayOfWeekDateMonth(Date date);

    String formatDayOfWeekLongDateMonth(Date date);

    int getWeekInYear(Date date);

    boolean isAmPmFormat();

    String getWeekdayNameShort(int weekday);

    String getWeekdayName(int weekday);

    String formatTime( Date date );

    String formatMinuteOfDay( int minuteOfDay );
    
    String formatMonthYear(Date startDate);

    String formatHour(int i);

    Locale newLocale(String language, String country);

    Comparator<String> getCollator();
}