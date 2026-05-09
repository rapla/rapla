package org.rapla.server.spring;

import org.rapla.client.spring.SupplierAutoWrapperBeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

// Spring Boot 4: spring-boot-starter-jdbc isn't on the classpath, so DataSourceAutoConfiguration
// is absent and there's nothing to exclude. (In SB 3 we excluded it explicitly because the
// starter-web group used to bring jdbc transitively.)
@SpringBootApplication
@EnableConfigurationProperties(RaplaServerProperties.class)
public class RaplaSpringBootApplication
{
    @Bean
    public static BeanFactoryPostProcessor supplierAutoWrapperBeanFactoryPostProcessor()
    {
        return new SupplierAutoWrapperBeanFactoryPostProcessor();
    }

    public static void main(String[] args)
    {
        SpringApplication.run(RaplaSpringBootApplication.class, args);
    }
}
