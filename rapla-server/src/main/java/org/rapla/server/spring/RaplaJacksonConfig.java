package org.rapla.server.spring;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.MapperFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Field-based JSON introspection — matches the model the legacy Gson serializer assumed.
 *
 * <p>Jackson defaults to JavaBeans getter introspection: it walks {@code getX/isX} accessors
 * and ignores Java's {@code transient} keyword on the underlying fields. That's wrong for
 * Rapla entities, where many getters (e.g. {@code Category.getParent()}, {@code Reservation.getAppointments()})
 * resolve references through {@code ReferenceHandler.resolver} — a {@code transient} field
 * — and cycle back to the source entity, producing infinite recursion / StackOverflowError.
 *
 * <p>This customizer flips the strategy:
 * <ul>
 *   <li>{@code FIELD = ANY}, {@code GETTER/IS_GETTER = NONE} — only stored fields are serialized.</li>
 *   <li>{@code PROPAGATE_TRANSIENT_MARKER = true} — Java {@code transient} on a field excludes it.</li>
 * </ul>
 * Result: only persistent state goes on the wire — primitive fields + the
 * {@code ReferenceHandler.links: Map<String,List<String>>} ID-ref table. Derived
 * getters that traverse the resolver are never called, so cycles are structurally impossible.
 */
@Configuration
public class RaplaJacksonConfig
{
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer raplaJacksonCustomizer()
    {
        return builder -> builder
                .featuresToEnable(MapperFeature.PROPAGATE_TRANSIENT_MARKER)
                .visibility(PropertyAccessor.FIELD,     Visibility.ANY)
                .visibility(PropertyAccessor.GETTER,    Visibility.NONE)
                .visibility(PropertyAccessor.IS_GETTER, Visibility.NONE)
                .visibility(PropertyAccessor.SETTER,    Visibility.NONE)
                .visibility(PropertyAccessor.CREATOR,   Visibility.ANY);
    }
}
