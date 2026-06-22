package org.rapla.server.spring.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import java.util.Collection;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaLocale;
import org.rapla.storage.StorageOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PRD 035 Cut C — programmatic wiring for the per-DynamicType generated
 * classification types. Runs alongside Spring's @SchemaMapping-driven wiring
 * (which handles the static interface/structural types in
 * {@link ClassificationGraphQLController}); this class handles the parts
 * Spring can't, because the generated types have no Java class to put
 * annotations on (rapla uses a single ClassificationImpl + DynamicType
 * pointer — see PRD 035 line 554-560).
 *
 * <p>Two things wired here:
 * <ol>
 *   <li><b>TypeResolver for {@code Classification}</b> — maps a runtime
 *       {@link Classification} entity to its concrete generated GraphQL type
 *       ({@code RoomClassification}, {@code LectureClassification}, ...) by
 *       reading {@code classification.getType().getKey()} and sanitizing
 *       per {@link ClassificationSdlGenerator#checkGraphQlCompliantName(String)}.</li>
 *   <li><b>Per-attribute DataFetchers</b> on each generated type — one
 *       closure per (typeKey, attributeKey) pair that reads the value from
 *       the source Classification. Reference values (ALLOCATABLE) are
 *       §12-filtered through {@link PermissionController#canRead(Allocatable, User)}.</li>
 * </ol>
 *
 * <p>Called by {@link HotSwappableGraphQlSource#rebuild()} on every schema
 * (re-)build with the current DynamicType set.
 */
public final class GeneratedClassificationWiring
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GeneratedClassificationWiring.class);

    private final StorageOperator operator;
    private final RaplaLocale     raplaLocale;
    /** PRD 073 — aggregated function descriptors used to generate function-fields (e.g. note). */
    private final java.util.Collection<org.rapla.entities.extensionpoints.FunctionDescriptor> functionDescriptors;

    public GeneratedClassificationWiring(StorageOperator operator, RaplaLocale raplaLocale,
            java.util.Collection<org.rapla.entities.extensionpoints.FunctionDescriptor> functionDescriptors)
    {
        this.operator    = operator;
        this.raplaLocale = raplaLocale;
        this.functionDescriptors = functionDescriptors == null ? java.util.List.of() : functionDescriptors;
    }

    /**
     * Register the {@code Classification} TypeResolver and one DataFetcher per
     * (generated-type, attribute) pair. The TypeResolver is referenced by name
     * via {@link RuntimeWiring.Builder#type(TypeRuntimeWiring)}.
     */
    public void configure(RuntimeWiring.Builder wiringBuilder, Collection<DynamicType> dynamicTypes)
    {
        // Interface dispatch: same key→typeName resolver wired against each of
        // the three interfaces. AllocatableClassification and ReservationClassification
        // each cover a subset of the generated types, but the runtime lookup is
        // identical — the concrete type's `implements` clause determines
        // membership in the interface, not the resolver.
        TypeResolver classificationResolver = classificationTypeResolver();
        wiringBuilder.type("Classification",            b -> b.typeResolver(classificationResolver));
        wiringBuilder.type("AllocatableClassification", b -> b.typeResolver(classificationResolver));
        wiringBuilder.type("ReservationClassification",       b -> b.typeResolver(classificationResolver));
        // PRD 080 — StatEntity union (typed group entity in StatKey).
        wiringBuilder.type("StatEntity", b -> b.typeResolver(statEntityResolver()));
        // PRD 081 — SearchHit interface (omnibox multisearch result rows).
        wiringBuilder.type("SearchHit", b -> b.typeResolver(searchHitResolver()));

        // Performance-critical structural type fields (Allocatable / DynamicType /
        // Classification interface) — programmatic LightDataFetcher singletons
        // bypass Spring's per-dispatch HandlerMethod construction.
        StructuralTypeFetchers.wire(wiringBuilder, operator, raplaLocale);

        // PRD 073 — descriptor-driven function-fields (e.g. AppointmentBlock.note): one EL-backed
        // DataFetcher per generated field, derived from the same rules as the generated SDL.
        for (String type : FunctionFieldGenerator.TARGET_TYPES)
        {
            var fields = FunctionFieldGenerator.eligibleFor(type, functionDescriptors);
            if (fields.isEmpty()) continue;
            wiringBuilder.type(type, b -> {
                for (var d : fields) b.dataFetcher(d.name(), functionFieldFetcher(d));
                return b;
            });
        }

        if (dynamicTypes == null) return;
        for (DynamicType dt : dynamicTypes)
        {
            if (dt == null || dt.getKey() == null || dt.getKey().isBlank()) continue;
            if (ClassificationSdlGenerator.isRaplaInternal(dt)) continue;
            String typeName = ClassificationSdlGenerator.checkGraphQlCompliantName(dt.getKey()) + "Classification";
            wiringBuilder.type(typeName, builder -> {
                // graphql-java doesn't auto-propagate interface-level DataFetchers
                // to concrete types (unlike Spring's @SchemaMapping walker), so we
                // re-register the inherited Classification interface fields on
                // each generated implementation explicitly.
                builder.dataFetcher("typeKey", StructuralTypeFetchers.CLASSIFICATION_TYPE_KEY);
                builder.dataFetcher("type",    StructuralTypeFetchers.CLASSIFICATION_TYPE);
                // Then the typed per-attribute fields generated for this DynamicType.
                for (Attribute attr : dt.getAttributes())
                {
                    if (attr == null || attr.getKey() == null || attr.getKey().isBlank()) continue;
                    String fieldName = ClassificationSdlGenerator.checkGraphQlCompliantName(attr.getKey());
                    builder.dataFetcher(fieldName, attributeFetcher(attr));
                }
                return builder;
            });
        }
    }

    /**
     * PRD 073 — DataFetcher for a descriptor-generated function-field: evaluate the function (as a
     * rapla expression) against the row subject. The source is the block DTO (use its block) or the
     * entity itself; §12 rides on the already-gated row set + the EL's {@code canReadInformation}.
     */
    private DataFetcher<Object> functionFieldFetcher(org.rapla.entities.extensionpoints.FunctionDescriptor d)
    {
        final String expr = FunctionFieldGenerator.exprFor(d);
        final String gqlType = FunctionFieldGenerator.returnTypeToGraphql(d.returnType());
        return env -> {
            Object src = env.getSource();
            Object subject = src instanceof ReservationGraphQLController.AppointmentBlockDto dto
                    ? dto.block() : src;
            var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
            org.rapla.entities.User user = rc == null ? null : rc.caller();
            if (!"String".equals(gqlType))
            {
                // Non-String scalar: take the RAW eval result and coerce to the scalar's Java type
                // (avoids locale-formatting a number/date through formatName).
                Object raw = StructuralTypeFetchers.computeEntityExprObject(subject, expr, user);
                return FunctionFieldGenerator.coerceScalar(gqlType, raw);
            }
            return StructuralTypeFetchers.computeEntityExpr(subject, expr, user);   // String
        };
    }

    /**
     * PRD 080 — resolves the concrete GraphQL type for a {@code StatEntity} union value
     * (the typed group entity carried by {@code StatKey.entity}).
     */
    private TypeResolver statEntityResolver()
    {
        return env -> {
            Object src = env.getObject();
            graphql.schema.GraphQLSchema schema = env.getSchema();
            String typeName =
                    src instanceof org.rapla.entities.domain.Allocatable ? "Allocatable"
                  : src instanceof org.rapla.entities.domain.Reservation ? "Reservation"
                  : src instanceof org.rapla.entities.Category          ? "Category"
                  : null;
            if (typeName == null)
            {
                LOGGER.warn("StatEntity TypeResolver got unexpected source: {}",
                        src == null ? "null" : src.getClass().getName());
                return null;
            }
            return schema.getObjectType(typeName);
        };
    }

    /**
     * PRD 081 — resolves the concrete GraphQL type for a {@code SearchHit}
     * interface value (omnibox multisearch row). One Java record per concrete
     * type; dispatch by {@code instanceof}.
     */
    private TypeResolver searchHitResolver()
    {
        return env -> {
            Object src = env.getObject();
            graphql.schema.GraphQLSchema schema = env.getSchema();
            String typeName =
                    src instanceof SearchGraphQLController.ResourceHit ? "ResourceHit"
                  : src instanceof SearchGraphQLController.EventHit    ? "EventHit"
                  : null;
            if (typeName == null)
            {
                LOGGER.warn("SearchHit TypeResolver got unexpected source: {}",
                        src == null ? "null" : src.getClass().getName());
                return null;
            }
            return schema.getObjectType(typeName);
        };
    }

    /**
     * Resolves the concrete GraphQL type for a {@link Classification} value
     * returned through any {@code Classification} interface field.
     */
    private TypeResolver classificationTypeResolver()
    {
        return env -> {
            Object src = env.getObject();
            if (!(src instanceof Classification c))
            {
                LOGGER.warn("Classification TypeResolver got non-Classification source: {}",
                        src == null ? "null" : src.getClass().getName());
                return null;
            }
            DynamicType dt = c.getType();
            if (dt == null || dt.getKey() == null) return null;
            // A reference can point to an unresolved/deleted resource (rapla:unresolvedResource) or any
            // rapla-internal type — these have NO generated GraphQL type, and their non-spec key would
            // make checkGraphQlCompliantName throw and 500 the whole query. Resolve to null instead
            // (the reference field renders null, like an unreadable §12 target).
            if (ClassificationSdlGenerator.isRaplaInternal(dt)) return null;
            String typeName = ClassificationSdlGenerator.checkGraphQlCompliantName(dt.getKey()) + "Classification";
            GraphQLSchema schema = env.getSchema();
            GraphQLObjectType objectType = schema.getObjectType(typeName);
            if (objectType == null)
            {
                LOGGER.warn("Classification of type '{}' has no generated GraphQL type '{}' — schema out of sync",
                        dt.getKey(), typeName);
            }
            return objectType;
        };
    }

    /**
     * Returns the (cached, immutable) per-attribute {@link AttributeDataFetcher}
     * instance to register against a generated type's typed field. One
     * instance per (DynamicType, Attribute) — built once at schema build,
     * reused for every row.
     */
    private DataFetcher<Object> attributeFetcher(Attribute attr)
    {
        return new AttributeDataFetcher(attr);
    }
}
