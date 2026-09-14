package org.rapla.storage.impl.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.Conflict;
import org.rapla.test.util.FacadeTestSupport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct sweep-line tests for {@link ConflictFinder#sweepLine}. Builds
 * real entities via {@link FacadeTestSupport} (because
 * {@code ConflictImpl(...)} casts the allocatable to {@code AllocatableImpl}
 * to grab its resolver — Proxy stubs don't work), then invokes the
 * static {@code sweepLine(...)} method with hand-controlled
 * {@link AppointmentBlock} inputs.
 * <p>
 * Complementary to {@code ConflictFinderViaFacadeTest} which drives the
 * full facade path. This class probes the algorithm:
 * <ul>
 *   <li>self-pair short-circuit</li>
 *   <li>duplicate-pair de-duplication</li>
 *   <li>"different reservations, but neither actually allocates this allocatable" skip</li>
 *   <li>three-way overlap → 3 conflict entries</li>
 *   <li>touching-edge non-overlap</li>
 * </ul>
 * Each test constructs a small set of stored reservations, expands their
 * appointments into blocks for one allocatable, and asserts on the
 * sweep-line output directly.
 */
class ConflictFinderSweepLineTest extends FacadeTestSupport
{
    private User actingUser;
    private DynamicType eventType;
    private Allocatable sharedRoom;
    private Allocatable otherRoom;

    @BeforeEach
    void resolveFixture() throws Exception
    {
        for (User u : facade.getUsers()) if (u.isAdmin()) { actingUser = u; break; }
        assertNotNull(actingUser, "fixture must include an admin");

        DynamicType[] reservationTypes =
                facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        assertTrue(reservationTypes.length > 0);
        eventType = reservationTypes[0];

        DynamicType[] resourceTypes =
                facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
        // Create two test allocatables so we can vary "is this resource actually allocated by either reservation?"
        sharedRoom = makeAllocatable(resourceTypes[0], "SweepRoom1");
        otherRoom  = makeAllocatable(resourceTypes[0], "SweepRoom2");
    }

    // ---------- empty / trivial ----------

    @Test
    void emptyIntervalsProduceNoConflicts()
    {
        Map<ReferenceInfo<Conflict>, Conflict> out = ConflictFinder.sweepLine(
                sharedRoom, today(), List.of());
        assertTrue(out.isEmpty());
    }

    @Test
    void singleIntervalProducesNoConflicts() throws Exception
    {
        Reservation a = makeAndStore("solo",
                LocalDateTime.parse("2030-07-01T09:00"),
                LocalDateTime.parse("2030-07-01T10:00"),
                sharedRoom);
        List<AppointmentBlock> blocks = expandBlocks(a, "2030-07-01T00:00", "2030-07-02T00:00");

        Map<ReferenceInfo<Conflict>, Conflict> out = ConflictFinder.sweepLine(
                sharedRoom, today(), blocks);
        assertTrue(out.isEmpty(), "a lone block cannot conflict with itself");
    }

    @Test
    void blocksFromSameAppointmentDoNotSelfConflict() throws Exception
    {
        // A daily-repeating appointment expanded over a window produces
        // multiple blocks pointing at the SAME appointment. The sweep
        // must not pair them against each other.
        Reservation r = makeAndStore("daily-repeating",
                LocalDateTime.parse("2030-08-01T09:00"),
                LocalDateTime.parse("2030-08-01T10:00"),
                sharedRoom);
        // Add a recurrence: 3 daily
        Appointment app = r.getAppointments()[0];
        Appointment mutableApp;
        {
            Reservation mutable = facade.edit(r);
            mutableApp = mutable.getAppointments()[0];
            mutableApp.setRepeatingEnabled(true);
            mutableApp.getRepeating().setType(org.rapla.entities.domain.RepeatingType.DAILY);
            mutableApp.getRepeating().setNumber(3);
            facade.store(mutable);
            r = mutable;
        }
        List<AppointmentBlock> blocks = expandBlocks(r, "2030-08-01T00:00", "2030-08-05T00:00");
        assertEquals(3, blocks.size(), "3 daily occurrences expected");

        Map<ReferenceInfo<Conflict>, Conflict> out = ConflictFinder.sweepLine(
                sharedRoom, today(), blocks);
        assertTrue(out.isEmpty(), "blocks from the same appointment must not self-pair");
    }

    // ---------- positive: two reservations overlapping ----------

    @Test
    void overlappingDifferentReservationsConflict() throws Exception
    {
        Reservation a = makeAndStore("OVERLAP-A",
                LocalDateTime.parse("2030-09-01T09:00"),
                LocalDateTime.parse("2030-09-01T11:00"),
                sharedRoom);
        Reservation b = makeAndStore("OVERLAP-B",
                LocalDateTime.parse("2030-09-01T10:00"),
                LocalDateTime.parse("2030-09-01T12:00"),
                sharedRoom);
        List<AppointmentBlock> blocks = new ArrayList<>();
        blocks.addAll(expandBlocks(a, "2030-09-01T00:00", "2030-09-02T00:00"));
        blocks.addAll(expandBlocks(b, "2030-09-01T00:00", "2030-09-02T00:00"));

        Map<ReferenceInfo<Conflict>, Conflict> out = ConflictFinder.sweepLine(
                sharedRoom, today(), blocks);
        assertEquals(1, out.size(), "one pair → one conflict");
    }

    @Test
    void touchingEdgeIsNotConflict() throws Exception
    {
        // A ends at 10:00, B starts at 10:00 — no overlap.
        Reservation a = makeAndStore("EDGE-A",
                LocalDateTime.parse("2030-09-02T09:00"),
                LocalDateTime.parse("2030-09-02T10:00"),
                sharedRoom);
        Reservation b = makeAndStore("EDGE-B",
                LocalDateTime.parse("2030-09-02T10:00"),
                LocalDateTime.parse("2030-09-02T11:00"),
                sharedRoom);
        List<AppointmentBlock> blocks = new ArrayList<>();
        blocks.addAll(expandBlocks(a, "2030-09-02T00:00", "2030-09-03T00:00"));
        blocks.addAll(expandBlocks(b, "2030-09-02T00:00", "2030-09-03T00:00"));

        Map<ReferenceInfo<Conflict>, Conflict> out = ConflictFinder.sweepLine(
                sharedRoom, today(), blocks);
        assertTrue(out.isEmpty(), "touching edges (end == start) must not conflict");
    }

    // ---------- de-duplication ----------

    @Test
    void duplicatePairIsReportedOnceAcrossMultipleOccurrences() throws Exception
    {
        // Two reservations both daily-repeating × 5, overlapping every day.
        // Naive pairing would emit one conflict per (block-a, block-b) pair = 25.
        // Sweep-line + foundConflictIds dedup must collapse to 1.
        Reservation a = makeAndStoreRepeating("DUP-A", "2030-10-01T09:00", "2030-10-01T10:30", 5, sharedRoom);
        Reservation b = makeAndStoreRepeating("DUP-B", "2030-10-01T10:00", "2030-10-01T11:00", 5, sharedRoom);

        List<AppointmentBlock> blocks = new ArrayList<>();
        blocks.addAll(expandBlocks(a, "2030-10-01T00:00", "2030-10-10T00:00"));
        blocks.addAll(expandBlocks(b, "2030-10-01T00:00", "2030-10-10T00:00"));
        assertEquals(10, blocks.size(), "5 + 5 daily occurrences");

        Map<ReferenceInfo<Conflict>, Conflict> out = ConflictFinder.sweepLine(
                sharedRoom, today(), blocks);
        assertEquals(1, out.size(), "same (allocatable, app1, app2) triple → exactly 1 conflict");
    }

    // ---------- "different reservations but neither uses this allocatable" ----------

    @Test
    void overlappingReservationsOnDifferentAllocatablesDoNotConflictOnUnrelatedAllocatable()
            throws Exception
    {
        // Even if A and B overlap in time, if NEITHER allocates `otherRoom`,
        // sweepLine called with otherRoom must return empty.
        Reservation a = makeAndStore("CROSS-A",
                LocalDateTime.parse("2030-11-01T09:00"),
                LocalDateTime.parse("2030-11-01T11:00"),
                sharedRoom);
        Reservation b = makeAndStore("CROSS-B",
                LocalDateTime.parse("2030-11-01T10:00"),
                LocalDateTime.parse("2030-11-01T12:00"),
                sharedRoom);
        List<AppointmentBlock> blocks = new ArrayList<>();
        blocks.addAll(expandBlocks(a, "2030-11-01T00:00", "2030-11-02T00:00"));
        blocks.addAll(expandBlocks(b, "2030-11-01T00:00", "2030-11-02T00:00"));

        // Ask the sweep about a DIFFERENT allocatable — should report nothing
        // because neither reservation actually allocates otherRoom.
        Map<ReferenceInfo<Conflict>, Conflict> out = ConflictFinder.sweepLine(
                otherRoom, today(), blocks);
        assertTrue(out.isEmpty(),
                "sweep-line must skip pairs where neither reservation allocates the queried allocatable");
    }

    // ---------- three-way overlap ----------

    @Test
    void threeWayOverlapProducesThreePairs() throws Exception
    {
        // Three reservations all overlapping at 10:00–11:00 on sharedRoom.
        // Pairs: (A,B), (A,C), (B,C) = 3 conflicts.
        Reservation a = makeAndStore("TRI-A",
                LocalDateTime.parse("2030-12-01T09:00"),
                LocalDateTime.parse("2030-12-01T11:00"),
                sharedRoom);
        Reservation b = makeAndStore("TRI-B",
                LocalDateTime.parse("2030-12-01T10:00"),
                LocalDateTime.parse("2030-12-01T12:00"),
                sharedRoom);
        Reservation c = makeAndStore("TRI-C",
                LocalDateTime.parse("2030-12-01T10:30"),
                LocalDateTime.parse("2030-12-01T11:30"),
                sharedRoom);

        List<AppointmentBlock> blocks = new ArrayList<>();
        blocks.addAll(expandBlocks(a, "2030-12-01T00:00", "2030-12-02T00:00"));
        blocks.addAll(expandBlocks(b, "2030-12-01T00:00", "2030-12-02T00:00"));
        blocks.addAll(expandBlocks(c, "2030-12-01T00:00", "2030-12-02T00:00"));

        Map<ReferenceInfo<Conflict>, Conflict> out = ConflictFinder.sweepLine(
                sharedRoom, today(), blocks);
        assertEquals(3, out.size(), "three-way overlap should produce 3 pairwise conflicts");
    }

    // ---------- helpers ----------

    private LocalDateTime today()
    {
        return LocalDateTime.parse("2026-01-01T00:00");
    }

    private Allocatable makeAllocatable(DynamicType type, String name) throws Exception
    {
        Classification c = type.newClassification();
        if (c.getType().getAttribute("name") != null) c.setValue("name", name);
        Allocatable a = facade.newAllocatable(c, actingUser);
        facade.store(a);
        return a;
    }

    private Reservation makeAndStore(String name, LocalDateTime start, LocalDateTime end,
                                     Allocatable... allocatables) throws Exception
    {
        Classification c = eventType.newClassification();
        if (c.getType().getAttribute("name") != null) c.setValue("name", name);
        Reservation r = facade.newReservation(c, actingUser);
        Appointment a = facade.newAppointmentWithUser(start, end, actingUser);
        r.addAppointment(a);
        for (Allocatable al : allocatables) r.addAllocatable(al);
        facade.store(r);
        return r;
    }

    private Reservation makeAndStoreRepeating(String name, String startIso, String endIso, int number,
                                              Allocatable... allocatables) throws Exception
    {
        Classification c = eventType.newClassification();
        if (c.getType().getAttribute("name") != null) c.setValue("name", name);
        Reservation r = facade.newReservation(c, actingUser);
        Appointment a = facade.newAppointmentWithUser(
                LocalDateTime.parse(startIso), LocalDateTime.parse(endIso), actingUser);
        a.setRepeatingEnabled(true);
        a.getRepeating().setType(org.rapla.entities.domain.RepeatingType.DAILY);
        a.getRepeating().setNumber(number);
        r.addAppointment(a);
        for (Allocatable al : allocatables) r.addAllocatable(al);
        facade.store(r);
        return r;
    }

    /** Expand all appointments of one reservation into AppointmentBlocks in the window. */
    private List<AppointmentBlock> expandBlocks(Reservation r, String windowStart, String windowEnd)
    {
        List<AppointmentBlock> blocks = new ArrayList<>();
        for (Appointment a : r.getAppointments())
        {
            a.createBlocks(LocalDateTime.parse(windowStart), LocalDateTime.parse(windowEnd), blocks);
        }
        return blocks;
    }
}
