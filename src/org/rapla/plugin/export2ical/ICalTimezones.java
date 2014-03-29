package org.rapla.plugin.export2ical;

import javax.jws.WebService;

import org.rapla.rest.gwtjsonrpc.common.FutureResult;
import org.rapla.rest.gwtjsonrpc.common.RemoteJsonService;
import org.rapla.rest.gwtjsonrpc.common.ResultType;

@WebService
public interface ICalTimezones extends RemoteJsonService
{
	 @ResultType(String.class)
	 FutureResult<String> getICalTimezones();
	 @ResultType(String.class)
	 FutureResult<String> getDefaultTimezone();
}