package org.rapla.rest.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.rapla.logger.ConsoleLogger;
import org.rapla.rest.JsonParserWrapper;
import org.rapla.rest.client.RemoteConnectException;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.sync.SynchronizedCompletablePromise;

import java.util.function.Supplier;
import java.io.Reader;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class JacksonParserWrapper  implements Supplier<JsonParserWrapper.JsonParser> {
    static ConsoleLogger logger = new ConsoleLogger();
    @Override
    public JsonParserWrapper.JsonParser get() {
        return new JsonParserWrapper.JsonParser() {
            ObjectMapper mapper = defaultObjectMapper();

            @Override
            public String toJson(Object object) {
                return mapper.writeValueAsString(object);
            }

            @Override
            public <T> T fromJson(String json, Class clazz, Class container)  {
                return (T) deserializeResultWithJackson(json, clazz, container);
            }

            @Override
            public Object fromJson(String json, Type type) {
                final JavaType javaType = mapper.getTypeFactory().constructType(type);
                return mapper.readValue(json, javaType);
            }

            @Override
            public <T> T fromJson(Reader json, Type type) {
                final JavaType javaType = mapper.getTypeFactory().constructType(type);
                return mapper.readValue(json, javaType);
            }

            @Override
            public String patch(Object unpatchedObject, Reader json) {
                return applyJsonMergePatch(unpatchedObject, json);
            }
        };
    }

    /** Create a default {@link ObjectMapper} with some extra types defined. */
    private static ObjectMapper defaultObjectMapper()
    {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
        SimpleModule module = new SimpleModule();
        module.addSerializer(Promise.class, new ValueSerializer<Promise>()
        {
            @Override
            public void serialize(Promise promise, JsonGenerator jsonGenerator, SerializationContext ctx)
            {
                try
                {
                    final Object result = SynchronizedCompletablePromise.waitFor(promise, 1000, logger);
                    ValueSerializer<Object> serializer = ctx.findValueSerializer(result.getClass());
                    serializer.serialize(result, jsonGenerator, ctx);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        });
        return JsonMapper.builder()
                .defaultTimeZone(TimeZone.getTimeZone("UTC"))
                .defaultDateFormat(df)
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                .changeDefaultVisibility(vc -> vc
                        .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                        .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withCreatorVisibility(JsonAutoDetect.Visibility.NONE))
                .addModule(module)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }



    private static String applyJsonMergePatch(Object unpatchedObject, Reader json) {
        final ObjectMapper mapper = defaultObjectMapper();
        JsonNode unpatchedObjectJson = mapper.valueToTree(unpatchedObject);
        JsonNode patchElement = mapper.readTree(json);
        final JacksonMergePatch patch = JacksonMergePatch.fromJson(patchElement);
        final JsonNode patchedObjectJson = patch.apply(unpatchedObjectJson);
        return patchedObjectJson.toString();
    }


    private static Object deserializeResultWithJackson(String unparsedResult, Class resultType, Class container) {
        if (resultType.equals(void.class))
        {
            return null;
        }
        ObjectMapper mapper = defaultObjectMapper();
        final JsonNode resultElement = mapper.readTree(unparsedResult);
        Object resultObject;
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
                for (JsonNode element : list)
                {
                    Object obj = mapper.reader().forType(resultType).readValue(element);
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
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<String, JsonNode> entry : map.properties())
                {
                    String key = entry.getKey();
                    JsonNode element = entry.getValue();
                    Object obj = mapper.reader().forType(resultType).readValue(element);
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
