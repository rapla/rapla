package org.rapla.client.edit.reservation;

import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AppointmentImpl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 coverage of {@link ReservationEditSelection} — the headless
 * extraction from {@code AllocatableSelection}'s mutable-state cluster.
 * <p>
 * Tests focus on the pure-state operations: appointment flattening,
 * original-match by id, allocated-set computation. Allocatable-typed
 * methods are exercised indirectly through {@link AllocationConflictModel}
 * tests once those land (Allocatable is a 30-method interface, expensive
 * to stub here).
 */
class ReservationEditSelectionTest
{
    @Test
    void initialStateIsEmpty()
    {
        ReservationEditSelection sel = new ReservationEditSelection();
        assertTrue(sel.mutableReservations().isEmpty());
        assertTrue(sel.originalReservations().isEmpty());
        assertEquals(0, sel.appointments().length);
        assertTrue(sel.allocatedAllocatables().isEmpty());
    }

    @Test
    void setReservationsAcceptsNullsAsEmpty()
    {
        ReservationEditSelection sel = new ReservationEditSelection();
        sel.setReservations(null, null);
        assertTrue(sel.mutableReservations().isEmpty());
        assertTrue(sel.originalReservations().isEmpty());
        assertEquals(0, sel.appointments().length);
    }

    @Test
    void findMatchingOriginalReturnsNullForNullInput()
    {
        ReservationEditSelection sel = new ReservationEditSelection();
        sel.setReservations(Collections.emptyList(), Collections.emptyList());
        assertNull(sel.findMatchingOriginal(null));
    }

    @Test
    void findMatchingOriginalReturnsNullWhenNoOriginals()
    {
        ReservationEditSelection sel = new ReservationEditSelection();
        Reservation r = stubReservationWithId("R1");
        sel.setReservations(List.of(r), Collections.emptyList());
        assertNull(sel.findMatchingOriginal(r));
    }

    @Test
    void appointmentsArrayIsRecomputedOnSetReservations()
    {
        ReservationEditSelection sel = new ReservationEditSelection();
        Appointment a1 = appointment("2026-06-01T09:00", "2026-06-01T10:00");
        Appointment a2 = appointment("2026-06-02T09:00", "2026-06-02T10:00");
        Reservation r = stubReservationWithAppointments("R1", a1, a2);
        sel.setReservations(List.of(r), List.of());
        assertEquals(2, sel.appointments().length);
        assertSame(a1, sel.appointments()[0]);
        assertSame(a2, sel.appointments()[1]);
    }

    @Test
    void reSetReservationsReplacesState()
    {
        ReservationEditSelection sel = new ReservationEditSelection();
        Appointment a1 = appointment("2026-06-01T09:00", "2026-06-01T10:00");
        Reservation r1 = stubReservationWithAppointments("R1", a1);
        sel.setReservations(List.of(r1), List.of());
        assertEquals(1, sel.appointments().length);

        // Replace
        Appointment b1 = appointment("2026-07-01T09:00", "2026-07-01T10:00");
        Appointment b2 = appointment("2026-07-02T09:00", "2026-07-02T10:00");
        Reservation r2 = stubReservationWithAppointments("R2", b1, b2);
        sel.setReservations(List.of(r2), List.of());
        assertEquals(2, sel.appointments().length);
        assertSame(b1, sel.appointments()[0]);
    }

    private static Appointment appointment(String startIso, String endIso)
    {
        return new AppointmentImpl(LocalDateTime.parse(startIso), LocalDateTime.parse(endIso));
    }

    private static Reservation stubReservationWithId(String id)
    {
        return stubReservationWithAppointments(id);
    }

    /**
     * Minimal Reservation stub via java.lang.reflect.Proxy. Only
     * {@code getId}, {@code getAppointments}, {@code getAllocatables}
     * are answered; other calls fail loudly. Stops the test from needing
     * a 30-method real entity hierarchy.
     */
    private static Reservation stubReservationWithAppointments(String id, Appointment... apps)
    {
        return (Reservation) java.lang.reflect.Proxy.newProxyInstance(
                Reservation.class.getClassLoader(),
                new Class[] { Reservation.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getId":                 return id;
                        case "getAppointments":       return apps;
                        case "getSortedAppointments": return java.util.Arrays.asList(apps);
                        case "getAllocatables":       return new org.rapla.entities.domain.Allocatable[0];
                        case "equals":                return proxy == args[0];
                        case "hashCode":              return System.identityHashCode(proxy);
                        case "toString":              return "StubReservation[" + id + "]";
                        default: throw new UnsupportedOperationException(
                                "stub reservation does not implement " + method.getName());
                    }
                });
    }
}
