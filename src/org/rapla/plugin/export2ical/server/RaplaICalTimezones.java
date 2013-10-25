package org.rapla.plugin.export2ical.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.fortuna.ical4j.model.TimeZone;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.plugin.export2ical.ICalTimezones;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;

public class RaplaICalTimezones extends RaplaComponent implements ICalTimezones, RemoteMethodFactory<ICalTimezones>{

	List<String> availableIDs;
	
	public RaplaICalTimezones(RaplaContext context) {
		super( context);
		availableIDs = new ArrayList<String>(Arrays.asList( TimeZone.getAvailableIDs()));
		Collections.sort(availableIDs, String.CASE_INSENSITIVE_ORDER);
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
		RaplaConfiguration entry = getQuery().getPreferences( null ).getEntry(PLUGIN_CONFIG);
		if ( entry != null)
		{
			Configuration find = entry.find("class", Export2iCalPlugin.PLUGIN_CLASS);
			if  ( find != null)
			{
				String timeZone = find.getChild("TIMEZONE").getValue( null);
				if ( timeZone != null && !timeZone.equals("Etc/UTC"))
				{
					return timeZone;
				}
			}
		}
		return getRaplaLocale().getSystemTimeZone().getID();
	}

}
