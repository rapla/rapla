package org.rapla.server.provider.resteasy;

import org.jboss.resteasy.spi.ApplicationException;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.logger.Logger;
import org.rapla.logger.RaplaBootstrapLogger;
import org.rapla.rest.JsonParserWrapper;
import org.rapla.server.provider.RaplaExceptionMapper;
import org.rapla.storage.RaplaInvalidTokenException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class ResteasyExceptionMapper extends RaplaExceptionMapper implements ExceptionMapper<ApplicationException>
{
    HttpServletRequest request;

    public ResteasyExceptionMapper(@Context HttpServletRequest request)
    {
        this.request = request;
    }

    @Override
    public Response toResponse(ApplicationException container)
    {
        return  toResponse(container, request);
    }
}
