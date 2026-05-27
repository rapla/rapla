package org.rapla.facade.internal;

import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.Conflict;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarModelConflictMatchTest
{
    @Test
    void reservationOnLeftSideOfSelectedConflictMatches()
    {
        Reservation r = stubReservation("res-1");
        Conflict c = stubConflict("res-1", "res-2");
        assertTrue(CalendarModelImpl.isReservationInAnySelectedConflict(r, List.of(c)));
    }

    @Test
    void reservationOnRightSideOfSelectedConflictMatches()
    {
        Reservation r = stubReservation("res-2");
        Conflict c = stubConflict("res-1", "res-2");
        assertTrue(CalendarModelImpl.isReservationInAnySelectedConflict(r, List.of(c)));
    }

    @Test
    void unrelatedReservationDoesNotMatch()
    {
        Reservation r = stubReservation("res-3");
        Conflict c = stubConflict("res-1", "res-2");
        assertFalse(CalendarModelImpl.isReservationInAnySelectedConflict(r, List.of(c)));
    }

    @Test
    void matchesAcrossMultipleSelectedConflicts()
    {
        Reservation r = stubReservation("res-5");
        Conflict c1 = stubConflict("res-1", "res-2");
        Conflict c2 = stubConflict("res-3", "res-4");
        Conflict c3 = stubConflict("res-5", "res-6");
        assertTrue(CalendarModelImpl.isReservationInAnySelectedConflict(r, List.of(c1, c2, c3)));
    }

    private static Reservation stubReservation(String id)
    {
        ReferenceInfo<Reservation> ref = new ReferenceInfo<>(id, Reservation.class);
        return (Reservation) Proxy.newProxyInstance(
                Reservation.class.getClassLoader(),
                new Class[] { Reservation.class },
                (proxy, method, args) ->
                {
                    if ("getReference".equals(method.getName())) return ref;
                    if ("getId".equals(method.getName())) return id;
                    return null;
                });
    }

    @SuppressWarnings("unchecked")
    private static Conflict stubConflict(String reservation1Id, String reservation2Id)
    {
        ReferenceInfo<Reservation> ref1 = new ReferenceInfo<>(reservation1Id, Reservation.class);
        ReferenceInfo<Reservation> ref2 = new ReferenceInfo<>(reservation2Id, Reservation.class);
        return (Conflict) Proxy.newProxyInstance(
                Conflict.class.getClassLoader(),
                new Class[] { Conflict.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getReservation1": return ref1;
                        case "getReservation2": return ref2;
                        default: return null;
                    }
                });
    }
}
