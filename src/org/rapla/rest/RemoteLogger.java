package org.rapla.rest;

import javax.jws.WebParam;
import javax.jws.WebService;

import org.rapla.rest.gwtjsonrpc.common.FutureResult;
import org.rapla.rest.gwtjsonrpc.common.RemoteJsonService;
import org.rapla.rest.gwtjsonrpc.common.ResultType;
import org.rapla.rest.gwtjsonrpc.common.VoidResult;

@WebService
public interface RemoteLogger extends RemoteJsonService
{
    @ResultType(VoidResult.class)
    FutureResult<VoidResult> info(@WebParam(name="id") String id, @WebParam(name="message") String message);
}
