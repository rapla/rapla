package org.rapla.server.spring;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Json;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.framework.DefaultConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Field-based ModelResolver for swagger-core's schema introspection.
 *
 * <p>Rapla's runtime serializer (Jackson 3, configured in
 * {@code rapla-core/.../JacksonObjectMapperFactory}) is field-based — only
 * {@code private} fields are visible, getters/setters are ignored, Java
 * {@code transient} excludes a field. swagger-core 2.x's default
 * ModelResolver uses Jackson 2 with the OPPOSITE rule (getter/setter
 * introspection), which means {@code /api/v3/api-docs} describes a
 * different property set than what {@code Reservation}/{@code Allocatable}
 * etc. actually serialize.
 *
 * <p>This bean mirrors {@code JacksonObjectMapperFactory}'s visibility rules
 * onto a Jackson 2 {@code ObjectMapper}, wraps it in a {@link ModelResolver},
 * and registers it via {@link ModelConverters#addConverter(io.swagger.v3.core.converter.ModelConverter)}
 * — the {@code @Bean} alone is not sufficient because swagger-core's
 * {@code ModelConverters} singleton may be initialised before Spring wires
 * the bean (springdoc-openapi #2574). After this:
 *
 * <ul>
 *   <li>Schema in {@code /api/v3/api-docs} matches the actual wire payload
 *       property-for-property.</li>
 *   <li>The {@code IllegalArgumentException: Conflicting setter definitions for
 *       property "value"} boot warning on {@code DefaultConfiguration} goes away
 *       because the three overloaded {@code setValue} methods are no longer
 *       discovered.</li>
 *   <li>The {@code openapi-generator-cli} produces TypeScript DTOs that match
 *       the JSON the SPA actually receives.</li>
 * </ul>
 *
 * <p>This shim is obsolete once swagger-core 3.x (Jackson 3 native) ships
 * and SpringDoc 4.x picks it up — see swagger-core #4991, expected 12-24
 * months out as of 2026-05-13. See {@code docs/architecture/rest-api.md}
 * §"OpenAPI / Swagger spec caveat" for the full architecture rationale.
 */
@Configuration
public class SwaggerJacksonConfig
{
    @Bean
    public ModelResolver swaggerModelResolver()
    {
        ObjectMapper mapper = Json.mapper().copy();
        mapper.setVisibility(mapper.getVisibilityChecker()
                .withFieldVisibility(Visibility.ANY)
                .withGetterVisibility(Visibility.NONE)
                .withIsGetterVisibility(Visibility.NONE)
                .withSetterVisibility(Visibility.NONE)
                .withCreatorVisibility(Visibility.ANY));
        mapper.enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER);

        // Suppress "Conflicting setter definitions" warnings for classes with
        // overloaded setters. Mixins are scoped to THIS ObjectMapper only —
        // rapla-core's entity classes stay clean of Jackson annotations, and
        // the Jackson 3 runtime mapper never sees the @JsonIgnore on the
        // mixin abstract methods. Adding a class here requires:
        //   1. an abstract mixin with the conflicting setter signatures
        //      annotated @JsonIgnore (see the two below);
        //   2. a mapper.addMixIn(realClass, mixinClass) call.
        // See docs/architecture/rest-api.md §"OpenAPI / Swagger spec caveat"
        // for the broader rationale.
        mapper.addMixIn(DefaultConfiguration.class, DefaultConfigurationSwaggerMixin.class);
        mapper.addMixIn(RaplaMapImpl.class, RaplaMapImplSwaggerMixin.class);

        ModelResolver resolver = new ModelResolver(mapper);
        ModelConverters.getInstance().addConverter(resolver);
        return resolver;
    }

    /**
     * Swagger-only mixin for {@link DefaultConfiguration}. Three
     * {@code setValue(...)} overloads — String/int/boolean — all write to the
     * same private {@code value} field. swagger-core 2.x's
     * {@code POJOPropertyBuilder} can't disambiguate them as setters for the
     * "value" property and logs an {@code IllegalArgumentException}. Marking
     * the int/boolean overloads {@code @JsonIgnore} here removes them from
     * the swagger property-discovery pass without touching rapla-core.
     *
     * <p>The {@code @JsonProperty} on the {@code value} field is a rescue —
     * Jackson 2's "any-ignorals propagate" rule would otherwise hide the
     * entire "value" property from the schema because two of its accessors
     * are marked {@code @JsonIgnore}. {@code @JsonProperty} on the field
     * explicitly tells Jackson "the field is a property regardless of what
     * the accessors say".
     */
    abstract static class DefaultConfigurationSwaggerMixin
    {
        @JsonProperty String value;
        @JsonIgnore abstract void setValue(int intValue);
        @JsonIgnore abstract void setValue(boolean selected);
    }

    /**
     * Swagger-only mixin for {@link RaplaMapImpl}. Two {@code setResolver(...)}
     * overloads — public {@code (EntityResolver)} and private
     * {@code (Map<String, ? extends EntityReferencer>)} — collide on the
     * "resolver" property name. The map overload is an internal helper, never
     * the JSON-serialization target; hidden here from swagger introspection.
     */
    abstract static class RaplaMapImplSwaggerMixin
    {
        @JsonIgnore abstract void setResolver(Map<String, ?> map);
    }
}
