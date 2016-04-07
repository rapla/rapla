package org.rapla.server.internal.rest.provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;

import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.rest.client.swing.JSONParserWrapper;

import com.google.gson.Gson;

@Provider
@Consumes(MediaType.APPLICATION_JSON)
public class GsonReader<T> implements MessageBodyReader<T>
{

    final Gson gson = JSONParserWrapper.defaultGsonBuilder(new Class[]{RaplaMapImpl.class}).create();

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return true;
    }

    @Override
    public T readFrom(Class<T> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders,
            InputStream entityStream) throws IOException, WebApplicationException
    {
        return gson.fromJson(new InputStreamReader(entityStream), type);
    }
}
