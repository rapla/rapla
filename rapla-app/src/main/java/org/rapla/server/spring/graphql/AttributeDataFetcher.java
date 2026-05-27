package org.rapla.server.spring.graphql;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.LightDataFetcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;

/**
 * PRD 035 Cut C — per-attribute {@link LightDataFetcher} for the typed
 * classification field path (e.g. {@code RaumClassification.seats}).
 *
 * <p>One instance per (DynamicType, Attribute) pair, allocated once at
 * schema build, reused for every row. Captures the attribute and its
 * derived flags ({@code key}, {@code type}, {@code multi}) as final fields
 * at construction.
 *
 * <p>Implements {@link LightDataFetcher} so:
 * <ul>
 *   <li>graphql-java's execution strategy uses the
 *       {@code get(fieldDef, source, envSupplier)} fast path — skipping the
 *       up-front {@link DataFetchingEnvironment} construction. We only
 *       invoke the supplier when we actually need the env (only for
 *       ALLOCATABLE attribute §12 filtering).</li>
 *   <li>Spring's {@code ContextDataFetcherDecorator$ContextTypeVisitor} sees
 *       the inherited {@code TrivialDataFetcher} marker and skips wrapping
 *       this in the Micrometer-context-capturing decorator. That alone
 *       saves ~16 leaf-profile samples worth of {@code captureFromContext}
 *       calls per 462k field dispatches on the 42k Person admin query.</li>
 * </ul>
 *
 * <p>§12 reading: for ALLOCATABLE-typed attributes we filter by the cached
 * caller / PermissionController on the per-query
 * {@link RequestContextInstrumentation.RequestCtx}. For other types no
 * gating is needed (STRING/INT/BOOLEAN/DATE values are facts; Category
 * values are global metadata).
 */
final class AttributeDataFetcher implements LightDataFetcher<Object>
{
    private final Attribute     attribute;
    private final String        key;
    private final AttributeType type;
    private final boolean       multi;

    AttributeDataFetcher(Attribute attribute)
    {
        this.attribute = attribute;
        this.key       = attribute.getKey();
        this.type      = attribute.getType();
        this.multi     = isMultiSelect(attribute);
    }

    @Override
    public Object get(GraphQLFieldDefinition fieldDef,
            Object source,
            Supplier<DataFetchingEnvironment> envSupplier)
    {
        if (!(source instanceof Classification c)) return null;
        return multi ? readMulti(c, envSupplier) : readSingle(c, envSupplier);
    }

    @Override
    public Object get(DataFetchingEnvironment env) throws Exception
    {
        // Cold fallback; graphql-java takes the Light path when available.
        return get(env.getFieldDefinition(), env.getSource(), () -> env);
    }

    private Object readSingle(Classification c, Supplier<DataFetchingEnvironment> envSupplier)
    {
        Object v = c.getValue(key);
        if (v == null) return null;
        return switch (type)
        {
            case STRING      -> v.toString();
            case INT         -> v instanceof Number n ? n.longValue() : null;
            case BOOLEAN     -> v instanceof Boolean b ? b : null;
            case DATE        -> v;
            case CATEGORY    -> v;
            case ALLOCATABLE -> {
                if (!(v instanceof Allocatable a)) yield null;
                yield canReadAllocatable(a, envSupplier) ? a : null;
            }
        };
    }

    private Object readMulti(Classification c, Supplier<DataFetchingEnvironment> envSupplier)
    {
        Collection<Object> values = c.getValues(attribute);
        if (values == null || values.isEmpty()) return List.of();
        if (type == AttributeType.CATEGORY)
        {
            return new ArrayList<>(values);
        }
        if (type == AttributeType.ALLOCATABLE)
        {
            List<Allocatable> out = new ArrayList<>(values.size());
            for (Object v : values)
            {
                if (v instanceof Allocatable a && canReadAllocatable(a, envSupplier)) out.add(a);
            }
            return out;
        }
        // No multi-select for STRING / INT / BOOLEAN / DATE in current data model.
        return List.of();
    }

    /**
     * §12 ALLOCATABLE filter. Reads the cached caller + PermissionController
     * from the per-query GraphQLContext (resolved once at
     * {@link RequestContextInstrumentation#beginExecution}). Anonymous
     * callers see nothing.
     */
    private static boolean canReadAllocatable(Allocatable a, Supplier<DataFetchingEnvironment> envSupplier)
    {
        DataFetchingEnvironment env = envSupplier.get();
        RequestContextInstrumentation.RequestCtx rc =
                RequestContextInstrumentation.from(env.getGraphQlContext());
        if (rc.caller() == null || rc.permissionController() == null) return false;
        return rc.permissionController().canRead(a, rc.caller());
    }

    private static boolean isMultiSelect(Attribute attr)
    {
        Object c = attr.getConstraint(ConstraintIds.KEY_MULTI_SELECT);
        if (c == null) return false;
        if (c instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(c.toString());
    }
}
