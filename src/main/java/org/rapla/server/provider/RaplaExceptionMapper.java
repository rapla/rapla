package org.rapla.server.provider;

import org.rapla.entities.EntityNotFoundException;
import org.rapla.logger.Logger;
import org.rapla.logger.RaplaBootstrapLogger;
import org.rapla.rest.JsonParserWrapper;
import org.rapla.storage.RaplaInvalidTokenException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

public class RaplaExceptionMapper
{
    public Response toResponse(Exception container, HttpServletRequest request)
    {
        Throwable exception = container.getCause();
        if (exception instanceof JsonParserWrapper.WrappedJsonSerializeException)
        {
            exception = exception.getCause();
        }
        if (exception instanceof EntityNotFoundException)
        {
            final Response.ResponseBuilder entity = Response.status(Response.Status.NOT_FOUND).entity(exception);
            final Response build = entity.build();
            return build;
        }
        if (exception instanceof RaplaInvalidTokenException)
        {
            final Response.ResponseBuilder entity = Response.status(Response.Status.UNAUTHORIZED).entity(exception);
            final Response build = entity.build();
            return build;
        }

        try
        {
            final Object loggerFromContext = request.getServletContext().getAttribute(Logger.class.getCanonicalName());
            final Logger raplaLogger;
            if (loggerFromContext != null && loggerFromContext instanceof Logger)
            {
                raplaLogger = (Logger) loggerFromContext;
            }
            else
            {
                raplaLogger = RaplaBootstrapLogger.createRaplaLogger();
            }
            raplaLogger.error(exception.getMessage(), exception);
        }
        catch (Throwable ex)
        {
        }
        final Response.ResponseBuilder entity = Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(exception);
        final Response build = entity.build();
        return build;
    }

}
