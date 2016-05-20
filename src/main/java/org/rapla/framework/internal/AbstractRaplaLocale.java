package org.rapla.framework.internal;

import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;

public abstract class AbstractRaplaLocale implements RaplaLocale {

    public final static TypedComponentRole<String> TIMEZONE = new TypedComponentRole<String>("org.rapla.timezone");
    public final static TypedComponentRole<String> LOCALE = new TypedComponentRole<String>("org.rapla.locale");
    public final static TypedComponentRole<String> TITLE = new TypedComponentRole<String>("org.rapla.title");
    protected final BundleManager bundleManager;

    protected AbstractRaplaLocale(BundleManager bundleManager){
        this.bundleManager=bundleManager;
    }

    public String formatTimestamp( Date date ) 
    {
    	Date raplaDate = fromUTCTimestamp(date);
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
     * @see org.rapla.common.IRaplaLocale#toDate(java.util.Date, boolean)
     */
    public Date toDate( Date date, boolean fillDate ) {
    	Date result = DateTools.cutDate(DateTools.addDay(date));
		return result;
//
//    	Calendar cal1 = createCalendar();
//        cal1.setTime( date );
//        if ( fillDate ) {
//            cal1.add( Calendar.DATE, 1);
//        }
//        cal1.set( Calendar.HOUR_OF_DAY, 0 );
//        cal1.set( Calendar.MINUTE, 0 );
//        cal1.set( Calendar.SECOND, 0 );
//        cal1.set( Calendar.MILLISECOND, 0 );
//        return cal1.getTime();
    }

    
    public Date toRaplaDate( int year,int month, int day ) {
    	Date result =  new Date(DateTools.toDate(year, month, day));
    	return result;
    }

    public Date toTime( int hour,int minute, int second ) {
    	Date result =  new Date(DateTools.toTime(hour, minute, second));
    	return result;
    }

    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#toDate(java.util.Date, java.util.Date)
     */
    public Date toDate( Date date, Date time ) {
    	return DateTools.toDateTime(date, time);
    }
    
	public SerializableDateTimeFormat getSerializableFormat()
	{
		return new SerializableDateTimeFormat();
	}

	public String formatTime(int minuteOfDay) {

        Date date = new Date(0 + minuteOfDay * DateTools.MILLISECONDS_PER_MINUTE);
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

      public String formatMonth(Date date)
      {    
          int month = DateTools.toDate( date.getTime()).month - 1;
          final String[] months = getFormats().getMonths();
          if(month >= months.length){
              throw new IllegalArgumentException("Month " + month + " not supported.");
          }
          return months[month];
      }


//    public String formatDateMonth(Date date ) {
//        DateWithoutTimezone date2 = DateTools.toDate( date.getTime());
//        return date2.month + "/" + date2.day;
//    }
  
    @Override
    public String formatDayOfWeekDateMonth(Date date)
    {
        int weekday = DateTools.getWeekday( date);
        String datePart = getFormats().getWeekdays()[weekday].substring(0,2);
        String dateOfMonthPart = formatDateMonth( date  );
        return datePart + " " + dateOfMonthPart ;
    }


    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#getWeekday(java.util.Date)
     */
    public String getWeekday( Date date ) {
        int weekday = DateTools.getWeekday(date);
        String datePart = getFormats().getWeekdays()[weekday].substring(0,2);
        return datePart;
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
    public String formatMonthYear(Date date)
    {
        int year = DateTools.toDate( date.getTime()).year;
        String result = formatMonth( date ) + " " + year;
        return result;
    }

    public TimeZone getTimeZone() {
        return DateTools.getTimeZone();
    }

    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#formatTime(java.util.Date)
     */
    public String formatTime( Date date ) {
        String formatHour = getFormats().getFormatHour();
        return _format(date, formatHour);
//        Locale locale = getLocale();
//        TimeZone timezone = getTimeZone();
//      DateFormat format = DateFormat.getTimeInstance( DateFormat.SHORT, locale );
//      format.setTimeZone( timezone );
//      String formatTime = format.format( date );
//      return formatTime;
    }
    
    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#formatDateShort(java.util.Date)
     */
    public String formatDateShort( Date date ) {
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
     * @see org.rapla.common.IRaplaLocale#formatDateLong(java.util.Date)
     */
    public String formatDateLong( Date date ) {
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
    public String formatDateMonth(Date date ) {
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
     * @see org.rapla.common.IRaplaLocale#formatDate(java.util.Date)
     */
    public String formatDate( Date date ) {
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
//        format.format(new Date(), new StringBuffer(),amPmPos);
//        return (amPmPos.getEndIndex()>0);
    }

    
    protected abstract String _format(Date date, final String pattern);

    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#getLocale()
     */
    public Locale getLocale() {
        return bundleManager.getLocale();
    }


}
