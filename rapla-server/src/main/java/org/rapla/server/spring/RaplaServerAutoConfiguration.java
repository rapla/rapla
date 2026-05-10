package org.rapla.server.spring;

import org.rapla.spring.SupplierAutoWrapperBeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration for rapla-server. Any deployment that pulls in {@code rapla-server}
 * (rapla-app, dhbwrapla, future custom deployments) gets the full server stack wired
 * via {@code META-INF/spring/AutoConfiguration.imports}.
 *
 * <p>{@code @EnableScheduling} (PRD 019 Phase 2) registers Spring's {@code TaskScheduler}
 * so {@code @Scheduled}-annotated methods on any bean fire automatically.
 */
@AutoConfiguration
@Import({
        ServerCoreConfig.class,
        ServerServiceConfig.class,
        JwtConfig.class,
        SecurityConfig.class,
        LegacyServerBridgeConfig.class,
        RaplaJacksonConfig.class
})
@ComponentScan("org.rapla.server.spring.web")
@EnableConfigurationProperties(RaplaServerProperties.class)
@EnableScheduling
public class RaplaServerAutoConfiguration
{
    @Bean
    public static BeanFactoryPostProcessor supplierAutoWrapperBeanFactoryPostProcessor()
    {
        return new SupplierAutoWrapperBeanFactoryPostProcessor();
    }
}
