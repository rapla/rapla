package org.rapla.plugin.reservationedit;

import java.time.LocalDate;
import java.util.List;

/**
 * Wire-format request for {@code POST /edit/check-conflicts}.
 * <p>
 * The user identifies a set of allocatables (by reference id) and a set
 * of <em>unsaved</em> appointment specs they want to schedule. The
 * server computes which appointments would conflict on each allocatable
 * (existing reservations) and which would be permission-denied.
 */
public record ConflictCheckRequest(
        List<String> allocatableIds,
        List<AppointmentSpec> appointments,
        LocalDate today)
{
    public ConflictCheckRequest
    {
        allocatableIds = allocatableIds == null ? List.of() : List.copyOf(allocatableIds);
        appointments   = appointments   == null ? List.of() : List.copyOf(appointments);
    }
}
