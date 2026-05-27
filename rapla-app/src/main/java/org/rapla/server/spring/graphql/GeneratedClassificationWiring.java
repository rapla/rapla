package org.rapla.server.spring.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeRuntimeWiring;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaException;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

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
 *       per {@link ClassificationSdlGenerator#sanitizeTypeName(String)}.</li>
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

    public GeneratedClassificationWiring(StorageOperator operator)
    {
        this.operator = operator;
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

        if (dynamicTypes == null) return;
        for (DynamicType dt : dynamicTypes)
        {
            if (dt == null || dt.getKey() == null || dt.getKey().isBlank()) continue;
            String typeName = ClassificationSdlGenerator.sanitizeTypeName(dt.getKey()) + "Classification";
            wiringBuilder.type(typeName, builder -> {
                for (Attribute attr : dt.getAttributes())
                {
                    if (attr == null || attr.getKey() == null || attr.getKey().isBlank()) continue;
                    String fieldName = ClassificationSdlGenerator.sanitizeFieldName(attr.getKey());
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
            String typeName = ClassificationSdlGenerator.sanitizeTypeName(dt.getKey()) + "Classification";
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
     * Builds a DataFetcher for one (generated-type, attribute) field. The
     * closure captures the attribute key + type so it doesn't repeat work
     * per request.
     */
    private DataFetcher<Object> attributeFetcher(Attribute attr)
    {
        String key = attr.getKey();
        AttributeType type = attr.getType();
        boolean multi = isMultiSelect(attr);
        return env -> {
            Object src = env.getSource();
            if (!(src instanceof Classification c)) return null;
            if (multi) return readMultiValued(c, attr, type);
            return readSingleValued(c, key, type);
        };
    }

    private Object readSingleValued(Classification c, String key, AttributeType type)
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
                yield filterAllocatable(a) ? a : null;
            }
        };
    }

    private Object readMultiValued(Classification c, Attribute attr, AttributeType type)
    {
        Collection<Object> values = c.getValues(attr);
        if (values == null) return List.of();
        if (type == AttributeType.CATEGORY)
        {
            List<Object> out = new ArrayList<>(values.size());
            for (Object v : values) out.add(v);
            return out;
        }
        if (type == AttributeType.ALLOCATABLE)
        {
            List<Allocatable> out = new ArrayList<>(values.size());
            for (Object v : values)
            {
                if (v instanceof Allocatable a && filterAllocatable(a)) out.add(a);
            }
            return out;
        }
        // No multi-select for other types in current rapla data model.
        return List.of();
    }

    /**
     * §12 gate for ALLOCATABLE attribute values. Anonymous callers see
     * nothing (matches the ClassificationGraphQLController policy); known
     * callers must {@code canRead} the target.
     */
    private boolean filterAllocatable(Allocatable a)
    {
        User caller = resolveCaller();
        if (caller == null) return false;
        return operator.getPermissionController().canRead(a, caller);
    }

    private User resolveCaller()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        String username = null;
        if (auth.getPrincipal() instanceof Jwt jwt)
        {
            username = jwt.getClaimAsString("preferred_username");
        }
        if (username == null || username.isBlank()) username = auth.getName();
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) return null;
        try { return operator.getUser(username); }
        catch (RaplaException e) { return null; }
    }

    private static boolean isMultiSelect(Attribute attr)
    {
        Object c = attr.getConstraint(ConstraintIds.KEY_MULTI_SELECT);
        if (c == null) return false;
        if (c instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(c.toString());
    }
}
