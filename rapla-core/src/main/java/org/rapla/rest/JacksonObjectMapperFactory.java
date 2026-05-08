package org.rapla.rest;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared Jackson {@link JsonMapper} configuration for client and server.
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
 * </ul>
 *
 * <p>Jackson 3 notes (vs the legacy 2.x implementation):
 * <ul>
 *   <li>{@code JavaTimeModule} is built into {@code jackson-databind} 3.x — no explicit
 *       {@code registerModule} needed.</li>
 *   <li>{@code WRITE_DATES_AS_TIMESTAMPS} defaults to {@code false} in 3.x (ISO-8601), so
 *       we no longer need the explicit {@code disable(...)}.</li>
 *   <li>{@code @Json…} annotations (incl. {@link Visibility}) deliberately stayed at
 *       {@code com.fasterxml.jackson.annotation.*} — that import line is unchanged.</li>
 * </ul>
 *
 * <p>Result: only persistent state crosses the wire — primitive fields plus the
 * {@code ReferenceHandler.links: Map<String,List<String>>} ID-ref table. Derived
 * getters that traverse the resolver are never called, so cycles are structurally impossible.
 */
public final class JacksonObjectMapperFactory
{
    private JacksonObjectMapperFactory() {}

    /** Build a fresh JsonMapper preconfigured for Rapla entities. */
    public static JsonMapper create()
    {
        return configure(JsonMapper.builder()).build();
    }

    /** Apply the Rapla configuration to a JsonMapper builder
     *  (e.g. one Spring Boot already created via {@code JsonMapperBuilderCustomizer}). */
    public static JsonMapper.Builder configure(JsonMapper.Builder builder)
    {
        return builder
                .enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER)
                .changeDefaultVisibility(vc -> vc
                        .withFieldVisibility(Visibility.ANY)
                        .withGetterVisibility(Visibility.NONE)
                        .withIsGetterVisibility(Visibility.NONE)
                        .withSetterVisibility(Visibility.NONE)
                        .withCreatorVisibility(Visibility.ANY));
    }
}
