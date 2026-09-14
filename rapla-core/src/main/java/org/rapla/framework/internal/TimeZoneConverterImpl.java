package org.rapla.framework.internal;

import org.rapla.components.util.IOUtil;
import org.rapla.framework.TimeZoneConverter;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.TimeZone;

import java.time.LocalDateTime;
import org.rapla.components.util.DateTools;
public class TimeZoneConverterImpl implements TimeZoneConverter
{
    TimeZone zone;
    TimeZone importExportTimeZone;

    @Autowired
    public TimeZoneConverterImpl()  
    {
        zone = IOUtil.getTimeZone();
        TimeZone systemTimezone = TimeZone.getDefault();
        importExportTimeZone = systemTimezone;
    }
    
    public TimeZone getImportExportTimeZone() {
		return importExportTimeZone;
	}

	public void setImportExportTimeZone(TimeZone importExportTimeZone) {
		this.importExportTimeZone = importExportTimeZone;
	}
    
    public long fromRaplaTime(TimeZone timeZone,long raplaTime)
	{
		long offset = TimeZoneConverterImpl.getOffset(zone,timeZone, raplaTime);
		return raplaTime - offset;
	}

	public long toRaplaTime(TimeZone timeZone,long time) 
	{
		long offset = TimeZoneConverterImpl.getOffset(zone,timeZone,time);
		return time + offset;
	}
	

	public LocalDateTime fromRaplaTime(TimeZone timeZone,LocalDateTime raplaTime)
	{
		return DateTools.toLocalDateTime(fromRaplaTime(timeZone, DateTools.toMilli(raplaTime)));
	}


	public LocalDateTime toRaplaTime(TimeZone timeZone,LocalDateTime time)
	{
		return DateTools.toLocalDateTime(toRaplaTime(timeZone, DateTools.toMilli(time)));
	}

	public static int getOffset(TimeZone zone1,TimeZone zone2,long time) {
		int offsetRapla = zone1.getOffset(time);
		int offsetSystem  =  zone2.getOffset(time);
		return offsetSystem - offsetRapla;
	}


	

	
}
