package org.rapla.server.spring.graphql;

import graphql.GraphQL;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.GraphQLSchema;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaException;
import org.rapla.storage.StorageOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.execution.SubscriptionExceptionResolver;

/**
 * PRD 035 Cut C — custom {@link GraphQlSource} backed by an
 * {@link AtomicReference} so the schema can be hot-swapped on DynamicType
 * admin changes without restarting the JVM.
 *
 * <p>Replaces Spring Boot's default auto-config bean (which has
 * {@code @ConditionalOnMissingBean(GraphQlSource.class)}). Replicates the
 * auto-config wiring: static schema-locations + discovered
 * {@link RuntimeWiringConfigurer}s, {@link Instrumentation}s, exception
 * resolvers, and {@link GraphQlSourceBuilderCustomizer}s. Layers on top:
 *
 * <ul>
 *   <li>Per-DynamicType generated classification SDL from
 *       {@link ClassificationSdlGenerator}, concatenated with the static
 *       {@code schema.graphqls}.</li>
 *   <li>{@link GeneratedClassificationWiring} runs as an additional
 *       {@code configureRuntimeWiring} step to install the
 *       {@code Classification} TypeResolver + per-attribute DataFetchers.</li>
 *   <li>{@link #rebuild()} re-runs the build with the current DynamicType
 *       set; a SHA-256 hash of the generated SDL short-circuits no-op
 *       rebuilds (annotation-only edits etc.).</li>
 * </ul>
 *
 * <p><b>In-flight queries:</b> Spring's
 * {@code ExecutionGraphQlService.execute()} reads {@link #graphQl()} once per
 * request, so each request finishes on the {@link GraphQL} instance it
 * started with — schema swaps are safe mid-request.
 *
 * <p>Hot-swap trigger lives in a separate component (PRD 035 task 5 — the
 * UpdateEvent listener); this class just exposes the swap mechanism.
 */
public class HotSwappableGraphQlSource implements GraphQlSource
{
    private static final Logger LOGGER = LoggerFactory.getLogger(HotSwappableGraphQlSource.class);

    /** Where the static SDL lives in the classpath. Mirrors the default
     *  Spring Boot location, hardcoded since rapla has exactly one file. */
    private static final String STATIC_SCHEMA_LOCATION_PATTERN = "classpath:graphql/*.graphqls";

    private final ResourcePatternResolver resourceResolver;
    private final ObjectProvider<RuntimeWiringConfigurer> wiringConfigurers;
    private final ObjectProvider<DataFetcherExceptionResolver> exceptionResolvers;
    private final ObjectProvider<SubscriptionExceptionResolver> subscriptionExceptionResolvers;
    private final ObjectProvider<Instrumentation> instrumentations;
    private final ObjectProvider<GraphQlSourceBuilderCustomizer> sourceBuilderCustomizers;
    private final StorageOperator operator;
    private final GeneratedClassificationWiring generatedWiring;

    private final AtomicReference<GraphQlSource> delegate = new AtomicReference<>();
    /** SHA-256 of the most-recently-applied generated SDL. Empty until first build. */
    private volatile String lastGeneratedSdlHash = "";

    public HotSwappableGraphQlSource(
            ResourcePatternResolver resourceResolver,
            ObjectProvider<RuntimeWiringConfigurer> wiringConfigurers,
            ObjectProvider<DataFetcherExceptionResolver> exceptionResolvers,
            ObjectProvider<SubscriptionExceptionResolver> subscriptionExceptionResolvers,
            ObjectProvider<Instrumentation> instrumentations,
            ObjectProvider<GraphQlSourceBuilderCustomizer> sourceBuilderCustomizers,
            StorageOperator operator)
    {
        this.resourceResolver = resourceResolver;
        this.wiringConfigurers = wiringConfigurers;
        this.exceptionResolvers = exceptionResolvers;
        this.subscriptionExceptionResolvers = subscriptionExceptionResolvers;
        this.instrumentations = instrumentations;
        this.sourceBuilderCustomizers = sourceBuilderCustomizers;
        this.operator = operator;
        this.generatedWiring = new GeneratedClassificationWiring(operator);
        // Build the initial source eagerly. The bean is consumed by
        // ExecutionGraphQlService; failure here = boot failure (correct).
        this.delegate.set(buildSource());
    }

    @Override
    public GraphQL graphQl()
    {
        return delegate.get().graphQl();
    }

    @Override
    public GraphQLSchema schema()
    {
        return delegate.get().schema();
    }

    /**
     * Rebuild the GraphQL schema from the current DynamicType set. Returns
     * {@code true} if the schema actually changed (generated SDL hash diff),
     * {@code false} if the rebuild was a no-op. Called by the UpdateEvent
     * listener after debouncing.
     */
    public boolean rebuild()
    {
        Collection<DynamicType> types = fetchDynamicTypes();
        String newSdl = ClassificationSdlGenerator.generate(types);
        String newHash = sha256(newSdl);
        if (newHash.equals(lastGeneratedSdlHash))
        {
            LOGGER.debug("GraphQL schema rebuild skipped — generated SDL hash unchanged");
            return false;
        }
        GraphQlSource newSource = buildSource(types, newSdl);
        delegate.set(newSource);
        lastGeneratedSdlHash = newHash;
        LOGGER.info("GraphQL schema rebuilt — {} DynamicType classification(s) regenerated", types.size());
        return true;
    }

    /** Initial build path — fetches types and SDL itself. */
    private GraphQlSource buildSource()
    {
        Collection<DynamicType> types = fetchDynamicTypes();
        String generatedSdl = ClassificationSdlGenerator.generate(types);
        lastGeneratedSdlHash = sha256(generatedSdl);
        return buildSource(types, generatedSdl);
    }

    /** Rebuild path — caller passes the freshly-generated state. */
    private GraphQlSource buildSource(Collection<DynamicType> types, String generatedSdl)
    {
        try
        {
            List<Resource> resources = resolveStaticSchemaResources();
            if (!generatedSdl.isEmpty())
            {
                resources.add(new ByteArrayResource(
                        generatedSdl.getBytes(StandardCharsets.UTF_8),
                        "<generated-classifications>"));
            }
            GraphQlSource.SchemaResourceBuilder builder = GraphQlSource.schemaResourceBuilder()
                    .schemaResources(resources.toArray(new Resource[0]))
                    .configureRuntimeWiring(wb -> generatedWiring.configure(wb, types));

            wiringConfigurers.orderedStream().forEach(builder::configureRuntimeWiring);
            builder.exceptionResolvers(exceptionResolvers.orderedStream().toList());
            builder.subscriptionExceptionResolvers(subscriptionExceptionResolvers.orderedStream().toList());
            builder.instrumentation(instrumentations.orderedStream().toList());
            sourceBuilderCustomizers.orderedStream().forEach(c -> c.customize(builder));

            return builder.build();
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Failed to load static GraphQL schema resources", e);
        }
    }

    private List<Resource> resolveStaticSchemaResources() throws IOException
    {
        Resource[] found = resourceResolver.getResources(STATIC_SCHEMA_LOCATION_PATTERN);
        List<Resource> out = new ArrayList<>(found.length);
        for (Resource r : found)
        {
            // Defensive copy: re-read into a ByteArrayResource so the builder
            // doesn't hold long-lived JAR-entry InputStream handles across
            // rebuilds.
            try (InputStream in = r.getInputStream())
            {
                out.add(new ByteArrayResource(in.readAllBytes(), r.getDescription()));
            }
        }
        if (out.isEmpty())
        {
            throw new IllegalStateException(
                    "No GraphQL schema resources matched " + STATIC_SCHEMA_LOCATION_PATTERN);
        }
        return out;
    }

    private Collection<DynamicType> fetchDynamicTypes()
    {
        try
        {
            Collection<DynamicType> types = operator.getDynamicTypes();
            return types == null ? List.of() : types;
        }
        catch (RaplaException e)
        {
            LOGGER.warn("Failed to fetch DynamicTypes for GraphQL schema build; classification types will be empty", e);
            return List.of();
        }
    }

    private static String sha256(String s)
    {
        if (s == null || s.isEmpty()) return "";
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
