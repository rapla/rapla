package org.rapla.server.internal.rest.provider;

import com.google.gson.Gson;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.rest.client.SerializableExceptionInformation;
import org.rapla.rest.client.swing.JSONParserWrapper;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class GsonWriter<T> implements MessageBodyWriter<T>
{
    final Gson gson = JSONParserWrapper.defaultGsonBuilder(new Class[] { RaplaMapImpl.class }).create();
    private final HttpServletRequest request;

    public GsonWriter(@Context HttpServletRequest request)
    {
        this.request = request;
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return true;
    }

    @Override
    public long getSize(T t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return -1;
    }

    @Override
    public void writeTo(T t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders,
            OutputStream entityStream) throws IOException, WebApplicationException
    {
        String json;
        if (t instanceof Throwable)
        {
            json = serializeException((Throwable) t);
        }
        else
        {
            json = gson.toJson(t);
        }
        entityStream.write(json.getBytes("UTF-8"));
    }

    private String serializeException(Throwable exception)
    {
        final SerializableExceptionInformation se = new SerializableExceptionInformation(exception);
        final String json = gson.toJson(se);
        return json;
    }
}
