package org.rapla.storage.impl.server.readmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mutation-churn differential: build a reservation, then walk it through a sequence of edits
 * (add appointment, remove appointment, shrink a weekly recurrence, move an appointment's start,
 * remove the whole reservation, re-create it), asserting after EACH state that the three read
 * paths agree (legacy appointmentMap == per-allocatable IntervalIndex flip == window-first global).
 * The point is to catch index drift that only shows up across copy-on-write mutation, not on a
 * freshly stored reservation.
 */
class MutationChurnDifferentialTest extends DifferentialReadSupport
{
    private static final LocalDateTime BASE_START = LocalDateTime.parse("2025-04-02T14:00:00");
    private static final LocalDateTime BASE_END   = LocalDateTime.parse("2025-04-02T16:00:00");

    @BeforeEach
    void setUp() throws Exception { initDifferential(); }

    /** A one-week window covering the Nth weekly occurrence (week of BASE + n weeks). */
    private LocalDateTime occFrom(int n) { return BASE_START.plusWeeks(n).toLocalDate().atStartOfDay(); }
    private LocalDateTime occTo(int n)   { return occFrom(n).plusWeeks(1); }

    @Test
    void addSecondAppointment_thenAssert() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Reservation stored = store("churn-add-appt", allocs, appt(BASE_START, BASE_END));
        assertAllConsistent("after initial store", occFrom(0), occTo(0), allocs);

        Reservation e = edit(stored);
        e.addAppointment(appt(BASE_START.plusWeeks(2), BASE_END.plusWeeks(2)));
        storeEdit(e);

        assertAllConsistent("after add 2nd appt [week 0]", occFrom(0), occTo(0), allocs);
        assertAllConsistent("after add 2nd appt [week 2]", occFrom(2), occTo(2), allocs);
        assertAllConsistent("after add 2nd appt [empty week 1]", occFrom(1), occTo(1), allocs);
        assertAllConsistent("after add 2nd appt [all-time]", null, null, allocs);
    }

    @Test
    void removeOneOfTwoAppointments_thenAssert() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment first = appt(BASE_START, BASE_END);
        Appointment second = appt(BASE_START.plusWeeks(2), BASE_END.plusWeeks(2));
        Reservation stored = store("churn-remove-appt", allocs, first, second);
        assertAllConsistent("after store both", occFrom(0), occTo(0), allocs);
        assertAllConsistent("after store both [week 2]", occFrom(2), occTo(2), allocs);

        Reservation e = edit(stored);
        Appointment editableSecond = appointmentById(e, second.getId());
        e.removeAppointment(editableSecond);
        storeEdit(e);

        assertAllConsistent("after remove 2nd [week 0 still present]", occFrom(0), occTo(0), allocs);
        assertAllConsistent("after remove 2nd [week 2 now empty]", occFrom(2), occTo(2), allocs);
        assertAllConsistent("after remove 2nd [all-time]", null, null, allocs);
    }

    @Test
    void shrinkWeeklyCount_thenAssertWindowsBeyondNewLast() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a = weekly(appt(BASE_START, BASE_END), 10);
        Reservation stored = store("churn-shrink-weekly", allocs, a);
        assertAllConsistent("count=10 [week 5 present]", occFrom(5), occTo(5), allocs);
        assertAllConsistent("count=10 [week 9 present]", occFrom(9), occTo(9), allocs);

        Reservation e = edit(stored);
        Appointment editableA = appointmentById(e, a.getId());
        editableA.getRepeating().setNumber(3);
        storeEdit(e);

        assertAllConsistent("count=3 [week 0 still present]", occFrom(0), occTo(0), allocs);
        assertAllConsistent("count=3 [week 2 last present]", occFrom(2), occTo(2), allocs);
        assertAllConsistent("count=3 [week 5 now beyond last]", occFrom(5), occTo(5), allocs);
        assertAllConsistent("count=3 [week 9 now beyond last]", occFrom(9), occTo(9), allocs);
        assertAllConsistent("count=3 [all-time]", null, null, allocs);
    }

    @Test
    void changeAppointmentStart_thenAssertOldAndNewWindows() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a = appt(BASE_START, BASE_END);
        Reservation stored = store("churn-move-appt", allocs, a);
        assertAllConsistent("before move [week 0 present]", occFrom(0), occTo(0), allocs);
        assertAllConsistent("before move [week 4 empty]", occFrom(4), occTo(4), allocs);

        Reservation e = edit(stored);
        Appointment editableA = appointmentById(e, a.getId());
        editableA.moveTo(BASE_START.plusWeeks(4));
        storeEdit(e);

        assertAllConsistent("after move [old week 0 now empty]", occFrom(0), occTo(0), allocs);
        assertAllConsistent("after move [new week 4 present]", occFrom(4), occTo(4), allocs);
        assertAllConsistent("after move [all-time]", null, null, allocs);
    }

    @Test
    void removeReservation_thenAbsentEverywhere() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Appointment a = weekly(appt(BASE_START, BASE_END), 5);
        Reservation stored = store("churn-remove-reservation", allocs, a);
        assertAllConsistent("before remove [week 0]", occFrom(0), occTo(0), allocs);
        assertAllConsistent("before remove [all-time]", null, null, allocs);

        removeReservation(stored);

        assertAllConsistent("after remove [week 0 absent]", occFrom(0), occTo(0), allocs);
        assertAllConsistent("after remove [week 3 absent]", occFrom(3), occTo(3), allocs);
        assertAllConsistent("after remove [all-time absent]", null, null, allocs);
    }

    @Test
    void recreateAfterRemove_thenPresentAgain() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Reservation stored = store("churn-recreate", allocs, appt(BASE_START, BASE_END));
        assertAllConsistent("first store [present]", occFrom(0), occTo(0), allocs);

        removeReservation(stored);
        assertAllConsistent("after remove [absent]", occFrom(0), occTo(0), allocs);

        store("churn-recreate-2", allocs, appt(BASE_START, BASE_END));
        assertAllConsistent("after recreate [present again]", occFrom(0), occTo(0), allocs);
        assertAllConsistent("after recreate [all-time]", null, null, allocs);
    }

    @Test
    void addRemoveAppointmentLoop_threeTimes() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        Reservation stored = store("churn-loop", allocs, appt(BASE_START, BASE_END));
        assertAllConsistent("loop base store", occFrom(0), occTo(0), allocs);

        for (int i = 1; i <= 3; i++)
        {
            Reservation eAdd = edit(stored);
            Appointment extra = appt(BASE_START.plusWeeks(i), BASE_END.plusWeeks(i));
            eAdd.addAppointment(extra);
            storeEdit(eAdd);
            stored = eAdd;
            assertAllConsistent("loop " + i + " [added, week " + i + "]", occFrom(i), occTo(i), allocs);
            assertAllConsistent("loop " + i + " [added, week 0 anchor]", occFrom(0), occTo(0), allocs);

            Reservation eRemove = edit(stored);
            Appointment editableExtra = appointmentById(eRemove, extra.getId());
            eRemove.removeAppointment(editableExtra);
            storeEdit(eRemove);
            stored = eRemove;
            assertAllConsistent("loop " + i + " [removed, week " + i + " empty]", occFrom(i), occTo(i), allocs);
            assertAllConsistent("loop " + i + " [removed, week 0 anchor]", occFrom(0), occTo(0), allocs);
        }
        assertAllConsistent("loop end [all-time]", null, null, allocs);
    }
}
