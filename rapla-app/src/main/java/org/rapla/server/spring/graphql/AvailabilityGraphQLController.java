package org.rapla.server.spring.graphql;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.rapla.RaplaResources;
import org.rapla.client.edit.reservation.AllocationConflictModel;
import org.rapla.client.edit.reservation.AllocationConflictModel.AllocationOutcome;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.RaplaException;
import org.rapla.server.spring.graphql.ReservationMutationController.ReservationMutationException;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.SyncStorageOperator;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * PRD 091 Phase 1 — the two availability queries.
 *
 * <p>{@code resourceAvailability} is the CHEAP display path (finder/picker):
 * per candidate a status + which own draft appointments clash.
 * {@code potentialConflicts} is the EXPENSIVE detail path (drill-down /
 * save preflight): full potential-conflict rows, same {@link ConflictRow}
 * shape as the realized {@code conflicts(reservationId:)} query.
 *
 * <p>Both compose {@code getAllAllocatableBindingsSync} +
 * {@link AllocationConflictModel} — the same service path
 * {@code /api/edit/check-conflicts} uses; no parallel conflict logic.
 *
 * <p>§12: candidate resolution silently drops unreadable/nonexistent ids
 * (indistinguishable); the filter case delegates to the §12-scoped
 * {@code allocatables(filter:)} resolver. In {@code potentialConflicts}
 * an unreadable counterparty is MASKED (side-2 fields null, generic
 * description) — never dropped, the resource IS busy.
 */
@Controller
public class AvailabilityGraphQLController
{
    private final StorageOperator operator;
    private final ClassificationGraphQLController allocatableQueries;
    private final RaplaResources i18n;

    public AvailabilityGraphQLController(StorageOperator operator,
            ClassificationGraphQLController allocatableQueries, RaplaResources i18n)
    {
        this.operator = operator;
        this.allocatableQueries = allocatableQueries;
        this.i18n = i18n;
    }

    // ============================================================ resourceAvailability

    @QueryMapping
    public List<ResourceAvailabilityRow> resourceAvailability(
            @Argument("input") Map<String, Object> input,
            graphql.schema.DataFetchingEnvironment env) throws RaplaException
    {
        var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = UnauthenticatedException.require(rc.caller());
        PermissionController pc = rc.permissionController() != null
                ? rc.permissionController() : operator.getPermissionController();

        List<Appointment> appointments = buildDraftAppointments(input);
        Collection<Reservation> ignoreList = resolveIgnoreList(input);
        List<Allocatable> candidates = resolveCandidates(input, rc);
        if (candidates.isEmpty()) return List.of();

        Map<ReferenceInfo<Allocatable>, Collection<Appointment>> bindings =
                flattenBindings(candidates, appointments, ignoreList);

        LocalDate today = operator.today();
        Appointment[] appointmentArray = appointments.toArray(Appointment[]::new);
        List<ResourceAvailabilityRow> rows = new ArrayList<>(candidates.size());
        for (Allocatable a : candidates)
        {
            AllocationOutcome outcome = AllocationConflictModel.compute(
                    a, appointmentArray, bindings, pc, caller, today);
            List<String> conflictingIds = new ArrayList<>();
            boolean[] flags = outcome.conflictingAppointments();
            for (int i = 0; i < flags.length; i++)
            {
                if (flags[i]) conflictingIds.add(appointmentArray[i].getId());
            }
            rows.add(new ResourceAvailabilityRow(a, statusOf(outcome, appointmentArray.length, a, pc, caller, today), conflictingIds));
        }
        return rows;
    }

    /** Wire row for the GraphQL {@code ResourceAvailability} type. */
    public record ResourceAvailabilityRow(Allocatable allocatable, String status,
            List<String> conflictingAppointmentIds)
    {
    }

    /**
     * v1 status mapping — {@code AllocatableRowStatusModel.statusOf} with
     * {@code checkRestrictions=false} semantics (no draft reservation state
     * on the availability path).
     */
    private static String statusOf(AllocationOutcome outcome, int total,
            Allocatable a, PermissionController pc, User caller, LocalDate today)
    {
        int conflicts = outcome.conflictCount();
        if (conflicts == 0) return "AVAILABLE";
        if (conflicts == total)
        {
            if (conflicts == outcome.permissionConflictCount())
            {
                return pc.canRequest(a, caller, today) ? "REQUEST_ONLY" : "FORBIDDEN";
            }
            return "CONFLICT";
        }
        return "PARTIAL";
    }

    // ============================================================ expandOccurrences

    /**
     * PRD 091 Phase 4.1 — the recurrence editor's occurrence preview.
     * Server-owned expansion via {@code AppointmentImpl.createBlocks}
     * (exceptions included, flagged — the preview strikes them through).
     * Pure function of the input — no stored data, no §12 surface beyond
     * the auth gate; capped at {@code limit} within a 2-year horizon.
     */
    @QueryMapping
    public List<OccurrenceRow> expandOccurrences(
            @Argument("appointment") Map<String, Object> appointment,
            @Argument("limit") Integer limit,
            graphql.schema.DataFetchingEnvironment env)
    {
        var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        UnauthenticatedException.require(rc.caller());

        AppointmentImpl a = buildDraftAppointment(appointment, "appointment");
        int cap = limit == null ? 30 : Math.max(1, Math.min(limit, 100));
        List<org.rapla.entities.domain.AppointmentBlock> blocks = new ArrayList<>();
        a.createBlocks(a.getStart(), a.getStart().plusYears(2), blocks, false);
        List<OccurrenceRow> rows = new ArrayList<>(Math.min(blocks.size(), cap));
        for (org.rapla.entities.domain.AppointmentBlock b : blocks)
        {
            if (rows.size() >= cap) break;
            rows.add(new OccurrenceRow(b.getStartDateTime(), b.getEndDateTime(), b.isException()));
        }
        return rows;
    }

    /** Wire row for the GraphQL {@code Occurrence} type. */
    public record OccurrenceRow(LocalDateTime start, LocalDateTime end, boolean exception)
    {
    }

    // ============================================================ potentialConflicts

    @QueryMapping
    public List<ConflictRow> potentialConflicts(
            @Argument("input") Map<String, Object> input,
            graphql.schema.DataFetchingEnvironment env) throws RaplaException
    {
        var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = UnauthenticatedException.require(rc.caller());
        PermissionController pc = rc.permissionController() != null
                ? rc.permissionController() : operator.getPermissionController();
        Locale locale = rc.locale();

        String draftReservationId = (String) input.get("reservationId");
        List<Appointment> appointments = buildDraftAppointments(input);
        Collection<Reservation> ignoreList = resolveIgnoreList(input);
        // Side 1 of every row is the draft; when it resolves to a stored
        // reservation (editing an existing event) it is self-ignored and,
        // if caller-readable, exposed as reservation1.
        Reservation ownStored = tryResolveReservation(draftReservationId);
        if (ownStored != null)
        {
            ignoreList.add(ownStored);
            if (!pc.canRead(ownStored, caller)) ownStored = null;
        }

        List<Allocatable> allocatables = new ArrayList<>();
        for (String id : stringList(input.get("allocatableIds")))
        {
            Allocatable a = tryResolveAllocatable(id);
            if (a == null || !rc.canReadAllocatable(a)) continue;   // §12 — hidden ≡ nonexistent
            allocatables.add(a);
        }
        if (allocatables.isEmpty()) return List.of();

        return buildConflictRows(draftReservationId, allocatables, appointments, ignoreList,
                ownStored, caller, pc, locale);
    }

    /**
     * The shared row builder behind {@code potentialConflicts} — also used by the PRD 105 pre-save
     * checks, so a CONFLICT finding can name WHAT it clashes with instead of only that it does.
     * §12 masking (unreadable counterparty → null fields + generic description) lives here, once.
     */
    List<ConflictRow> buildConflictRows(String draftReservationId, List<Allocatable> allocatables,
            List<Appointment> appointments, Collection<Reservation> ignoreList, Reservation ownStored,
            User caller, PermissionController pc, Locale locale) throws RaplaException
    {
        Map<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> raw =
                ((SyncStorageOperator) operator).getAllAllocatableBindingsSync(
                        allocatables, appointments, ignoreList);

        String masked = i18n.getString("not_visible", locale);
        List<ConflictRow> rows = new ArrayList<>();
        for (Allocatable a : allocatables)
        {
            Map<Appointment, Collection<Appointment>> perAppointment = raw.get(a.getReference());
            if (perAppointment == null) continue;
            for (Appointment ownApp : appointments)
            {
                Collection<Appointment> foreignApps = perAppointment.get(ownApp);
                if (foreignApps == null) continue;
                for (Appointment foreignApp : foreignApps)
                {
                    if (foreignApp == null) continue;
                    Reservation foreignRes = foreignApp.getReservation();
                    if (foreignRes == null) continue;
                    boolean readable = pc.canRead(foreignRes, caller);

                    LocalDateTime startDate = ConflictImpl.getFirstConflictDate(null, null, ownApp, foreignApp);
                    if (startDate == null) startDate = ownApp.getStart();

                    String rowId = ownApp.getId() + "/" + a.getId() + "/"
                            + (readable ? foreignApp.getId() : startDate.toString());
                    rows.add(new ConflictRow(
                            rowId,
                            a,
                            draftReservationId, ownApp.getId(),
                            readable ? foreignRes.getId() : null,
                            readable ? foreignApp.getId() : null,
                            ownApp,
                            ownStored,
                            readable ? foreignRes : null,
                            readable ? foreignApp : null,
                            readable ? displayName(foreignRes, locale) : masked,
                            startDate));
                }
            }
        }
        return rows;
    }

    /** description must never be blank — nameless events fall back to their id. */
    private static String displayName(Reservation r, Locale locale)
    {
        String name = r.getName(locale);
        return name == null || name.isBlank() ? r.getId() : name;
    }

    // ============================================================ shared input handling

    /**
     * Materialize the draft appointments from the GraphQL input — the
     * PRD 024 transient-appointment pattern. Appointment ids are REQUIRED
     * (D3 / PRD 056 §9): they are the join key for the result; missing id →
     * loud ValidationError. Recurrence is not materialized by the mutation
     * path yet (PRD 056 v1) — reject it here rather than compute silently
     * wrong availability.
     */
    private static List<Appointment> buildDraftAppointments(Map<String, Object> input)
    {
        List<?> specs = (List<?>) input.get("appointments");
        if (specs == null || specs.isEmpty())
        {
            throw new ReservationMutationException("REQUIRED", "input.appointments",
                    "at least one appointment is required");
        }
        List<Appointment> out = new ArrayList<>(specs.size());
        int i = 0;
        for (Object o : specs)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> spec = (Map<String, Object>) o;
            out.add(buildDraftAppointment(spec, "input.appointments[" + i++ + "]"));
        }
        return out;
    }

    private static AppointmentImpl buildDraftAppointment(Map<String, Object> spec, String path)
    {
        String id = (String) spec.get("id");
        if (id == null || id.isBlank())
        {
            throw new ReservationMutationException("REQUIRED", path + ".id",
                    "appointment id is REQUIRED for availability queries (id-first drafts, PRD 056 §9)");
        }
        LocalDateTime start = (LocalDateTime) spec.get("start");
        LocalDateTime end = (LocalDateTime) spec.get("end");
        if (start == null || end == null || !end.isAfter(start))
        {
            throw new ReservationMutationException("INVALID", path,
                    "appointment needs start < end");
        }
        AppointmentImpl a = new AppointmentImpl(start, end);
        a.setId(id);
        // mutation-path parity (PRD 091 Phase 4.5): whole-day normalization +
        // repeating materialization — availability must evaluate exactly what
        // a save would persist
        if (Boolean.TRUE.equals(spec.get("allDay")))
        {
            a.setWholeDays(true);
        }
        AppointmentInputMapper.applyRepeating(a, spec, path);
        return a;
    }

    private Collection<Reservation> resolveIgnoreList(Map<String, Object> input)
    {
        Collection<Reservation> ignore = new ArrayList<>();
        Reservation own = tryResolveReservation((String) input.get("reservationId"));
        if (own != null) ignore.add(own);
        for (String id : stringList(input.get("ignoreReservationIds")))
        {
            Reservation r = tryResolveReservation(id);
            if (r != null) ignore.add(r);
        }
        return ignore;
    }

    private List<Allocatable> resolveCandidates(Map<String, Object> input,
            RequestContextInstrumentation.RequestCtx rc) throws RaplaException
    {
        Map<?, ?> candidates = (Map<?, ?>) input.get("candidates");
        Object filter = candidates.get("filter");
        if (filter != null)
        {
            // §12-scoped resolution incl. dynamic where<TypeKey> fields —
            // delegate to the existing allocatables(filter:) resolver.
            @SuppressWarnings("unchecked")
            Map<String, Object> filterMap = (Map<String, Object>) filter;
            return allocatableQueries.allocatables(filterMap);
        }
        List<Allocatable> out = new ArrayList<>();
        for (String id : stringList(candidates.get("ids")))
        {
            Allocatable a = tryResolveAllocatable(id);
            if (a == null || !rc.canReadAllocatable(a)) continue;   // §12 — hidden ≡ nonexistent
            out.add(a);
        }
        return out;
    }

    /** Flatten the nested bindings map to allocatable → own conflicting appointments. */
    private Map<ReferenceInfo<Allocatable>, Collection<Appointment>> flattenBindings(
            Collection<Allocatable> allocatables, List<Appointment> appointments,
            Collection<Reservation> ignoreList) throws RaplaException
    {
        Map<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> raw =
                ((SyncStorageOperator) operator).getAllAllocatableBindingsSync(
                        allocatables, appointments, ignoreList);
        Map<ReferenceInfo<Allocatable>, Collection<Appointment>> flat = new LinkedHashMap<>();
        for (Map.Entry<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> e : raw.entrySet())
        {
            List<Appointment> conflicting = new ArrayList<>();
            for (Map.Entry<Appointment, Collection<Appointment>> inner : e.getValue().entrySet())
            {
                if (inner.getValue() != null && !inner.getValue().isEmpty())
                {
                    conflicting.add(inner.getKey());
                }
            }
            if (!conflicting.isEmpty()) flat.put(e.getKey(), conflicting);
        }
        return flat;
    }

    private Reservation tryResolveReservation(String id)
    {
        if (id == null || id.isBlank()) return null;
        try
        {
            return operator.tryResolve(new ReferenceInfo<>(id, Reservation.class));
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    private Allocatable tryResolveAllocatable(String id)
    {
        if (id == null || id.isBlank()) return null;
        try
        {
            return operator.tryResolve(new ReferenceInfo<>(id, Allocatable.class));
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    private static List<String> stringList(Object value)
    {
        if (value == null) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : (List<?>) value)
        {
            if (o != null) out.add(o.toString());
        }
        return out;
    }
}
