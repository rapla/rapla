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

    public GeneratedClassificationWiring(StorageOperator operator, RaplaLocale raplaLocale)
    {
        this.operator    = operator;
        this.raplaLocale = raplaLocale;
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

        // Performance-critical structural type fields (Allocatable / DynamicType /
        // Classification interface) — programmatic LightDataFetcher singletons
        // bypass Spring's per-dispatch HandlerMethod construction.
        StructuralTypeFetchers.wire(wiringBuilder, operator, raplaLocale);

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
