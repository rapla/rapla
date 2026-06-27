package org.rapla.storage.impl.server.readmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DENSE-KEY differential. All reservations are bound to the SAME three allocatables, so each
 * per-allocatable index key accumulates appointments from MANY reservations (single + several
 * weeklies on different start days + dailies + open-ended + {@code >}cap). A per-key bug — dropping
 * one recurring occurrence amongst the dense set — surfaces here even when the union still looks
 * plausible. Each state asserts legacy == per-allocatable-flip == window-first global.
 */
class DenseKeyDifferentialTest extends DifferentialReadSupport
{
    @BeforeEach
    void setUp() throws Exception { initDifferential(); }

    /** Window covering the calendar week starting at {@code day}. */
    private LocalDateTime weekFrom(LocalDateTime day) { return day.toLocalDate().atStartOfDay(); }
    private LocalDateTime weekTo(LocalDateTime day)   { return weekFrom(day).plusWeeks(1); }

    /** Populate the dense fixture on {@code allocs}: a year of mixed reservations, all on the same keys. */
    private void populateDense(List<Allocatable> allocs) throws Exception
    {
        // a spread of single appointments scattered over a year
        store("single-jan", allocs, appt(LocalDateTime.parse("2025-01-08T09:00:00"), LocalDateTime.parse("2025-01-08T10:00:00")));
        store("single-feb", allocs, appt(LocalDateTime.parse("2025-02-12T11:00:00"), LocalDateTime.parse("2025-02-12T12:00:00")));
        store("single-mar", allocs, appt(LocalDateTime.parse("2025-03-19T13:00:00"), LocalDateTime.parse("2025-03-19T14:00:00")));
        store("single-jun", allocs, appt(LocalDateTime.parse("2025-06-04T15:00:00"), LocalDateTime.parse("2025-06-04T16:00:00")));
        store("single-sep", allocs, appt(LocalDateTime.parse("2025-09-10T08:00:00"), LocalDateTime.parse("2025-09-10T09:30:00")));
        store("single-nov", allocs, appt(LocalDateTime.parse("2025-11-26T17:00:00"), LocalDateTime.parse("2025-11-26T18:00:00")));

        // several weekly repeatings starting on different weekdays
        store("weekly-mon", allocs, weekly(appt(LocalDateTime.parse("2025-04-07T14:00:00"), LocalDateTime.parse("2025-04-07T16:00:00")), 12));
        store("weekly-tue", allocs, weekly(appt(LocalDateTime.parse("2025-04-08T10:00:00"), LocalDateTime.parse("2025-04-08T11:00:00")), 20));
        store("weekly-wed", allocs, weekly(appt(LocalDateTime.parse("2025-04-09T18:00:00"), LocalDateTime.parse("2025-04-09T19:30:00")), 8));
        store("weekly-fri", allocs, weekly(appt(LocalDateTime.parse("2025-04-11T12:00:00"), LocalDateTime.parse("2025-04-11T13:00:00")), 15));

        // a couple of daily repeatings
        store("daily-may", allocs, daily(appt(LocalDateTime.parse("2025-05-05T07:00:00"), LocalDateTime.parse("2025-05-05T07:30:00")), 10));
        store("daily-jul", allocs, daily(appt(LocalDateTime.parse("2025-07-14T20:00:00"), LocalDateTime.parse("2025-07-14T21:00:00")), 14));

        // one open-ended weekly (number = -1 -> index side-set)
        store("weekly-open", allocs, weeklyOpenEnded(appt(LocalDateTime.parse("2025-04-10T09:00:00"), LocalDateTime.parse("2025-04-10T10:00:00"))));

        // one >52 weekly (over MATERIALIZE_CAP -> envelope row)
        store("weekly-overcap", allocs, weekly(appt(LocalDateTime.parse("2025-04-12T16:00:00"), LocalDateTime.parse("2025-04-12T17:00:00")), 60));

        // a monthly and a yearly to push key density further
        store("monthly", allocs, monthly(appt(LocalDateTime.parse("2025-04-15T10:00:00"), LocalDateTime.parse("2025-04-15T11:00:00")), 12));
        store("yearly", allocs, yearly(appt(LocalDateTime.parse("2025-04-20T10:00:00"), LocalDateTime.parse("2025-04-20T11:00:00")), 3));

        // a couple more weeklies overlapping the same busy weeks to thicken the dense key
        store("weekly-mon-b", allocs, weekly(appt(LocalDateTime.parse("2025-04-07T08:00:00"), LocalDateTime.parse("2025-04-07T09:00:00")), 18));
        store("weekly-thu", allocs, weekly(appt(LocalDateTime.parse("2025-04-10T15:00:00"), LocalDateTime.parse("2025-04-10T16:00:00")), 25));
    }

    @Test
    void denseWeek_manyOverlap() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        populateDense(allocs);
        // the week of 2025-04-07 has every weekly's first occurrence plus the overcap + monthly base
        LocalDateTime busy = LocalDateTime.parse("2025-04-07T00:00:00");
        assertAllConsistent("dense busy week (2025-04-07)", weekFrom(busy), weekTo(busy), allocs);
    }

    @Test
    void denseWeek_secondBusyWeek() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        populateDense(allocs);
        // week of 2025-04-14 — second occurrences of the weeklies, plus monthly-15, all overlapping
        LocalDateTime week2 = LocalDateTime.parse("2025-04-14T00:00:00");
        assertAllConsistent("dense second week (2025-04-14)", weekFrom(week2), weekTo(week2), allocs);
    }

    @Test
    void lateWeek_onlyLongRunningSurvive() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        populateDense(allocs);
        // week of 2025-08-04 — past the short weeklies; only weekly-tue(20), weekly-fri(15),
        // weekly-thu(25), the overcap(60) and open-ended remain. A dropped occurrence amid the
        // dense key shows here.
        LocalDateTime late = LocalDateTime.parse("2025-08-04T00:00:00");
        assertAllConsistent("late week (2025-08-04, sparse survivors)", weekFrom(late), weekTo(late), allocs);
    }

    @Test
    void overCapWeek_pastMaterializeCap() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        populateDense(allocs);
        // ~55 weeks after 2025-04-12 -> only weekly-overcap(60) and weekly-open survive here
        LocalDateTime past = LocalDateTime.parse("2025-04-12T16:00:00").plusWeeks(55);
        assertAllConsistent("week 55 past cap", weekFrom(past), weekTo(past), allocs);
    }

    @Test
    void singleReservationWeek_amidDense() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        populateDense(allocs);
        // week containing only the lone single-feb appointment — no recurring occurrence here
        LocalDateTime feb = LocalDateTime.parse("2025-02-12T00:00:00");
        assertAllConsistent("lone single-feb week", weekFrom(feb), weekTo(feb), allocs);
    }

    @Test
    void emptyWeek_noOccurrence() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        populateDense(allocs);
        // week of 2025-12-22 — after every bounded series and singles; only open-ended + overcap-window
        // already past. Expect at most the open-ended weekly.
        LocalDateTime late = LocalDateTime.parse("2025-12-22T00:00:00");
        assertAllConsistent("december tail week", weekFrom(late), weekTo(late), allocs);
    }

    @Test
    void allTime_denseUnbounded() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        populateDense(allocs);
        assertAllConsistent("all-time dense", null, null, allocs);
    }

    @Test
    void dailySeriesWeek_amidDense() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);
        populateDense(allocs);
        // week of 2025-05-05 — the daily-may series fires every day; weekly-tue/fri/thu also overlap
        LocalDateTime mayWeek = LocalDateTime.parse("2025-05-05T00:00:00");
        assertAllConsistent("daily-series week (2025-05-05)", weekFrom(mayWeek), weekTo(mayWeek), allocs);
    }
}
