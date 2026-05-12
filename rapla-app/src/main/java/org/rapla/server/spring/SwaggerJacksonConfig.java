package org.rapla.server.spring;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Json;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
        ModelResolver resolver = new ModelResolver(mapper);
        ModelConverters.getInstance().addConverter(resolver);
        return resolver;
    }
}
