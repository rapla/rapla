package org.rapla.server.spring.graphql;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * PRD 055 — reservation read resolvers. @QueryMapping for
 * {@code reservation(id:)} and {@code reservations(filter:)};
 * @SchemaMapping for the derived fields on Reservation, Appointment,
 * Allocation, RepeatingRule, AppointmentBlock.
 *
 * <p>Goes through {@link StorageOperator} directly (NOT
 * {@link org.rapla.facade.RaplaFacade}) per the 2026-05-25 decision; §12
 * filter is inline at the output boundary.
 *
 * <p>Hot-path field resolution (Reservation.id, Appointment.start/end)
 * relies on graphql-java's default property accessors via the rapla
 * entity getters. Only the truly-derived fields land here.
 *
 * <p><b>v1 limitations:</b>
 * <ul>
 *   <li>{@code reservations(filter:)} requires a time window; default
 *       limit applies if none supplied. Pagination beyond limit deferred.</li>
 *   <li>Conflict-aware fields not exposed (PRD 055 §"checkConflicts" —
 *       separate query, separate PRD).</li>
 *   <li>{@code Appointment.blocks(from:, to:)} materializes via the
 *       existing {@code Appointment.createBlocks} call — the same path
 *       the TableViewController already uses.</li>
 * </ul>
 */
@Controller
@EnableConfigurationProperties(RaplaGraphqlProperties.class)
public class ReservationGraphQLController
{
    private final StorageOperator operator;
    private final RaplaGraphqlProperties graphqlProps;
    private final ClassificationGraphQLController classificationController;
    /** Optional — present only when the eventtimecalculator plugin is on the classpath/enabled. */
    private final org.springframework.beans.factory.ObjectProvider<
            org.rapla.plugin.eventtimecalculator.EventTimeCalculatorFactory> eventTimeFactory;

    public ReservationGraphQLController(StorageOperator operator,
            RaplaGraphqlProperties graphqlProps,
            ClassificationGraphQLController classificationController,
            org.springframework.beans.factory.ObjectProvider<
                    org.rapla.plugin.eventtimecalculator.EventTimeCalculatorFactory> eventTimeFactory)
    {
        this.operator = operator;
        this.graphqlProps = graphqlProps;
        this.classificationController = classificationController;
        this.eventTimeFactory = eventTimeFactory;
    }

    // ============================================================ query roots

    @QueryMapping
    public Reservation reservation(@Argument("id") String id,
            graphql.schema.DataFetchingEnvironment env) throws RaplaException
    {
        if (id == null || id.isBlank()) return null;
        var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = UnauthenticatedException.require(rc.caller());
        Reservation r;
        try
        {
            r = operator.tryResolve(new ReferenceInfo<>(id, Reservation.class));
        }
        catch (RuntimeException e)
        {
            return null;
        }
        if (r == null) return null;
        PermissionController pc = rc.permissionController() != null
                ? rc.permissionController() : operator.getPermissionController();
        if (!pc.canRead(r, caller)) return null;
        return r;
    }

    @QueryMapping
    public List<Reservation> reservations(@Argument("filter") ReservationFilter filter,
            graphql.schema.DataFetchingEnvironment env) throws RaplaException
    {
        var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = UnauthenticatedException.require(rc.caller());
        if (filter == null || filter.from() == null || filter.to() == null)
        {
            throw new IllegalArgumentException(
                    "reservations(filter:) requires a mandatory time window (from + to)");
        }
        // Optional window cap — configurable via rapla.graphql.max-query-window-days.
        // Default is null (no cap); deployers opt in. Small deployments leave it off.
        Integer cap = graphqlProps.getMaxQueryWindowDays();
        if (cap != null)
        {
            long windowDays = java.time.temporal.ChronoUnit.DAYS.between(filter.from(), filter.to());
            if (windowDays > cap)
            {
                throw new IllegalArgumentException(
                        "Time window > " + cap + " days; reduce the range or paginate "
                                + "(configured via rapla.graphql.max-query-window-days)");
            }
        }
        int limit = filter.limit() != null && filter.limit() > 0
                ? Math.min(filter.limit(), 5000)
                : 500;                                // default 500, hard cap 5000

        // Query via the storage operator's existing reservation query path.
        // rapla's queryAppointments expects the caller-visible target set
        // (allocatables + users) — passing null/empty for both returns no
        // results. We pre-resolve to "all allocatables visible to the caller"
        // which is what the SPA calendar effectively does. §12 falls out
        // naturally: reservations on hidden allocatables don't show up.
        // Filter narrowing on allocatableIdsIn is applied at this stage.
        PermissionController pc = rc.permissionController() != null
                ? rc.permissionController() : operator.getPermissionController();
        // PRD 069 — admin-scoped access-by-target predicate (null if no access
        // selector). Throws uniform FORBIDDEN for unknown/out-of-scope handles.
        AccessTargetFilter accessFilter = AccessTargetFilter.create(
                filter.accessibleByUsername(), filter.accessibleByUserId(),
                filter.accessibleByGroup(), parseAccessLevel(filter.accessLevel()),
                caller, operator, pc);
        Collection<Allocatable> visibleAllocatables;

        // PRD 066 — collect the visible allocatables from BOTH `allocatableIdsIn`
        // (explicit list) and `allocatableMatching` (predicate-driven), UNIONed.
        // If neither is set, fall back to "everything the caller can read."
        boolean hasIdsIn = filter.allocatableIdsIn() != null && !filter.allocatableIdsIn().isEmpty();
        boolean hasMatching = filter.allocatableMatching() != null && !filter.allocatableMatching().isEmpty();

        if (hasIdsIn || hasMatching)
        {
            // PERF (perf-investigation 2026-06-22): resolve the scoped allocatables DIRECTLY via the
            // catalog resolver (its idIn / typeKeyIn / where<TypeKey> passes already run §12 canRead
            // AND drop internal types) instead of materializing+scanning ALL allocatables. The old
            // `getAllocatables(null)` copy (`new HashSet<>(~48k)` on the dhbw store) + full stream-
            // filter was a fixed O(N) tax on EVERY scoped query, even one naming a single id — the
            // dominant fixed-floor cost measured (year window: 880ms unscoped → 20ms one-building).
            // Both arms go through the same resolver, deduped by id (LinkedHashMap = union semantics).
            java.util.LinkedHashMap<String, Allocatable> byId = new java.util.LinkedHashMap<>();
            try
            {
                if (hasMatching)
                {
                    for (Allocatable a : classificationController.allocatables(filter.allocatableMatching()))
                        if (a != null && a.getId() != null) byId.putIfAbsent(a.getId(), a);
                }
                if (hasIdsIn)
                {
                    for (Allocatable a : classificationController.allocatables(java.util.Map.of("idIn", filter.allocatableIdsIn())))
                        if (a != null && a.getId() != null) byId.putIfAbsent(a.getId(), a);
                }
            }
            catch (RaplaException e) { /* fall through — empty scope */ }
            visibleAllocatables = new ArrayList<>(byId.values());
        }
        else
        {
            visibleAllocatables = operator.getAllocatables(null).stream()
                    .filter(a -> pc.canRead(a, caller))
                    .collect(Collectors.toList());
        }
        // Pass `null` as the user — NOT the caller. The storage-layer
        // {@code AppointmentImpl.getAppointments(user, ...)} filters by
        // appointment-OWNER when user is non-null (it's used by
        // "my events" queries elsewhere). For a read query we want every
        // appointment in the window; §12 is enforced by the post-loop
        // {@code pc.canRead(r, caller)} check below. Pre-fix this resolver
        // returned only the caller-owned subset — exactly the bug the
        // legacy REST {@link RemoteStorageController#queryAppointments}
        // also avoids by passing null.
        Collection<Reservation> all = ((org.rapla.storage.SyncStorageOperator) operator)
                .queryAppointmentsSync(null, visibleAllocatables, null,
                        filter.from(), filter.to(), null, null, false)
                .getAllReservations();

        List<Reservation> visible = new ArrayList<>(Math.min(limit, 256));
        for (Reservation r : all)
        {
            if (r == null) continue;
            if (!pc.canRead(r, caller)) continue;
            if (!matches(r, filter)) continue;
            if (accessFilter != null && !accessFilter.test(r)) continue;   // PRD 069
            visible.add(r);
            if (visible.size() >= limit) break;
        }
        // PRD 028 Phase 1 — server-side rank when searchText is set.
        if (filter.searchText() != null && !filter.searchText().isBlank())
        {
            SearchMatcher.MatchKind kind = filter.matchKind() != null
                    ? filter.matchKind() : SearchMatcher.MatchKind.SUBSTRING;
            String needle = filter.searchText();
            visible.sort((a, b) -> {
                int ra = SearchMatcher.rank(a.getName(java.util.Locale.getDefault()), needle, kind);
                int rb = SearchMatcher.rank(b.getName(java.util.Locale.getDefault()), needle, kind);
                if (ra != rb) return Integer.compare(ra, rb);
                String ia = a.getId();
                String ib = b.getId();
                return (ia == null ? "" : ia).compareTo(ib == null ? "" : ib);
            });
        }
        return visible;
    }

    /**
     * PRD 074 — block-rooted read. Reuses {@link #reservations} (so the §12
     * canRead gate, window cap, allocatable narrowing, and search ranking all
     * apply identically), then flattens each visible reservation's appointments
     * into recurrence blocks within the window via the same
     * {@code Appointment.createBlocks} path the nested {@code blocks} field uses.
     * Returns a FLAT list, ascending by start, capped at the same limit.
     */
    @QueryMapping
    public List<AppointmentBlockDto> appointmentBlocks(@Argument("filter") ReservationFilter filter,
            @Argument("sort") List<BlockSort> sort,
            @Argument("offset") Integer offsetArg,
            graphql.schema.DataFetchingEnvironment env) throws RaplaException
    {
        List<Reservation> visible = reservations(filter, env);
        LocalDateTime from = filter.from();
        LocalDateTime to = filter.to();
        int limit = filter.limit() != null && filter.limit() > 0
                ? Math.min(filter.limit(), 5000)
                : 500;
        int offset = offsetArg != null && offsetArg > 0 ? offsetArg : 0;

        // Bounded top-N over the SORT comparator (default START ASC). We keep the
        // `offset + limit + 1` smallest-by-comparator blocks via a max-heap (so the
        // root is the largest of the kept set, evicted when a smaller one arrives).
        // Memory is O(offset+limit) even when recurrence expansion is huge. The +1
        // lets us report `hasMore` without expanding/counting the full set — the
        // window cap is opt-in, so a daily appointment over years is thousands of
        // blocks. The heap generalises the old earliest-N: any sort order, paginated.
        java.util.Comparator<AppointmentBlockDto> cmp = buildBlockComparator(sort);
        long keepL = (long) offset + limit + 1;
        int keep = (int) Math.min(keepL, 20_000);
        java.util.PriorityQueue<AppointmentBlockDto> heap =
                new java.util.PriorityQueue<>(cmp.reversed());   // max by cmp
        List<AppointmentBlock> blocks = new ArrayList<>();
        for (Reservation r : visible)
        {
            for (org.rapla.entities.domain.Appointment a : r.getAppointments())
            {
                blocks.clear();
                a.createBlocks(from, to, blocks);
                for (AppointmentBlock b : blocks)
                {
                    AppointmentBlockDto dto = new AppointmentBlockDto(
                            b.getStartDateTime(), b.getEndDateTime(), b.isException(), r, a, b);
                    if (heap.size() < keep)
                    {
                        heap.offer(dto);
                    }
                    else if (cmp.compare(dto, heap.peek()) < 0)
                    {
                        heap.poll();
                        heap.offer(dto);
                    }
                }
            }
        }
        List<AppointmentBlockDto> sorted = new ArrayList<>(heap);
        sorted.sort(cmp);
        boolean hasMore = sorted.size() > offset + limit;
        int fromIdx = Math.min(offset, sorted.size());
        int toIdx = Math.min(offset + limit, sorted.size());
        List<AppointmentBlockDto> page = new ArrayList<>(sorted.subList(fromIdx, toIdx));

        // Pagination meta → extensions.view.page (render hint for the flat table; with @view).
        java.util.Map<String, Object> pageMeta = new java.util.LinkedHashMap<>();
        pageMeta.put("offset", offset);
        pageMeta.put("limit", limit);
        pageMeta.put("returned", page.size());
        pageMeta.put("hasMore", hasMore);
        env.getGraphQlContext().put(ViewMetaInstrumentation.PAGE_CTX_KEY, pageMeta);
        return page;
    }

    /**
     * PRD 079 — grouped/bucketed analytics over the FULL matched set. `groupBy` declares the
     * dimensions (date bucket / §12 allocatable / custom compute `expr`); `aggregate` the metrics
     * (numeric block fields × fn). Aggregate with empty `groupBy` = one global bucket. Result is
     * normal typed `data` (no extensions side-channel). Server-evaluated; §12-safe (built from the
     * canRead-gated reservation set). Cost-guarded by the mandatory window + a bucket cap.
     */
    @QueryMapping
    public List<BlockStatBucket> appointmentBlockStats(@Argument("filter") ReservationFilter filter,
            @Argument("groupBy") List<BlockGroupKey> groupBy,
            @Argument("aggregate") List<BlockAggregate> aggregate,
            @Argument("limit") Integer limit,
            graphql.schema.DataFetchingEnvironment env) throws RaplaException
    {
        List<BlockGroupKey> groups = groupBy == null ? List.of() : groupBy;
        List<BlockAggregate> aggs = aggregate == null ? List.of() : aggregate;
        for (BlockGroupKey g : groups)
        {
            int dimCount = (g.date() != null ? 1 : 0) + (g.allocatables() != null ? 1 : 0)
                    + (g.expr() != null && !g.expr().isBlank() ? 1 : 0)
                    + (Boolean.TRUE.equals(g.reservation()) ? 1 : 0);
            if (dimCount != 1)
            {
                throw new IllegalArgumentException(
                        "groupBy entry needs exactly one of date/allocatables/expr/reservation (key=" + g.key() + ")");
            }
        }
        List<Reservation> visible = reservations(filter, env);
        LocalDateTime from = filter.from();
        LocalDateTime to = filter.to();
        org.rapla.plugin.eventtimecalculator.EventTimeModel etm = resolveEventTimeModel(env);
        var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        org.rapla.entities.User caller = rc.caller();
        PermissionController pc = rc.permissionController() != null
                ? rc.permissionController() : operator.getPermissionController();
        int nAgg = aggs.size();
        final int maxBuckets = 5000;
        java.util.Map<String, StatBucketAcc> buckets = new java.util.LinkedHashMap<>();
        List<AppointmentBlock> blocks = new ArrayList<>();
        for (Reservation r : visible)
        {
            for (org.rapla.entities.domain.Appointment a : r.getAppointments())
            {
                blocks.clear();
                a.createBlocks(from, to, blocks);
                for (AppointmentBlock b : blocks)
                {
                    AppointmentBlockDto dto = new AppointmentBlockDto(
                            b.getStartDateTime(), b.getEndDateTime(), b.isException(), r, a, b);
                    List<List<Object>> dims = new ArrayList<>(groups.size());
                    boolean skip = false;
                    for (BlockGroupKey g : groups)
                    {
                        List<Object> vals = statGroupValues(g, dto, b, caller, pc, operator);
                        if (vals.isEmpty()) { skip = true; break; }   // can't attribute → drop
                        dims.add(vals);
                    }
                    if (skip) continue;
                    for (List<Object> combo : cartesian(dims))
                    {
                        StringBuilder keyStr = new StringBuilder();
                        for (Object v : combo) keyStr.append(((DimVal) v).value()).append((char) 1);
                        StatBucketAcc acc = buckets.get(keyStr.toString());
                        if (acc == null)
                        {
                            if (buckets.size() >= maxBuckets) continue;   // cost guard
                            List<StatKey> keys = new ArrayList<>(groups.size());
                            for (int gi = 0; gi < groups.size(); gi++)
                            {
                                DimVal dv = (DimVal) combo.get(gi);
                                keys.add(new StatKey(groups.get(gi).key(), dv.value(), dv.entity()));
                            }
                            acc = new StatBucketAcc(keys, nAgg);
                            buckets.put(keyStr.toString(), acc);
                        }
                        acc.count++;
                        for (int i = 0; i < nAgg; i++)
                        {
                            Double v = metricValue(aggs.get(i), dto, b, etm, caller);
                            if (v != null)
                            {
                                acc.sum[i] += v; acc.cnt[i]++;
                                if (v < acc.min[i]) acc.min[i] = v;
                                if (v > acc.max[i]) acc.max[i] = v;
                            }
                        }
                    }
                }
            }
        }
        List<BlockStatBucket> out = new ArrayList<>(buckets.size());
        for (StatBucketAcc acc : buckets.values())
        {
            List<StatValue> values = new ArrayList<>(nAgg);
            for (int i = 0; i < nAgg; i++)
            {
                values.add(statResult(aggs.get(i), acc.sum[i], acc.cnt[i], acc.min[i], acc.max[i], etm));
            }
            out.add(new BlockStatBucket(acc.keys, values, (int) acc.count));
        }
        out.sort(java.util.Comparator.comparing(bk -> bk.keys().toString()));
        if (limit != null && limit > 0 && out.size() > limit)
        {
            out = new ArrayList<>(out.subList(0, limit));
        }
        return out;
    }

    /**
     * PRD 080 item 6 — grouped/bucketed analytics over the §12-visible ALLOCATABLE population
     * (resources/persons), not over bookings. Iterates the filtered allocatable set (reusing the
     * {@code allocatables(filter:)} resolver — same §12 canRead gate + typed where + access filters),
     * groups by DynamicType / custom expr / the allocatable itself, and reduces into the shared
     * {@link BlockStatBucket} result. e.g. "seats per building". {@code self} dimension carries the
     * Allocatable as {@code StatKey.entity}.
     */
    @QueryMapping
    public List<BlockStatBucket> allocatableStats(
            @Argument("filter") java.util.Map<String, Object> filter,
            @Argument("groupBy") List<AllocatableGroupKey> groupBy,
            @Argument("aggregate") List<AllocatableAggregate> aggregate,
            @Argument("limit") Integer limit,
            graphql.schema.DataFetchingEnvironment env) throws RaplaException
    {
        List<AllocatableGroupKey> groups = groupBy == null ? List.of() : groupBy;
        List<AllocatableAggregate> aggs = aggregate == null ? List.of() : aggregate;
        for (AllocatableGroupKey g : groups)
        {
            int dimCount = (Boolean.TRUE.equals(g.type()) ? 1 : 0)
                    + (g.expr() != null && !g.expr().isBlank() ? 1 : 0)
                    + (Boolean.TRUE.equals(g.self()) ? 1 : 0);
            if (dimCount != 1)
            {
                throw new IllegalArgumentException(
                        "allocatableStats groupBy entry needs exactly one of type/expr/self (key=" + g.key() + ")");
            }
        }
        var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = rc.caller();
        PermissionController pc = rc.permissionController() != null
                ? rc.permissionController() : operator.getPermissionController();
        // §12-gated, filtered allocatable set (same resolver as Query.allocatables).
        List<Allocatable> visible = classificationController.allocatables(filter);
        int nAgg = aggs.size();
        final int maxBuckets = 5000;
        java.util.Map<String, StatBucketAcc> buckets = new java.util.LinkedHashMap<>();
        for (Allocatable alloc : visible)
        {
            if (alloc == null) continue;
            List<List<Object>> dims = new ArrayList<>(groups.size());
            boolean skip = false;
            for (AllocatableGroupKey g : groups)
            {
                List<Object> vals = allocGroupValues(g, alloc, caller, pc);
                if (vals.isEmpty()) { skip = true; break; }
                dims.add(vals);
            }
            if (skip) continue;
            for (List<Object> combo : cartesian(dims))
            {
                StringBuilder keyStr = new StringBuilder();
                for (Object v : combo) keyStr.append(((DimVal) v).value()).append((char) 1);
                StatBucketAcc acc = buckets.get(keyStr.toString());
                if (acc == null)
                {
                    if (buckets.size() >= maxBuckets) continue;
                    List<StatKey> keys = new ArrayList<>(groups.size());
                    for (int gi = 0; gi < groups.size(); gi++)
                    {
                        DimVal dv = (DimVal) combo.get(gi);
                        keys.add(new StatKey(groups.get(gi).key(), dv.value(), dv.entity()));
                    }
                    acc = new StatBucketAcc(keys, nAgg);
                    buckets.put(keyStr.toString(), acc);
                }
                acc.count++;
                for (int i = 0; i < nAgg; i++)
                {
                    Double v = entityMetricValue(aggs.get(i).fn(), aggs.get(i).expr(), alloc, caller);
                    if (v != null)
                    {
                        acc.sum[i] += v; acc.cnt[i]++;
                        if (v < acc.min[i]) acc.min[i] = v;
                        if (v > acc.max[i]) acc.max[i] = v;
                    }
                }
            }
        }
        List<BlockStatBucket> out = new ArrayList<>(buckets.size());
        for (StatBucketAcc acc : buckets.values())
        {
            List<StatValue> values = new ArrayList<>(nAgg);
            for (int i = 0; i < nAgg; i++)
            {
                values.add(statResultGeneric(aggs.get(i).key(), aggs.get(i).fn(),
                        acc.sum[i], acc.cnt[i], acc.min[i], acc.max[i]));
            }
            out.add(new BlockStatBucket(acc.keys, values, (int) acc.count));
        }
        out.sort(java.util.Comparator.comparing(bk -> bk.keys().toString()));
        if (limit != null && limit > 0 && out.size() > limit)
        {
            out = new ArrayList<>(out.subList(0, limit));
        }
        return out;
    }

    /**
     * PRD 080 item 7 — grouped/bucketed analytics over the §12-visible RESERVATION set in the window
     * (reuses {@link #reservations} for gating/window/search), grouped by DynamicType / custom expr /
     * the reservation itself, reduced into the shared {@link BlockStatBucket}. {@code self} dimension
     * carries the Reservation as {@code StatKey.entity}. e.g. "events per course type".
     */
    @QueryMapping
    public List<BlockStatBucket> reservationStats(@Argument("filter") ReservationFilter filter,
            @Argument("groupBy") List<ReservationGroupKey> groupBy,
            @Argument("aggregate") List<ReservationAggregate> aggregate,
            @Argument("limit") Integer limit,
            graphql.schema.DataFetchingEnvironment env) throws RaplaException
    {
        List<ReservationGroupKey> groups = groupBy == null ? List.of() : groupBy;
        List<ReservationAggregate> aggs = aggregate == null ? List.of() : aggregate;
        for (ReservationGroupKey g : groups)
        {
            int dimCount = (Boolean.TRUE.equals(g.type()) ? 1 : 0)
                    + (g.expr() != null && !g.expr().isBlank() ? 1 : 0)
                    + (Boolean.TRUE.equals(g.self()) ? 1 : 0);
            if (dimCount != 1)
            {
                throw new IllegalArgumentException(
                        "reservationStats groupBy entry needs exactly one of type/expr/self (key=" + g.key() + ")");
            }
        }
        List<Reservation> visible = reservations(filter, env);
        var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = rc.caller();
        PermissionController pc = rc.permissionController() != null
                ? rc.permissionController() : operator.getPermissionController();
        int nAgg = aggs.size();
        final int maxBuckets = 5000;
        java.util.Map<String, StatBucketAcc> buckets = new java.util.LinkedHashMap<>();
        for (Reservation r : visible)
        {
            if (r == null) continue;
            List<List<Object>> dims = new ArrayList<>(groups.size());
            boolean skip = false;
            for (ReservationGroupKey g : groups)
            {
                List<Object> vals = reservationGroupValues(g, r, caller, pc);
                if (vals.isEmpty()) { skip = true; break; }
                dims.add(vals);
            }
            if (skip) continue;
            for (List<Object> combo : cartesian(dims))
            {
                StringBuilder keyStr = new StringBuilder();
                for (Object v : combo) keyStr.append(((DimVal) v).value()).append((char) 1);
                StatBucketAcc acc = buckets.get(keyStr.toString());
                if (acc == null)
                {
                    if (buckets.size() >= maxBuckets) continue;
                    List<StatKey> keys = new ArrayList<>(groups.size());
                    for (int gi = 0; gi < groups.size(); gi++)
                    {
                        DimVal dv = (DimVal) combo.get(gi);
                        keys.add(new StatKey(groups.get(gi).key(), dv.value(), dv.entity()));
                    }
                    acc = new StatBucketAcc(keys, nAgg);
                    buckets.put(keyStr.toString(), acc);
                }
                acc.count++;
                for (int i = 0; i < nAgg; i++)
                {
                    Double v = entityMetricValue(aggs.get(i).fn(), aggs.get(i).expr(), r, caller);
                    if (v != null)
                    {
                        acc.sum[i] += v; acc.cnt[i]++;
                        if (v < acc.min[i]) acc.min[i] = v;
                        if (v > acc.max[i]) acc.max[i] = v;
                    }
                }
            }
        }
        List<BlockStatBucket> out = new ArrayList<>(buckets.size());
        for (StatBucketAcc acc : buckets.values())
        {
            List<StatValue> values = new ArrayList<>(nAgg);
            for (int i = 0; i < nAgg; i++)
            {
                values.add(statResultGeneric(aggs.get(i).key(), aggs.get(i).fn(),
                        acc.sum[i], acc.cnt[i], acc.min[i], acc.max[i]));
            }
            out.add(new BlockStatBucket(acc.keys, values, (int) acc.count));
        }
        out.sort(java.util.Comparator.comparing(bk -> bk.keys().toString()));
        if (limit != null && limit > 0 && out.size() > limit)
        {
            out = new ArrayList<>(out.subList(0, limit));
        }
        return out;
    }

    /** PRD 080 — group-dimension values for an allocatable (type name / expr / self entity). */
    private static List<Object> allocGroupValues(AllocatableGroupKey g, Allocatable alloc,
            User caller, PermissionController pc)
    {
        if (Boolean.TRUE.equals(g.self()))
        {
            return List.of(new DimVal(alloc.getName(StructuralTypeFetchers.serverLocale()), alloc));
        }
        if (g.expr() != null && !g.expr().isBlank())
        {
            return exprDimVals(g.expr(), alloc, caller, pc);   // PRD 080 item 5 — entity-aware
        }
        if (Boolean.TRUE.equals(g.type()))
        {
            var t = alloc.getClassification() == null ? null : alloc.getClassification().getType();
            if (t == null) return List.of();
            return List.of(new DimVal(t.getName(StructuralTypeFetchers.serverLocale()), null));
        }
        return List.of();
    }

    /** PRD 080 — group-dimension values for a reservation (type name / expr / self entity). */
    private static List<Object> reservationGroupValues(ReservationGroupKey g, Reservation r,
            User caller, PermissionController pc)
    {
        if (Boolean.TRUE.equals(g.self()))
        {
            return List.of(new DimVal(r.getName(StructuralTypeFetchers.serverLocale()), r));
        }
        if (g.expr() != null && !g.expr().isBlank())
        {
            return exprDimVals(g.expr(), r, caller, pc);   // PRD 080 item 5 — entity-aware
        }
        if (Boolean.TRUE.equals(g.type()))
        {
            var t = r.getClassification() == null ? null : r.getClassification().getType();
            if (t == null) return List.of();
            return List.of(new DimVal(t.getName(StructuralTypeFetchers.serverLocale()), null));
        }
        return List.of();
    }

    /**
     * PRD 080 — numeric metric value for an entity (allocatable/reservation) stat. COUNT contributes
     * 1 per row (the {@code cnt} accumulator becomes the bucket population); SUM/MEAN/MIN/MAX coerce
     * the {@code expr} result to a double (canonical '.' decimal; ',' tolerated). Null = skipped.
     */
    private static Double entityMetricValue(AggregateFn fn, String expr, Object entity, User caller)
    {
        if (fn == AggregateFn.COUNT) return 1.0;
        if (expr == null || expr.isBlank()) return null;
        String r = StructuralTypeFetchers.computeEntityExpr(entity, expr, caller);
        if (r == null || r.isBlank()) return null;
        try { return Double.valueOf(r.trim().replace(',', '.')); } catch (NumberFormatException e) { return null; }
    }

    /** PRD 080 — function → StatValue for the entity families (no plugin-formatted text). */
    private static StatValue statResultGeneric(String key, AggregateFn fn,
            double sum, long cnt, double min, double max)
    {
        if (fn == AggregateFn.COUNT) return new StatValue(key, (double) cnt, null);
        if (cnt == 0) return new StatValue(key, null, null);
        double raw = switch (fn)
        {
            case SUM  -> sum;
            case MEAN -> sum / cnt;
            case MIN  -> min;
            case MAX  -> max;
            default    -> Double.NaN;
        };
        if (Double.isNaN(raw)) return new StatValue(key, null, null);
        return new StatValue(key, (double) Math.round(raw), null);
    }

    /**
     * Numeric metric value of a block, or null if unavailable. PRD 074 Stufe b: a metric may be a
     * typed {@code field} OR a numeric {@code expr} (same EL as compute) — the expr result is
     * coerced to a double (canonical '.' decimal); non-numeric results (e.g. a formatted duration
     * "2,45") yield null and are skipped. For break-adjusted/formatted UE use {@code field: DURATION_UNIT}.
     */
    private static Double metricValue(BlockAggregate s, AppointmentBlockDto dto, AppointmentBlock b,
            org.rapla.plugin.eventtimecalculator.EventTimeModel etm, org.rapla.entities.User caller)
    {
        if (s.expr() != null && !s.expr().isBlank())
        {
            String r = StructuralTypeFetchers.computeBlockExpr(b, s.expr(), caller);
            if (r == null || r.isBlank()) return null;
            try { return Double.valueOf(r.trim()); } catch (NumberFormatException e) { return null; }
        }
        BlockMetricField field = s.field();
        if (field == null) return null;
        switch (field)
        {
            case DURATION_MINUTES:
                if (dto.start() == null || dto.end() == null) return null;
                return (double) java.time.Duration.between(dto.start(), dto.end()).toMinutes();
            case DURATION_UNIT:
                if (etm == null) return null;
                long m = etm.calcDuration(b);
                return m > 0 ? (double) m : 0.0;
            default:
                return null;
        }
    }

    /** Apply a function to its accumulators → StatValue (number; +formatted text for DURATION_UNIT). */
    private static StatValue statResult(BlockAggregate s, double sum, long cnt, double min, double max,
            org.rapla.plugin.eventtimecalculator.EventTimeModel etm)
    {
        if (s.fn() == AggregateFn.COUNT) return new StatValue(s.key(), (double) cnt, null);
        if (cnt == 0) return new StatValue(s.key(), null, null);
        double raw = switch (s.fn())
        {
            case SUM  -> sum;
            case MEAN -> sum / cnt;
            case MIN  -> min;
            case MAX  -> max;
            default    -> Double.NaN;
        };
        if (Double.isNaN(raw)) return new StatValue(s.key(), null, null);
        long rounded = Math.round(raw);
        String text = (s.field() == BlockMetricField.DURATION_UNIT && etm != null) ? etm.format(rounded) : null;
        return new StatValue(s.key(), (double) rounded, text);
    }

    /** Dimension values for one group key on one block: compute expr / time bucket / §12 allocatable names. */
    private static List<Object> statGroupValues(BlockGroupKey g, AppointmentBlockDto dto, AppointmentBlock b,
            org.rapla.entities.User caller, PermissionController pc, StorageOperator operator)
    {
        // PRD 080 item 3 — reservation dimension: group by the block's event; entity = Reservation.
        if (Boolean.TRUE.equals(g.reservation()))
        {
            Reservation r = dto.reservation();
            if (r == null) return List.of();
            return List.of(new DimVal(r.getName(StructuralTypeFetchers.serverLocale()), r));
        }
        if (g.expr() != null && !g.expr().isBlank())
        {
            // PRD 080 item 5 — expr may resolve to a typed entity (e.g. attribute(item,"Gebaeude")
            // → the building); §12-gated, list-fan-out. Non-entity results keep the legacy string key.
            return exprDimVals(g.expr(), b, caller, pc);
        }
        if (g.date() != null)
        {
            LocalDateTime t = g.date() == BlockDateField.END ? dto.end() : dto.start();
            String bucket = timeBucket(t, g.by() == null ? null : g.by().name());
            return bucket == null ? List.of() : List.of(new DimVal(bucket, null));
        }
        if (g.allocatables() != null)
        {
            // PRD 080 items 1/2 — entity dimension: each §12-readable allocatable becomes a bucket
            // key carrying the real Allocatable (selectable as StatKey.entity).
            List<Object> out = new ArrayList<>();
            for (Allocatable alloc : StructuralTypeFetchers.filterAllocatables(
                    dto.appointment(), caller, pc, g.allocatables(), operator))
            {
                out.add(new DimVal(alloc.getName(StructuralTypeFetchers.serverLocale()), alloc));
            }
            return out;
        }
        return List.of();
    }

    /**
     * PRD 080 item 5 — resolve a group {@code expr} to dimension values. If the expr evaluates to a
     * typed entity (Allocatable / Reservation / Category) or a collection of them, each readable one
     * becomes an entity-carrying {@link DimVal} (fan-out; §12 canRead-gated so a hidden referenced
     * entity is dropped, never name-leaked). Otherwise the legacy string key (formatName) is used.
     */
    private static List<Object> exprDimVals(String expr, Object subject, User caller, PermissionController pc)
    {
        Object o = StructuralTypeFetchers.computeEntityExprObject(subject, expr, caller);
        if (isEntityResult(o))
        {
            List<Object> out = new ArrayList<>();
            addEntityDimVals(o, caller, pc, out);
            return out;   // entity path: §12-filtered; no string fallback → no name leak
        }
        String s = StructuralTypeFetchers.computeEntityExpr(subject, expr, caller);
        return s == null ? List.of() : List.of(new DimVal(s, null));
    }

    private static boolean isEntityResult(Object o)
    {
        if (o == null) return false;
        if (o instanceof Allocatable || o instanceof Reservation || o instanceof org.rapla.entities.Category)
        {
            return true;
        }
        if (o instanceof java.util.Collection<?> col)
        {
            for (Object e : col) if (isEntityResult(e)) return true;
        }
        return false;
    }

    private static void addEntityDimVals(Object o, User caller, PermissionController pc, List<Object> out)
    {
        if (o == null) return;
        if (o instanceof java.util.Collection<?> col)
        {
            for (Object e : col) addEntityDimVals(e, caller, pc, out);
            return;
        }
        java.util.Locale loc = StructuralTypeFetchers.serverLocale();
        if (o instanceof Allocatable a)
        {
            if (caller == null || pc == null || pc.canRead(a, caller)) out.add(new DimVal(a.getName(loc), a));
        }
        else if (o instanceof Reservation r)
        {
            if (caller == null || pc == null || pc.canRead(r, caller)) out.add(new DimVal(r.getName(loc), r));
        }
        else if (o instanceof org.rapla.entities.Category c)
        {
            out.add(new DimVal(c.getName(loc), c));
        }
        // else: not an entity → ignored (handled by the string fallback in exprDimVals)
    }

    /** Date → bucket label per granularity. ISO_WEEK is ISO-8601 week-based. */
    private static String timeBucket(LocalDateTime t, String by)
    {
        if (t == null) return null;
        return switch (by == null ? "DAY" : by)
        {
            case "ISO_WEEK" -> t.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) + "-W"
                    + String.format("%02d", t.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case "MONTH" -> String.format("%04d-%02d", t.getYear(), t.getMonthValue());
            case "YEAR"  -> String.valueOf(t.getYear());
            default       -> t.toLocalDate().toString();   // DAY
        };
    }

    /** Cartesian product of per-dimension value lists. */
    private static List<List<Object>> cartesian(List<List<Object>> dims)
    {
        List<List<Object>> res = new ArrayList<>();
        res.add(new ArrayList<>());
        for (List<Object> dim : dims)
        {
            List<List<Object>> next = new ArrayList<>();
            for (List<Object> prefix : res)
            {
                for (Object v : dim)
                {
                    List<Object> c = new ArrayList<>(prefix);
                    c.add(v);
                    next.add(c);
                }
            }
            res = next;
        }
        return res;
    }

    // ============================================================ PRD 079 stats types

    /** Aggregation function, mirrors schema {@code AggregateFn}. */
    public enum AggregateFn { SUM, COUNT, MEAN, MIN, MAX }

    /** Date bucket granularity, mirrors schema {@code TimeBucket}. */
    public enum TimeBucket { DAY, ISO_WEEK, MONTH, YEAR }

    /** Which block date a time-bucket group uses, mirrors schema {@code BlockDateField}. */
    public enum BlockDateField { START, END }

    /** Numeric metric source, mirrors schema {@code BlockMetricField}. */
    public enum BlockMetricField { DURATION_MINUTES, DURATION_UNIT }

    /** One grouping dimension (exactly one of date/allocatables/expr), mirrors input {@code BlockGroupKey}. */
    public record BlockGroupKey(String key, BlockDateField date, TimeBucket by,
            java.util.Map<String, Object> allocatables, String expr, Boolean reservation) {}

    /** Internal: one resolved group-dimension value — display string + optional typed entity (PRD 080). */
    private record DimVal(String value, Object entity) {}

    /** One metric spec, mirrors input {@code BlockAggregate}. */
    public record BlockAggregate(String key, BlockMetricField field, String expr, AggregateFn fn) {}

    /** PRD 080 item 6 — one allocatable grouping dimension, mirrors input {@code AllocatableGroupKey}. */
    public record AllocatableGroupKey(String key, Boolean type, String expr, Boolean self) {}

    /** PRD 080 item 6 — one allocatable metric, mirrors input {@code AllocatableAggregate}. */
    public record AllocatableAggregate(String key, String expr, AggregateFn fn) {}

    /** PRD 080 item 7 — one reservation grouping dimension, mirrors input {@code ReservationGroupKey}. */
    public record ReservationGroupKey(String key, Boolean type, String expr, Boolean self) {}

    /** PRD 080 item 7 — one reservation metric, mirrors input {@code ReservationAggregate}. */
    public record ReservationAggregate(String key, String expr, AggregateFn fn) {}

    /** One bucket dimension key/value, mirrors output {@code StatKey}. */
    public record StatKey(String key, String value, Object entity) {}

    /** One aggregate result: {@code number} always; {@code text} = plugin-formatted (DURATION_UNIT). */
    public record StatValue(String key, Double number, String text) {}

    /** A grouped bucket, mirrors output {@code BlockStatBucket}. */
    public record BlockStatBucket(List<StatKey> keys, List<StatValue> values, int count) {}

    /** Per-bucket accumulator (PRD 079): dimension keys + per-metric sum/count/min/max. */
    private static final class StatBucketAcc
    {
        final List<StatKey> keys;
        long count;
        final double[] sum;
        final long[] cnt;
        final double[] min;
        final double[] max;
        StatBucketAcc(List<StatKey> keys, int n)
        {
            this.keys = keys;
            this.sum = new double[n];
            this.cnt = new long[n];
            this.min = new double[n];
            this.max = new double[n];
            java.util.Arrays.fill(min, Double.POSITIVE_INFINITY);
            java.util.Arrays.fill(max, Double.NEGATIVE_INFINITY);
        }
    }

    /** The caller's {@link org.rapla.plugin.eventtimecalculator.EventTimeModel}, or null when the
     * eventtimecalculator plugin is absent/disabled or its config can't be read. */
    private org.rapla.plugin.eventtimecalculator.EventTimeModel resolveEventTimeModel(
            graphql.schema.DataFetchingEnvironment env)
    {
        var factory = eventTimeFactory.getIfAvailable();
        if (factory == null) return null;
        try
        {
            var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
            return factory.getEventTimeModel(rc.caller());
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    /**
     * Builds the block sort comparator from the {@code sort} argument (default START ASC),
     * always appending a stable reservation-id tiebreaker so pagination is deterministic.
     * NAME uses a locale {@link java.text.Collator}; DURATION is intentionally unsupported
     * (it's a formatted string, not numerically comparable).
     */
    private static java.util.Comparator<AppointmentBlockDto> buildBlockComparator(List<BlockSort> sort)
    {
        java.util.Locale loc = StructuralTypeFetchers.serverLocale();
        java.text.Collator collator = java.text.Collator.getInstance(loc);
        java.util.Comparator<AppointmentBlockDto> cmp = null;
        if (sort != null)
        {
            for (BlockSort s : sort)
            {
                if (s == null || s.field() == null) continue;
                java.util.Comparator<AppointmentBlockDto> c = switch (s.field())
                {
                    case START -> java.util.Comparator.comparing(AppointmentBlockDto::start);
                    case END   -> java.util.Comparator.comparing(AppointmentBlockDto::end);
                    case NAME  -> java.util.Comparator.comparing(
                            (AppointmentBlockDto d) -> blockNameKey(d, loc), collator);
                };
                if (s.dir() == SortDir.DESC) c = c.reversed();
                cmp = cmp == null ? c : cmp.thenComparing(c);
            }
        }
        if (cmp == null)
        {
            cmp = java.util.Comparator.comparing(AppointmentBlockDto::start)
                    .thenComparing(AppointmentBlockDto::end);
        }
        return cmp.thenComparing(d -> d.reservation() == null ? ""
                : String.valueOf(d.reservation().getId()));
    }

    private static String blockNameKey(AppointmentBlockDto d, java.util.Locale loc)
    {
        if (d.block() == null) return "";
        String n = org.rapla.entities.domain.NameFormatUtil.getName(d.block(), loc);
        return n == null ? "" : n;
    }

    private static org.rapla.entities.domain.Permission.AccessLevel parseAccessLevel(String s)
    {
        if (s == null || s.isBlank()) return null;
        try { return org.rapla.entities.domain.Permission.AccessLevel.valueOf(s); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unknown accessLevel: " + s); }
    }

    private static boolean matches(Reservation r, ReservationFilter f)
    {
        if (f.typeKeyEq() != null && !f.typeKeyEq().isBlank())
        {
            var type = r.getClassification() == null ? null : r.getClassification().getType();
            if (type == null || !f.typeKeyEq().equals(type.getKey())) return false;
        }
        if (f.ownerEq() != null && !f.ownerEq().isBlank())
        {
            var ownerRef = r.getOwnerRef();
            if (ownerRef == null || !f.ownerEq().equals(ownerRef.getId())) return false;
        }
        if (f.nameContains() != null && !f.nameContains().isBlank())
        {
            String hay = r.getName(java.util.Locale.getDefault());
            if (hay == null || !hay.toLowerCase().contains(f.nameContains().toLowerCase())) return false;
        }
        if (f.searchText() != null && !f.searchText().isBlank())
        {
            String hay = r.getName(java.util.Locale.getDefault());
            SearchMatcher.MatchKind kind = f.matchKind() != null
                    ? f.matchKind() : SearchMatcher.MatchKind.SUBSTRING;
            if (!SearchMatcher.matches(hay, f.searchText(), kind)) return false;
        }
        return true;
    }

    // Per-row resolvers for Reservation + Appointment moved to
    // StructuralTypeFetchers as LightDataFetcher singletons (PRD 055 Tier-1
    // perf migration, 2026-05-29). See StructuralTypeFetchers.wire() for
    // the registration; ReservationGraphQLController now only carries the
    // @QueryMapping roots.

    // ============================================================ DTOs

    /** Mirror of {@code ReservationFilter} input from schema.graphqls. */
    public record ReservationFilter(
            LocalDateTime from,
            LocalDateTime to,
            String typeKeyEq,
            String ownerEq,
            List<String> allocatableIdsIn,
            java.util.Map<String, Object> allocatableMatching,    // PRD 066 — raw AllocatableFilter map
            String nameContains,
            String searchText,
            SearchMatcher.MatchKind matchKind,
            String accessibleByUsername,        // PRD 069
            String accessibleByUserId,          // PRD 069
            List<String> accessibleByGroup,     // PRD 069
            String accessLevel,                 // PRD 069 — AccessLevel enum name
            Integer limit) {}

    /** PRD 074 — sort direction, mirrors schema {@code SortDir}. */
    public enum SortDir { ASC, DESC }

    /** PRD 074 — sortable block fields, mirrors schema {@code BlockSortField}. */
    public enum BlockSortField { START, END, NAME }

    /** PRD 074 — one block sort key, mirrors schema input {@code BlockSort}. */
    public record BlockSort(BlockSortField field, SortDir dir) {}

    /** Mirror of {@code Allocation} output type. */
    public record AllocationDto(Allocatable allocatable, List<String> appointmentIds) {}

    /** Mirror of {@code AppointmentBlock} output type. Carries the owning {@code reservation}
     * (block → reservation → displayName, Baustein 2) and the source {@code appointment}
     * (block → allocatables(filter:), Baustein 3 — the appointment is needed for the
     * per-appointment allocatable restriction). */
    public record AppointmentBlockDto(LocalDateTime start, LocalDateTime end, boolean isException,
            Reservation reservation, org.rapla.entities.domain.Appointment appointment,
            org.rapla.entities.domain.AppointmentBlock block) {}

    /** Mirror of {@code RepeatingRule} output type. Maps rapla {@link Repeating}. */
    public record RepeatingRuleDto(
            RepeatingType type,
            int interval,
            LocalDate end,
            Integer count,
            List<Integer> weekdays,
            List<LocalDate> exceptions)
    {
        static RepeatingRuleDto from(Repeating r)
        {
            LocalDate end = r.getEnd() == null ? null : r.getEnd().toLocalDate();
            Integer count = r.isFixedNumber() && r.getNumber() >= 0 ? r.getNumber() : null;
            List<Integer> weekdays = r.getWeekdays() == null ? List.of()
                    : new ArrayList<>(r.getWeekdays());
            LocalDateTime[] excArr = r.getExceptions();
            List<LocalDate> exceptions = excArr == null ? List.of()
                    : Arrays.stream(excArr)
                            .filter(java.util.Objects::nonNull)
                            .map(LocalDateTime::toLocalDate)
                            .collect(Collectors.toList());
            return new RepeatingRuleDto(r.getType(), r.getInterval(), end, count, weekdays, exceptions);
        }
    }
}
