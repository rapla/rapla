package org.rapla.server;

import java.util.Date;
import java.util.TimeZone;


public interface TimeZoneConverter
{
    /**
    returns the timezone configured via main options, this is per default the system timezon. This timezone is used for ical/exchange import/export
    If Rapla will support timezones in the future, than this will be the default timezone for all times. Now its only used on import and export. It works as with system time above. 10:00am GMT+0 is converted to 10:00am of the configured timezone on export and on import all times are converted to GMT+0.
    @see TimeZoneConverter#toRaplaTime(TimeZone, long)
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
	 */
	Date toRaplaTime(TimeZone timeZone,Date time);	

}