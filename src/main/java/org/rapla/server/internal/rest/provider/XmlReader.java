package org.rapla.server.internal.rest.provider;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlRootElement;

@Provider
@Consumes(MediaType.APPLICATION_XML)
public class XmlReader<T> implements MessageBodyReader<T>
{

    private Map<Class<T>, JAXBContext> contextMap = new HashMap<>();

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return type.getAnnotation(XmlRootElement.class) != null;
    }

    @Override
    public T readFrom(Class<T> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders,
            InputStream entityStream) throws IOException, WebApplicationException
    {
        try
        {
            JAXBContext jaxbContext = contextMap.get(type);
            if (jaxbContext == null)
            {
                jaxbContext = JAXBContext.newInstance(type);
                contextMap.put(type, jaxbContext);
            }
            final Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            return (T) unmarshaller.unmarshal(entityStream);
        }
        catch (Exception e)
        {
            throw new IOException(e.getMessage(), e);
        }
    }

}
