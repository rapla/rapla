package org.rapla.server.provider.resteasy;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
@Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_OCTET_STREAM})
public class MessageHTMLWriter implements MessageBodyWriter<WebApplicationException>
{
    @Override
    public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType)
    {
        return MediaType.TEXT_HTML_TYPE.isCompatible(mediaType) || MediaType.APPLICATION_OCTET_STREAM_TYPE.isCompatible( mediaType);
    }

    @Override
    public long getSize(final WebApplicationException e,
                        final Class<?> aClass,
                        final Type type,
                        final Annotation[] annotations,
                        final MediaType mediaType) {
        return 0;
    }

    @Override
    public void writeTo(WebApplicationException e, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, Object> multivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException
    {
        outputStream.write( e.getMessage().toString().getBytes());
    }
}
