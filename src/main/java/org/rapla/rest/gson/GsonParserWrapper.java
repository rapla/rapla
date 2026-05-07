package org.rapla.rest.gson;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.rapla.logger.NullLogger;
import org.rapla.rest.GenericObjectSerializable;
import org.rapla.rest.JsonParserWrapper;
import org.rapla.rest.client.RemoteConnectException;
import org.rapla.rest.client.internal.isodate.ISODateTimeFormat;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.sync.SynchronizedCompletablePromise;

import jakarta.inject.Provider;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.*;

public class GsonParserWrapper implements Provider<JsonParserWrapper.JsonParser> {
    GsonBuilder builder = defaultGsonBuilder();

    public JsonParserWrapper.JsonParser get() {
        return new JsonParserWrapper.JsonParser() {
            Gson gson = builder.create();

            @Override
            public String toJson(Object object) {
                return gson.toJson(object);
            }

            @Override
            public <T> T fromJson(String json, Class clazz, Class container) {
                return (T) deserializeResultWithGson(json, clazz, container);
            }

            @Override
            public Object fromJson(String json, Type clazz) {
                return gson.fromJson(json, clazz);
            }

            @Override
            public <T> T fromJson(Reader json, Type clazz) {
                return gson.fromJson(json, clazz);
            }

            @Override
            public String patch(Object unpatchedObject, Reader json) {
                return patchGson(unpatchedObject, json);
            }
        };
    }


    /** Create a default GsonBuilder with some extra types defined. */
    private static GsonBuilder defaultGsonBuilder()
    {
        final GsonBuilder gb = new GsonBuilder();
        gb.registerTypeAdapter(Set.class, new InstanceCreator<Set<Object>>()
        {
            @Override
            public Set<Object> createInstance(final Type arg0)
            {
                return new LinkedHashSet<Object>();
            }
        });
        gb.registerTypeHierarchyAdapter(Promise.class, new PromiseAdapter());
        Map<Type, InstanceCreator<?>> instanceCreators = new LinkedHashMap<Type, InstanceCreator<?>>();
        instanceCreators.put(Map.class, new InstanceCreator<Map>()
        {
            public Map createInstance(Type type)
            {
                return new LinkedHashMap();
            }
        });
        instanceCreators.put(Set.class, new InstanceCreator<Set>()
        {
            public Set createInstance(Type type)
            {
                return new LinkedHashSet();
            }
        });
//        ConstructorConstructor constructorConstructor = new ConstructorConstructor(instanceCreators);
//        FieldNamingStrategy fieldNamingPolicy = FieldNamingPolicy.IDENTITY;
//        Excluder excluder = Excluder.DEFAULT;
//        JsonAdapterAnnotationTypeAdapterFactory jsonAdapterFactory= new JsonAdapterAnnotationTypeAdapterFactory(constructorConstructor);
//        final ReflectiveTypeAdapterFactory reflectiveTypeAdapterFactory = new ReflectiveTypeAdapterFactory(constructorConstructor, fieldNamingPolicy, excluder, jsonAdapterFactory);
//
//        gb.registerTypeAdapterFactory(new MapTypeAdapterFactory(constructorConstructor, false));
        //gb.registerTypeAdapterFactory(new MyAdaptorFactory(reflectiveTypeAdapterFactory));
        gb.registerTypeAdapter(Date.class, new GmtDateTypeAdapter());
        GsonBuilder configured = gb.disableHtmlEscaping();
        return configured;
    }



    private static String patchGson(Object unpatchedObject, Reader json) {
        final Gson gs = defaultGsonBuilder().create();
        JsonElement unpatchedObjectJson = gs.toJsonTree(unpatchedObject);
        JsonElement patchElement = new com.google.gson.JsonParser().parse(json);
        final JsonMergePatch patch = JsonMergePatch.fromJson(patchElement);
        final JsonElement patchedObjectJson = patch.apply(unpatchedObjectJson);
        return gs.toJson(patchedObjectJson);
    }

    private static Object deserializeResultWithGson(String unparsedResult, Type resultType, Type container)
    {
        if (resultType.equals(void.class))
        {
            return null;
        }

        com.google.gson.JsonParser jsonParser = new com.google.gson.JsonParser();
        final JsonElement resultElement;
        try
        {
            resultElement = jsonParser.parse(unparsedResult);
        }
        catch (JsonParseException ex)
        {
            throw new RemoteConnectException("Unparsable json " + ex.getMessage() + ":" + unparsedResult);
        }

        Object resultObject;
        Gson  gson = defaultGsonBuilder().create();
        if (container != null && !Object.class.equals(container))
        {
            if (List.class.equals(container) || Collection.class.equals(container) || Set.class.equals(container))
            {
                if (!resultElement.isJsonArray())
                {
                    throw new RemoteConnectException("Array expected as json result");
                }
                Collection<Object> result = Set.class.equals(container) ? new LinkedHashSet<>(): new ArrayList<>();
                JsonArray list = resultElement.getAsJsonArray();
                for (JsonElement element : list)
                {
                    Object obj = gson.fromJson(element, resultType);
                    result.add(obj);
                }
                resultObject = result;
            }
            else if (Map.class.equals(container))
            {
                if (!resultElement.isJsonObject())
                {
                    throw new RemoteConnectException("JsonObject expected as json result");
                }
                final JsonObject map = resultElement.getAsJsonObject();
                Map<String, Object> result = new LinkedHashMap<String, Object>();
                for (Map.Entry<String, JsonElement> entry : map.entrySet())
                {
                    String key = entry.getKey();
                    JsonElement element = entry.getValue();
                    Object obj = gson.fromJson(element, resultType);
                    result.put(key, obj);
                }
                resultObject = result;
            }
            else
            {
                throw new RemoteConnectException("List,Set or Map expected as json container");
            }
        }
        else
        {
            resultObject = gson.fromJson(resultElement, resultType);
        }
        return resultObject;
    }


    private static class PromiseAdapter implements JsonSerializer<Promise>
    {
        NullLogger logger = new NullLogger();

        @Override
        public JsonElement serialize(Promise promise, Type type, JsonSerializationContext jsonSerializationContext)
        {
            final Object result;
            try
            {
                result = SynchronizedCompletablePromise.waitFor(promise, -1, logger);
            }
            catch (Throwable ex)
            {
                throw new JsonParserWrapper.WrappedJsonSerializeException(ex);
            }
            return jsonSerializationContext.serialize(result);
        }
    }

    private static class GmtDateTypeAdapter implements JsonSerializer<Date>, JsonDeserializer<Date>
    {

        private GmtDateTypeAdapter()
        {
        }

        @Override
        public synchronized JsonElement serialize(Date date, Type type, JsonSerializationContext jsonSerializationContext)
        {
            String timestamp = ISODateTimeFormat.INSTANCE.formatTimestamp(date);
            return new JsonPrimitive(timestamp);
        }

        @Override
        public synchronized Date deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext)
        {
            String asString = jsonElement.getAsString();
            try
            {
                Date timestamp = ISODateTimeFormat.INSTANCE.parseTimestamp(asString);
                return timestamp;
            }
            catch (Exception e)
            {
                throw new JsonSyntaxException(asString, e);
            }
        }
    }

   /* private static class MyAdaptorFactory implements TypeAdapterFactory
    {
        ReflectiveTypeAdapterFactory reflectiveTypeAdapterFactory;

        public MyAdaptorFactory(ReflectiveTypeAdapterFactory reflectiveTypeAdapterFactory)
        {
            this.reflectiveTypeAdapterFactory = reflectiveTypeAdapterFactory;
        }

        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
        {
            Class<? super T> raw = type.getRawType();
            if (GenericObjectSerializable.class.isAssignableFrom(raw))
            {
                return reflectiveTypeAdapterFactory.create(gson, type);
            }
            else
            {
                return null;
            }
        }
    }*/
}
