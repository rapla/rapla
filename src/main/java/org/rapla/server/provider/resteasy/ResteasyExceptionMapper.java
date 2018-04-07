package org.rapla.server.provider.resteasy;

import org.jboss.resteasy.spi.ApplicationException;
import org.rapla.server.provider.ExceptionResponseBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class ResteasyExceptionMapper  implements ExceptionMapper<ApplicationException>
{
    HttpServletRequest request;

    public ResteasyExceptionMapper(@Context HttpServletRequest request)
    {
        this.request = request;
    }

    @Override
    public Response toResponse(ApplicationException container)
    {
        return  ExceptionResponseBuilder.toResponse(container, request);
    }
}
