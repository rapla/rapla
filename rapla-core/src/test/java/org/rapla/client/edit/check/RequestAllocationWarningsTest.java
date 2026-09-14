package org.rapla.client.edit.check;

import org.junit.jupiter.api.Test;
import org.rapla.client.edit.check.ReservationWarning.Code;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.RequestStatus;
import org.rapla.entities.domain.Reservation;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 coverage of {@link RequestAllocationWarnings}.
 */
class RequestAllocationWarningsTest
{
    @Test
    void nullReservationsReturnsEmpty()
    {
        assertTrue(RequestAllocationWarnings.evaluate(null, Locale.ROOT).isEmpty());
    }

    @Test
    void noAllocatablesProducesNoWarnings()
    {
        Reservation r = stubReservation(Map.of());
        assertTrue(RequestAllocationWarnings.evaluate(List.of(r), Locale.ROOT).isEmpty());
    }

    @Test
    void noneInRequestStateProducesNoWarnings()
    {
        Allocatable a = stubAllocatable("Room A");
        Map<Allocatable, RequestStatus> statuses = new HashMap<>();
        statuses.put(a, null);
        Reservation r = stubReservation(statuses);
        assertTrue(RequestAllocationWarnings.evaluate(List.of(r), Locale.ROOT).isEmpty());
    }

    @Test
    void singleRequestProducesOneWarning()
    {
        Allocatable a = stubAllocatable("Room A");
        Reservation r = stubReservation(Map.of(a, RequestStatus.REQUESTED));
        List<ReservationWarning> ws = RequestAllocationWarnings.evaluate(List.of(r), Locale.ROOT);
        assertEquals(1, ws.size());
        assertEquals(Code.REQUEST_PENDING, ws.get(0).code());
        assertEquals("Room A", ws.get(0).args().get(0));
    }

    @Test
    void multipleRequestsAcrossReservationsAllAppear()
    {
        Allocatable a1 = stubAllocatable("Room A");
        Allocatable a2 = stubAllocatable("Room B");
        Allocatable a3 = stubAllocatable("Room C");
        Map<Allocatable, RequestStatus> m1 = new HashMap<>();
        m1.put(a1, RequestStatus.REQUESTED);
        m1.put(a2, null);
        Reservation r1 = stubReservation(m1);
        Map<Allocatable, RequestStatus> m2 = new HashMap<>();
        m2.put(a3, RequestStatus.REQUESTED);
        Reservation r2 = stubReservation(m2);

        List<ReservationWarning> ws = RequestAllocationWarnings.evaluate(List.of(r1, r2), Locale.ROOT);
        assertEquals(2, ws.size());
        assertEquals("Room A", ws.get(0).args().get(0));
        assertEquals("Room C", ws.get(1).args().get(0));
    }

    // ---------- helpers ----------

    private static Reservation stubReservation(Map<Allocatable, RequestStatus> statuses)
    {
        Allocatable[] allocs = statuses.keySet().toArray(new Allocatable[0]);
        return (Reservation) Proxy.newProxyInstance(
                Reservation.class.getClassLoader(),
                new Class[] { Reservation.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getAllocatables":  return allocs;
                        case "getRequestStatus": return statuses.get(args[0]);
                        case "equals":           return proxy == args[0];
                        case "hashCode":         return System.identityHashCode(proxy);
                        default: return null;
                    }
                });
    }

    private static Allocatable stubAllocatable(String name)
    {
        return (Allocatable) Proxy.newProxyInstance(
                Allocatable.class.getClassLoader(),
                new Class[] { Allocatable.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getName":  return name;
                        case "equals":   return proxy == args[0];
                        case "hashCode": return System.identityHashCode(proxy);
                        default: return null;
                    }
                });
    }
}
