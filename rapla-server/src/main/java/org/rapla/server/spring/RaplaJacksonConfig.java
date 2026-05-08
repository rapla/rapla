package org.rapla.server.spring;

import org.rapla.rest.JacksonObjectMapperFactory;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Server-side Jackson — applies the shared Rapla config from {@link JacksonObjectMapperFactory}. */
@Configuration
public class RaplaJacksonConfig
{
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer raplaJacksonCustomizer()
    {
        return builder -> builder.postConfigurer(JacksonObjectMapperFactory::configure);
    }
}
