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
public class ReservationGraphQLController
{
    private final StorageOperator operator;

    public ReservationGraphQLController(StorageOperator operator)
    {
        this.operator = operator;
    }

    // ============================================================ query roots

    @QueryMapping
    public Reservation reservation(@Argument("id") String id,
            graphql.schema.DataFetchingEnvironment env) throws RaplaException
    {
        if (id == null || id.isBlank()) return null;
        var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = rc.caller();
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
        if (caller == null) return null;       // anonymous → §12 hide
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
        User caller = rc.caller();
        if (caller == null) return List.of();        // anonymous
        if (filter == null || filter.from() == null || filter.to() == null)
        {
            throw new IllegalArgumentException(
                    "reservations(filter:) requires a mandatory time window (from + to)");
        }
        // Hard window cap — PRD 055 §"Mandatory query bounds"
        long windowDays = java.time.temporal.ChronoUnit.DAYS.between(filter.from(), filter.to());
        if (windowDays > 365)
        {
            throw new IllegalArgumentException(
                    "Time window > 365 days; reduce the range or paginate");
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
        Collection<Allocatable> allocatables = operator.getAllocatables(null);
        Collection<Allocatable> visibleAllocatables;
        if (filter.allocatableIdsIn() != null && !filter.allocatableIdsIn().isEmpty())
        {
            Set<String> wanted = new HashSet<>(filter.allocatableIdsIn());
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
        Collection<Reservation> all = waitFor(operator.queryAppointmentsByLocalDateTime(
                caller, visibleAllocatables, null, filter.from(), filter.to(), null, null, false))
                .getAllReservations();

        List<Reservation> visible = new ArrayList<>(Math.min(limit, 256));
        for (Reservation r : all)
        {
            if (r == null) continue;
            if (!pc.canRead(r, caller)) continue;
            if (!matches(r, filter)) continue;
            visible.add(r);
            if (visible.size() >= limit) break;
        }
        return visible;
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
        return true;
    }

    // ============================================================ Reservation derived fields

    @SchemaMapping(typeName = "Reservation", field = "firstDate")
    public LocalDateTime firstDate(Reservation r)
    {
        return r.getFirstDate();
    }

    @SchemaMapping(typeName = "Reservation", field = "lastDate")
    public LocalDateTime lastDate(Reservation r)
    {
        return r.getMaxEnd();
    }

    @SchemaMapping(typeName = "Reservation", field = "canModify")
    public boolean canModify(Reservation r, graphql.schema.DataFetchingEnvironment env)
    {
        // Per-row hot field. Use the per-query RequestContextInstrumentation
        // cache instead of re-resolving the caller against SecurityContextHolder
        // on every dispatch — the 462k-dispatch antipattern PRD 035 Cut C
        // eliminated on the allocatables side.
        var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = rc.caller();
        if (caller == null) return false;
        if (caller.isAdmin()) return true;
        return rc.permissionController() != null && rc.permissionController().canModify(r, caller);
    }

    @SchemaMapping(typeName = "Reservation", field = "owner")
    public User reservationOwner(Reservation r) throws RaplaException
    {
        ReferenceInfo<User> ref = r.getOwnerRef();
        if (ref == null) return null;
        return operator.tryResolve(ref);
    }

    @SchemaMapping(typeName = "Reservation", field = "createdAt")
    public OffsetDateTime reservationCreatedAt(Reservation r)
    {
        LocalDateTime ts = r.getCreateDate();
        return ts == null ? null : ts.atOffset(ZoneOffset.UTC);
    }

    @SchemaMapping(typeName = "Reservation", field = "lastModifiedAt")
    public OffsetDateTime reservationLastModifiedAt(Reservation r)
    {
        LocalDateTime ts = r.getLastChanged();
        return ts == null ? null : ts.atOffset(ZoneOffset.UTC);
    }

    @SchemaMapping(typeName = "Reservation", field = "appointments")
    public List<Appointment> reservationAppointments(Reservation r)
    {
        Appointment[] arr = r.getAppointments();
        return arr == null ? List.of() : Arrays.asList(arr);
    }

    /**
     * Build the restriction-aware Allocation list — per Allocatable in the
     * reservation, the {@code appointmentIds} list (null = bound to all).
     * Per PRD 055 Q4, this is the editor's lossless source-of-truth.
     */
    @SchemaMapping(typeName = "Reservation", field = "allocations")
    public List<AllocationDto> reservationAllocations(Reservation r,
            graphql.schema.DataFetchingEnvironment env)
    {
        var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = rc.caller();
        PermissionController pc = rc.permissionController() != null
                ? rc.permissionController() : operator.getPermissionController();
        List<AllocationDto> out = new ArrayList<>();
        Allocatable[] allocatables = r.getAllocatables();
        if (allocatables == null) return List.of();
        Set<String> reservationAppointmentIds = new HashSet<>();
        for (Appointment a : r.getAppointments())
        {
            if (a != null && a.getId() != null) reservationAppointmentIds.add(a.getId());
        }
        for (Allocatable a : allocatables)
        {
            if (a == null) continue;
            // §12 — if the caller can't read the allocatable, drop it
            if (caller != null && !pc.canRead(a, caller)) continue;
            Appointment[] restriction = r.getRestriction(a);
            List<String> appointmentIds = null;
            if (restriction != null && restriction.length > 0)
            {
                appointmentIds = new ArrayList<>(restriction.length);
                for (Appointment appt : restriction)
                {
                    if (appt != null && appt.getId() != null)
                    {
                        appointmentIds.add(appt.getId());
                    }
                }
            }
            out.add(new AllocationDto(a, appointmentIds));
        }
        return out;
    }

    @SchemaMapping(typeName = "Reservation", field = "classification")
    public org.rapla.entities.dynamictype.Classification reservationClassification(Reservation r)
    {
        return r.getClassification();
    }

    // ============================================================ Appointment derived fields

    @SchemaMapping(typeName = "Appointment", field = "allDay")
    public boolean appointmentAllDay(Appointment a)
    {
        // Rapla doesn't have a stored allDay flag; derive from "start at 00:00 and end at 24:00"
        // (or end-start is a multiple of 24h with hour/minute zero). Simple heuristic:
        LocalDateTime start = a.getStart();
        LocalDateTime end = a.getEnd();
        if (start == null || end == null) return false;
        return start.getHour() == 0 && start.getMinute() == 0
                && end.getHour() == 0 && end.getMinute() == 0
                && !end.isEqual(start);
    }

    @SchemaMapping(typeName = "Appointment", field = "repeating")
    public RepeatingRuleDto appointmentRepeating(Appointment a)
    {
        Repeating r = a.getRepeating();
        if (r == null) return null;
        return RepeatingRuleDto.from(r);
    }

    /**
     * Per-appointment pre-resolved allocatables view (PRD 055 Q4 — the
     * listview/iCal/calendar read shape). Server-side join of parent
     * Reservation.allocations with this appointment's id.
     */
    @SchemaMapping(typeName = "Appointment", field = "allocatables")
    public List<Allocatable> appointmentAllocatables(Appointment a,
            graphql.schema.DataFetchingEnvironment env)
    {
        Reservation r = a.getReservation();
        if (r == null) return List.of();
        var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = rc.caller();
        PermissionController pc = rc.permissionController() != null
                ? rc.permissionController() : operator.getPermissionController();
        List<Allocatable> out = new ArrayList<>();
        Allocatable[] all = r.getAllocatables();
        if (all == null) return List.of();
        for (Allocatable alloc : all)
        {
            if (alloc == null) continue;
            if (caller != null && !pc.canRead(alloc, caller)) continue;
            Appointment[] restriction = r.getRestriction(alloc);
            // null/empty restriction = bound to all → include
            // non-empty restriction → include only if THIS appointment's id is in the list
            if (restriction == null || restriction.length == 0)
            {
                out.add(alloc);
                continue;
            }
            for (Appointment ra : restriction)
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

    @SchemaMapping(typeName = "Appointment", field = "blocks")
    public List<AppointmentBlockDto> appointmentBlocks(Appointment a,
            @Argument("from") LocalDateTime from,
            @Argument("to") LocalDateTime to)
    {
        if (from == null || to == null) return List.of();
        List<AppointmentBlock> blocks = new ArrayList<>();
        a.createBlocks(from, to, blocks);
        List<AppointmentBlockDto> out = new ArrayList<>(blocks.size());
        for (AppointmentBlock b : blocks)
        {
            out.add(new AppointmentBlockDto(b.getStartDateTime(), b.getEndDateTime(), b.isException()));
        }
        return out;
    }

    // ============================================================ helpers

    /** Synchronous-wait on a rapla {@code Promise}. Used for the query path
     *  since GraphQL resolvers are sync. ~30 LOC bridge; same shape as
     *  TableViewController.waitFor. */
    private static <T> T waitFor(org.rapla.scheduler.Promise<T> promise) throws RaplaException
    {
        java.util.concurrent.atomic.AtomicReference<T> result = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> err = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        promise.thenAccept(v -> { result.set((T) v); done.countDown(); })
                .exceptionally(t -> { err.set(t); done.countDown(); });
        try
        {
            if (!done.await(30, java.util.concurrent.TimeUnit.SECONDS))
            {
                throw new RaplaException("reservation query timed out");
            }
        }
        catch (InterruptedException ie)
        {
            Thread.currentThread().interrupt();
            throw new RaplaException("reservation query interrupted", ie);
        }
        if (err.get() != null)
        {
            Throwable t = err.get();
            if (t instanceof RaplaException re) throw re;
            throw new RaplaException(t.getMessage(), t);
        }
        return result.get();
    }

    // ============================================================ DTOs

    /** Mirror of {@code ReservationFilter} input from schema.graphqls. */
    public record ReservationFilter(
            LocalDateTime from,
            LocalDateTime to,
            String typeKeyEq,
            String ownerEq,
            List<String> allocatableIdsIn,
            String nameContains,
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
