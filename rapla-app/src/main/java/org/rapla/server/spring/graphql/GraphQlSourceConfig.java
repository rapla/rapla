package org.rapla.server.spring.graphql;

import graphql.execution.instrumentation.Instrumentation;
import org.rapla.framework.RaplaLocale;
import org.rapla.storage.StorageOperator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.execution.SubscriptionExceptionResolver;

/**
 * PRD 035 Cut C — replaces Spring Boot's default {@code graphQlSource} bean
 * with {@link HotSwappableGraphQlSource}. The auto-config bean is annotated
 * {@code @ConditionalOnMissingBean(GraphQlSource.class)} so this explicit
 * definition wins.
 *
 * <p>All the same dependencies Spring Boot's auto-config injects flow
 * through {@code ObjectProvider}s so any user-registered
 * {@link RuntimeWiringConfigurer}, {@link Instrumentation}, etc. still
 * applies — we add the per-DynamicType classification wiring on top, not
 * instead.
 */
@Configuration
public class GraphQlSourceConfig
{
    @Bean
    public HotSwappableGraphQlSource graphQlSource(
            ResourcePatternResolver resourceResolver,
            ObjectProvider<RuntimeWiringConfigurer> wiringConfigurers,
            ObjectProvider<DataFetcherExceptionResolver> exceptionResolvers,
            ObjectProvider<SubscriptionExceptionResolver> subscriptionExceptionResolvers,
            ObjectProvider<Instrumentation> instrumentations,
            ObjectProvider<GraphQlSourceBuilderCustomizer> sourceBuilderCustomizers,
            StorageOperator operator,
            RaplaLocale raplaLocale)
    {
        return new HotSwappableGraphQlSource(
                resourceResolver,
                wiringConfigurers,
                exceptionResolvers,
                subscriptionExceptionResolvers,
                instrumentations,
                sourceBuilderCustomizers,
                operator,
                raplaLocale);
    }
}
