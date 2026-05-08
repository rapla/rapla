package org.rapla.rest.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.rapla.logger.ConsoleLogger;
import org.rapla.logger.NullLogger;
import org.rapla.rest.JsonParserWrapper;
import org.rapla.rest.client.RemoteConnectException;
import org.rapla.rest.client.internal.isodate.ISODateTimeFormat;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.sync.SynchronizedCompletablePromise;

import jakarta.inject.Provider;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class JacksonParserWrapper  implements Provider<JsonParserWrapper.JsonParser> {
    static ConsoleLogger logger = new ConsoleLogger();
    @Override
    public JsonParserWrapper.JsonParser get() {
        return new JsonParserWrapper.JsonParser() {
            ObjectMapper gson = defaultObjectMapper();

            @Override
            public String toJson(Object object) {
                try {
                    return gson.writeValueAsString(object);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public <T> T fromJson(String json, Class clazz, Class container)  {
                try {
                    return (T) deserializeResultWithJackson(json, clazz, container);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public Object fromJson(String json, Type type) {
                try
                {
                    final JavaType javaType = gson.getTypeFactory().constructType(type);
                    return gson.readValue(json, javaType);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public <T> T fromJson(Reader json, Type type) {
                try {
                    final JavaType javaType = gson.getTypeFactory().constructType(type);
                    return gson.readValue(json, javaType);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public String patch(Object unpatchedObject, Reader json) {
                try {
                    return patchGson(unpatchedObject, json);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /** Create a default GsonBuilder with some extra types defined. */
    private static ObjectMapper defaultObjectMapper()
    {
        ObjectMapper objectMapper = new ObjectMapper();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
        objectMapper.setTimeZone( TimeZone.getTimeZone("UTC"));
        objectMapper.setDateFormat(df);
        objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        SimpleModule module = new SimpleModule();
        module.addSerializer(Promise.class, new JsonSerializer<Promise>()
        {
            @Override
            public void serialize(Promise promise, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException
            {
                try
                {
                    final Object result = SynchronizedCompletablePromise.waitFor(promise, 1000, logger);
                    JsonSerializer<Object> serializer = serializerProvider.getUnknownTypeSerializer(result.getClass());
                    serializer.serialize(result,jsonGenerator,serializerProvider);
                    //serializerProvider.ser
                }
                catch (Exception e)
                {
                    throw new IOException( e);
                }
            }
        });
        objectMapper.registerModule( module);
        objectMapper.registerModule( new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }



    private static String patchGson(Object unpatchedObject, Reader json) throws IOException {
        final ObjectMapper mapper = defaultObjectMapper();
        JsonNode unpatchedObjectJson = mapper.valueToTree(unpatchedObject);
        JsonNode patchElement = mapper.readTree(json);
        final JacksonMergePatch patch = JacksonMergePatch.fromJson(patchElement);
        final JsonNode patchedObjectJson = patch.apply(unpatchedObjectJson);
        return patchedObjectJson.toString();
    }


    private static Object deserializeResultWithJackson(String unparsedResult, Class resultType, Class container) throws IOException {
        if (resultType.equals(void.class))
        {
            return null;
        }
        ObjectMapper mapper = defaultObjectMapper();
        final JsonNode resultElement = mapper.readTree(unparsedResult);
        Object resultObject;
        //Gson  gson = defaultObjectMapper().create();
        if (container != null && !Object.class.equals(container))
        {
            if (List.class.equals(container) || Collection.class.equals(container) || Set.class.equals(container))
            {
                if (!resultElement.isArray())
                {
                    throw new RemoteConnectException("Array expected as json result");
                }
                Collection<Object> result = Set.class.equals(container) ? new LinkedHashSet<>(): new ArrayList<>();
                ArrayNode list = (ArrayNode)resultElement;
                for (Iterator<JsonNode> it = list.elements();it.hasNext();)
                {
                    JsonNode element = it.next();
                    Object obj = mapper.reader().forType( resultType).readValue( element);
                    result.add(obj);
                }
                resultObject = result;
            }
            else if (Map.class.equals(container))
            {
                if (!resultElement.isObject())
                {
                    throw new RemoteConnectException("JsonObject expected as json result");
                }
                final ObjectNode map = ((ObjectNode)resultElement);
                Map<String, Object> result = new LinkedHashMap<String, Object>();
                for (Iterator<Map.Entry<String, JsonNode>> it = map.fields();it.hasNext();)
                {
                    Map.Entry<String, JsonNode> entry = it.next();
                    String key = entry.getKey();
                    JsonNode element = entry.getValue();
                    Object obj = mapper.reader().forType(resultType).readValue( element);
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
            resultObject = mapper.reader().forType(resultType).readValue(resultElement);
        }
        return resultObject;
    }




}
