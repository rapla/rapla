package org.rapla.plugin.reservationedit;

import java.util.List;

/**
 * Wire-format mirror of
 * {@link org.rapla.client.edit.reservation.AllocationConflictModel.AllocationOutcome}.
 * <p>
 * One per allocatable in the request. The boolean array is parallel to
 * the request's {@code appointments} list — index N indicates whether
 * appointment N conflicts (or is permission-denied) against this
 * allocatable.
 * <p>
 * {@code aggregateRequestStatus} mirrors the entity-side enum stringly
 * ({@code "requested"} / {@code "confirmed"} / etc.) per
 * {@link org.rapla.entities.domain.RequestStatus#toString}; {@code null}
 * when no current reservation has a request status against this
 * allocatable. Kept stringly to avoid leaking the entity enum into the
 * wire shape (Angular shouldn't care about Java enum names).
 */
public record AllocationOutcomeDto(
        String allocatableId,
        boolean[] conflictingAppointments,
        int conflictCount,
        int permissionConflictCount,
        String aggregateRequestStatus)
{
    public AllocationOutcomeDto
    {
        // Defensive copy: callers may mutate the source array.
        conflictingAppointments = conflictingAppointments == null
                ? new boolean[0]
                : conflictingAppointments.clone();
    }
}
