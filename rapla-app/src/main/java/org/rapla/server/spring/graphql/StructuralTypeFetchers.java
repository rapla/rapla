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
import java.util.Map;
import java.util.function.Supplier;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.NameFormatUtil;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
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

    /** The server-configured locale captured at schema-build time (see {@link #wire}). */
    static Locale serverLocale() { return serverLocale; }

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

    /** PRD 080 — structural person flag (mirrors {@code Allocatable.type == PERSON}); universal across instances. */
    static final LightDataFetcher<Boolean> ALLOCATABLE_IS_PERSON =
            new LightSourceFetcher<Allocatable, Boolean>(Allocatable.class)
            {
                @Override protected Boolean read(Allocatable a, Supplier<DataFetchingEnvironment> env)
                {
                    return a.isPerson();
                }
            };

    /** PRD 080 — true when the allocatable's DynamicType carries the {@code location=true} annotation
     * (the same room/location marker {@code Export2iCalConverter} uses). Deployment-configured, but
     * the annotation key is universal — no instance-specific type key in the query. */
    static final LightDataFetcher<Boolean> ALLOCATABLE_IS_LOCATION =
            new LightSourceFetcher<Allocatable, Boolean>(Allocatable.class)
            {
                @Override protected Boolean read(Allocatable a, Supplier<DataFetchingEnvironment> env)
                {
                    var cls = a.getClassification();
                    if (cls == null || cls.getType() == null) return false;
                    return "true".equals(cls.getType().getAnnotation(
                            org.rapla.entities.dynamictype.DynamicTypeAnnotations.KEY_LOCATION));
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

    // DYNAMIC_TYPE_ATTRIBUTES fetcher dropped 2026-05-28 (PRD 055 β refactor)
    // — the `attributes: [AttributeDescriptor!]!` field is removed from
    // DynamicType. SPA reads attribute metadata via introspection of the
    // generated <TypeKey>Classification types + custom directives
    // (@displayName, @expectedType, @rootCategory, @multiplicity, @required).

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
     * Category path is slash-separated KEY path from the super-category
     * root (per PRD 035 §5a — Category API uses keys, not localized names).
     * Localized rendering is a separate consumer concern handled via the
     * {@code name} field.
     */
    static LightDataFetcher<String> categoryPath(StorageOperator operator)
    {
        return new LightSourceFetcher<Category, String>(Category.class)
        {
            @Override protected String read(Category c, Supplier<DataFetchingEnvironment> env)
            {
                return CategoryKindClassifier.keyPath(c);
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

    /**
     * Resolves {@code Category.kind} via the PRD 035 §5a deployment-agnostic
     * three-tier rule: rapla-core hardcoded → admin annotation → depth heuristic.
     */
    static LightDataFetcher<String> categoryKind(StorageOperator operator)
    {
        return new LightSourceFetcher<Category, String>(Category.class)
        {
            @Override protected String read(Category c, Supplier<DataFetchingEnvironment> env)
            {
                return CategoryKindClassifier.kindOf(c, operator.getSuperCategory()).name();
            }
        };
    }

    // === Classification interface fetchers ====================================

    static final LightDataFetcher<String> CLASSIFICATION_TYPE_KEY =
            new LightSourceFetcher<Classification, String>(Classification.class)
            {
                @Override protected String read(Classification c, Supplier<DataFetchingEnvironment> env)
                {
                    DynamicType dt = c.getType();
                    return dt == null ? null : dt.getKey();
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

    // CLASSIFICATION_ATTRIBUTES fetcher + buildAttributeValue helper dropped
    // 2026-05-28 (PRD 055 β refactor) — the `attributes: [AttributeValue!]!`
    // field is removed from the Classification interface. Typed-narrow
    // fragments on generated `<TypeKey>Classification` types are the read
    // path; descriptors carried via custom directives on those fields.

    // === Reservation field fetchers (PRD 055 Tier-1 perf migration, 2026-05-29) ===
    //
    // Moved off @SchemaMapping in ReservationGraphQLController to follow the
    // Cut C pattern (Pattern 1 in docs/graphql.md). For Reservation reads at
    // calendar scale (500 reservations × 3 appts avg × 9 derived fields per
    // reservation × 4 fields per appt = ~21k dispatches per query), the
    // per-dispatch Spring HandlerMethod + Micrometer wrap cost dominated.

    static final LightDataFetcher<String> RESERVATION_DISPLAY_NAME =
            new LightSourceFetcher<org.rapla.entities.domain.Reservation, String>(
                    org.rapla.entities.domain.Reservation.class)
            {
                @Override protected String read(org.rapla.entities.domain.Reservation r,
                        Supplier<DataFetchingEnvironment> env)
                {
                    // PRD 074 — the reservation's nameformat composition (DISPLAY variant),
                    // server-resolved. getName(locale) runs the KEY_NAME_FORMAT ParsedText.
                    return r.getName(localeFrom(env));
                }
            };

    /**
     * PRD 074 Baustein 5 (model A) — {@code Reservation.name(variant:)}. Reads the
     * NameVariant arg → the nameformat-family annotation; EXPORT/PLANNING fall back to
     * DISPLAY when the type lacks that variant. {@code displayName} stays as the
     * (deprecated) DISPLAY alias.
     */
    static final LightDataFetcher<String> RESERVATION_NAME =
            new LightSourceFetcher<org.rapla.entities.domain.Reservation, String>(
                    org.rapla.entities.domain.Reservation.class)
            {
                @Override protected String read(org.rapla.entities.domain.Reservation r,
                        Supplier<DataFetchingEnvironment> env)
                {
                    Object v = env.get().getArgument("variant");
                    String variant = v == null ? "DISPLAY" : v.toString();
                    String annotationName = switch (variant)
                    {
                        case "EXPORT" ->
                                org.rapla.entities.dynamictype.DynamicTypeAnnotations.KEY_NAME_FORMAT_EXPORT;
                        case "PLANNING" ->
                                org.rapla.entities.dynamictype.DynamicTypeAnnotations.KEY_NAME_FORMAT_PLANNING;
                        default ->
                                org.rapla.entities.dynamictype.DynamicTypeAnnotations.KEY_NAME_FORMAT;
                    };
                    return resolveVariantName(r, r, annotationName);
                }
            };

    /**
     * PRD 074 Baustein 7 — {@code Allocatable.name(variant:)}, mirroring Reservation.name.
     * (Allocatable.displayName stays — broadly used by the SPA + AllocatableFilter.nameContains —
     * its @deprecation is a coordinated later step.)
     */
    static final LightDataFetcher<String> ALLOCATABLE_NAME =
            new LightSourceFetcher<Allocatable, String>(Allocatable.class)
            {
                @Override protected String read(Allocatable a, Supplier<DataFetchingEnvironment> env)
                {
                    Object v = env.get().getArgument("variant");
                    String variant = v == null ? "DISPLAY" : v.toString();
                    String annotationName = switch (variant)
                    {
                        case "EXPORT" ->
                                org.rapla.entities.dynamictype.DynamicTypeAnnotations.KEY_NAME_FORMAT_EXPORT;
                        case "PLANNING" ->
                                org.rapla.entities.dynamictype.DynamicTypeAnnotations.KEY_NAME_FORMAT_PLANNING;
                        default ->
                                org.rapla.entities.dynamictype.DynamicTypeAnnotations.KEY_NAME_FORMAT;
                    };
                    return resolveVariantName(a, a, annotationName);
                }
            };

    /**
     * PRD 074 Baustein 9 — {@code AppointmentBlock.name(variant:)} = the block's name, FLAT and
     * <b>block-aware</b>: resolved via {@link NameFormatUtil#getName(AppointmentBlock, Locale)} /
     * {@code reservation.formatAppointmentBlock(...)}, so the name function is evaluated with the
     * <i>block</i> as context object. This is NOT the reservation name — an appointment can carry
     * an appointment-note override (appointmentnote plugin) that produces a different name than the
     * reservation. Saves the `reservation { name }` nesting for the common table column.
     */
    /**
     * PRD 074 — {@code AppointmentBlock.durationMinutes}: wall-clock occupancy (end−start) in whole
     * minutes. Numeric, so it can be summed via {@code @aggregate} or grouped (PRD 079). No break
     * adjustment / no plugin — that's the {@code duration} string's job.
     */
    static final LightDataFetcher<Integer> APPOINTMENT_BLOCK_DURATION_MINUTES =
            new LightSourceFetcher<ReservationGraphQLController.AppointmentBlockDto, Integer>(
                    ReservationGraphQLController.AppointmentBlockDto.class)
            {
                @Override protected Integer read(ReservationGraphQLController.AppointmentBlockDto dto,
                        Supplier<DataFetchingEnvironment> env)
                {
                    if (dto == null || dto.start() == null || dto.end() == null) return null;
                    return (int) java.time.Duration.between(dto.start(), dto.end()).toMinutes();
                }
            };

    /**
     * PRD 095 — {@code AppointmentBlock.color}: the single effective block color —
     * the event color when the reservation carries one, else the first readable
     * allocatable color — resolved via
     * {@link org.rapla.plugin.abstractcalendar.RaplaBuilder#getColorForClassifiable}
     * and merged through {@link org.rapla.plugin.calendarview.BlockColors#resolve}.
     * §12 (PRD 095 D3): when the color-bearing allocatable is not readable by the
     * caller the color resolves to <b>null</b> (the block itself already passed the
     * read gate) — a hidden resource's color must never leak.
     */
    static final LightDataFetcher<String> APPOINTMENT_BLOCK_COLOR =
            new LightSourceFetcher<ReservationGraphQLController.AppointmentBlockDto, String>(
                    ReservationGraphQLController.AppointmentBlockDto.class)
            {
                @Override protected String read(ReservationGraphQLController.AppointmentBlockDto dto,
                        Supplier<DataFetchingEnvironment> env)
                {
                    if (dto == null || dto.reservation() == null) return null;
                    String eventColor = org.rapla.plugin.abstractcalendar.RaplaBuilder
                            .getColorForClassifiable(dto.reservation());
                    List<String> resourceColors = new ArrayList<>();
                    if (eventColor == null && dto.appointment() != null)
                    {
                        var rc = RequestContextInstrumentation.from(env.get().getGraphQlContext());
                        java.util.Iterator<Allocatable> it = dto.reservation()
                                .getAllocatablesFor(dto.appointment()).iterator();
                        while (it.hasNext())
                        {
                            Allocatable a = it.next();
                            String c = org.rapla.plugin.abstractcalendar.RaplaBuilder
                                    .getColorForClassifiable(a);
                            if (c == null) continue;
                            if (!rc.canReadAllocatable(a)) return null;   // D3: null, not drop
                            resourceColors.add(c);
                            break;   // first color-bearing allocatable decides
                        }
                    }
                    List<String> merged = org.rapla.plugin.calendarview.BlockColors
                            .resolve(true, eventColor, true, resourceColors);
                    return merged.isEmpty() ? null : merged.get(0);
                }
            };

    /** PRD 095 Phase 3b (OQ3) — the owning appointment, navigable from the block.
     *  Returns the same entity {@code Reservation.appointments} exposes, so the wired
     *  Appointment fetchers (repeating, allDay, …) apply unchanged — no new §12 surface. */
    static final LightDataFetcher<org.rapla.entities.domain.Appointment> APPOINTMENT_BLOCK_APPOINTMENT =
            new LightSourceFetcher<ReservationGraphQLController.AppointmentBlockDto,
                    org.rapla.entities.domain.Appointment>(
                    ReservationGraphQLController.AppointmentBlockDto.class)
            {
                @Override protected org.rapla.entities.domain.Appointment read(
                        ReservationGraphQLController.AppointmentBlockDto dto,
                        Supplier<DataFetchingEnvironment> env)
                {
                    return dto == null ? null : dto.appointment();
                }
            };

    /** PRD 095 Phase 3b (OQ3) — appointment cardinality without shipping the list;
     *  the month-grid drag gate reads it per block row. */
    static final LightDataFetcher<Integer> RESERVATION_APPOINTMENT_COUNT =
            new LightSourceFetcher<org.rapla.entities.domain.Reservation, Integer>(
                    org.rapla.entities.domain.Reservation.class)
            {
                @Override protected Integer read(org.rapla.entities.domain.Reservation r,
                        Supplier<DataFetchingEnvironment> env)
                {
                    org.rapla.entities.domain.Appointment[] arr = r.getAppointments();
                    return arr == null ? 0 : arr.length;
                }
            };

    /** PRD 094 D4 — the owning appointment's id; the SPA delete-scope flow
     *  keys on the (appointmentId, blockStart) pair per row. */
    static final LightDataFetcher<String> APPOINTMENT_BLOCK_APPOINTMENT_ID =
            new LightSourceFetcher<ReservationGraphQLController.AppointmentBlockDto, String>(
                    ReservationGraphQLController.AppointmentBlockDto.class)
            {
                @Override protected String read(ReservationGraphQLController.AppointmentBlockDto dto,
                        Supplier<DataFetchingEnvironment> env)
                {
                    return dto == null || dto.appointment() == null ? null : dto.appointment().getId();
                }
            };

    static final LightDataFetcher<String> APPOINTMENT_BLOCK_NAME =
            new LightSourceFetcher<ReservationGraphQLController.AppointmentBlockDto, String>(
                    ReservationGraphQLController.AppointmentBlockDto.class)
            {
                @Override protected String read(ReservationGraphQLController.AppointmentBlockDto dto,
                        Supplier<DataFetchingEnvironment> env)
                {
                    if (dto == null || dto.reservation() == null || dto.block() == null) return null;
                    Object v = env.get().getArgument("variant");
                    String variant = v == null ? "DISPLAY" : v.toString();
                    return resolveBlockVariantName(dto.reservation(), dto.block(), variant);
                }
            };

    /**
     * Block-aware name resolution mirroring {@link NameFormatUtil}'s variant semantics: DISPLAY =
     * {@code getName(block)}; EXPORT uses the export format only if the type defines it, else falls
     * back to DISPLAY ({@link NameFormatUtil#getExportName(AppointmentBlock, Locale)}); PLANNING
     * uses the planning format if defined, else DISPLAY. All paths go through
     * {@code reservation.formatAppointmentBlock(...)} so appointment-note overrides are honored.
     */
    private static String resolveBlockVariantName(org.rapla.entities.domain.Reservation reservation,
            org.rapla.entities.domain.AppointmentBlock block, String variant)
    {
        switch (variant)
        {
            case "EXPORT":
                return NameFormatUtil.getExportName(block, serverLocale);
            case "PLANNING":
            {
                org.rapla.entities.dynamictype.Classification cls = reservation.getClassification();
                if (cls != null && cls.getType().getAnnotation(
                        org.rapla.entities.dynamictype.DynamicTypeAnnotations.KEY_NAME_FORMAT_PLANNING) != null)
                {
                    return reservation.formatAppointmentBlock(serverLocale,
                            org.rapla.entities.dynamictype.DynamicTypeAnnotations.KEY_NAME_FORMAT_PLANNING, block);
                }
                return NameFormatUtil.getName(block, serverLocale);
            }
            default:
                return NameFormatUtil.getName(block, serverLocale);
        }
    }

    /** Shared name-variant resolution for Reservation + Allocatable (both Named + Classifiable):
     * DISPLAY = the plain nameformat; EXPORT/PLANNING use the variant only if the type defines it,
     * else fall back to DISPLAY. */
    private static String resolveVariantName(org.rapla.entities.Named named,
            org.rapla.entities.dynamictype.Classifiable classifiable, String annotationName)
    {
        if (org.rapla.entities.dynamictype.DynamicTypeAnnotations.KEY_NAME_FORMAT.equals(annotationName))
        {
            return named.getName(serverLocale);
        }
        org.rapla.entities.dynamictype.Classification cls = classifiable.getClassification();
        if (cls != null && cls.getType().getAnnotation(annotationName) != null)
        {
            return cls.format(serverLocale, annotationName);
        }
        return named.getName(serverLocale);
    }

    static final LightDataFetcher<LocalDateTime> RESERVATION_FIRST_DATE =
            new LightSourceFetcher<org.rapla.entities.domain.Reservation, LocalDateTime>(
                    org.rapla.entities.domain.Reservation.class)
            {
                @Override protected LocalDateTime read(org.rapla.entities.domain.Reservation r,
                        Supplier<DataFetchingEnvironment> env)
                {
                    return r.getFirstDate();
                }
            };

    static final LightDataFetcher<LocalDateTime> RESERVATION_LAST_DATE =
            new LightSourceFetcher<org.rapla.entities.domain.Reservation, LocalDateTime>(
                    org.rapla.entities.domain.Reservation.class)
            {
                @Override protected LocalDateTime read(org.rapla.entities.domain.Reservation r,
                        Supplier<DataFetchingEnvironment> env)
                {
                    return r.getMaxEnd();
                }
            };

    static final LightDataFetcher<OffsetDateTime> RESERVATION_CREATED_AT =
            new LightSourceFetcher<org.rapla.entities.domain.Reservation, OffsetDateTime>(
                    org.rapla.entities.domain.Reservation.class)
            {
                @Override protected OffsetDateTime read(org.rapla.entities.domain.Reservation r,
                        Supplier<DataFetchingEnvironment> env)
                {
                    LocalDateTime ts = r.getCreateDate();
                    return ts == null ? null : ts.atOffset(ZoneOffset.UTC);
                }
            };

    static final LightDataFetcher<OffsetDateTime> RESERVATION_LAST_MODIFIED_AT =
            new LightSourceFetcher<org.rapla.entities.domain.Reservation, OffsetDateTime>(
                    org.rapla.entities.domain.Reservation.class)
            {
                @Override protected OffsetDateTime read(org.rapla.entities.domain.Reservation r,
                        Supplier<DataFetchingEnvironment> env)
                {
                    LocalDateTime ts = r.getLastChanged();
                    return ts == null ? null : ts.atOffset(ZoneOffset.UTC);
                }
            };

    static final LightDataFetcher<List<org.rapla.entities.domain.Appointment>> RESERVATION_APPOINTMENTS =
            new LightSourceFetcher<org.rapla.entities.domain.Reservation,
                    List<org.rapla.entities.domain.Appointment>>(
                    org.rapla.entities.domain.Reservation.class)
            {
                @Override protected List<org.rapla.entities.domain.Appointment> read(
                        org.rapla.entities.domain.Reservation r,
                        Supplier<DataFetchingEnvironment> env)
                {
                    org.rapla.entities.domain.Appointment[] arr = r.getAppointments();
                    return arr == null ? List.of() : java.util.Arrays.asList(arr);
                }
            };

    static final LightDataFetcher<Classification> RESERVATION_CLASSIFICATION =
            new LightSourceFetcher<org.rapla.entities.domain.Reservation, Classification>(
                    org.rapla.entities.domain.Reservation.class)
            {
                @Override protected Classification read(org.rapla.entities.domain.Reservation r,
                        Supplier<DataFetchingEnvironment> env)
                {
                    return r.getClassification();
                }
            };

    static LightDataFetcher<User> reservationOwner(StorageOperator operator)
    {
        return new LightSourceFetcher<org.rapla.entities.domain.Reservation, User>(
                org.rapla.entities.domain.Reservation.class)
        {
            @Override protected User read(org.rapla.entities.domain.Reservation r,
                    Supplier<DataFetchingEnvironment> env) throws RaplaException
            {
                ReferenceInfo<User> ref = r.getOwnerRef();
                return ref == null ? null : operator.tryResolve(ref);
            }
        };
    }

    /** PRD 028 Phase 1 — `Reservation.hasConflicts: Boolean!`. Per-row;
     *  uses the existing operator.getConflicts(r) call. §12-gated: a
     *  conflict only "counts" if the caller can read both sides + the
     *  allocatable (mirrors {@link ConflictGraphQLController}). */
    static LightDataFetcher<Boolean> reservationHasConflicts(StorageOperator operator)
    {
        return new LightSourceFetcher<org.rapla.entities.domain.Reservation, Boolean>(
                org.rapla.entities.domain.Reservation.class)
        {
            @Override protected Boolean read(org.rapla.entities.domain.Reservation r,
                    java.util.function.Supplier<DataFetchingEnvironment> env) throws Exception
            {
                var rc = ctxFrom(env);
                User caller = rc.caller();
                if (caller == null) return false;
                PermissionController pc = rc.permissionController() != null
                        ? rc.permissionController() : operator.getPermissionController();
                java.util.Collection<org.rapla.facade.Conflict> raw =
                        ((org.rapla.storage.SyncStorageOperator) operator).getConflictsSync(r);
                if (raw == null || raw.isEmpty()) return false;
                for (org.rapla.facade.Conflict c : raw)
                {
                    if (c == null) continue;
                    var r1 = operator.tryResolve(c.getReservation1());
                    var r2 = operator.tryResolve(c.getReservation2());
                    var alloc = c.getAllocatable();
                    if (r1 == null || r2 == null || alloc == null) continue;
                    if (!pc.canRead(r1, caller)) continue;
                    if (!pc.canRead(r2, caller)) continue;
                    if (!rc.canReadAllocatable(alloc)) continue;   // PRD 082 #8 — index membership when flipped, else canRead (§12)
                    return true;        // first visible conflict wins
                }
                return false;
            }
        };
    }

    /** Per-row hot field — §12 read of canModify. envSupplier materialization
     *  is unavoidable here (need the per-query caller from RequestCtx). */
    static final LightDataFetcher<Boolean> RESERVATION_CAN_MODIFY =
            new LightSourceFetcher<org.rapla.entities.domain.Reservation, Boolean>(
                    org.rapla.entities.domain.Reservation.class)
            {
                @Override protected Boolean read(org.rapla.entities.domain.Reservation r,
                        Supplier<DataFetchingEnvironment> env)
                {
                    var rc = ctxFrom(env);
                    User caller = rc.caller();
                    if (caller == null) return false;
                    if (caller.isAdmin()) return true;
                    return rc.permissionController() != null
                            && rc.permissionController().canModify(r, caller);
                }
            };

    /** PRD 096 — Allocatable mirror of RESERVATION_CAN_MODIFY. */
    static final LightDataFetcher<Boolean> ALLOCATABLE_CAN_MODIFY =
            new LightSourceFetcher<org.rapla.entities.domain.Allocatable, Boolean>(
                    org.rapla.entities.domain.Allocatable.class)
            {
                @Override protected Boolean read(org.rapla.entities.domain.Allocatable a,
                        Supplier<DataFetchingEnvironment> env)
                {
                    var rc = ctxFrom(env);
                    User caller = rc.caller();
                    if (caller == null) return false;
                    if (caller.isAdmin()) return true;
                    return rc.permissionController() != null
                            && rc.permissionController().canModify(a, caller);
                }
            };

    /**
     * Restriction-aware editor view of allocations. Drops per-allocatable
     * entries the caller can't read (§12 rule 4).
     */
    static LightDataFetcher<List<ReservationGraphQLController.AllocationDto>> reservationAllocations(
            StorageOperator operator)
    {
        return new LightSourceFetcher<org.rapla.entities.domain.Reservation,
                List<ReservationGraphQLController.AllocationDto>>(
                org.rapla.entities.domain.Reservation.class)
        {
            @Override protected List<ReservationGraphQLController.AllocationDto> read(
                    org.rapla.entities.domain.Reservation r,
                    Supplier<DataFetchingEnvironment> env)
            {
                var rc = ctxFrom(env);
                User caller = rc.caller();
                PermissionController pc = rc.permissionController() != null
                        ? rc.permissionController() : operator.getPermissionController();
                List<ReservationGraphQLController.AllocationDto> out = new ArrayList<>();
                Allocatable[] allocatables = r.getAllocatables();
                if (allocatables == null) return List.of();
                for (Allocatable a : allocatables)
                {
                    if (a == null) continue;
                    if (caller != null && !rc.canReadAllocatable(a)) continue;   // PRD 082 #8 — index membership when flipped, else canRead (§12)
                    org.rapla.entities.domain.Appointment[] restriction = r.getRestriction(a);
                    List<String> appointmentIds = null;
                    if (restriction != null && restriction.length > 0)
                    {
                        appointmentIds = new ArrayList<>(restriction.length);
                        for (org.rapla.entities.domain.Appointment appt : restriction)
                        {
                            if (appt != null && appt.getId() != null)
                            {
                                appointmentIds.add(appt.getId());
                            }
                        }
                    }
                    out.add(new ReservationGraphQLController.AllocationDto(a, appointmentIds));
                }
                return out;
            }
        };
    }

    // === Appointment field fetchers ===========================================

    /** Wall-time all-day heuristic — start/end at 00:00, end > start. */
    /**
     * PRD 074 Baustein 9 — {@code Appointment.name(variant:)} = appointment-aware name via
     * {@link NameFormatUtil#getName(org.rapla.entities.domain.Appointment, Locale)} /
     * {@code reservation.formatAppointment(...)}. Like the block-level name it honors
     * appointment-note overrides; it differs only in that it has no single occurrence's
     * date context (the name function is evaluated against the appointment, not a block).
     */
    static final LightDataFetcher<String> APPOINTMENT_NAME =
            new LightSourceFetcher<org.rapla.entities.domain.Appointment, String>(
                    org.rapla.entities.domain.Appointment.class)
            {
                @Override protected String read(org.rapla.entities.domain.Appointment a,
                        Supplier<DataFetchingEnvironment> env)
                {
                    if (a == null || a.getReservation() == null) return null;
                    Object v = env.get().getArgument("variant");
                    String variant = v == null ? "DISPLAY" : v.toString();
                    org.rapla.entities.domain.Reservation r = a.getReservation();
                    switch (variant)
                    {
                        case "EXPORT":
                            return NameFormatUtil.getExportName(a, serverLocale);
                        case "PLANNING":
                        {
                            org.rapla.entities.dynamictype.Classification cls = r.getClassification();
                            if (cls != null && cls.getType().getAnnotation(
                                    org.rapla.entities.dynamictype.DynamicTypeAnnotations.KEY_NAME_FORMAT_PLANNING) != null)
                            {
                                return r.formatAppointment(serverLocale,
                                        org.rapla.entities.dynamictype.DynamicTypeAnnotations.KEY_NAME_FORMAT_PLANNING, a);
                            }
                            return NameFormatUtil.getName(a, serverLocale);
                        }
                        default:
                            return NameFormatUtil.getName(a, serverLocale);
                    }
                }
            };

    static final LightDataFetcher<Boolean> APPOINTMENT_ALL_DAY =
            new LightSourceFetcher<org.rapla.entities.domain.Appointment, Boolean>(
                    org.rapla.entities.domain.Appointment.class)
            {
                @Override protected Boolean read(org.rapla.entities.domain.Appointment a,
                        Supplier<DataFetchingEnvironment> env)
                {
                    LocalDateTime start = a.getStart();
                    LocalDateTime end = a.getEnd();
                    if (start == null || end == null) return false;
                    return start.getHour() == 0 && start.getMinute() == 0
                            && end.getHour() == 0 && end.getMinute() == 0
                            && !end.isEqual(start);
                }
            };

    static final LightDataFetcher<ReservationGraphQLController.RepeatingRuleDto> APPOINTMENT_REPEATING =
            new LightSourceFetcher<org.rapla.entities.domain.Appointment,
                    ReservationGraphQLController.RepeatingRuleDto>(
                    org.rapla.entities.domain.Appointment.class)
            {
                @Override protected ReservationGraphQLController.RepeatingRuleDto read(
                        org.rapla.entities.domain.Appointment a,
                        Supplier<DataFetchingEnvironment> env)
                {
                    org.rapla.entities.domain.Repeating r = a.getRepeating();
                    return r == null ? null : ReservationGraphQLController.RepeatingRuleDto.from(r);
                }
            };

    /** Per-appointment pre-resolved allocatables (PRD 055 Q4). §12-gated. */
    static LightDataFetcher<List<Allocatable>> appointmentAllocatables(StorageOperator operator)
    {
        return new LightSourceFetcher<org.rapla.entities.domain.Appointment, List<Allocatable>>(
                org.rapla.entities.domain.Appointment.class)
        {
            @Override protected List<Allocatable> read(org.rapla.entities.domain.Appointment a,
                    Supplier<DataFetchingEnvironment> env)
            {
                return resolveAppointmentAllocatables(a, env.get(), operator);
            }
        };
    }

    /**
     * PRD 074 Baustein 3 — {@code AppointmentBlock.allocatables(filter:)}. Reuses the exact
     * appointment-allocatable resolution (§12 canRead gate + scalar filter applied AFTER the
     * gate + per-appointment restriction), sourced from the block's own appointment so the
     * restriction matches the right appointment.
     */
    static LightDataFetcher<List<Allocatable>> appointmentBlockAllocatables(StorageOperator operator)
    {
        return new LightSourceFetcher<ReservationGraphQLController.AppointmentBlockDto, List<Allocatable>>(
                ReservationGraphQLController.AppointmentBlockDto.class)
        {
            @Override protected List<Allocatable> read(ReservationGraphQLController.AppointmentBlockDto dto,
                    Supplier<DataFetchingEnvironment> env)
            {
                if (dto == null || dto.appointment() == null) return List.of();
                return resolveAppointmentAllocatables(dto.appointment(), env.get(), operator);
            }
        };
    }

    /** Shared core for Appointment.allocatables + AppointmentBlock.allocatables. */
    private static List<Allocatable> resolveAppointmentAllocatables(
            org.rapla.entities.domain.Appointment a, DataFetchingEnvironment dfe, StorageOperator operator)
    {
        var rc = RequestContextInstrumentation.from(dfe.getGraphQlContext());
        PermissionController pc = rc.permissionController() != null
                ? rc.permissionController() : operator.getPermissionController();
        // PRD 073 — optional scalar filter, applied AFTER the §12 canRead gate
        // so a hidden matching allocatable can't leak.
        @SuppressWarnings("unchecked")
        Map<String, Object> filterArg = dfe.getArgument("filter") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : null;
        return filterAllocatables(a, rc.caller(), pc, filterArg, operator);
    }

    /**
     * §12-safe per-appointment allocatable resolution (canRead gate BEFORE the optional scalar
     * filter; per-appointment restriction applied). Shared by {@code AppointmentBlock.allocatables}
     * and the {@code @group} room dimension (PRD 079) so both honor the exact same leak rules.
     */
    static List<Allocatable> filterAllocatables(org.rapla.entities.domain.Appointment a,
            User caller, PermissionController pc, Map<String, Object> filterArg, StorageOperator operator)
    {
        org.rapla.entities.domain.Reservation r = a.getReservation();
        if (r == null) return List.of();
        // PRD 082 #8 — flipped: O(1) membership against the caller's readable-id set instead of a
        // per-allocatable canRead walk; null when off / not the server operator → canRead (§12-identical).
        final java.util.Set<String> readableIds =
                (operator instanceof org.rapla.storage.impl.server.LocalAbstractCachableOperator lo && lo.isReadModelAuthoritative())
                        ? lo.readableAllocatableIds(caller) : null;
        // PRD 074 A/Option 2 — the nested allocatables list honors the FULL AllocatableFilter,
        // reusing the SAME helpers as Query.allocatables (no second filter path). §12: canRead runs
        // FIRST, so every later predicate only narrows the already-readable set (cannot leak).
        AccessTargetFilter accessFilter = null;
        boolean hasAccessSel = filterArg != null && (filterArg.get("accessibleByUsername") != null
                || filterArg.get("accessibleByUserId") != null
                || filterArg.get("accessibleByGroup") != null
                || filterArg.get("accessLevel") != null);
        if (hasAccessSel)
        {
            try
            {
                accessFilter = AccessTargetFilter.create(
                        ClassificationGraphQLController.stringArg(filterArg, "accessibleByUsername"),
                        ClassificationGraphQLController.stringArg(filterArg, "accessibleByUserId"),
                        ClassificationGraphQLController.stringListArg(filterArg, "accessibleByGroup"),
                        ClassificationGraphQLController.accessLevelArg(filterArg), caller, operator, pc);
            }
            catch (org.rapla.framework.RaplaException e)
            {
                throw new RuntimeException(e);
            }
        }
        List<String> idIn = filterArg == null ? null
                : ClassificationGraphQLController.stringListArg(filterArg, "idIn");
        Object limObj = filterArg == null ? null : filterArg.get("limit");
        int limit = limObj instanceof Number num ? num.intValue() : 0;
        List<Allocatable> out = new ArrayList<>();
        Allocatable[] all = r.getAllocatables();
        if (all == null) return List.of();
        for (Allocatable alloc : all)
        {
            if (alloc == null) continue;
            if (caller != null && !(readableIds != null ? readableIds.contains(alloc.getId()) : pc.canRead(alloc, caller))) continue;  // §12 FIRST (PRD 082 #8 membership when flipped)
            if (filterArg != null)
            {
                if (!ClassificationGraphQLController.matchesMap(alloc, filterArg)) continue;  // scalar
                if (!WhereEvaluator.evaluate(alloc, filterArg, caller, pc)) continue;           // where<TypeKey> (+ §12 ref-recursion)
                if (idIn != null && !idIn.isEmpty() && !idInMatchesHierarchy(alloc, idIn)) continue; // idIn (belongsTo-aware)
                if (accessFilter != null && !accessFilter.test(alloc)) continue;              // PRD 069 access
            }
            if (!appointmentBound(r, alloc, a)) continue;                          // per-appointment restriction
            out.add(alloc);
            if (limit > 0 && out.size() >= limit) break;                          // limit
        }
        return out;
    }

    /**
     * belongsTo-aware {@code idIn} match for the stats fan-out / nested allocatable filter. An
     * allocatable matches if its OWN id is in {@code idIn} OR a belongsTo ancestor's id is — so a
     * building id selects the building's rooms, mirroring the filter path's {@code getDependentRef}
     * down-expansion (here read upward, from the room to its building). Scoped to this nested context
     * ONLY; the global {@code Query.allocatables} idIn keeps exact-id semantics (returns the building,
     * not its rooms).
     */
    private static boolean idInMatchesHierarchy(Allocatable alloc, List<String> idIn)
    {
        Allocatable current = alloc;
        for (int guard = 0; current != null && guard <= 20; guard++)
        {
            String id = current.getId();
            if (id != null && idIn.contains(id)) return true;
            current = belongsToParent(current);
        }
        return false;
    }

    /** The allocatable referenced by {@code alloc}'s belongsTo attribute (its hierarchy parent), or
     *  null if the type has no belongsTo attribute or the value is unset/unresolved. Mirrors
     *  {@code DynamicTypeImpl.getBelongsToAttribute} via the public constraint API (no impl cast). */
    private static Allocatable belongsToParent(Allocatable alloc)
    {
        Classification c = alloc.getClassification();
        if (c == null) return null;
        DynamicType t = c.getType();
        if (t == null) return null;
        for (Attribute attr : t.getAttributes())
        {
            Object bt = attr.getConstraint(ConstraintIds.KEY_BELONGS_TO);
            boolean isBelongsTo = bt instanceof Boolean b ? b : (bt != null && "true".equalsIgnoreCase(bt.toString()));
            if (!isBelongsTo) continue;
            Object v = c.getValue(attr.getKey());
            return v instanceof Allocatable parent ? parent : null;
        }
        return null;
    }

    /** True if {@code alloc} is bound to appointment {@code a} (no restriction = bound to all). */
    private static boolean appointmentBound(org.rapla.entities.domain.Reservation r,
            Allocatable alloc, org.rapla.entities.domain.Appointment a)
    {
        org.rapla.entities.domain.Appointment[] restriction = r.getRestriction(alloc);
        if (restriction == null || restriction.length == 0) return true;
        for (org.rapla.entities.domain.Appointment ra : restriction)
        {
            if (ra != null && ra.getId() != null && ra.getId().equals(a.getId())) return true;
        }
        return false;
    }

    // === PRD 074 Baustein 4 — server-evaluated block fields via the rapla function bridge ===

    static LightDataFetcher<String> appointmentBlockDuration(StorageOperator operator)
    {
        return new LightSourceFetcher<ReservationGraphQLController.AppointmentBlockDto, String>(
                ReservationGraphQLController.AppointmentBlockDto.class)
        {
            @Override protected String read(ReservationGraphQLController.AppointmentBlockDto dto,
                    Supplier<DataFetchingEnvironment> env)
            {
                return dto == null ? null : evalBlockFunction(
                        org.rapla.plugin.eventtimecalculator.DurationFunctions.NAMESPACE,
                        "duration", dto, operator, env.get());
            }
        };
    }

    static LightDataFetcher<String> appointmentBlockTimes(StorageOperator operator)
    {
        return new LightSourceFetcher<ReservationGraphQLController.AppointmentBlockDto, String>(
                ReservationGraphQLController.AppointmentBlockDto.class)
        {
            @Override protected String read(ReservationGraphQLController.AppointmentBlockDto dto,
                    Supplier<DataFetchingEnvironment> env)
            {
                return dto == null ? null : evalBlockFunction(
                        org.rapla.entities.dynamictype.internal.StandardFunctions.NAMESPACE,
                        "times", dto, operator, env.get());
            }
        };
    }

    /**
     * PRD 073/074 bridge — evaluate a rapla {@code Function} (by name) on the real
     * AppointmentBlock via the existing {@code EvalContext} machinery. The factory is
     * resolved by function name from the operator (plugin functions like
     * {@code duration} included); an identity arg feeds the block as the single
     * context object ({@code times} requires one arg, {@code duration} accepts 0..1).
     * Both return String. Returns null on unknown function / parse error.
     */
    /**
     * PRD 074 Baustein 6 — {@code AppointmentBlock.compute(expr:)}. An inline composition
     * column: evaluates an arbitrary rapla ParsedText format string against the block, reusing
     * the exact table-column machinery (`DefaultRaplaTableColumn.format`): guess the
     * classification (block → reservation), parse against the type's ParseContext, eval with
     * the type's EvalContext (which carries the §12 PermissionController + the internal_request
     * environment). The `expr` is the same language as a nameformat / table column — e.g.
     * {@code {p->concat(substring(times(p),0,5),"-",substring(times(p),8,13))}} (p = the block).
     * Invalid/unknown expr → null (save-time validation lands with the view-store, PRD 074).
     */
    static final LightDataFetcher<String> APPOINTMENT_BLOCK_COMPUTE =
            new LightSourceFetcher<ReservationGraphQLController.AppointmentBlockDto, String>(
                    ReservationGraphQLController.AppointmentBlockDto.class)
            {
                @Override protected String read(ReservationGraphQLController.AppointmentBlockDto dto,
                        Supplier<DataFetchingEnvironment> env)
                {
                    if (dto == null || dto.block() == null) return null;
                    DataFetchingEnvironment dfe = env.get();
                    String expr = dfe.getArgument("expr");
                    var rc = RequestContextInstrumentation.from(dfe.getGraphQlContext());
                    return computeBlockExpr(dto.block(), expr, rc.caller());
                }
            };

    /**
     * Evaluate a rapla expression against a single block (the {@code compute(expr:)} engine),
     * shared by the {@code compute} field and PRD 079 {@code appointmentBlockStats} expr group-keys.
     * Reuses the block's DynamicType parse context + {@code createEvalContext} (which carries §12's
     * PermissionController). Returns null on blank/invalid expr; throws on over-long input.
     */
    static String computeBlockExpr(org.rapla.entities.domain.AppointmentBlock block, String expr, User user)
    {
        return computeEntityExpr(block, expr, user);
    }

    /**
     * PRD 080 — generalized {@link #computeBlockExpr} to any classifiable entity (AppointmentBlock,
     * Allocatable, Reservation). Resolves the entity's DynamicType via
     * {@code ParsedText.guessClassification}, parses the (bare-body, subject {@code item}) expr,
     * and evaluates it with the entity as the single context object. Returns null on blank/invalid
     * expr or unresolvable classification; throws on over-long input. §12-safe: the EvalContext
     * carries no PermissionController for these read-only stat exprs (the entity set is already
     * canRead-gated by the caller).
     */
    static String computeEntityExpr(Object entity, String expr, User user)
    {
        if (entity == null || expr == null || expr.isBlank()) return null;
        if (expr.length() > 2000)
        {
            throw new IllegalArgumentException("compute expr too long (max 2000 chars)");
        }
        // PRD 074 V2 — the GraphQL expr surface is bare-body with subject `item`. If the author
        // didn't write the lambda wrapper, wrap it as `{item -> … }` so ParsedText evaluates it
        // (vs. treating it as literal text). A leading `{` means the author wrote the full form.
        String src = expr.trim().startsWith("{") ? expr : "{item->" + expr + "}";
        org.rapla.entities.dynamictype.Classification cls =
                org.rapla.entities.dynamictype.internal.ParsedText.guessClassification(entity);
        if (cls == null) return null;
        org.rapla.entities.dynamictype.internal.DynamicTypeImpl type =
                (org.rapla.entities.dynamictype.internal.DynamicTypeImpl) cls.getType();
        try
        {
            org.rapla.entities.dynamictype.internal.ParsedText pt =
                    new org.rapla.entities.dynamictype.internal.ParsedText(src);
            pt.init(type.getParseContext());
            org.rapla.entities.dynamictype.internal.EvalContext ctx = type.createEvalContext(
                    user, serverLocale,
                    org.rapla.entities.dynamictype.DynamicTypeAnnotations.KEY_NAME_FORMAT,
                    java.util.Collections.singletonList(entity));
            return pt.formatName(ctx);
        }
        catch (org.rapla.entities.IllegalAnnotationException e)
        {
            return null;   // invalid expr — save-time validation will reject at view-store time
        }
    }

    /**
     * PRD 080 item 5 — like {@link #computeEntityExpr} but returns the RAW evaluated object (entity /
     * Collection / String) instead of the formatName string, so a group expr can resolve to a typed
     * entity (e.g. {@code attribute(item,"Gebaeude")} → the building Allocatable). Returns null on
     * blank/invalid expr or unresolvable classification; throws on over-long input.
     */
    static Object computeEntityExprObject(Object entity, String expr, User user)
    {
        if (entity == null || expr == null || expr.isBlank()) return null;
        if (expr.length() > 2000)
        {
            throw new IllegalArgumentException("compute expr too long (max 2000 chars)");
        }
        String src = expr.trim().startsWith("{") ? expr : "{item->" + expr + "}";
        org.rapla.entities.dynamictype.Classification cls =
                org.rapla.entities.dynamictype.internal.ParsedText.guessClassification(entity);
        if (cls == null) return null;
        org.rapla.entities.dynamictype.internal.DynamicTypeImpl type =
                (org.rapla.entities.dynamictype.internal.DynamicTypeImpl) cls.getType();
        try
        {
            org.rapla.entities.dynamictype.internal.ParsedText pt =
                    new org.rapla.entities.dynamictype.internal.ParsedText(src);
            pt.init(type.getParseContext());
            org.rapla.entities.dynamictype.internal.EvalContext ctx = type.createEvalContext(
                    user, serverLocale,
                    org.rapla.entities.dynamictype.DynamicTypeAnnotations.KEY_NAME_FORMAT,
                    java.util.Collections.singletonList(entity));
            return pt.evalToObject(ctx);
        }
        catch (org.rapla.entities.IllegalAnnotationException e)
        {
            return null;
        }
    }

    private static String evalBlockFunction(String namespace, String fnName,
            ReservationGraphQLController.AppointmentBlockDto dto, StorageOperator operator,
            DataFetchingEnvironment dfe)
    {
        org.rapla.entities.domain.AppointmentBlock block = dto.block();
        if (block == null) return null;
        // The factory map is keyed by NAMESPACE (@Bean(name=NAMESPACE)); ParsedText
        // resolves functions the same way. times → org.rapla, duration → the plugin ns.
        org.rapla.entities.extensionpoints.FunctionFactory ff = operator.getFunctionFactory(namespace);
        if (ff == null) return null;
        var rc = RequestContextInstrumentation.from(dfe.getGraphQlContext());
        User user = rc.caller();
        PermissionController pc = rc.permissionController() != null
                ? rc.permissionController() : operator.getPermissionController();
        org.rapla.entities.extensionpoints.Function identity =
                new org.rapla.entities.extensionpoints.Function("org.rapla", "this",
                        java.util.List.<org.rapla.entities.extensionpoints.Function>of())
                {
                    @Override public Object eval(org.rapla.entities.dynamictype.internal.EvalContext c)
                    {
                        return c.getFirstContextObject();
                    }
                };
        try
        {
            org.rapla.entities.extensionpoints.Function fn =
                    ff.createFunction(fnName, java.util.List.of(identity));
            org.rapla.entities.dynamictype.internal.EvalContext ctx =
                    new org.rapla.entities.dynamictype.internal.EvalContext(
                            serverLocale, null, pc, java.util.Map.of(), user,
                            java.util.List.<Object>of(block));
            Object res = fn.eval(ctx);
            return res == null ? null : res.toString();
        }
        catch (org.rapla.entities.IllegalAnnotationException e)
        {
            return null;
        }
    }

    /**
     * Materialize recurrence blocks within a window. Arguments (from, to)
     * come from the env; this fetcher needs envSupplier.get() — but the
     * field's natural caller pattern (calendar query: once per appointment
     * with a single window) makes the env materialization amortize.
     */
    static final LightDataFetcher<List<ReservationGraphQLController.AppointmentBlockDto>> APPOINTMENT_BLOCKS =
            new LightSourceFetcher<org.rapla.entities.domain.Appointment,
                    List<ReservationGraphQLController.AppointmentBlockDto>>(
                    org.rapla.entities.domain.Appointment.class)
            {
                @Override protected List<ReservationGraphQLController.AppointmentBlockDto> read(
                        org.rapla.entities.domain.Appointment a,
                        Supplier<DataFetchingEnvironment> envSupplier)
                {
                    DataFetchingEnvironment env = envSupplier.get();
                    LocalDateTime from = env.getArgument("from");
                    LocalDateTime to   = env.getArgument("to");
                    if (from == null || to == null) return List.of();
                    List<org.rapla.entities.domain.AppointmentBlock> blocks = new ArrayList<>();
                    a.createBlocks(from, to, blocks);
                    List<ReservationGraphQLController.AppointmentBlockDto> out =
                            new ArrayList<>(blocks.size());
                    for (org.rapla.entities.domain.AppointmentBlock b : blocks)
                    {
                        out.add(new ReservationGraphQLController.AppointmentBlockDto(
                                b.getStartDateTime(), b.getEndDateTime(), b.isException(),
                                a.getReservation(), a, b));
                    }
                    return out;
                }
            };

    // === wiring ==============================================================

    /**
     * Register every structural-type field fetcher above. Called by
     * {@link HotSwappableGraphQlSource} on every schema build.
     * Snapshots the server-configured locale into {@link #serverLocale}.
     */
    public static void wire(RuntimeWiring.Builder b, StorageOperator operator, RaplaLocale raplaLocale)
    {
        // Server-configured "Server Sprache" (system preference), NOT the JVM
        // default — see ServerLocaleResolver.
        Locale l = org.rapla.server.internal.ServerLocaleResolver.resolve(operator, raplaLocale);
        if (l != null) serverLocale = l;
        b.type("Allocatable", t -> t
                .dataFetcher("type",           ALLOCATABLE_TYPE)
                .dataFetcher("isPerson",       ALLOCATABLE_IS_PERSON)
                .dataFetcher("isLocation",     ALLOCATABLE_IS_LOCATION)
                .dataFetcher("name",           ALLOCATABLE_NAME)
                .dataFetcher("displayName",    ALLOCATABLE_DISPLAY_NAME)
                .dataFetcher("classification", ALLOCATABLE_CLASSIFICATION)
                .dataFetcher("createdAt",      ALLOCATABLE_CREATED_AT)
                .dataFetcher("lastModifiedAt", ALLOCATABLE_LAST_MODIFIED_AT)
                .dataFetcher("canModify",      ALLOCATABLE_CAN_MODIFY)
                .dataFetcher("owner",          allocatableOwner(operator)));
        b.type("DynamicType", t -> t
                .dataFetcher("name",               DYNAMIC_TYPE_NAME)
                .dataFetcher("classificationType", DYNAMIC_TYPE_CLASSIFICATION_TYPE));
        b.type("Classification", t -> t
                .dataFetcher("typeKey", CLASSIFICATION_TYPE_KEY)
                .dataFetcher("type",    CLASSIFICATION_TYPE));
        b.type("Category", t -> t
                .dataFetcher("name",     CATEGORY_NAME)
                .dataFetcher("path",     categoryPath(operator))
                .dataFetcher("parent",   categoryParent(operator))
                .dataFetcher("children", CATEGORY_CHILDREN)
                .dataFetcher("kind",     categoryKind(operator)));
        b.type("Reservation", t -> t
                .dataFetcher("name",           RESERVATION_NAME)
                .dataFetcher("displayName",    RESERVATION_DISPLAY_NAME)
                .dataFetcher("firstDate",      RESERVATION_FIRST_DATE)
                .dataFetcher("lastDate",       RESERVATION_LAST_DATE)
                .dataFetcher("canModify",      RESERVATION_CAN_MODIFY)
                .dataFetcher("hasConflicts",   reservationHasConflicts(operator))
                .dataFetcher("owner",          reservationOwner(operator))
                .dataFetcher("createdAt",      RESERVATION_CREATED_AT)
                .dataFetcher("lastModifiedAt", RESERVATION_LAST_MODIFIED_AT)
                .dataFetcher("appointments",   RESERVATION_APPOINTMENTS)
                .dataFetcher("appointmentCount", RESERVATION_APPOINTMENT_COUNT)
                .dataFetcher("allocations",    reservationAllocations(operator))
                .dataFetcher("classification", RESERVATION_CLASSIFICATION));
        b.type("Appointment", t -> t
                .dataFetcher("name",         APPOINTMENT_NAME)
                .dataFetcher("allDay",       APPOINTMENT_ALL_DAY)
                .dataFetcher("repeating",    APPOINTMENT_REPEATING)
                .dataFetcher("allocatables", appointmentAllocatables(operator))
                .dataFetcher("blocks",       APPOINTMENT_BLOCKS));
        b.type("AppointmentBlock", t -> t
                .dataFetcher("name",         APPOINTMENT_BLOCK_NAME)
                .dataFetcher("appointmentId", APPOINTMENT_BLOCK_APPOINTMENT_ID)
                .dataFetcher("appointment",  APPOINTMENT_BLOCK_APPOINTMENT)
                .dataFetcher("color",        APPOINTMENT_BLOCK_COLOR)
                .dataFetcher("allocatables", appointmentBlockAllocatables(operator))
                .dataFetcher("duration",     appointmentBlockDuration(operator))
                .dataFetcher("durationMinutes", APPOINTMENT_BLOCK_DURATION_MINUTES)
                .dataFetcher("times",        appointmentBlockTimes(operator))
                .dataFetcher("compute",      APPOINTMENT_BLOCK_COMPUTE));
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

}
