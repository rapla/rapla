package org.rapla.server.spring.graphql;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.rapla.client.edit.check.ReservationWarning;
import org.rapla.entities.User;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaException;
import org.rapla.server.spring.JwtUserResolver;
import org.rapla.server.spring.graphql.checks.ReservationCheckService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * PRD 105 Phase 2 — {@code reservationChecks(input:)}: run the shared pre-save checks over a draft.
 *
 * <p>Advisory by contract (D2): this reads, it never writes and never refuses. The client decides
 * what to do with the codes — the same freedom Swing's dialogs give, minus the dialog.
 *
 * <p>§12: the draft is built through the mutation controller's own mapper, which already rejects an
 * allocatable the caller may not read (PERMISSION_DENIED) and an unknown id (REFERENCE_NOT_FOUND) —
 * so a check cannot be used to probe for entities a save could not touch either.
 */
@Controller
public class ReservationChecksController
{
    private final ReservationMutationController mutations;
    private final AvailabilityGraphQLController availability;
    private final org.rapla.storage.StorageOperator operator;
    private final ReservationCheckService checks;
    private final JwtUserResolver jwtUserResolver;
    private final org.rapla.framework.RaplaLocale raplaLocale;

    public ReservationChecksController(ReservationMutationController mutations,
            AvailabilityGraphQLController availability, org.rapla.storage.StorageOperator operator,
            ReservationCheckService checks, JwtUserResolver jwtUserResolver,
            org.rapla.framework.RaplaLocale raplaLocale)
    {
        this.mutations = mutations;
        this.availability = availability;
        this.operator = operator;
        this.checks = checks;
        this.jwtUserResolver = jwtUserResolver;
        this.raplaLocale = raplaLocale;
    }

    @QueryMapping
    @SuppressWarnings("unchecked")
    public List<WarningDto> reservationChecks(@Argument("input") Map<String, Object> input)
            throws RaplaException
    {
        User caller = jwtUserResolver.resolveCurrentUserOrNull();
        if (caller == null)
        {
            throw new ReservationMutationController.ReservationMutationException("PERMISSION_DENIED", "caller",
                    "reservationChecks requires an authenticated caller");
        }
        Map<String, Object> draft = (Map<String, Object>) input.get("draft");
        if (draft == null)
        {
            throw new ReservationMutationController.ReservationMutationException("REQUIRED", "reservationChecks.draft",
                    "draft is required");
        }
        List<String> scope = (List<String>) input.get("scopeAllocatableIds");
        Reservation transientReservation = mutations.buildTransientForCheck(draft, caller);
        List<ReservationWarning> warnings = checks.check(transientReservation, caller,
                raplaLocale.getLocale(), scope == null ? Set.of() : Set.copyOf(scope));
        return toDtos(warnings, transientReservation, caller);
    }

    /**
     * PRD 105 — the same checks for a move the user has only dragged, not confirmed. The server
     * builds the state the move verb WOULD store and runs the chain over it; the client never has
     * to reconstruct move semantics to ask "would this clash?" (PRD 101 keeps them server-side).
     *
     * <p>Exactly one gesture: {@code appointmentId} (SERIE — one appointment shifts) or
     * {@code reservationIds} (EVENT — every appointment of those reservations shifts). SINGLE on a
     * repeating appointment (`splitOccurrence`) has no dry-run yet and is simply unchecked.
     */
    @QueryMapping
    @SuppressWarnings("unchecked")
    public List<WarningDto> moveChecks(@Argument("input") Map<String, Object> input)
            throws RaplaException
    {
        User caller = jwtUserResolver.resolveCurrentUserOrNull();
        if (caller == null)
        {
            throw new ReservationMutationController.ReservationMutationException("PERMISSION_DENIED",
                    "caller", "moveChecks requires an authenticated caller");
        }
        String appointmentId = (String) input.get("appointmentId");
        List<String> reservationIds = (List<String>) input.get("reservationIds");
        Map<String, Object> target = (Map<String, Object>) input.get("target");
        boolean hasAppointment = appointmentId != null && !appointmentId.isBlank();
        boolean hasReservations = reservationIds != null && !reservationIds.isEmpty();
        if (hasAppointment == hasReservations)
        {
            throw new ReservationMutationController.ReservationMutationException("INVALID_VALUE",
                    "moveChecks.input",
                    "exactly one of appointmentId / reservationIds is required");
        }
        List<String> scope = (List<String>) input.get("scopeAllocatableIds");
        Set<String> scopeIds = scope == null ? Set.of() : Set.copyOf(scope);

        List<Reservation> prospective = hasAppointment
                ? List.of(mutations.prospectiveAppointmentMove(appointmentId,
                        (java.time.LocalDateTime) input.get("occurrence"), target, caller))
                : mutations.prospectiveReservationsMove(reservationIds,
                        (java.time.LocalDateTime) input.get("reference"), target, caller);

        List<WarningDto> out = new java.util.ArrayList<>();
        for (Reservation r : prospective)
        {
            out.addAll(toDtos(checks.check(r, caller, raplaLocale.getLocale(), scopeIds), r, caller));
        }
        return out;
    }

    /** Mirror of the {@code ReservationWarning} output type. */
    public record WarningDto(String code, List<String> args, String severity,
            List<ConflictRow> conflicts) {}

    /**
     * PRD 105 — turn the chain's findings into wire rows and attach the CONFLICT evidence. The rows
     * come from the SAME builder `potentialConflicts` uses, so the §12 masking is not reimplemented
     * and a client gets identical shapes from both fields.
     */
    private List<WarningDto> toDtos(List<ReservationWarning> warnings, Reservation prospective,
            User caller) throws RaplaException
    {
        List<ConflictRow> rows = null;
        List<WarningDto> out = new java.util.ArrayList<>(warnings.size());
        for (ReservationWarning w : warnings)
        {
            List<ConflictRow> evidence = List.of();
            if (w.code() == ReservationWarning.Code.CONFLICT)
            {
                if (rows == null) rows = conflictRows(prospective, caller);
                evidence = rows;
            }
            out.add(new WarningDto(w.code().name(), w.args(), w.code().severity().name(), evidence));
        }
        return out;
    }

    private List<ConflictRow> conflictRows(Reservation prospective, User caller) throws RaplaException
    {
        List<org.rapla.entities.domain.Allocatable> allocatables =
                java.util.Arrays.asList(prospective.getAllocatables());
        if (allocatables.isEmpty()) return List.of();
        return availability.buildConflictRows(prospective.getId(), allocatables,
                java.util.Arrays.asList(prospective.getAppointments()),
                List.of(prospective), prospective,
                caller, operator.getPermissionController(), raplaLocale.getLocale());
    }
}
