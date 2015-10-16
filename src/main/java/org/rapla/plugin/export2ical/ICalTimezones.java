package org.rapla.plugin.export2ical;

import java.util.List;

import org.rapla.gwtjsonrpc.RemoteJsonMethod;
import org.rapla.gwtjsonrpc.common.FutureResult;
import org.rapla.gwtjsonrpc.common.ResultType;

@RemoteJsonMethod
public interface ICalTimezones 
{
    @ResultType(value=String.class,container=List.class)
    FutureResult<List<String>> getICalTimezones();
    @ResultType(String.class)
    FutureResult<String> getDefaultTimezone();
}