package org.rapla.framework.internal;

import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;

import java.util.Collection;
import java.util.Locale;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
public abstract class AbstractRaplaLocale implements RaplaLocale {

    public final static TypedComponentRole<String> TIMEZONE = new TypedComponentRole<>("org.rapla.timezone");
    public final static TypedComponentRole<String> CSV_CHARSET = new TypedComponentRole<>("org.rapla.csv.charset");
    public final static TypedComponentRole<String> HTML_CHARSET = new TypedComponentRole<>("org.rapla.html.charset");
    public final static TypedComponentRole<String> LOCALE = new TypedComponentRole<>("org.rapla.locale");
    public final static TypedComponentRole<String> TITLE = new TypedComponentRole<>("org.rapla.title");
    public static final String UTF_CHARSET = "UTF-8";
    public static final String ISO_8859_1_CHARSET = "ISO-8859-1";
    public static final String HTML_CHARSET_DEFAULT = UTF_CHARSET;
    public static final String CSV_CHARSET_DEFAULT = ISO_8859_1_CHARSET;


    protected final BundleManager bundleManager;

    protected AbstractRaplaLocale(BundleManager bundleManager){
        this.bundleManager=bundleManager;
    }

    public String formatTimestamp( LocalDateTime date ) 
    {
    	LocalDateTime raplaDate = fromUTCTimestamp(date);
        StringBuffer buf = new StringBuffer();
		{
    		String formatDate= formatDate( raplaDate );
			buf.append( formatDate);
        }
        buf.append(" ");
        {
    		String formatTime = formatTime( raplaDate );
			buf.append( formatTime);
        }
        return buf.toString();
    }

    @Override
    public I18nLocaleFormats getFormats() {
        return bundleManager.getFormats();
    }


    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#getAvailableLanguages()
     */
    public Collection<String> getAvailableLanguages()
    {
        return bundleManager.getAvailableLanguages();
    }


    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#toDate(java.time.LocalDateTime, boolean)
     */
//
//    	Calendar cal1 = createCalendar();
//        cal1.setTime( date );
//            cal1.add( Calendar.DATE, 1);
//        cal1.set( Calendar.HOUR_OF_DAY, 0 );
//        cal1.set( Calendar.MINUTE, 0 );
//        cal1.set( Calendar.SECOND, 0 );
//        cal1.set( Calendar.MILLISECOND, 0 );
//        return cal1.getTime();
    public LocalDateTime toDate( LocalDateTime date, boolean fillDate ) {
    	return DateTools.cutDate(fillDate ? DateTools.addDay(date) : date);
    }


    public LocalDateTime toRaplaDate( int year, int month, int day ) {
    	return DateTools.toLocalDateTime(DateTools.toDate(year, month, day));
    }

    public LocalDateTime toTime( int hour, int minute, int second ) {
    	return DateTools.toLocalDateTime(DateTools.toTime(hour, minute, second));
    }

    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#toDate(java.time.LocalDateTime, java.time.LocalDateTime)
     */
    public LocalDateTime toDate( LocalDateTime date, LocalDateTime time ) {
    	return date.toLocalDate().atTime(time.toLocalTime());
    }
    
	public SerializableDateTimeFormat getSerializableFormat()
	{
		return new SerializableDateTimeFormat();
	}

	public String formatMinuteOfDay(int minuteOfDay) {

        LocalDateTime date = DateTools.toLocalDateTime(minuteOfDay * DateTools.MILLISECONDS_PER_MINUTE);
        return formatTime(date);
//        boolean useAM_PM = isAmPmFormat();
//        int minute = minuteOfDay%60;
//        int hour = minuteOfDay/60;
//        String displayedHour = "" + (useAM_PM ? hour %12 : hour);
//        String displayedMinute = minute > 9 ? ""+ minute : "0"+minute ;
//        String string = displayedHour + ":" + displayedMinute;
//        if (useAM_PM ) {
//            if ( hour >= 12)
//            {
//                string += " PM";
//            }
//            else
//            {
//                string += " AM";
//            }
//        }
//        return string;
    }

    public String formatHour(int hour) {
        boolean useAM_PM = isAmPmFormat();
        String displayedHour = "" + (useAM_PM ? hour %12 : hour);
        String string = displayedHour;
        if (useAM_PM ) {
            if ( hour >= 12)
            {
                string += " PM";
            }
            else
            {
                string += " AM";
            }
        }
        return string;
    }

      public String formatMonth(LocalDateTime date)
      {
          int month = date.getMonthValue() - 1;
          final String[] months = getFormats().getMonths();
          if(month >= months.length){
              throw new IllegalArgumentException("Month " + month + " not supported.");
          }
          return months[month];
      }


//    public String formatDateMonth(LocalDateTime date ) {
//        DateWithoutTimezone date2 =  date.getTime();
//        return date2.month + "/" + date2.day;
//    }
  
    @Override
    public String formatDayOfWeekDateMonth(LocalDateTime date)
    {
        int weekday = DateTools.getWeekday( date);
        String datePart = getWeekdayNameShort(weekday);
        String dateOfMonthPart = formatDateMonth( date  );
        return datePart + " " + dateOfMonthPart ;
    }

    @Override
    public String formatDayOfWeekLongDateMonth(LocalDateTime date)
    {
        int weekday = DateTools.getWeekday( date);
        String datePart = getWeekdayName(weekday);
        String dateOfMonthPart = formatDateMonth( date  );
        return datePart + " " + dateOfMonthPart ;
    }



    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#getWeekday(java.time.LocalDateTime)
     */
    public String getWeekday( LocalDateTime date ) {
        int weekday = DateTools.getWeekday(date);
        String datePart = getWeekdayNameShort(weekday);
        return datePart;
    }

    @Override
    public String getWeekdayNameShort(int weekday)
    {
        return getFormats().getShortWeekdays()[weekday].substring(0,2);
    }

    @Override
    public int getWeekInYear(LocalDateTime date)
    {
        final Locale locale = getLocale();
        final int weekInYear = DateTools.getWeekInYear(date, locale);
        return weekInYear;
    }

    @Override
    public String getWeekdayName(int weekday)
    {
        final String[] weekdays = getFormats().getWeekdays();
        if(weekday >= weekdays.length ){
            throw new IllegalArgumentException("Weekday " + weekday + " not supported.");
        }
        return weekdays[weekday];
    }


    @Override
    public String formatMonthYear(LocalDateTime date)
    {
        int year = date.getYear();
        String result = formatMonth( date ) + " " + year;
        return result;
    }

    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#formatTime(java.time.LocalDateTime)
     */
    public String formatTime( LocalDateTime date ) {
        String formatHour = getFormats().getFormatHour();
        return _format(date, formatHour);
    }
    
    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#formatDateShort(java.time.LocalDateTime)
     */
    public String formatDateShort( LocalDateTime date ) {
        final String origPattern = getFormats().getFormatDateShort();
        StringBuffer buf = new StringBuffer(origPattern);
        int begin = origPattern.indexOf("y");
        int end = origPattern.lastIndexOf("y");
        if ( end <buf.length() ) {
            buf.delete( begin, end +1 );
        } else if ( begin>=0 ) {
            buf.delete( begin, end);
        }
        char lastChar = buf.charAt(buf.length()-1);
        if (lastChar == '/' || lastChar == '-' ) {
            buf.deleteCharAt(buf.length() - 1);
        }
        String pattern = buf.toString();
        return _format(date, pattern);
//      Locale locale = getLocale();
//      TimeZone timezone = zone;
//      StringBuffer buf = new StringBuffer();
//      FieldPosition fieldPosition = new FieldPosition( DateFormat.YEAR_FIELD );
//      DateFormat format = DateFormat.getDateInstance( DateFormat.SHORT, locale );
//      format.setTimeZone( timezone );
//      buf = format.format(date,
//                          buf,
//                          fieldPosition
//                          );
//      if ( fieldPosition.getEndIndex()<buf.length() ) {
//          buf.delete( fieldPosition.getBeginIndex(), fieldPosition.getEndIndex()+1 );
//      } else if ( (fieldPosition.getBeginIndex()>=0) ) {
//          buf.delete( fieldPosition.getBeginIndex(), fieldPosition.getEndIndex() );
//      }
//      String result = buf.toString();
//      return result;
    }

    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#formatDateLong(java.time.LocalDateTime)
     */
    public String formatDateLong( LocalDateTime date ) {
        String formatDateLong = getFormats().getFormatDateLong();
        return _format(date, formatDateLong);
//      TimeZone timezone = zone;
//        Locale locale = getLocale();
//      DateFormat format = DateFormat.getDateInstance( DateFormat.MEDIUM, locale );
//      format.setTimeZone( timezone );
//      String dateFormat = format.format( date);
//      return dateFormat + " (" + getWeekday(date) + ")";
    }
    
    /** formats the date and month in the selected locale and timeZone*/
    public String formatDateMonth(LocalDateTime date ) {
        return formatDateShort(date);
//        String formatMonthYear = getFormats().getFormatMonthYear();
//        return _format(date, formatMonthYear);
//        Locale locale = getLocale();
//        FieldPosition fieldPosition = new FieldPosition( DateFormat.YEAR_FIELD );
//        StringBuffer buf = new StringBuffer();
//        DateFormat format = DateFormat.getDateInstance( DateFormat.SHORT, locale);
//        buf = format.format(date,
//                buf,
//                fieldPosition
//                );
//        if ( fieldPosition.getEndIndex()<buf.length() ) {
//            buf.delete( fieldPosition.getBeginIndex(), fieldPosition.getEndIndex()+1 );
//        } else if ( (fieldPosition.getBeginIndex()>=0) ) {
//            buf.delete( fieldPosition.getBeginIndex(), fieldPosition.getEndIndex() );
//        }
//        char lastChar = buf.charAt(buf.length()-1);
//        if (lastChar == '/' || lastChar == '-' ) {
//            String result = buf.substring(0,buf.length()-1);
//            return result;
//        } else {
//            String result = buf.toString();
//            return result;
//        }
    }

    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#formatDate(java.time.LocalDateTime)
     */
    public String formatDate( LocalDateTime date ) {
        String formatDateLong = getFormats().getFormatDateShort();
        return _format(date, formatDateLong);
//      TimeZone timezone = zone;
//        Locale locale = getLocale();
//      DateFormat format = DateFormat.getDateInstance( DateFormat.SHORT, locale );
//      format.setTimeZone( timezone );
//      return format.format( date );
    }


    public boolean isAmPmFormat() {
        return getFormats().isAmPmFormat();
//        Locale locale = getLocale();
//        DateFormat format= DateFormat.getTimeInstance(DateFormat.SHORT, locale);
//        FieldPosition amPmPos = new FieldPosition(DateFormat.AM_PM_FIELD);
//        format.format(LocalDateTime.of(), new StringBuffer(),amPmPos);
//        return (amPmPos.getEndIndex()>0);
    }


    protected String _format(LocalDateTime date, final String pattern)
    {
        final I18nLocaleFormats formats = bundleManager.getFormats();
        StringBuilder builder = new StringBuilder();
        long millis = DateTools.toMilli(date);

        final char[] chars = pattern.toCharArray();
        final int length = chars.length;
        for (int i = 0; i< length; i++)
        {
            char c = chars[i];
            String toInsert;
            switch (c)
            {
                case 'M':
                    final int month = DateTools.getMonth(date);
                    if ( i==length-1 || chars[i+1]!= 'M' )
                    {
                        toInsert = Integer.toString(month);
                    }
                    else if (i == length - 2 || chars[i+2]!= 'M')
                    {
                        toInsert = fillFirstDigit( month);
                        i+=1;
                    }
                    else if (i == length - 3 || chars[i+3]!= 'M')
                    {
                        toInsert = formats.getShortMonths()[month - 1];
                        i+=2;
                    }
                    else
                    {
                        toInsert = formats.getMonths()[month - 1];
                        i+=3;
                    }
                    break;
                case 'y':
                    int year = DateTools.getYear( date);
                    if ( i==length-1 || chars[i+1]!= 'y' )
                    {
                        toInsert = Integer.toString(year);
                    }
                    else
                    {
                        toInsert = fillFirstDigit(year%100);
                        i+=1;
                    }
                    break;
                case 'd':
                    int day = DateTools.getDayOfMonth( date);
                    if ( i==length-1 || chars[i+1]!= 'd' )
                    {
                        toInsert = Integer.toString( day);
                    }
                    else
                    {
                        toInsert = fillFirstDigit(day);
                        i+=1;
                    }
                    break;
                case 'h':
                    int hourAmPm = DateTools.getHourOfDay( millis) % 12;
                    if ( i==length-1 || chars[i+1]!= 'h' )
                    {
                        toInsert = Integer.toString( hourAmPm);
                    }
                    else
                    {
                        toInsert = fillFirstDigit(hourAmPm);
                        i+=1;
                    }
                    break;
                case 'H':
                    int hour = DateTools.getHourOfDay( millis);
                    if ( i==length-1 || chars[i+1]!= 'H' )
                    {
                        toInsert = Integer.toString( hour);
                    }
                    else
                    {
                        toInsert = fillFirstDigit(hour);
                        i+=1;
                    }
                    break;
                case 'm':
                    final int minuteOfHour = DateTools.getMinuteOfHour(millis);
                    if ( i==length-1 || chars[i+1]!= 'm' )
                    {
                        toInsert = Integer.toString(minuteOfHour);
                    }
                    else
                    {
                        toInsert = fillFirstDigit(minuteOfHour);
                        i+=1;
                    }
                    break;
                case 's':
                    final int secondOfMinute = DateTools.getSecondOfMinute(millis);
                    if ( i==length-1 || chars[i+1]!= 's' )
                    {
                        toInsert = Integer.toString(secondOfMinute);
                    }
                    else
                    {
                        toInsert = fillFirstDigit( secondOfMinute);
                        i+=1;
                    }
                    break;
                case 'a':
                    boolean isAm = DateTools.getHourOfDay( millis) <12;
                    toInsert = isAm ? formats.getAmFormat() : formats.getPmFormat();
                    break;
                default: toInsert = "" + c;
            }
            builder.append(toInsert);
        }
        return builder.toString();
    }

    private String fillFirstDigit(int number)
    {
        if ( number <10)
        {
            return "0" + number;
        }
        else
        {
            return Integer.toString( number );
        }
    }

    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#getLocale()
     */
    public Locale getLocale() {
        return bundleManager.getLocale();
    }


}
