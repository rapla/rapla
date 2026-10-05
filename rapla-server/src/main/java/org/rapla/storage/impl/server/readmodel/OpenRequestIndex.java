package org.rapla.storage.impl.server.readmodel;

import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.RaplaComponent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PRD 128 D4 — open resource requests keyed by the requested resource id ({@code Reservation.getRequestStatus != null}),
 * so the request list needs no scan over every appointment of every resource. Maintained at the operator's read-model
 * seam: rebuilt on load, {@link #put} on every stored reservation, {@link #remove} on delete. Templates are skipped
 * like in {@code queryAppointmentsSync(requestsOnly)}.
 */
public final class OpenRequestIndex
{
    private final Map<String, Set<String>> reservationsByResource = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> resourcesByReservation = new ConcurrentHashMap<>();

    public synchronized void put(Reservation reservation)
    {
        String id = reservation.getId();
        remove(id);
        if (RaplaComponent.isTemplate(reservation)) return;
        Set<String> resources = ConcurrentHashMap.newKeySet();
        for (Allocatable a : reservation.getRequestedAllocatables())
        {
            resources.add(a.getId());
            reservationsByResource.computeIfAbsent(a.getId(), k -> ConcurrentHashMap.newKeySet()).add(id);
        }
        if (!resources.isEmpty()) resourcesByReservation.put(id, resources);
    }

    public synchronized void remove(String reservationId)
    {
        Set<String> resources = resourcesByReservation.remove(reservationId);
        if (resources == null) return;
        for (String resourceId : resources)
        {
            Set<String> reservations = reservationsByResource.get(resourceId);
            if (reservations == null) continue;
            reservations.remove(reservationId);
            if (reservations.isEmpty()) reservationsByResource.remove(resourceId);
        }
    }

    public synchronized void clear()
    {
        reservationsByResource.clear();
        resourcesByReservation.clear();
    }

    /** The resources with at least one open request. */
    public Set<String> resourceIds()
    {
        return Set.copyOf(reservationsByResource.keySet());
    }

    /** The reservations with an open request on {@code resourceId}. */
    public Set<String> reservationIds(String resourceId)
    {
        Set<String> reservations = reservationsByResource.get(resourceId);
        return reservations == null ? Set.of() : Set.copyOf(reservations);
    }
}
