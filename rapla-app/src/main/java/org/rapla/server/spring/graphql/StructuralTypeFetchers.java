package org.rapla.server.spring.graphql;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.LightDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.server.spring.graphql.ClassificationGraphQLController.AttributeDescriptorDto;
import org.rapla.server.spring.graphql.ClassificationGraphQLController.AttributeValueDto;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;

/**
 * PRD 035 Cut C — performance-tuned {@link LightDataFetcher} singletons for
 * the hot-path structural type fields (Allocatable, DynamicType,
 * Classification interface, AttributeValue). Replaces the matching
 * {@code @SchemaMapping} methods on {@link ClassificationGraphQLController}
 * that Spring's annotation path turned into a per-dispatch
 * {@code DataFetcherHandlerMethod} allocation + {@code Method.toGenericString}
 * reflection cost.
 *
 * <p>Per the 2026-05-27 profile (42k Persons × 11 typed fields = 462k
 * dispatches), Spring's {@code SchemaMappingDataFetcher.get} chain showed
 * up at ~34 leaf samples; this rewiring bypasses it. Each fetcher here is
 * a singleton (zero allocations per dispatch), implements
 * {@link LightDataFetcher} so:
 * <ul>
 *   <li>graphql-java's execution strategy uses the lazy-env path
 *       ({@code get(fieldDef, source, envSupplier)}), skipping the up-front
 *       {@link DataFetchingEnvironment} allocation, and</li>
 *   <li>Spring's {@code ContextDataFetcherDecorator$ContextTypeVisitor}
 *       sees the {@code TrivialDataFetcher} marker (inherited via
 *       {@code LightDataFetcher}) and skips wrapping these in the
 *       Micrometer-context-capturing decorator.</li>
 * </ul>
 *
 * <p>Bodies read {@link RequestContextInstrumentation.RequestCtx} from the
 * per-query {@link GraphQLContext} for locale + caller + PermissionController
 * — all resolved once at {@code beginExecution} instead of per-field.
 */
public final class StructuralTypeFetchers
{
    /**
     * Server-configured locale, snapshot at every schema rebuild via
     * {@link #wire}. Read by every fetcher that resolves a localized name
     * (Allocatable.displayName, DynamicType.name, Category.name, etc.).
     * volatile so a rebuild's update is visible to in-flight queries —
     * harmless even if a query reads the old value once before the swap.
     *
     * <p>Initialized to {@link Locale#getDefault()} as a fallback for the
     * narrow window between class-load and first {@link #wire} call (only
     * affects unit tests bypassing the wiring path).
     */
    private static volatile Locale serverLocale = Locale.getDefault();

    private StructuralTypeFetchers() {}

    // === base ===================================================================

    /**
     * Boilerplate trimmer: a {@link LightDataFetcher} that casts the source
     * to {@code S} and delegates to a 2-arg {@link #read} method. Subclasses
     * implement the actual logic without re-coding the source-type cast.
     */
    private abstract static class LightSourceFetcher<S, T> implements LightDataFetcher<T>
    {
        private final Class<S> sourceType;

        protected LightSourceFetcher(Class<S> sourceType) { this.sourceType = sourceType; }

        @Override
        public final T get(GraphQLFieldDefinition fieldDef, Object source,
                Supplier<DataFetchingEnvironment> envSupplier) throws Exception
        {
            return sourceType.isInstance(source)
                    ? read(sourceType.cast(source), envSupplier)
                    : null;
        }

        @Override
        public final T get(DataFetchingEnvironment env) throws Exception
        {
            // Cold fallback for callers that don't take the Light fast path.
            // graphql-java's ExecutionStrategy prefers LightDataFetcher.get
            // when available, so this should be rare.
            return get(env.getFieldDefinition(), env.getSource(), () -> env);
        }

        protected abstract T read(S source, Supplier<DataFetchingEnvironment> envSupplier) throws Exception;
    }

    // === Allocatable type fetchers ============================================

    static final LightDataFetcher<String> ALLOCATABLE_TYPE =
            new LightSourceFetcher<Allocatable, String>(Allocatable.class)
            {
                @Override protected String read(Allocatable a, Supplier<DataFetchingEnvironment> env)
                {
                    return a.isPerson() ? "PERSON" : "RESOURCE";
                }
            };

    static final LightDataFetcher<String> ALLOCATABLE_DISPLAY_NAME =
            new LightSourceFetcher<Allocatable, String>(Allocatable.class)
            {
                @Override protected String read(Allocatable a, Supplier<DataFetchingEnvironment> env)
                {
                    return a.getName(localeFrom(env));
                }
            };

    static final LightDataFetcher<Classification> ALLOCATABLE_CLASSIFICATION =
            new LightSourceFetcher<Allocatable, Classification>(Allocatable.class)
            {
                @Override protected Classification read(Allocatable a, Supplier<DataFetchingEnvironment> env)
                {
                    return a.getClassification();
                }
            };

    static final LightDataFetcher<OffsetDateTime> ALLOCATABLE_CREATED_AT =
            new LightSourceFetcher<Allocatable, OffsetDateTime>(Allocatable.class)
            {
                @Override protected OffsetDateTime read(Allocatable a, Supplier<DataFetchingEnvironment> env)
                {
                    LocalDateTime ts = a.getCreateDate();
                    return ts == null ? null : ts.atOffset(ZoneOffset.UTC);
                }
            };

    static final LightDataFetcher<OffsetDateTime> ALLOCATABLE_LAST_MODIFIED_AT =
            new LightSourceFetcher<Allocatable, OffsetDateTime>(Allocatable.class)
            {
                @Override protected OffsetDateTime read(Allocatable a, Supplier<DataFetchingEnvironment> env)
                {
                    LocalDateTime ts = a.getLastChanged();
                    return ts == null ? null : ts.atOffset(ZoneOffset.UTC);
                }
            };

    /**
     * Resolves an Allocatable's owner User via operator.tryResolve. The
     * operator dependency comes from the singleton {@link StorageOperator}
     * captured at wiring time (see {@link #wire}).
     */
    static LightDataFetcher<User> allocatableOwner(StorageOperator operator)
    {
        return new LightSourceFetcher<Allocatable, User>(Allocatable.class)
        {
            @Override protected User read(Allocatable a, Supplier<DataFetchingEnvironment> env)
                    throws RaplaException
            {
                ReferenceInfo<User> ref = a.getOwnerRef();
                return ref == null ? null : operator.tryResolve(ref);
            }
        };
    }

    // === DynamicType field fetchers ===========================================

    static final LightDataFetcher<String> DYNAMIC_TYPE_NAME =
            new LightSourceFetcher<DynamicType, String>(DynamicType.class)
            {
                @Override protected String read(DynamicType dt, Supplier<DataFetchingEnvironment> env)
                {
                    return dt.getName(localeFrom(env));
                }
            };

    static final LightDataFetcher<String> DYNAMIC_TYPE_CLASSIFICATION_TYPE =
            new LightSourceFetcher<DynamicType, String>(DynamicType.class)
            {
                @Override protected String read(DynamicType dt, Supplier<DataFetchingEnvironment> env)
                {
                    String v = dt.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
                    if (DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON.equals(v))      return "PERSON";
                    if (DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION.equals(v)) return "RESERVATION";
                    return "RESOURCE";
                }
            };

    static final LightDataFetcher<List<AttributeDescriptorDto>> DYNAMIC_TYPE_ATTRIBUTES =
            new LightSourceFetcher<DynamicType, List<AttributeDescriptorDto>>(DynamicType.class)
            {
                @Override protected List<AttributeDescriptorDto> read(DynamicType dt,
                        Supplier<DataFetchingEnvironment> env)
                {
                    Attribute[] attrs = dt.getAttributes();
                    if (attrs == null) return List.of();
                    List<AttributeDescriptorDto> out = new ArrayList<>(attrs.length);
                    for (Attribute attr : attrs)
                    {
                        if (attr == null) continue;
                        out.add(AttributeDescriptorDto.from(attr));
                    }
                    return out;
                }
            };

    // === Category field fetchers ==============================================
    //
    // High-traffic path because every Category-valued attribute (Raumart,
    // SyncStatus, AkteurTypen multi-select etc.) routes through Category.name
    // per row × per category. Pre-fix profile (2026-05-27 post the structural
    // refactor) showed Category resolution still hitting Spring's HandlerMethod
    // chain because Category fetchers were the remaining @SchemaMapping methods.

    static final LightDataFetcher<String> CATEGORY_NAME =
            new LightSourceFetcher<Category, String>(Category.class)
            {
                @Override protected String read(Category c, Supplier<DataFetchingEnvironment> env)
                {
                    return c.getName(localeFrom(env));
                }
            };

    /**
     * Category path is slash-separated from the super-category root. Needs
     * the operator to resolve the root, so it's a factory not a singleton.
     */
    static LightDataFetcher<String> categoryPath(StorageOperator operator)
    {
        return new LightSourceFetcher<Category, String>(Category.class)
        {
            @Override protected String read(Category c, Supplier<DataFetchingEnvironment> env)
            {
                return c.getPath(operator.getSuperCategory(), localeFrom(env));
            }
        };
    }

    /**
     * Category parent — null at the top-level (super-category is hidden from
     * clients). Needs the operator to compare against super.
     */
    static LightDataFetcher<Category> categoryParent(StorageOperator operator)
    {
        return new LightSourceFetcher<Category, Category>(Category.class)
        {
            @Override protected Category read(Category c, Supplier<DataFetchingEnvironment> env)
            {
                Category parent = c.getParent();
                return parent == operator.getSuperCategory() ? null : parent;
            }
        };
    }

    static final LightDataFetcher<List<Category>> CATEGORY_CHILDREN =
            new LightSourceFetcher<Category, List<Category>>(Category.class)
            {
                @Override protected List<Category> read(Category c, Supplier<DataFetchingEnvironment> env)
                {
                    Category[] arr = c.getCategories();
                    return arr == null ? List.of() : java.util.Arrays.asList(arr);
                }
            };

    // === Classification interface fetchers ====================================

    static final LightDataFetcher<String> CLASSIFICATION_TYPE_ID =
            new LightSourceFetcher<Classification, String>(Classification.class)
            {
                @Override protected String read(Classification c, Supplier<DataFetchingEnvironment> env)
                {
                    DynamicType dt = c.getType();
                    return dt == null ? null : dt.getId();
                }
            };

    static final LightDataFetcher<DynamicType> CLASSIFICATION_TYPE =
            new LightSourceFetcher<Classification, DynamicType>(Classification.class)
            {
                @Override protected DynamicType read(Classification c, Supplier<DataFetchingEnvironment> env)
                {
                    return c.getType();
                }
            };

    static final LightDataFetcher<List<AttributeValueDto>> CLASSIFICATION_ATTRIBUTES =
            new LightSourceFetcher<Classification, List<AttributeValueDto>>(Classification.class)
            {
                @Override protected List<AttributeValueDto> read(Classification c,
                        Supplier<DataFetchingEnvironment> env)
                {
                    DynamicType dt = c.getType();
                    if (dt == null) return List.of();
                    RequestContextInstrumentation.RequestCtx rc = ctxFrom(env);
                    List<AttributeValueDto> out = new ArrayList<>();
                    for (Attribute attr : dt.getAttributes())
                    {
                        if (attr == null) continue;
                        AttributeValueDto dto = buildAttributeValue(c, attr, rc);
                        if (dto != null) out.add(dto);
                    }
                    return out;
                }
            };

    /** Build an {@link AttributeValueDto} for a single attribute on the
     *  interface path. §12 applies to ALLOCATABLE values (Category passes
     *  through — global metadata). */
    private static AttributeValueDto buildAttributeValue(Classification c, Attribute attr,
            RequestContextInstrumentation.RequestCtx rc)
    {
        String key = attr.getKey();
        AttributeType t = attr.getType();
        if (t == null) return null;
        boolean multi = isMultiSelect(attr);

        if (multi)
        {
            Collection<Object> values = c.getValues(attr);
            if (values == null || values.isEmpty()) return AttributeValueDto.bare(key);
            if (t == AttributeType.CATEGORY)
            {
                List<Category> out = new ArrayList<>(values.size());
                for (Object v : values) if (v instanceof Category cat) out.add(cat);
                return AttributeValueDto.categoryList(key, out);
            }
            if (t == AttributeType.ALLOCATABLE)
            {
                List<Allocatable> out = new ArrayList<>(values.size());
                for (Object v : values)
                {
                    if (!(v instanceof Allocatable a)) continue;
                    if (!canReadAllocatable(a, rc)) continue;
                    out.add(a);
                }
                return AttributeValueDto.allocatableList(key, out);
            }
            return AttributeValueDto.bare(key);
        }

        Object v = c.getValueForAttribute(attr);
        if (v == null) return AttributeValueDto.bare(key);

        return switch (t)
        {
            case STRING      -> AttributeValueDto.stringV(key, v.toString());
            case INT         -> AttributeValueDto.intV(key,
                    v instanceof Number n ? n.longValue() : null);
            case BOOLEAN     -> AttributeValueDto.boolV(key, v instanceof Boolean b ? b : null);
            case DATE        -> AttributeValueDto.dateV(key, v instanceof LocalDateTime ldt ? ldt : null);
            case CATEGORY    -> AttributeValueDto.categoryV(key, v instanceof Category cat ? cat : null);
            case ALLOCATABLE -> {
                if (!(v instanceof Allocatable a)) yield AttributeValueDto.bare(key);
                yield canReadAllocatable(a, rc) ? AttributeValueDto.allocatableV(key, a)
                                                : AttributeValueDto.bare(key);
            }
        };
    }

    /** §12 read gate for ALLOCATABLE references. Anonymous = no access;
     *  authenticated = canRead via the cached PermissionController. */
    static boolean canReadAllocatable(Allocatable a, RequestContextInstrumentation.RequestCtx rc)
    {
        if (rc == null || rc.caller() == null || rc.permissionController() == null) return false;
        return rc.permissionController().canRead(a, rc.caller());
    }

    // === wiring ==============================================================

    /**
     * Register every structural-type field fetcher above. Called by
     * {@link HotSwappableGraphQlSource} on every schema build.
     * Snapshots the server-configured locale into {@link #serverLocale}.
     */
    public static void wire(RuntimeWiring.Builder b, StorageOperator operator, RaplaLocale raplaLocale)
    {
        if (raplaLocale != null)
        {
            Locale l = raplaLocale.getLocale();
            if (l != null) serverLocale = l;
        }
        b.type("Allocatable", t -> t
                .dataFetcher("type",           ALLOCATABLE_TYPE)
                .dataFetcher("displayName",    ALLOCATABLE_DISPLAY_NAME)
                .dataFetcher("classification", ALLOCATABLE_CLASSIFICATION)
                .dataFetcher("createdAt",      ALLOCATABLE_CREATED_AT)
                .dataFetcher("lastModifiedAt", ALLOCATABLE_LAST_MODIFIED_AT)
                .dataFetcher("owner",          allocatableOwner(operator)));
        b.type("DynamicType", t -> t
                .dataFetcher("name",               DYNAMIC_TYPE_NAME)
                .dataFetcher("classificationType", DYNAMIC_TYPE_CLASSIFICATION_TYPE)
                .dataFetcher("attributes",         DYNAMIC_TYPE_ATTRIBUTES));
        b.type("Classification", t -> t
                .dataFetcher("typeId",     CLASSIFICATION_TYPE_ID)
                .dataFetcher("type",       CLASSIFICATION_TYPE)
                .dataFetcher("attributes", CLASSIFICATION_ATTRIBUTES));
        b.type("Category", t -> t
                .dataFetcher("name",     CATEGORY_NAME)
                .dataFetcher("path",     categoryPath(operator))
                .dataFetcher("parent",   categoryParent(operator))
                .dataFetcher("children", CATEGORY_CHILDREN));
        // The Classification interface's fields are inherited by the
        // AllocatableClassification + ReservationClassification interfaces
        // automatically per the GraphQL spec — no separate wiring needed.
    }

    // === helpers ==============================================================

    private static Locale localeFrom(Supplier<DataFetchingEnvironment> envSupplier)
    {
        // Read the server-configured locale captured at schema-build time.
        // Sourcing it from rapla's RaplaLocale bean (not Locale.getDefault())
        // keeps the GraphQL surface honoring the deployment config. Cached
        // in a volatile static so the read is a regular memory load (~1 ns)
        // vs the alternative — envSupplier.get() to read the per-request
        // GraphQLContext — which materializes the full DataFetchingEnvironment
        // (~25 µs / call; 108 leaf samples on the Person query before this fix).
        //
        // Per-request locale (Accept-Language) isn't honored here — a
        // future enhancement can route it through the RequestContextInstrumentation
        // + ThreadLocal mirror IF the cost is justified by real multi-locale demand.
        return serverLocale;
    }

    private static RequestContextInstrumentation.RequestCtx ctxFrom(Supplier<DataFetchingEnvironment> envSupplier)
    {
        DataFetchingEnvironment env = envSupplier.get();
        return RequestContextInstrumentation.from(env.getGraphQlContext());
    }

    private static boolean isMultiSelect(Attribute attr)
    {
        Object c = attr.getConstraint(ConstraintIds.KEY_MULTI_SELECT);
        if (c == null) return false;
        if (c instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(c.toString());
    }
}
