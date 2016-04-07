package org.rapla.server.internal.rest.provider;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.rapla.entities.DependencyException;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.rest.client.swing.JSONParserWrapper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

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
        final JsonObject obj = new JsonObject();
        obj.addProperty("message", exception.getMessage());
        JsonArray stackTrace = new JsonArray();
        for (StackTraceElement el : exception.getStackTrace())
        {
            JsonElement jsonRep = gson.toJsonTree(el);
            stackTrace.add(jsonRep);
        }
        final JsonObject data = new JsonObject();
        data.addProperty("exception", exception.getClass().getName());
        data.add("stacktrace", stackTrace);
        final JsonElement params = getParams(exception);
        if (params != null)
        {
            data.add("params", params);
        }
        obj.add("data", data);
        final JsonObject wrapper = new JsonObject();
        wrapper.add("error", obj);
        final String json = gson.toJson(wrapper);
        return json;
    }

    private JsonElement getParams(Throwable exception)
    {
        JsonArray params = null;
        if (exception instanceof DependencyException)
        {
            params = new JsonArray();
            for (String dep : ((DependencyException) exception).getDependencies())
            {
                params.add(new JsonPrimitive(dep));
            }
        }
        return params;
    }

}
