package org.rapla.server.internal;

import org.rapla.components.util.IOUtil;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.server.TimeZoneConverter;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Date;
import java.util.TimeZone;

@DefaultImplementation(of=TimeZoneConverter.class,context = InjectionContext.server)
@Singleton
public class TimeZoneConverterImpl implements TimeZoneConverter
{
    TimeZone zone;
    TimeZone importExportTimeZone;

    @Inject
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
	

	public Date fromRaplaTime(TimeZone timeZone,Date raplaTime) 
	{
		return new Date( fromRaplaTime(timeZone, raplaTime.getTime()));
	}


	public Date toRaplaTime(TimeZone timeZone,Date time) 
	{
		return new Date( toRaplaTime(timeZone, time.getTime()));
	}

	public static int getOffset(TimeZone zone1,TimeZone zone2,long time) {
		int offsetRapla = zone1.getOffset(time);
		int offsetSystem  =  zone2.getOffset(time);
		return offsetSystem - offsetRapla;
	}


	

	
}
