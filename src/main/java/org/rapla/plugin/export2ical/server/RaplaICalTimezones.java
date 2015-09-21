package org.rapla.plugin.export2ical.server;

import net.fortuna.ical4j.model.TimeZone;
import org.rapla.framework.RaplaContextException;
import org.rapla.gwtjsonrpc.common.FutureResult;
import org.rapla.gwtjsonrpc.common.ResultImpl;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.export2ical.ICalTimezones;
import org.rapla.server.TimeZoneConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


@DefaultImplementation(of=ICalTimezones.class,context = InjectionContext.server)
public class RaplaICalTimezones implements ICalTimezones{

	List<String> availableIDs;
	TimeZoneConverter converter;
	
	public RaplaICalTimezones(TimeZoneConverter converter) throws RaplaContextException {
		availableIDs = new ArrayList<String>(Arrays.asList( TimeZone.getAvailableIDs()));
		Collections.sort(availableIDs, String.CASE_INSENSITIVE_ORDER);
		this.converter = converter;
	}
	
	public FutureResult<List<String>> getICalTimezones()  {
		List<String> result = new ArrayList<String>();
		for (String id:availableIDs)
		{
		    result.add( id );
		}
		return new ResultImpl<List<String>>( result);
	}

	//public static final String TIMEZONE = "timezone";

	public FutureResult<String> getDefaultTimezone() 
	{
		String id = converter.getImportExportTimeZone().getID();
		return new ResultImpl<String>(id);
	}

}
