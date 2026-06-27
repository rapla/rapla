package org.rapla.storage.impl.server.readmodel;

import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentMapping;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.storage.SyncStorageOperator;
import org.rapla.storage.impl.server.LocalAbstractCachableOperator;
import org.rapla.test.util.FacadeTestSupport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base for the read-model differential suite (PRD 082/086). Builds reservations with the full
 * complexity the real store exhibits — repeating (all types, bounded / {@code >}cap / open-ended),
 * exceptions, per-allocatable restrictions, multi-allocatable bindings — then mutates them, and after
 * every state asserts the THREE read paths return the SAME thing:
 *
 * <ol>
 *   <li><b>Legacy</b> ({@code readModelAuthoritative=false}) — the original {@code appointmentMap} scan,
 *       the ground truth.</li>
 *   <li><b>Per-allocatable index flip</b> ({@code readModelAuthoritative=true}) — {@code IntervalIndex}
 *       per allocatable; serves scoped queries.</li>
 *   <li><b>Window-first global index</b> — {@code reservationsInWindowGlobal}; serves the full-admin
 *       unscoped path.</li>
 * </ol>
 *
 * <p>Every assertion is restricted to reservations CREATED by the test ({@link #created}), so fixture
 * noise (existing reservations / templates in {@code testdefault.xml}) never affects the differential.
 * The suite is a permanent regression guard: any future refactor of the maintenance seam or the index
 * that drops/duplicates an occurrence fails here.
 */
public abstract class DifferentialReadSupport extends FacadeTestSupport
{
    protected SyncStorageOperator sync;
    protected LocalAbstractCachableOperator op;
    protected User owner;
    protected DynamicType eventType;
    /** Ids of reservations the test created — the differential is scoped to these only. */
    protected final Set<String> created = new LinkedHashSet<>();

    protected void initDifferential() throws Exception
    {
        sync = (SyncStorageOperator) operator;
        op = (LocalAbstractCachableOperator) operator;
        op.setReadModelAuthoritative(false);
        DynamicType[] types = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        assertTrue(types.length > 0, "fixture must have a reservation type");
        eventType = types[0];
        User admin = null;
        for (User u : facade.getUsers()) { if (u.isAdmin()) { admin = u; break; } }
        assertNotNull(admin, "fixture must include an admin");
        owner = admin;
    }

    // ---- allocatables -------------------------------------------------------

    /** All non-internal allocatables (resolved fresh). */
    protected List<Allocatable> allAllocatables() throws Exception
    {
        return new ArrayList<>(Arrays.asList(facade.getAllocatables()));
    }

    /** The first {@code n} allocatables of the fixture — stable handles for multi-allocatable scenarios. */
    protected List<Allocatable> someAllocatables(int n) throws Exception
    {
        List<Allocatable> all = allAllocatables();
        assertTrue(all.size() >= n, "fixture needs at least " + n + " allocatables, has " + all.size());
        return new ArrayList<>(all.subList(0, n));
    }

    // ---- building -----------------------------------------------------------

    /** A single (non-repeating) appointment. Mutable — configure repeating before adding to a reservation. */
    protected Appointment appt(LocalDateTime start, LocalDateTime end) throws Exception
    {
        return facade.newAppointmentWithUser(start, end, owner);
    }

    protected Appointment weekly(Appointment a, int count) { return repeat(a, RepeatingType.WEEKLY, count); }
    protected Appointment daily(Appointment a, int count) { return repeat(a, RepeatingType.DAILY, count); }
    protected Appointment monthly(Appointment a, int count) { return repeat(a, RepeatingType.MONTHLY, count); }
    protected Appointment yearly(Appointment a, int count) { return repeat(a, RepeatingType.YEARLY, count); }
    /** Open-ended weekly (number = -1) — routes to the index side-set. */
    protected Appointment weeklyOpenEnded(Appointment a) { return repeat(a, RepeatingType.WEEKLY, -1); }

    protected Appointment repeat(Appointment a, RepeatingType type, int count)
    {
        a.setRepeatingEnabled(true);
        a.getRepeating().setType(type);
        a.getRepeating().setNumber(count);
        return a;
    }

    protected void addException(Appointment a, LocalDateTime exceptionDay)
    {
        assertTrue(a.getRepeating() != null, "addException needs a repeating appointment");
        a.getRepeating().addException(exceptionDay);
    }

    /** Store a fresh reservation with the given appointments bound to the given allocatables (unrestricted). */
    protected Reservation store(String name, List<Allocatable> allocs, Appointment... appts) throws Exception
    {
        Classification c = eventType.newClassification();
        if (c.getType().getAttribute("name") != null) c.setValue("name", name);
        Reservation r = facade.newReservation(c, owner);
        for (Appointment a : appts) r.addAppointment(a);
        for (Allocatable alloc : allocs) r.addAllocatable(alloc);
        facade.store(r);
        created.add(r.getId());
        return r;
    }

    /** Edit handle for mutation; pass the result of a mutator lambda to {@link #storeEdit}. */
    protected Reservation edit(Reservation r) throws Exception
    {
        return facade.edit(r);
    }

    protected void storeEdit(Reservation editable) throws Exception
    {
        facade.store(editable);
    }

    protected void removeReservation(Reservation r) throws Exception
    {
        facade.remove(r);
        // keep its id in `created` so the differential still asserts it is absent from ALL paths.
    }

    /** Restrict {@code a} (an appointment of editable reservation {@code r}) to exactly {@code allocs}. */
    protected void restrictAppointment(Reservation r, Appointment a, List<Allocatable> allocs)
    {
        r.setRestrictionForAppointment(a, allocs.toArray(new Allocatable[0]));
    }

    /** Clear the restriction on {@code a} (bind it to all the reservation's allocatables again). */
    protected void unrestrictAppointment(Reservation r, Appointment a)
    {
        r.setRestrictionForAppointment(a, new Allocatable[0]);
    }

    /** Find the editable copy's appointment matching {@code original} by id (after {@link #edit}). */
    protected Appointment appointmentById(Reservation editable, String appointmentId)
    {
        for (Appointment a : editable.getAppointments()) if (appointmentId.equals(a.getId())) return a;
        return null;
    }

    // ---- the differential ----------------------------------------------------

    /**
     * The core invariant for a SCOPED query: the per-allocatable index (flip on) returns exactly the same
     * (allocatable → appointment-ids) mapping as the legacy {@code appointmentMap} (flip off), restricted
     * to the test's own reservations. Catches dropped / duplicated / mis-keyed occurrences.
     */
    protected void assertScopedConsistent(String label, LocalDateTime from, LocalDateTime to, List<Allocatable> scope) throws Exception
    {
        op.setReadModelAuthoritative(false);
        Map<String, TreeSet<String>> legacy = mineByAlloc(sync.queryAppointmentsSync(null, scope, null, from, to, null, null, false), scope);
        op.setReadModelAuthoritative(true);
        Map<String, TreeSet<String>> flip = mineByAlloc(sync.queryAppointmentsSync(null, scope, null, from, to, null, null, false), scope);
        op.setReadModelAuthoritative(false);
        assertEquals(legacy, flip,
                () -> "[" + label + "] per-allocatable index ≠ legacy appointmentMap; window=[" + from + "," + to + "] scope=" + allocIds(scope));
    }

    /**
     * The window-first invariant: the global index ({@code reservationsInWindowGlobal}) returns the same
     * test-reservations as the legacy UNSCOPED read over all allocatables. (Both operator-level, no
     * canRead; restricted to the test's reservations so fixture templates/noise are irrelevant.)
     */
    protected void assertGlobalConsistent(String label, LocalDateTime from, LocalDateTime to) throws Exception
    {
        op.setReadModelAuthoritative(false);
        TreeSet<String> legacyAll = mineReservations(sync.queryAppointmentsSync(null, allAllocatables(), null, from, to, null, null, false).getAllReservations());
        TreeSet<String> global = mineReservations(op.reservationsInWindowGlobal(from, to));
        assertEquals(legacyAll, global,
                () -> "[" + label + "] window-first global ≠ legacy unscoped-all; window=[" + from + "," + to + "]");
    }

    /** Both invariants at once for the common case (scope = all created allocatables passed in). */
    protected void assertAllConsistent(String label, LocalDateTime from, LocalDateTime to, List<Allocatable> scope) throws Exception
    {
        assertScopedConsistent(label, from, to, scope);
        // each allocatable individually, too — a per-key bug hides in the union otherwise
        for (Allocatable a : scope) assertScopedConsistent(label + " [single " + a.getId() + "]", from, to, List.of(a));
        assertGlobalConsistent(label, from, to);
    }

    private Map<String, TreeSet<String>> mineByAlloc(AppointmentMapping mapping, List<Allocatable> scope)
    {
        Map<String, TreeSet<String>> out = new TreeMap<>();
        for (Allocatable alloc : scope)
        {
            Collection<Appointment> appts = mapping.getAppointments(alloc);
            if (appts == null) continue;
            TreeSet<String> ids = new TreeSet<>();
            for (Appointment a : appts)
            {
                Reservation r = a.getReservation();
                if (r != null && created.contains(r.getId())) ids.add(a.getId());
            }
            if (!ids.isEmpty()) out.put(alloc.getId(), ids);
        }
        return out;
    }

    private TreeSet<String> mineReservations(Collection<Reservation> reservations)
    {
        TreeSet<String> ids = new TreeSet<>();
        for (Reservation r : reservations) if (r != null && created.contains(r.getId())) ids.add(r.getId());
        return ids;
    }

    private static TreeSet<String> allocIds(List<Allocatable> allocs)
    {
        TreeSet<String> ids = new TreeSet<>();
        for (Allocatable a : allocs) ids.add(a.getId());
        return ids;
    }
}
