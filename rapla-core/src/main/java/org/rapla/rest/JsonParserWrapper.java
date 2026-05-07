package org.rapla.rest;

import org.rapla.rest.gson.GsonParserWrapper;
import org.rapla.rest.jackson.JacksonParserWrapper;

import jakarta.inject.Provider;
import java.io.Reader;
import java.lang.reflect.Type;

public class JsonParserWrapper
{
    static Provider<JsonParser> factory = new GsonParserWrapper();
    public interface JsonParser
    {
        String toJson(Object object);

        <T> T fromJson(String json, Type clazz);

        <T> T fromJson(String json, Class clazz, Class container);

        <T> T fromJson(Reader json, Type clazz);

        String patch(Object unpatchedObject, Reader json);
    }

    public static Provider<JsonParser> defaultJson() {
        return factory;
    }

    static public void setFactory( Provider<JsonParser> factory )
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



