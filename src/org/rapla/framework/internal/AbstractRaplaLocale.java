package org.rapla.framework.internal;

import java.util.Date;
import java.util.TimeZone;

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

    @Deprecated
    public Date toDate( int year,int month, int day ) {
    	Date result =  toRaplaDate(year, month+1, day);
    	return result;
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

	public long fromRaplaTime(TimeZone timeZone,long raplaTime)
	{
		long offset = getOffset(timeZone, raplaTime);
		return raplaTime - offset;
	}

	public long toRaplaTime(TimeZone timeZone,long time) 
	{
		long offset = getOffset(timeZone,time);
		return time + offset;
	}
	

	public Date fromRaplaTime(TimeZone timeZone,Date raplaTime) 
	{
		return new Date( fromRaplaTime(timeZone, raplaTime.getTime()));
	}


	public Date toRaplaTime(TimeZone timeZone,Date time) 
	{
		return new Date( toRaplaTime(timeZone, time.getTime()));
	}

	
	private long getOffset(TimeZone timeZone,long time) {
		long offsetSystem;
		long offsetRapla;
		{
			
//			Calendar cal =Calendar.getInstance( timeZone);
//			cal.setTimeInMillis( time );
//			int zoneOffset = cal.get(Calendar.ZONE_OFFSET);
//			int dstOffset = cal.get(Calendar.DST_OFFSET);
			offsetSystem =  timeZone.getOffset(time);
		}
		
		{
			TimeZone utc = getTimeZone();
//			Calendar cal =Calendar.getInstance( utc);
//			cal.setTimeInMillis( time );
//			offsetRapla =  cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET);
			offsetRapla = utc.getOffset(time);
		}
		
		return offsetSystem - offsetRapla;
	}
	
	public SerializableDateTimeFormat getSerializableFormat()
	{
		return new SerializableDateTimeFormat();
	}

}
