package org.rapla.rest;

import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.rapla.framework.RaplaException;

@Path("logger")
public interface RemoteLogger
{
    @PUT
    @Path("{id}")
    void info(@PathParam("id") String id, String message)throws RaplaException;
}
