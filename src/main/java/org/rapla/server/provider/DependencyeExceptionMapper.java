package org.rapla.server.provider;

import org.rapla.entities.DependencyException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class DependencyeExceptionMapper extends  RaplaExceptionMapper implements ExceptionMapper<DependencyException>
{
    HttpServletRequest request;

    public DependencyeExceptionMapper(@Context HttpServletRequest request)
    {
        this.request = request;
    }

    @Override
    public Response toResponse(DependencyException container)
    {
        return toResponse( container, request);
    }
}
