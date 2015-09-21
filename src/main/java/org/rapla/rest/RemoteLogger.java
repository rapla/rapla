package org.rapla.rest;

import javax.jws.WebParam;
import javax.jws.WebService;

import org.rapla.gwtjsonrpc.RemoteJsonMethod;
import org.rapla.gwtjsonrpc.common.FutureResult;
import org.rapla.gwtjsonrpc.common.RemoteJsonService;
import org.rapla.gwtjsonrpc.common.ResultType;
import org.rapla.gwtjsonrpc.common.VoidResult;

@WebService
@RemoteJsonMethod
public interface RemoteLogger extends RemoteJsonService
{
    @ResultType(VoidResult.class)
    FutureResult<VoidResult> info(@WebParam(name="id") String id, @WebParam(name="message") String message);
}
