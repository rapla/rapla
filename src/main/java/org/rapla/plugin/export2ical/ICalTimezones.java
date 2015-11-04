package org.rapla.plugin.export2ical;

import java.util.List;

import org.rapla.jsonrpc.common.FutureResult;
import org.rapla.jsonrpc.common.RemoteJsonMethod;

@RemoteJsonMethod
public interface ICalTimezones 
{
    FutureResult<List<String>> getICalTimezones();

    FutureResult<String> getDefaultTimezone();
}