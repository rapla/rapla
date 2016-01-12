package org.rapla.rest;

import org.rapla.jsonrpc.common.FutureResult;
import org.rapla.jsonrpc.common.RemoteJsonMethod;
import org.rapla.jsonrpc.common.VoidResult;

import javax.jws.WebParam;

@RemoteJsonMethod
public interface RemoteLogger 
{
    FutureResult<VoidResult> info(@WebParam(name="id") String id, @WebParam(name="message") String message);
}
