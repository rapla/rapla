package org.rapla.server.provider;

import org.rapla.entities.EntityNotFoundException;
import org.rapla.logger.Logger;
import org.rapla.logger.RaplaBootstrapLogger;
import org.rapla.rest.JsonParserWrapper;
import org.rapla.storage.RaplaInvalidTokenException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;
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
            if (cause.getCause() != null)
            {
                cause = cause.getCause();
            }
        }
        if (cause instanceof EntityNotFoundException)
        {
            final Response.ResponseBuilder entity = Response.status(Response.Status.NOT_FOUND).entity(cause);
            final Response build = entity.build();
            return build;
        }
        if (cause instanceof RaplaInvalidTokenException)
        {
            final Response.ResponseBuilder entity = Response.status(Response.Status.UNAUTHORIZED).entity(cause);
            final Response build = entity.build();
            return build;
        }
        Logger raplaLogger = null;
        try
        {

            if ( request != null)
            {
                final Object loggerFromContext = request.getServletContext().getAttribute(Logger.class.getCanonicalName());
                if (loggerFromContext != null && loggerFromContext instanceof Logger)
                {
                    raplaLogger = (Logger) loggerFromContext;
                }
            }
            if ( raplaLogger == null)
            {
                raplaLogger = RaplaBootstrapLogger.createRaplaLogger();
                raplaLogger.warn("Could not get logger from request. Using bootstrap.");
            }
        }
        catch (Throwable ex)
        {
            final Response.ResponseBuilder entity = Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(cause);
            final Response build = entity.build();
            return build;
        }
        if (cause instanceof NotFoundException)
        {
            final Response.ResponseBuilder entity = Response.status(Response.Status.NOT_FOUND).entity(cause);
            final Response build = entity.build();
            raplaLogger.warn( cause.getMessage());
            return build;
        }
        final String message = cause.getMessage();
        raplaLogger.error(message, cause);
        final Response.ResponseBuilder entity = Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(cause);
        final Response build = entity.build();
        return build;
    }

}
