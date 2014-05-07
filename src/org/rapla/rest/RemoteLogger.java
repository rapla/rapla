package org.rapla.rest;

import javax.jws.WebParam;
import javax.jws.WebService;

import org.rapla.rest.gwtjsonrpc.common.RemoteJsonService;

@WebService
public interface RemoteLogger extends RemoteJsonService
{
    public void info(@WebParam(name="id") String id, @WebParam(name="message") String message);
}
