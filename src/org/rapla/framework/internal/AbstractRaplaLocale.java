package org.rapla.framework.internal;

import java.util.Date;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.DateTools.DateWithoutTimezone;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.framework.RaplaLocale;

public abstract class AbstractRaplaLocale implements RaplaLocale {
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
    	long millisTime = time.getTime() - DateTools.cutDate( time.getTime());
    	Date result = new Date( DateTools.cutDate(date.getTime()) + millisTime);
    	return result;
//        Calendar cal1 = createCalendar();
//        Calendar cal2 = createCalendar();
//        cal1.setTime( date );
//        cal2.setTime( time );
//        cal1.set( Calendar.HOUR_OF_DAY, cal2.get(Calendar.HOUR_OF_DAY) );
//        cal1.set( Calendar.MINUTE, cal2.get(Calendar.MINUTE) );
//        cal1.set( Calendar.SECOND, cal2.get(Calendar.SECOND) );
//        cal1.set( Calendar.MILLISECOND, cal2.get(Calendar.MILLISECOND) );
//        return cal1.getTime();
    }
    
	public SerializableDateTimeFormat getSerializableFormat()
	{
		return new SerializableDateTimeFormat();
	}

    /** formats the date and month in the selected locale and timeZone*/
    public String formatDateMonth(Date date ) {
        DateWithoutTimezone date2 = DateTools.toDate( date.getTime());
        return date2.month + "/" + date2.day;
//      FieldPosition fieldPosition = new FieldPosition( DateFormat.YEAR_FIELD );
//      StringBuffer buf = new StringBuffer();
//      DateFormat format = DateFormat.getDateInstance( DateFormat.SHORT, locale);
//      buf = format.format(date,
//                         buf,
//                         fieldPosition
//                         );
//      if ( fieldPosition.getEndIndex()<buf.length() ) {
//          buf.delete( fieldPosition.getBeginIndex(), fieldPosition.getEndIndex()+1 );
//      } else if ( (fieldPosition.getBeginIndex()>=0) ) {
//          buf.delete( fieldPosition.getBeginIndex(), fieldPosition.getEndIndex() );
//      }
//      char lastChar = buf.charAt(buf.length()-1);
//      if (lastChar == '/' || lastChar == '-' ) {
//          String result = buf.substring(0,buf.length()-1);
//          return result;
//      } else {
//          String result = buf.toString();
//          return result;
//      }
    }

    /** formats the day of week, date and month in the selected locale and timeZone*/
    public String formatDayOfWeekDateMonth(Date date) {
        //SimpleDateFormat format =  new SimpleDateFormat("EEE", locale);
        int weekday = DateTools.getWeekday( date);
        String datePart = getWeekdayName(weekday).substring(0,3);
        String dateOfMonthPart = formatDateMonth( date  );
        return datePart + " " + dateOfMonthPart ;
    }


    public boolean isAmPmFormat() {
        // Determines if am-pm-format should be used.
        return false;
//      DateFormat format= DateFormat.getTimeInstance(DateFormat.SHORT, locale);
//      FieldPosition amPmPos = new FieldPosition(DateFormat.AM_PM_FIELD);
//      format.format(new Date(), new StringBuffer(),amPmPos);
//      return (amPmPos.getEndIndex()>0);
    }

    public String formatMonthYear(Date date)
    {
        int year = DateTools.toDate( date.getTime()).year;
        String result = formatMonth( date ) + " " + year;
        return result;

    }
    
    public String getWeekdayName(int weekday)
    {
        String result;
        switch (weekday)
        {
            case 1: result= "sunday";break;
            case 2: result= "monday";break;
            case 3: result= "tuesday";break;
            case 4: result= "wednesday";break;
            case 5: result= "thursday";break;
            case 6: result= "friday";break;
            case 7: result= "saturday";break;
            default: throw new IllegalArgumentException("Weekday " + weekday + " not supported.");
        }
        return result;
    }

    public String formatMonth(Date date)
    {    
        int month = DateTools.toDate( date.getTime()).month;
        String result;
        switch (month)
        {
            case 0: result= "january";break;
            case 1: result= "february";break;
            case 2: result= "march";break;
            case 3: result= "april";break;
            case 4: result= "may";break;
            case 5: result= "june";break;
            case 6: result= "july";break;
            case 7: result= "august";break;
            case 8: result= "september";break;
            case 9: result= "october";break;
            case 10: result= "november";break;
            case 11: result= "december";break;
            default: throw new IllegalArgumentException("Month " + month + " not supported.");
        }
        return result;
    }

    
    public String formatTime(int minuteOfDay) {
        boolean useAM_PM = isAmPmFormat();
        int minute = minuteOfDay%60;
        int hour = minuteOfDay/60;
        String displayedHour = "" + (useAM_PM ? hour %12 : hour);
        String displayedMinute = minute > 9 ? ""+ minute : "0"+minute ;
        String string = displayedHour + ":" + displayedMinute;
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


    

}
