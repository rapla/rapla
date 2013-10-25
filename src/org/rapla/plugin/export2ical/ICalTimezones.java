package org.rapla.plugin.export2ical;

import javax.jws.WebService;

import org.rapla.framework.RaplaException;

@WebService
public interface ICalTimezones
{
	 String getICalTimezones() throws RaplaException;
	 String getDefaultTimezone() throws RaplaException;
}