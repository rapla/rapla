package org.rapla.rest;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Shared Jackson {@link ObjectMapper} configuration for client and server.
 *
 * <p>Field-based introspection (matches the model the legacy Gson serializer used):
 * Jackson defaults to JavaBeans getters and ignores Java's {@code transient} keyword.
 * That's wrong for Rapla entities, where many getters resolve cross-references through
 * a {@code transient} resolver and cycle back to the source — producing infinite
 * recursion / StackOverflowError during serialization.
 *
 * <p>Configuration:
 * <ul>
 *   <li>{@code FIELD = ANY}, {@code GETTER/IS_GETTER/SETTER = NONE}, {@code CREATOR = ANY}
 *       — only stored fields are serialized; constructor params still inject on read.</li>
 *   <li>{@code PROPAGATE_TRANSIENT_MARKER = true} — Java {@code transient} on a field excludes it.</li>
 *   <li>{@code JavaTimeModule} — handles {@code LocalDateTime}, {@code Instant}, etc.
 *       (default Gson can't reflect these under the JDK module system).</li>
 *   <li>{@code WRITE_DATES_AS_TIMESTAMPS = false} — ISO strings, not numeric arrays.</li>
 * </ul>
 *
 * <p>Result: only persistent state crosses the wire — primitive fields plus the
 * {@code ReferenceHandler.links: Map<String,List<String>>} ID-ref table. Derived
 * getters that traverse the resolver are never called, so cycles are structurally impossible.
 */
public final class JacksonObjectMapperFactory
{
    private JacksonObjectMapperFactory() {}

    /** Build a fresh ObjectMapper preconfigured for Rapla entities. */
    public static ObjectMapper create()
    {
        return configure(new ObjectMapper());
    }

    /** Apply the Rapla configuration to an existing mapper (e.g. one Spring Boot already built). */
    public static ObjectMapper configure(ObjectMapper mapper)
    {
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER);
        mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(Visibility.ANY)
                .withGetterVisibility(Visibility.NONE)
                .withIsGetterVisibility(Visibility.NONE)
                .withSetterVisibility(Visibility.NONE)
                .withCreatorVisibility(Visibility.ANY));
        return mapper;
    }
}
