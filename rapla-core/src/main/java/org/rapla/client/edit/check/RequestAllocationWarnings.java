package org.rapla.client.edit.check;

import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.RequestStatus;
import org.rapla.entities.domain.Reservation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Pure-Java decision: emit one {@link ReservationWarning.Code#REQUEST_PENDING}
 * warning per allocatable that's in {@code REQUESTED} state on any of
 * the reservations being saved.
 * <p>
 * Carved out of {@code rapla-client/.../check/RequestAllocationCheck}
 * (PRD 023 Phase 10).
 */
public final class RequestAllocationWarnings
{
    private RequestAllocationWarnings() {}

    /**
     * @param reservations  the reservations being saved
     * @param locale        locale for {@code allocatable.getName(locale)} — passed
     *                      as the warning argument so the view can render it
     */
    public static List<ReservationWarning> evaluate(Collection<Reservation> reservations, Locale locale)
    {
        List<ReservationWarning> out = new ArrayList<>();
        if (reservations == null) return out;
        for (Reservation r : reservations)
        {
            for (Allocatable a : r.getAllocatables())
            {
                if (r.getRequestStatus(a) == RequestStatus.REQUESTED)
                {
                    String name = a.getName(locale);
                    out.add(ReservationWarning.of(ReservationWarning.Code.REQUEST_PENDING,
                            name != null ? name : ""));
                }
            }
        }
        return out;
    }
}
