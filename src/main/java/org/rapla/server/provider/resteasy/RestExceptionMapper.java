package org.rapla.server.provider.resteasy;

import org.jboss.resteasy.spi.ApplicationException;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.logger.Logger;
import org.rapla.logger.RaplaBootstrapLogger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class RestExceptionMapper implements ExceptionMapper<ApplicationException>
{
    HttpServletRequest request;
    public RestExceptionMapper(@Context HttpServletRequest request)
    {
        this.request = request;
    }

    @Override
    public Response toResponse(ApplicationException container)
    {
        Throwable exception = container.getCause();
        if ( exception instanceof EntityNotFoundException)
        {
            final Response.ResponseBuilder entity = Response.status(Status.NOT_FOUND).entity(exception);
            final Response build = entity.build();
            return build;
        }
        try
        {
            final Object loggerFromContext = request.getServletContext().getAttribute(Logger.class.getCanonicalName());
            final Logger raplaLogger;
            if ( loggerFromContext != null && loggerFromContext instanceof  Logger)
            {
                raplaLogger = (Logger) loggerFromContext;
            } else
            {
                 raplaLogger = RaplaBootstrapLogger.createRaplaLogger();
            }
            raplaLogger.error(exception.getMessage(), exception);
        }
        catch (Throwable ex)
        {
        }
        final Response.ResponseBuilder entity = Response.status(Status.INTERNAL_SERVER_ERROR).entity(exception);
        final Response build = entity.build();
        return build;
    }
}
