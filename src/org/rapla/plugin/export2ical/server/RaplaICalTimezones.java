package org.rapla.plugin.export2ical.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.fortuna.ical4j.model.TimeZone;

import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.export2ical.ICalTimezones;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;
import org.rapla.server.TimeZoneConverter;

public class RaplaICalTimezones extends RaplaComponent implements ICalTimezones, RemoteMethodFactory<ICalTimezones>{

	List<String> availableIDs;
	TimeZoneConverter converter;
	
	public RaplaICalTimezones(RaplaContext context) throws RaplaContextException {
		super( context);
		availableIDs = new ArrayList<String>(Arrays.asList( TimeZone.getAvailableIDs()));
		Collections.sort(availableIDs, String.CASE_INSENSITIVE_ORDER);
		this.converter = context.lookup( TimeZoneConverter.class);
	}
	
	public ICalTimezones createService(RemoteSession remoteSession) {
		return this;
	}

	public String getICalTimezones() throws RaplaException {
		StringBuffer buf = new StringBuffer();
		for (String id:availableIDs)
		{
			buf.append(id);
			buf.append(";");
		}
		String result = buf.toString();
		return result;
	}

	//public static final String TIMEZONE = "timezone";

	public String getDefaultTimezone() throws RaplaException 
	{
		return converter.getImportExportTimeZone().getID();
	}

}
