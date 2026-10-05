package org.rapla.server.spring.graphql;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.rapla.RaplaResources;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.Conflict;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.RaplaException;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.SyncStorageOperator;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * PRD 128 D5/D6 — {@code conflicts(filter:)}: the conflicts the caller may modify, exactly the list Swing gets
 * ({@code getConflictsSync(user)}: {@code ConflictFinder}'s in-memory map, {@code canModify(conflict, user)},
 * disable state per user), narrowed by {@link ConflictFilter}.
 *
 * <p>§12 at the output boundary: the resource must be readable (index membership when flipped, else canRead);
 * side 1 is a readable side — the queried reservation when {@code reservationIdsIn} names it, otherwise the side the
 * caller may modify; an unreadable side 2
 * is masked (objects null, generic description) but keeps its ids (D6 — a party to the conflict may know them,
 * and an id alone resolves to nothing). Unknown and unreadable filter ids are dropped silently.
 */
@Controller
public class ConflictGraphQLController
{
    private final StorageOperator operator;
    private final ClassificationGraphQLController allocatableQueries;
    private final RaplaResources i18n;

    public ConflictGraphQLController(StorageOperator operator, ClassificationGraphQLController allocatableQueries,
            RaplaResources i18n)
    {
        this.operator = operator;
        this.allocatableQueries = allocatableQueries;
        this.i18n = i18n;
    }

    /** Mirror of the {@code ConflictFilter} input; {@code resourceIds} is null when no resource criterion is set. */
    record ConflictFilter(Set<String> reservationIds, Set<String> resourceIds, LocalDateTime from, LocalDateTime to,
            Boolean disabledEq) {}

    @QueryMapping
    public List<ConflictRow> conflicts(@Argument("filter") Map<String, Object> filter,
            graphql.schema.DataFetchingEnvironment env) throws RaplaException
    {
        var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = UnauthenticatedException.require(rc.caller());
        ConflictFilter f = parse(filter);
        PermissionController pc = rc.permissionController() != null
                ? rc.permissionController() : operator.getPermissionController();
        Locale locale = rc.locale();
        String masked = i18n.getString("not_visible", locale);

        // ponytail: scans every conflict the caller may modify, then filters — fine at ~11 000 conflicts (super
        // admin on the DHBW test system); a per-resource lookup into ConflictFinder.conflictMap if that grows.
        Collection<Conflict> raw = ((SyncStorageOperator) operator).getConflictsSync(caller);
        List<ConflictRow> rows = new ArrayList<>();
        for (Conflict c : raw)
        {
            if (f.resourceIds() != null && !f.resourceIds().contains(c.getAllocatableId().getId())) continue;
            if (f.reservationIds() != null && !f.reservationIds().contains(c.getReservation1().getId())
                    && !f.reservationIds().contains(c.getReservation2().getId())) continue;
            boolean disabled = !c.checkEnabled();
            if (f.disabledEq() != null && f.disabledEq() != disabled) continue;

            Allocatable alloc = c.getAllocatable();
            Reservation r1 = operator.tryResolve(c.getReservation1());
            Reservation r2 = operator.tryResolve(c.getReservation2());
            Appointment a1 = operator.tryResolve(c.getAppointment1());
            Appointment a2 = operator.tryResolve(c.getAppointment2());
            if (alloc == null || r1 == null || r2 == null || a1 == null || a2 == null) continue;
            if (!rc.canReadAllocatable(alloc)) continue;
            if ((f.from() != null || f.to() != null) && ConflictImpl.getFirstConflictDate(f.from(), f.to(), a1, a2) == null) continue;

            boolean read1 = pc.canRead(r1, caller);
            boolean read2 = pc.canRead(r2, caller);
            if (!read1 && !read2) continue;
            boolean preferSide2 = f.reservationIds() != null ? !f.reservationIds().contains(r1.getId())
                    : !c.isAppointment1Editable() && c.isAppointment2Editable();
            boolean flip = !read1 || (preferSide2 && read2);
            Reservation own = flip ? r2 : r1;
            Reservation other = flip ? r1 : r2;
            Appointment ownApp = flip ? a2 : a1;
            Appointment otherApp = flip ? a1 : a2;
            boolean otherReadable = flip ? read1 : read2;

            rows.add(new ConflictRow(
                    c.getId(),
                    alloc,
                    own.getId(), ownApp.getId(),
                    other.getId(), otherApp.getId(),
                    ownApp, own,
                    otherReadable ? other : null,
                    otherReadable ? otherApp : null,
                    otherReadable ? AvailabilityGraphQLController.displayName(other, locale) : masked,
                    c.getStartDate(),
                    disabled));
        }
        return rows;
    }

    /** PRD 128 D5 — counts over exactly the rows {@link #conflicts} returns, so a bucket never sees more than the list. */
    @QueryMapping
    public List<ReservationGraphQLController.BlockStatBucket> conflictStats(@Argument("filter") Map<String, Object> filter,
            @Argument("groupBy") List<String> groupBy, @Argument("aggregate") List<String> aggregate,
            graphql.schema.DataFetchingEnvironment env) throws RaplaException
    {
        List<String> groups = groupBy == null ? List.of() : groupBy;
        List<String> aggs = aggregate == null || aggregate.isEmpty() ? List.of("COUNT") : aggregate;
        Map<List<ReservationGraphQLController.StatKey>, Integer> counts = new java.util.LinkedHashMap<>();
        for (ConflictRow row : conflicts(filter, env))
        {
            List<ReservationGraphQLController.StatKey> keys = new ArrayList<>(groups.size());
            for (String g : groups)
            {
                keys.add(switch (g)
                {
                    case "RESOURCE" -> new ReservationGraphQLController.StatKey(g, row.resource().getId(), row.resource());
                    case "TYPE" -> new ReservationGraphQLController.StatKey(g, row.resource().getClassification().getType().getKey(), null);
                    default -> new ReservationGraphQLController.StatKey(g, String.valueOf(row.disabled()), null);
                });
            }
            counts.merge(keys, 1, Integer::sum);
        }
        List<ReservationGraphQLController.BlockStatBucket> out = new ArrayList<>(counts.size());
        counts.forEach((keys, count) -> out.add(new ReservationGraphQLController.BlockStatBucket(keys,
                aggs.stream().map(a -> new ReservationGraphQLController.StatValue(a, count.doubleValue(), null)).toList(), count)));
        out.sort(java.util.Comparator.comparing(b -> b.keys().toString()));
        return out;
    }

    /**
     * One row of {@code resourceRequests}. {@code reservation} / {@code appointments} are null / empty when masked.
     */
    public record ResourceRequestRow(Allocatable resource, String reservationId, Reservation reservation,
            List<Appointment> appointments) {}


    /**
     * PRD 128 D4/D5/OQ6 — open requests the caller may approve, like Swing {@code FacadeImpl.getResourceRequests} +
     * {@code isRequestFor}: per resource with an open request (operator index, no appointment scan) that the caller
     * may modify, every reservation with an open request on it. §12: the resource must be readable; an unreadable
     * reservation is masked (OQ22: null object, no appointments; its times show as anonymous calendar blocks).
     */
    @QueryMapping
    public List<ResourceRequestRow> resourceRequests(@Argument("filter") Map<String, Object> filter,
            graphql.schema.DataFetchingEnvironment env) throws RaplaException
    {
        var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = UnauthenticatedException.require(rc.caller());
        if (!(operator instanceof org.rapla.storage.impl.server.LocalAbstractCachableOperator lo)) return List.of();
        PermissionController pc = rc.permissionController() != null
                ? rc.permissionController() : operator.getPermissionController();
        Set<String> resourceIds = filter == null ? null : resourceSet(filter);
        Set<String> reservationIds = filter == null ? null : idSet(filter.get("reservationIdsIn"));
        List<ResourceRequestRow> rows = new ArrayList<>();
        for (String resourceId : new java.util.TreeSet<>(lo.openRequestResourceIds()))
        {
            if (resourceIds != null && !resourceIds.contains(resourceId)) continue;
            Allocatable resource = operator.tryResolve(resourceId, Allocatable.class);
            if (resource == null || !rc.canReadAllocatable(resource) || !pc.canModify(resource, caller)) continue;
            for (String reservationId : new java.util.TreeSet<>(lo.openRequestReservationIds(resourceId)))
            {
                if (reservationIds != null && !reservationIds.contains(reservationId)) continue;
                Reservation reservation = operator.tryResolve(reservationId, Reservation.class);
                if (reservation == null) continue;
                boolean readable = pc.canRead(reservation, caller);
                rows.add(new ResourceRequestRow(resource, reservationId, readable ? reservation : null,
                        readable ? List.of(reservation.getAppointmentsFor(resource)) : List.of()));
            }
        }
        return rows;
    }

    private ConflictFilter parse(Map<String, Object> m) throws RaplaException
    {
        if (m == null) return new ConflictFilter(null, null, null, null, null);
        return new ConflictFilter(idSet(m.get("reservationIdsIn")), resourceSet(m),
                (LocalDateTime) m.get("from"), (LocalDateTime) m.get("to"), (Boolean) m.get("disabledEq"));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> idSet(Object ids)
    {
        return ids == null ? null : new HashSet<>((List<String>) ids);
    }

    /** {@code resourceIdsIn} ∪ {@code resourceMatching}; null when neither is set. */
    @SuppressWarnings("unchecked")
    private Set<String> resourceSet(Map<String, Object> m) throws RaplaException
    {
        Set<String> resources = idSet(m.get("resourceIdsIn"));
        Map<String, Object> matching = (Map<String, Object>) m.get("resourceMatching");
        if (matching == null) return resources;
        if (resources == null) resources = new HashSet<>();
        for (Allocatable a : allocatableQueries.allocatables(matching)) resources.add(a.getId());
        return resources;
    }
}
