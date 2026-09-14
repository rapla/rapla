package org.rapla.server.spring;

import org.rapla.rest.JacksonObjectMapperFactory;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Server-side Jackson 3 — applies the shared Rapla config from {@link JacksonObjectMapperFactory}.
 *  Spring Boot 4 default mapper is Jackson 3 ({@code tools.jackson.databind.json.JsonMapper}); this
 *  customizer hooks the build of that mapper before Spring caches it.
 *  <p>swagger-core / OpenAPI schema introspection uses Jackson 2 in a separate config bean
 *  (rapla-app's {@code SwaggerJacksonConfig}); rapla-server doesn't have swagger-core on its
 *  classpath. See {@code docs/architecture/rest-api.md} §"OpenAPI / Swagger spec caveat". */
@Configuration
public class RaplaJacksonConfig
{
    @Bean
    public JsonMapperBuilderCustomizer raplaJacksonCustomizer()
    {
        return JacksonObjectMapperFactory::configure;
    }
}
