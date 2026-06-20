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

    /** §12 read gate for ALLOCATABLE references. Anonymous = no access;
     *  authenticated = canRead via the cached PermissionController. */
    static boolean canReadAllocatable(Allocatable a, RequestContextInstrumentation.RequestCtx rc)
    {
        if (rc == null || rc.caller() == null || rc.permissionController() == null) return false;
        return rc.permissionController().canRead(a, rc.caller());
    }

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
                    if (!pc.canRead(alloc, caller)) continue;
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
                    if (caller != null && !pc.canRead(a, caller)) continue;
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
        org.rapla.entities.domain.Reservation r = a.getReservation();
        if (r == null) return List.of();
        var rc = RequestContextInstrumentation.from(dfe.getGraphQlContext());
        User caller = rc.caller();
        PermissionController pc = rc.permissionController() != null
                ? rc.permissionController() : operator.getPermissionController();
        // PRD 073 — optional scalar filter, applied AFTER the §12 canRead gate
        // so a hidden matching allocatable can't leak.
        @SuppressWarnings("unchecked")
        Map<String, Object> filterArg = dfe.getArgument("filter") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : null;
        List<Allocatable> out = new ArrayList<>();
        Allocatable[] all = r.getAllocatables();
        if (all == null) return List.of();
        for (Allocatable alloc : all)
        {
            if (alloc == null) continue;
            if (caller != null && !pc.canRead(alloc, caller)) continue;
            if (filterArg != null
                    && !ClassificationGraphQLController.matchesMap(alloc, filterArg)) continue;
            org.rapla.entities.domain.Appointment[] restriction = r.getRestriction(alloc);
            if (restriction == null || restriction.length == 0)
            {
                out.add(alloc);
                continue;
            }
            for (org.rapla.entities.domain.Appointment ra : restriction)
            {
                if (ra != null && ra.getId() != null && ra.getId().equals(a.getId()))
                {
                    out.add(alloc);
                    break;
                }
            }
        }
        return out;
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
                    if (expr == null || expr.isBlank()) return null;
                    if (expr.length() > 2000)
                    {
                        throw new IllegalArgumentException("compute expr too long (max 2000 chars)");
                    }
                    org.rapla.entities.domain.AppointmentBlock block = dto.block();
                    org.rapla.entities.dynamictype.Classification cls =
                            org.rapla.entities.dynamictype.internal.ParsedText.guessClassification(block);
                    if (cls == null) return null;
                    org.rapla.entities.dynamictype.internal.DynamicTypeImpl type =
                            (org.rapla.entities.dynamictype.internal.DynamicTypeImpl) cls.getType();
                    var rc = RequestContextInstrumentation.from(dfe.getGraphQlContext());
                    User user = rc.caller();
                    try
                    {
                        org.rapla.entities.dynamictype.internal.ParsedText pt =
                                new org.rapla.entities.dynamictype.internal.ParsedText(expr);
                        pt.init(type.getParseContext());
                        org.rapla.entities.dynamictype.internal.EvalContext ctx = type.createEvalContext(
                                user, serverLocale,
                                org.rapla.entities.dynamictype.DynamicTypeAnnotations.KEY_NAME_FORMAT,
                                java.util.Collections.singletonList(block));
                        return pt.formatName(ctx);
                    }
                    catch (org.rapla.entities.IllegalAnnotationException e)
                    {
                        return null;   // invalid expr — save-time validation will reject at view-store time
                    }
                }
            };

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
        if (raplaLocale != null)
        {
            Locale l = raplaLocale.getLocale();
            if (l != null) serverLocale = l;
        }
        b.type("Allocatable", t -> t
                .dataFetcher("type",           ALLOCATABLE_TYPE)
                .dataFetcher("name",           ALLOCATABLE_NAME)
                .dataFetcher("displayName",    ALLOCATABLE_DISPLAY_NAME)
                .dataFetcher("classification", ALLOCATABLE_CLASSIFICATION)
                .dataFetcher("createdAt",      ALLOCATABLE_CREATED_AT)
                .dataFetcher("lastModifiedAt", ALLOCATABLE_LAST_MODIFIED_AT)
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
                .dataFetcher("allocations",    reservationAllocations(operator))
                .dataFetcher("classification", RESERVATION_CLASSIFICATION));
        b.type("Appointment", t -> t
                .dataFetcher("allDay",       APPOINTMENT_ALL_DAY)
                .dataFetcher("repeating",    APPOINTMENT_REPEATING)
                .dataFetcher("allocatables", appointmentAllocatables(operator))
                .dataFetcher("blocks",       APPOINTMENT_BLOCKS));
        b.type("AppointmentBlock", t -> t
                .dataFetcher("allocatables", appointmentBlockAllocatables(operator))
                .dataFetcher("duration",     appointmentBlockDuration(operator))
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

    private static boolean isMultiSelect(Attribute attr)
    {
        Object c = attr.getConstraint(ConstraintIds.KEY_MULTI_SELECT);
        if (c == null) return false;
        if (c instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(c.toString());
    }

}
