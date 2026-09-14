package org.rapla.rest;

import org.rapla.rest.jackson.JacksonParserWrapper;

import java.util.function.Supplier;
import java.io.Reader;
import java.lang.reflect.Type;

public class JsonParserWrapper
{
    static Supplier<JsonParser> factory = new JacksonParserWrapper();
    public interface JsonParser
    {
        String toJson(Object object);

        <T> T fromJson(String json, Type clazz);

        <T> T fromJson(String json, Class clazz, Class container);

        <T> T fromJson(Reader json, Type clazz);

        String patch(Object unpatchedObject, Reader json);
    }

    public static Supplier<JsonParser> defaultJson() {
        return factory;
    }

    static public void setFactory( Supplier<JsonParser> factory )
    {
        JsonParserWrapper.factory = factory;
    }

    public static class WrappedJsonSerializeException extends RuntimeException
    {
        public WrappedJsonSerializeException(Throwable ex)
        {
            super(ex.getMessage(), ex);
        }
    }
}



