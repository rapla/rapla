package org.rapla.rest;

import org.rapla.framework.RaplaException;

import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

@Path("logger")
public interface RemoteLogger
{
    @PUT
    @Path("{id}")
    void info(@PathParam("id") String id, String message)throws RaplaException;
}
