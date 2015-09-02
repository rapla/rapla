package org.rapla.plugin.export2ical;

import java.util.List;

import javax.jws.WebService;

import org.rapla.gwtjsonrpc.common.FutureResult;
import org.rapla.gwtjsonrpc.common.RemoteJsonService;
import org.rapla.gwtjsonrpc.common.ResultType;

@WebService
public interface ICalTimezones extends RemoteJsonService
{
    @ResultType(value=String.class,container=List.class)
    FutureResult<List<String>> getICalTimezones();
    @ResultType(String.class)
    FutureResult<String> getDefaultTimezone();
}