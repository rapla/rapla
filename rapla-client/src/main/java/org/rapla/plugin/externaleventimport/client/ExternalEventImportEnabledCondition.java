package org.rapla.plugin.externaleventimport.client;

import org.rapla.plugin.externaleventimport.ExternalEventImportPlugin;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Spring {@code @Conditional} matcher that activates beans only when the property
 * {@value ExternalEventImportPlugin#ENABLE_PROPERTY} is set to {@code true}.
 * Equivalent to Spring Boot's {@code @ConditionalOnProperty}, but reimplemented
 * here because rapla-client deliberately stays on plain {@code spring-context}
 * (no {@code spring-boot-autoconfigure} dependency).
 */
public class ExternalEventImportEnabledCondition implements Condition
{
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata)
    {
        return Boolean.TRUE.equals(context.getEnvironment().getProperty(ExternalEventImportPlugin.ENABLE_PROPERTY, Boolean.class, Boolean.FALSE));
    }
}
