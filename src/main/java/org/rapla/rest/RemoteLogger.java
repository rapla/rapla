package org.rapla.rest;

import javax.jws.WebParam;
import javax.jws.WebService;

import org.rapla.jsonrpc.common.FutureResult;
import org.rapla.jsonrpc.common.RemoteJsonMethod;
import org.rapla.jsonrpc.common.VoidResult;

@RemoteJsonMethod
public interface RemoteLogger 
{
    FutureResult<VoidResult> info(@WebParam(name="id") String id, @WebParam(name="message") String message);
}
