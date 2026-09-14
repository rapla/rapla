package org.rapla.client.edit.reservation;

import org.rapla.client.edit.reservation.AllocationConflictModel.AllocationOutcome;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RequestStatus;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.storage.PermissionController;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * Pure-Java decision for the resource-picker row status icon — what
 * category does each allocatable row render as? Carved out of
 * {@code AllocatableSelection.getIcon(Allocatable)} (PRD 023 Phase 6a).
 * <p>
 * The Swing tree renderer (and any future Angular row renderer) maps
 * the returned {@link Status} to its widget-specific icon / colour.
 * The decision itself is pure: given current selection state +
 * permissions + the binding map, which of five categories is this row?
 * <p>
 * No Swing imports. Reused via the picker-side tree in
 * {@code AllocatableSelection} today; an Angular allocatable picker
 * (PRD 026 / 028) needs the exact same decision and can call this
 * directly (no REST needed — all inputs are already on the client).
 */
public final class AllocatableRowStatusModel
{
    private AllocatableRowStatusModel() {}

    /** Visual category for an allocatable row in the picker. */
    public enum Status
    {
        /** All requested appointments fit; user can allocate. */
        AVAILABLE,
        /** Some appointments would conflict but it's still bookable. */
        NOT_ALWAYS_AVAILABLE,
        /** User cannot directly allocate but is allowed to request. */
        REQUEST,
        /** All appointments would conflict and the user CAN'T request. */
        CONFLICT,
        /** User is not allowed to allocate this resource at all. */
        FORBIDDEN
    }

    /**
     * All inputs to the decision in one bag — verbose but honest about
     * dependencies. Callers either construct this once per row or reuse
     * the bag across rows that share most inputs.
     */
    public record Inputs(
            Allocatable allocatable,
            AllocationOutcome outcome,
            Appointment[] appointments,
            Collection<Reservation> mutableReservations,
            Collection<Reservation> originalReservations,
            Map<ReferenceInfo<Allocatable>, Collection<Appointment>> allocatableBindings,
            PermissionController permissionController,
            User user,
            LocalDate today,
            boolean checkRestrictions)
    {
    }

    /**
     * Compute the row status. Mirrors the existing branch tree in
     * {@code AllocatableSelection.getIcon(Allocatable)}; do not change
     * branch order or short-circuits without aligning the Swing tests.
     */
    public static Status statusOf(Inputs in)
    {
        AllocationOutcome out = in.outcome;
        int conflictCount = out.conflictCount();
        int permissionConflictCount = out.permissionConflictCount();
        int total = in.appointments.length;

        if (conflictCount == 0)
        {
            return Status.AVAILABLE;
        }
        if (conflictCount == total)
        {
            if (conflictCount == permissionConflictCount)
            {
                if (!in.checkRestrictions)
                {
                    return in.permissionController.canRequest(in.allocatable, in.user, in.today)
                            ? Status.REQUEST
                            : Status.FORBIDDEN;
                }
            }
            else
            {
                return Status.CONFLICT;
            }
        }
        else if (!in.checkRestrictions)
        {
            return Status.NOT_ALWAYS_AVAILABLE;
        }

        // request-only with at least one REQUESTED status → REQUEST
        if (in.checkRestrictions
                && in.permissionController.isRequestOnly(in.allocatable, in.user, in.today))
        {
            if (out.aggregateRequestStatus() == RequestStatus.REQUESTED)
            {
                return Status.REQUEST;
            }
        }

        // per-appointment forbidden check
        for (Appointment app : in.appointments)
        {
            for (Reservation r : in.mutableReservations)
            {
                if (r.hasAllocatedOn(in.allocatable, app)
                        && !hasPermissionToAllocate(app, in.allocatable, in.user,
                                in.originalReservations, in.permissionController, in.today))
                {
                    return Status.FORBIDDEN;
                }
            }
        }

        if (permissionConflictCount - conflictCount == 0)
        {
            return Status.AVAILABLE;
        }

        // restriction-aware fallback: walk the restriction list and check
        // intersection with the binding map.
        Collection<Appointment> restriction = restrictionFor(in);
        Collection<Appointment> binding = in.allocatableBindings != null
                ? in.allocatableBindings.get(in.allocatable.getReference())
                : null;
        if (binding != null)
        {
            for (Appointment app : restriction)
            {
                if (binding.contains(app))
                {
                    return Status.CONFLICT;
                }
            }
        }
        return Status.NOT_ALWAYS_AVAILABLE;
    }

    /**
     * Decision: is the user permitted to allocate {@code allocatable}
     * for the given {@code appointment}? Carved out of
     * {@code AllocatableSelection.hasPermissionToAllocate(...)}.
     * <p>
     * Behaviour:
     * <ul>
     *   <li>When there are no {@code originalReservations} (new event):
     *       check {@code permissionController.canAllocate(...)} for the
     *       appointment's time window.</li>
     *   <li>When there are originals (edit existing): require
     *       {@code permissionController.hasPermissionToAllocate(...)} to
     *       hold for EVERY original reservation. Conservative — one
     *       original-denied → overall denied.</li>
     * </ul>
     */
    public static boolean hasPermissionToAllocate(
            Appointment appointment,
            Allocatable allocatable,
            User user,
            Collection<Reservation> originalReservations,
            PermissionController permissionController,
            LocalDate today)
    {
        if (originalReservations == null || originalReservations.isEmpty())
        {
            return permissionController.canAllocate(allocatable, user,
                    appointment.getStart(), appointment.getMaxEnd(), today);
        }
        for (Reservation r : originalReservations)
        {
            if (!permissionController.hasPermissionToAllocate(user, appointment, allocatable, r, today))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Collect the union of per-reservation restrictions for one allocatable.
     * Mirrors {@code AllocatableSelection.getAllAppointmentsFor(Allocatable)}.
     * Public so callers (Swing, Angular over REST) can reuse the same
     * fallback list when needed.
     */
    public static Collection<Appointment> restrictionFor(Inputs in)
    {
        java.util.List<Appointment> out = new java.util.ArrayList<>();
        for (Reservation r : in.mutableReservations)
        {
            Appointment[] restriction = r.getRestriction(in.allocatable);
            if (restriction.length == 0)
            {
                restriction = r.getAppointmentsFor(in.allocatable);
            }
            for (Appointment a : restriction) out.add(a);
        }
        return out;
    }
}
