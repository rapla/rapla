package org.rapla.server.spring;

import org.rapla.spring.SupplierAutoWrapperBeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

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
public class RaplaServerAutoConfiguration
{
    @Bean
    public static BeanFactoryPostProcessor supplierAutoWrapperBeanFactoryPostProcessor()
    {
        return new SupplierAutoWrapperBeanFactoryPostProcessor();
    }
}
