package org.rapla.plugin.export2ical;

import org.rapla.jsonrpc.common.FutureResult;
import org.rapla.jsonrpc.common.RemoteJsonMethod;

import java.util.List;

@RemoteJsonMethod
public interface ICalTimezones 
{
    FutureResult<List<String>> getICalTimezones();

    FutureResult<String> getDefaultTimezone();
}