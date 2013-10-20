package org.rapla.plugin.export2ical;

import org.rapla.framework.RaplaException;

public interface ICalTimezones
{
	 String getICalTimezones() throws RaplaException;
	 String getDefaultTimezone() throws RaplaException;
}