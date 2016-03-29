package org.rapla.rest;

import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.rapla.jsonrpc.common.FutureResult;
import org.rapla.jsonrpc.common.VoidResult;

@Path("logger")
public interface RemoteLogger
{
    @PUT
    @Path("{id}")
    FutureResult<VoidResult> info(@PathParam("id") String id, String message);
}
