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

    public ReservationGraphQLController(StorageOperator operator,
            RaplaGraphqlProperties graphqlProps,
            ClassificationGraphQLController classificationController)
    {
        this.operator = operator;
        this.graphqlProps = graphqlProps;
        this.classificationController = classificationController;
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
        Collection<Allocatable> allocatables = operator.getAllocatables(null);
        Collection<Allocatable> visibleAllocatables;

        // PRD 066 — collect ids from BOTH `allocatableIdsIn` (explicit list)
        // and `allocatableMatching` (predicate-driven). Union is the visible
        // allocatable set for the storage query. If neither is set, fall
        // back to "everything the caller can read."
        boolean hasIdsIn = filter.allocatableIdsIn() != null && !filter.allocatableIdsIn().isEmpty();
        boolean hasMatching = filter.allocatableMatching() != null && !filter.allocatableMatching().isEmpty();

        if (hasIdsIn || hasMatching)
        {
            Set<String> wanted = new HashSet<>();
            if (hasIdsIn) wanted.addAll(filter.allocatableIdsIn());
            if (hasMatching)
            {
                // Resolve the inner AllocatableFilter via the existing
                // resolver — gets us §12 + idIn + typeKeyIn + whereXxx in
                // one call. ClassificationGraphQLController already runs
                // canRead, so we only need the ids.
                try
                {
                    for (Allocatable matched : classificationController.allocatables(filter.allocatableMatching()))
                    {
                        if (matched != null && matched.getId() != null) wanted.add(matched.getId());
                    }
                }
                catch (RaplaException e) { /* fall through — empty match set */ }
            }
            visibleAllocatables = allocatables.stream()
                    .filter(a -> a != null && a.getId() != null && wanted.contains(a.getId()))
                    .filter(a -> pc.canRead(a, caller))
                    .collect(Collectors.toList());
        }
        else
        {
            visibleAllocatables = allocatables.stream()
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

    /** Mirror of {@code Allocation} output type. */
    public record AllocationDto(Allocatable allocatable, List<String> appointmentIds) {}

    /** Mirror of {@code AppointmentBlock} output type. */
    public record AppointmentBlockDto(LocalDateTime start, LocalDateTime end, boolean isException) {}

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
