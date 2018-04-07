package org.rapla.server.provider;

import org.rapla.entities.EntityNotFoundException;
import org.rapla.logger.Logger;
import org.rapla.logger.RaplaBootstrapLogger;
import org.rapla.rest.JsonParserWrapper;
import org.rapla.storage.RaplaInvalidTokenException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

public class ExceptionResponseBuilder
{
    static public Response toResponse(Exception exception, HttpServletRequest request)
    {
        Throwable cause = exception;
        if (exception.getCause() != null)
        {
            cause = exception.getCause();
        }
        if (cause instanceof JsonParserWrapper.WrappedJsonSerializeException )
        {
            if (exception.getCause() != null)
            {
                cause = exception.getCause();
            }
        }
        if (cause instanceof EntityNotFoundException)
        {
            final Response.ResponseBuilder entity = Response.status(Response.Status.NOT_FOUND).entity(exception);
            final Response build = entity.build();
            return build;
        }
        if (cause instanceof RaplaInvalidTokenException)
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
            final String message = cause.getMessage();
            raplaLogger.error(message, exception);
        }
        catch (Throwable ex)
        {
        }
        final Response.ResponseBuilder entity = Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(exception);
        final Response build = entity.build();
        return build;
    }

}
