package org.rapla.server.spring.graphql;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.LightDataFetcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.rapla.entities.Category;
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
    /**
     * True if the attribute targets a VALUE_LIST root (PRD 035 §5a). When
     * set, the fetcher coerces each Category value to its enum-value-name
     * (sanitized leaf key) so the wire format matches the generated enum
     * type in the schema. False → return raw Category (ORGANIZATION /
     * unknown roots stay typed as {@code Category}).
     */
    private final boolean       coerceToEnum;

    AttributeDataFetcher(Attribute attribute)
    {
        this.attribute = attribute;
        this.key       = attribute.getKey();
        this.type      = attribute.getType();
        this.multi     = isMultiSelect(attribute);
        this.coerceToEnum = isValueListCategory(attribute);
    }

    /**
     * True iff this attribute is CATEGORY-typed with a VALUE_LIST-kind root.
     * Matches {@link ClassificationSdlGenerator#graphqlTypeFor}'s decision
     * — when the SDL emits {@code Raumart} (enum) for the field, this
     * fetcher coerces values to enum-name strings; otherwise it returns
     * raw Categories.
     */
    private static boolean isValueListCategory(Attribute attr)
    {
        if (attr.getType() != AttributeType.CATEGORY) return false;
        Object root = attr.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
        if (!(root instanceof Category cat)) return false;
        return CategoryKindClassifier.kindOf(cat, null) == CategoryKindClassifier.Kind.VALUE_LIST;
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

    /** True if a referenced allocatable has no generated GraphQL type — a dangling/deleted target
     * ({@code rapla:unresolvedResource}) or any rapla-internal type. Such a value must not be exposed
     * through a reference field: descending into its non-null {@code classification} would fail
     * abstract-type resolution. The reference renders {@code null} instead. */
    private static boolean isUnresolvedReference(Allocatable a)
    {
        Classification c = a.getClassification();
        return c == null || c.getType() == null
                || ClassificationSdlGenerator.isRaplaInternal(c.getType());
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
            case CATEGORY    -> coerceCategory(v);
            case ALLOCATABLE -> {
                if (!(v instanceof Allocatable a)) yield null;
                // Dangling/deleted target (rapla:unresolvedResource) has no generated GraphQL type;
                // returning it would fail abstract-type resolution on its non-null `classification`.
                // A nullable reference field renders null instead — same posture as §12-unreadable.
                if (isUnresolvedReference(a)) yield null;
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
            if (!coerceToEnum) return new ArrayList<>(values);
            // VALUE_LIST → coerce each Category to its sanitized enum value name
            List<String> out = new ArrayList<>(values.size());
            for (Object v : values)
            {
                if (v instanceof Category cat)
                {
                    String name = ClassificationSdlGenerator.enumValueFor(cat);
                    if (!name.isEmpty()) out.add(name);
                }
            }
            return out;
        }
        if (type == AttributeType.ALLOCATABLE)
        {
            List<Allocatable> out = new ArrayList<>(values.size());
            for (Object v : values)
            {
                if (v instanceof Allocatable a && !isUnresolvedReference(a) && canReadAllocatable(a, envSupplier))
                {
                    out.add(a);
                }
            }
            return out;
        }
        // No multi-select for STRING / INT / BOOLEAN / DATE in current data model.
        return List.of();
    }

    /** Coerce a CATEGORY value to either an enum value name (VALUE_LIST) or
     *  the raw Category instance (ORGANIZATION). Returns null for unexpected
     *  non-Category inputs or unsanitizable keys. */
    private Object coerceCategory(Object v)
    {
        if (!(v instanceof Category cat)) return null;
        if (!coerceToEnum) return cat;
        String name = ClassificationSdlGenerator.enumValueFor(cat);
        return name.isEmpty() ? null : name;
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
        // PRD 082 #8 — index membership when flipped, else canRead (identical result, §12).
        return rc.canReadAllocatable(a);
    }

    private static boolean isMultiSelect(Attribute attr)
    {
        Object c = attr.getConstraint(ConstraintIds.KEY_MULTI_SELECT);
        if (c == null) return false;
        if (c instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(c.toString());
    }
}
