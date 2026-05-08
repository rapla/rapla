package org.rapla.client.spring;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Phase 1 of PRD 002 (Swing UI DI migration).
 *
 * <p>Component-scans the Swing UI packages so Spring picks up classes that
 * carry {@code @Service} alongside their legacy {@code @DefaultImplementation}
 * annotation. Adding the {@code @Service} annotation is the work of Phase 2 —
 * with this config alone in scope, the scan finds nothing yet and produces
 * an empty Swing tier.
 */
@Configuration
@ComponentScan(
        basePackages = {
                "org.rapla.client.swing",
                "org.rapla.client.menu",
                "org.rapla.client.dialog",
                "org.rapla.client.internal",
                "org.rapla.client.event",
                "org.rapla.plugin"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = ".*\\.server\\..*"
        )
)
public class SwingClientConfig
{
}
