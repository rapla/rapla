package org.rapla.server.provider.resteasy;

import org.rapla.server.provider.ExceptionResponseBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class RestWebApplicationExceptionMapper  implements ExceptionMapper<WebApplicationException>
{
    HttpServletRequest request;

    public RestWebApplicationExceptionMapper(@Context HttpServletRequest request)
    {
        this.request = request;
    }

    @Override
    public Response toResponse(WebApplicationException container)
    {
        return ExceptionResponseBuilder.toResponse( container, request);
    }
}
