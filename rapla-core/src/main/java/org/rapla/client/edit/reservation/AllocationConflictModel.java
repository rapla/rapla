package org.rapla.client.edit.reservation;

import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RequestStatus;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.ResourceAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.storage.PermissionController;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * Pure-Java conflict + permission computation for one allocatable over a
 * set of appointments. Carved out of
 * {@code AllocatableSelection.calcConflictingAppointments(Allocatable)}
 * (the inner-class {@code AllocationRendering} of the Swing tier).
 * <p>
 * No Swing, no facade — only the entity / permission types in rapla-core.
 * Re-usable from server REST endpoints (PRD 024 §3 {@code /edit/check-conflicts})
 * and from a future Angular client.
 */
public final class AllocationConflictModel
{
    private AllocationConflictModel() {}

    /**
     * Output of {@link #compute}: parallel boolean array marking which
     * appointments conflict, count summary, and the first non-null
     * request-status encountered. {@code conflictingAppointments[i] = true}
     * means appointment {@code appointments[i]} is shown as conflicting
     * in the UI.
     */
    public record AllocationOutcome(boolean[] conflictingAppointments,
                                    int conflictCount,
                                    int permissionConflictCount,
                                    RequestStatus aggregateRequestStatus) {}

    /**
     * Compute the outcome for {@code allocatable} given the list of
     * appointments and the current binding map.
     *
     * @param allocatable          the resource being checked
     * @param appointments         appointments to inspect (order preserved
     *                             in the returned boolean array)
     * @param allocatableBindings  pre-computed {@code allocatable → conflicting
     *                             appointments} map (typically maintained
     *                             by the caller)
     * @param permissionController shared permission engine
     * @param user                 the user whose permissions are checked
     * @param today                "today" for permission-window checks
     */
    public static AllocationOutcome compute(Allocatable allocatable,
                                            Appointment[] appointments,
                                            Map<ReferenceInfo<Allocatable>, Collection<Appointment>> allocatableBindings,
                                            PermissionController permissionController,
                                            User user,
                                            LocalDate today)
    {
        if (allocatable == null) throw new IllegalArgumentException("allocatable must not be null");
        if (appointments == null) throw new IllegalArgumentException("appointments must not be null");

        boolean[] conflicts = new boolean[appointments.length];
        int conflictCount = 0;
        int permissionConflictCount = 0;
        RequestStatus aggregateRequestStatus = null;

        String annotation = allocatable.getAnnotation(ResourceAnnotations.KEY_CONFLICT_CREATION);
        boolean holdBackConflicts = annotation != null
                && annotation.equals(ResourceAnnotations.VALUE_CONFLICT_CREATION_IGNORE);

        Collection<Appointment> binding = allocatableBindings != null
                ? allocatableBindings.get(allocatable.getReference())
                : null;

        for (int i = 0; i < appointments.length; i++)
        {
            Appointment appointment = appointments[i];
            boolean isConflict = binding != null && binding.contains(appointment);

            // appointment.getReservation() may be null for transient
            // appointments built from a wire DTO (PRD 024 P2 controller).
            // Treat null-reservation as "no request status set".
            Reservation parent = appointment.getReservation();
            if (parent != null)
            {
                RequestStatus status = parent.getRequestStatus(allocatable);
                if (status != null && aggregateRequestStatus == null)
                {
                    aggregateRequestStatus = status;
                }
            }

            if (isConflict)
            {
                if (!holdBackConflicts)
                {
                    conflicts[i] = true;
                    conflictCount++;
                }
            }
            else if (!isAllowed(permissionController, allocatable, appointment, user, today))
            {
                if (!holdBackConflicts)
                {
                    conflicts[i] = true;
                    conflictCount++;
                }
                permissionConflictCount++;
            }
        }

        return new AllocationOutcome(conflicts, conflictCount, permissionConflictCount, aggregateRequestStatus);
    }

    /**
     * Standalone helper mirroring {@code AllocatableSelection.isAllowed}.
     * Kept public so callers that need only the permission check (e.g.
     * a permission-coloring path) can reuse it.
     */
    public static boolean isAllowed(PermissionController permissionController,
                                    Allocatable allocatable,
                                    Appointment appointment,
                                    User user,
                                    LocalDate today)
    {
        return permissionController.canAllocate(allocatable, user,
                appointment.getStart(), appointment.getMaxEnd(), today);
    }
}
