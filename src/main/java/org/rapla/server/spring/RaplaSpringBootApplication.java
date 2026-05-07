package org.rapla.server.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class
})
@EnableConfigurationProperties(RaplaServerProperties.class)
public class RaplaSpringBootApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(RaplaSpringBootApplication.class, args);
    }
}
