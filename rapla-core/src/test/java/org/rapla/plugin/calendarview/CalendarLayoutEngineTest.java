package org.rapla.plugin.calendarview;

import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AppointmentImpl;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 unit coverage of {@link CalendarLayoutEngine} (PRD 024 Phase 3).
 * Exercises the engine with real {@link AppointmentImpl} entities and
 * Proxy-stubbed {@link Reservation}s — keeps the test sub-second without
 * a facade.
 */
class CalendarLayoutEngineTest
{
    @Test
    void rejectsToBeforeFrom()
    {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to   = LocalDate.of(2026, 6, 1); // same, not after
        assertThrows(IllegalArgumentException.class, () ->
                CalendarLayoutEngine.layout(from, to, LayoutStrategyId.BEST_FIT, GroupBy.DAY, List.of(), null));
    }

    @Test
    void emptyReservationsYieldEmptyBlocks()
    {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to   = LocalDate.of(2026, 6, 8);
        CalendarPage page = CalendarLayoutEngine.layout(from, to,
                LayoutStrategyId.BEST_FIT, GroupBy.DAY, List.of(), null);
        assertEquals(7, page.columns().size(), "7 days in [Jun 1, Jun 8)");
        assertTrue(page.blocks().isEmpty());
        assertEquals(LayoutStrategyId.BEST_FIT, page.strategy());
        assertEquals(GroupBy.DAY, page.groupBy());
    }

    @Test
    void dayLayoutPlacesBlockInCorrectColumn()
    {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to   = LocalDate.of(2026, 6, 8);
        Appointment a = appointment("2026-06-03T09:00", "2026-06-03T10:00");
        Reservation r = stubReservation("R1", "MyEvent", a);

        CalendarPage page = CalendarLayoutEngine.layout(from, to,
                LayoutStrategyId.BEST_FIT, GroupBy.DAY, List.of(r), null);

        assertEquals(7, page.columns().size());
        assertEquals(1, page.blocks().size());
        RenderedBlock block = page.blocks().get(0);
        // Jun 3 is index 2 in [Jun 1, Jun 8)
        assertEquals(2, block.columnIndex());
        assertEquals(0, block.slotIndex(), "single block lives in slot 0");
        assertEquals(1, block.slotCount(), "exactly one slot in its column");
        assertEquals("R1", block.reservationId());
        assertEquals("MyEvent", block.name());
    }

    @Test
    void dayLayoutOverlappingBlocksGetDistinctSlots()
    {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to   = LocalDate.of(2026, 6, 8);
        Appointment a1 = appointment("2026-06-03T09:00", "2026-06-03T11:00");
        Appointment a2 = appointment("2026-06-03T10:00", "2026-06-03T12:00"); // overlaps a1
        Reservation r1 = stubReservation("R1", "Event1", a1);
        Reservation r2 = stubReservation("R2", "Event2", a2);

        CalendarPage page = CalendarLayoutEngine.layout(from, to,
                LayoutStrategyId.BEST_FIT, GroupBy.DAY, List.of(r1, r2), null);

        assertEquals(2, page.blocks().size());
        // Both blocks live in column 2 (Jun 3) but in distinct slots.
        assertEquals(2, page.blocks().get(0).columnIndex());
        assertEquals(2, page.blocks().get(1).columnIndex());
        assertEquals(2, page.blocks().get(0).slotCount(),
                "slot-count reflects the 2-way overlap");
        assertEquals(2, page.blocks().get(1).slotCount());
        // Slot indices differ
        assertTrue(page.blocks().get(0).slotIndex() != page.blocks().get(1).slotIndex(),
                "overlapping blocks must get different slot indices");
    }

    @Test
    void groupStartTimesStrategyAlsoWorks()
    {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to   = LocalDate.of(2026, 6, 8);
        Appointment a = appointment("2026-06-03T09:00", "2026-06-03T10:00");
        Reservation r = stubReservation("R1", "MyEvent", a);

        CalendarPage page = CalendarLayoutEngine.layout(from, to,
                LayoutStrategyId.GROUP_START_TIMES, GroupBy.DAY, List.of(r), null);

        assertEquals(1, page.blocks().size());
        assertEquals(LayoutStrategyId.GROUP_START_TIMES, page.strategy());
    }

    @Test
    void outOfRangeAppointmentIsSkipped()
    {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to   = LocalDate.of(2026, 6, 8);
        Appointment a = appointment("2026-07-15T09:00", "2026-07-15T10:00");
        Reservation r = stubReservation("R1", "FarAway", a);

        CalendarPage page = CalendarLayoutEngine.layout(from, to,
                LayoutStrategyId.BEST_FIT, GroupBy.DAY, List.of(r), null);

        assertTrue(page.blocks().isEmpty(), "out-of-range appointment must not appear");
    }

    @Test
    void resourceLayoutBuildsOneColumnPerAllocatable()
    {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to   = LocalDate.of(2026, 6, 8);
        Appointment a = appointment("2026-06-03T09:00", "2026-06-03T10:00");
        Allocatable room1 = stubAllocatable("A1", "Room 1");
        Allocatable room2 = stubAllocatable("A2", "Room 2");
        Reservation r = stubReservationWithAllocatables("R1", "E", new Appointment[]{a},
                new Allocatable[]{room1});

        CalendarPage page = CalendarLayoutEngine.layout(from, to,
                LayoutStrategyId.BEST_FIT, GroupBy.RESOURCE, List.of(r),
                List.of(room1, room2));
        assertEquals(2, page.columns().size());
        assertEquals("A1", page.columns().get(0).id());
        assertEquals("A2", page.columns().get(1).id());
        // The reservation only uses room1 → block should be in column 0
        assertEquals(1, page.blocks().size());
        assertEquals(0, page.blocks().get(0).columnIndex());
    }

    @Test
    void resourceLayoutAutoDetectsAllocatables()
    {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to   = LocalDate.of(2026, 6, 8);
        Appointment a = appointment("2026-06-03T09:00", "2026-06-03T10:00");
        Allocatable room = stubAllocatable("A1", "Room");
        Reservation r = stubReservationWithAllocatables("R1", "E", new Appointment[]{a},
                new Allocatable[]{room});

        CalendarPage page = CalendarLayoutEngine.layout(from, to,
                LayoutStrategyId.BEST_FIT, GroupBy.RESOURCE, List.of(r), null);
        assertEquals(1, page.columns().size());
        assertEquals("A1", page.columns().get(0).id());
    }

    // ---------- stub helpers ----------

    private static Appointment appointment(String s, String e)
    {
        return new AppointmentImpl(LocalDateTime.parse(s), LocalDateTime.parse(e));
    }

    private static Reservation stubReservation(String id, String name, Appointment... apps)
    {
        return stubReservationWithAllocatables(id, name, apps, new Allocatable[0]);
    }

    private static Reservation stubReservationWithAllocatables(
            String id, String name, Appointment[] apps, Allocatable[] allocs)
    {
        return (Reservation) Proxy.newProxyInstance(
                Reservation.class.getClassLoader(),
                new Class[] { Reservation.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getId":                 return id;
                        case "getName":               return name;
                        case "getAppointments":       return apps;
                        case "getSortedAppointments": return java.util.Arrays.asList(apps);
                        case "getAllocatables":       return allocs;
                        case "getRequestStatus":      return null;
                        case "hasAllocated":          return java.util.Arrays.asList(allocs).contains(args[0]);
                        case "equals":                return proxy == args[0];
                        case "hashCode":              return System.identityHashCode(proxy);
                        case "toString":              return "StubReservation[" + id + "]";
                        default: throw new UnsupportedOperationException("stub does not implement " + method.getName());
                    }
                });
    }

    private static Allocatable stubAllocatable(String id, String name)
    {
        return (Allocatable) Proxy.newProxyInstance(
                Allocatable.class.getClassLoader(),
                new Class[] { Allocatable.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getId":     return id;
                        case "getName":   return name;
                        case "equals":    return proxy == args[0];
                        case "hashCode":  return System.identityHashCode(proxy);
                        case "toString":  return "StubAllocatable[" + id + "]";
                        default: throw new UnsupportedOperationException("stub does not implement " + method.getName());
                    }
                });
    }
}
