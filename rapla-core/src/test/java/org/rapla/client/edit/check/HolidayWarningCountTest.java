package org.rapla.client.edit.check;

import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tier-1 contract pin for {@link HolidayWarningModel#countAllPeriodConflicts}
 * — the aggregation behind {@code ConflictPeriodReservationButton.updateButton}.
 */
class HolidayWarningCountTest
{
    private static Appointment a(String id)
    {
        return (Appointment) Proxy.newProxyInstance(
                Appointment.class.getClassLoader(),
                new Class[] { Appointment.class },
                (proxy, m, args) -> {
                    if (m.getName().equals("toString")) return "Appointment[" + id + "]";
                    if (m.getName().equals("hashCode")) return id.hashCode();
                    if (m.getName().equals("equals")) return proxy == args[0];
                    throw new UnsupportedOperationException(m.getName());
                });
    }

    private static Period p(String name)
    {
        return (Period) Proxy.newProxyInstance(
                Period.class.getClassLoader(),
                new Class[] { Period.class },
                (proxy, m, args) -> {
                    if (m.getName().equals("toString")) return "Period[" + name + "]";
                    if (m.getName().equals("hashCode")) return name.hashCode();
                    if (m.getName().equals("equals")) return proxy == args[0];
                    throw new UnsupportedOperationException(m.getName());
                });
    }

    @Test
    void emptyMapZeroCount()
    {
        assertEquals(0, HolidayWarningModel.countAllPeriodConflicts(Collections.emptyMap()));
    }

    @Test
    void nullMapZeroCount()
    {
        assertEquals(0, HolidayWarningModel.countAllPeriodConflicts(null));
    }

    @Test
    void singleAppointmentTwoPeriods()
    {
        Map<Appointment, Set<Period>> map = new LinkedHashMap<>();
        Set<Period> periods = new HashSet<>();
        periods.add(p("easter"));
        periods.add(p("xmas"));
        map.put(a("a1"), periods);
        assertEquals(2, HolidayWarningModel.countAllPeriodConflicts(map));
    }

    @Test
    void multipleAppointmentsSumAcross()
    {
        Map<Appointment, Set<Period>> map = new LinkedHashMap<>();
        map.put(a("a1"), java.util.Set.of(p("p1"), p("p2"), p("p3")));
        map.put(a("a2"), java.util.Set.of(p("p4")));
        map.put(a("a3"), java.util.Set.of(p("p5"), p("p6")));
        assertEquals(6, HolidayWarningModel.countAllPeriodConflicts(map));
    }

    @Test
    void emptySetEntriesContributeZero()
    {
        Map<Appointment, Set<Period>> map = new LinkedHashMap<>();
        map.put(a("a1"), java.util.Set.of());
        map.put(a("a2"), java.util.Set.of(p("p1")));
        assertEquals(1, HolidayWarningModel.countAllPeriodConflicts(map));
    }

    @Test
    void nullSetEntriesIgnored()
    {
        Map<Appointment, Set<Period>> map = new LinkedHashMap<>();
        map.put(a("a1"), null);
        map.put(a("a2"), java.util.Set.of(p("p1")));
        assertEquals(1, HolidayWarningModel.countAllPeriodConflicts(map));
    }
}
