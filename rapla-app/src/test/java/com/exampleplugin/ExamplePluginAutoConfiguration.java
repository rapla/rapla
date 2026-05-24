package com.exampleplugin;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.rapla.server.spring.RaplaServerAutoConfiguration;

/**
 * Test fixture: a drop-in plugin's {@code @AutoConfiguration} as described in
 * PRD 045 §4 + Phase 5. Lives outside {@code org.rapla.*} to demonstrate the
 * package convention plugin authors must follow.
 *
 * <p>Used by {@code DropInPluginContractTest} (rapla-app) to verify the contract:
 * <ul>
 *   <li>{@code @AutoConfiguration(after = RaplaServerAutoConfiguration.class)}
 *       guarantees rapla beans are available before the plugin loads.</li>
 *   <li>{@code @ConditionalOnProperty} gating lets the operator disable a
 *       deployed plugin via {@code application.yml} without removing the jar.</li>
 *   <li>{@code @ComponentScan} on the plugin's own package picks up its
 *       {@code @RestController}s without touching stock rapla.</li>
 * </ul>
 *
 * <p>A real plugin jar would additionally list this class in its
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * The test bypasses the imports file via Spring Boot's
 * {@code ApplicationContextRunner.withConfiguration(AutoConfigurations.of(...))}.
 */
@AutoConfiguration(after = RaplaServerAutoConfiguration.class)
@ConditionalOnProperty(prefix = "rapla.plugins", name = "example.enabled", matchIfMissing = true)
@ComponentScan("com.exampleplugin")
public class ExamplePluginAutoConfiguration
{
    @Bean
    public ExamplePluginBean exampleBean()
    {
        return new ExamplePluginBean();
    }

    public static class ExamplePluginBean
    {
        public String greet() { return "hello from the example plugin"; }
    }
}
