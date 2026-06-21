package org.rapla.server.spring.graphql;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.rapla.entities.extensionpoints.FunctionDescriptor;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * PRD 073 — the {@code computeFunctions} catalog: aggregates every registered
 * {@link FunctionFactory}'s declared {@link FunctionDescriptor}s (core + active plugins) into the
 * served GraphQL surface. An editor uses it for autocomplete; a view validator uses it to type-check
 * an {@code expr} before save. The descriptor SPI keeps each factory GraphQL-agnostic (it declares
 * functions in rapla terms); this controller is the single place that turns them into GraphQL data.
 *
 * <p>The {@code Map<String, FunctionFactory>} is the same namespace-keyed registry the storage
 * operators receive — Spring injects it from the {@code @Bean(name = NAMESPACE)} factory beans.
 */
@Controller
public class ComputeFunctionsController
{
    private final Map<String, FunctionFactory> functionFactories;

    public ComputeFunctionsController(Map<String, FunctionFactory> functionFactories)
    {
        this.functionFactories = functionFactories;
    }

    @QueryMapping
    public List<FunctionDescriptor> computeFunctions()
    {
        // Dedupe by namespace+name (a factory could appear under more than one map key) and sort
        // for a stable, browsable catalog.
        Map<String, FunctionDescriptor> byKey = new LinkedHashMap<>();
        for (FunctionFactory factory : functionFactories.values())
        {
            if (factory == null) continue;
            for (FunctionDescriptor d : factory.getDescriptors())
            {
                if (d == null || d.name() == null) continue;
                byKey.putIfAbsent(d.namespace() + ":" + d.name(), d);
            }
        }
        List<FunctionDescriptor> out = new ArrayList<>(byKey.values());
        out.sort(Comparator.comparing(FunctionDescriptor::namespace)
                .thenComparing(FunctionDescriptor::name));
        return out;
    }
}
