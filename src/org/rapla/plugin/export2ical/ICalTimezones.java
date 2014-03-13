package org.rapla.plugin.export2ical;

import javax.jws.WebService;

import com.google.gwtjsonrpc.common.FutureResult;
import com.google.gwtjsonrpc.common.RemoteJsonService;
import com.google.gwtjsonrpc.common.ResultType;

@WebService
public interface ICalTimezones extends RemoteJsonService
{
	 @ResultType(String.class)
	 FutureResult<String> getICalTimezones();
	 @ResultType(String.class)
	 FutureResult<String> getDefaultTimezone();
}