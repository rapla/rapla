package org.rapla.framework.internal;

import java.util.Date;

import org.rapla.components.util.DateTools;
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
    	return DateTools.toDateTime(date, time);
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


}
