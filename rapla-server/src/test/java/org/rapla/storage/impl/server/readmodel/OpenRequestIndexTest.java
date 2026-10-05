package org.rapla.storage.impl.server.readmodel;

import org.junit.jupiter.api.Test;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.RequestStatus;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.test.util.FacadeTestSupport;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** PRD 128 D4 — the operator's index of open resource requests (requested resource id → reservation ids). */
class OpenRequestIndexTest extends FacadeTestSupport
{
    private Allocatable room(String name) throws Exception
    {
        Classification c = facade.getDynamicType("room").newClassification();
        c.setValue("name", name);
        Allocatable a = facade.newAllocatable(c, operator.getUser("homer"));
        facade.store(a);
        return operator.tryResolve(a.getReference());
    }

    private Reservation event(Allocatable... rooms) throws Exception
    {
        Reservation r = facade.newReservation(facade.getDynamicTypes("reservation")[0].newClassification(), operator.getUser("monty"));
        r.addAppointment(facade.newAppointmentWithUser(LocalDateTime.of(2027, 6, 1, 10, 0), LocalDateTime.of(2027, 6, 1, 11, 0), operator.getUser("monty")));
        for (Allocatable a : rooms) r.addAllocatable(a);
        return r;
    }

    private Map<String, Set<String>> index()
    {
        Map<String, Set<String>> out = new HashMap<>();
        for (String resourceId : operator.openRequestResourceIds())
        {
            out.put(resourceId, operator.openRequestReservationIds(resourceId));
        }
        return out;
    }

    private Map<String, Set<String>> scan() throws Exception
    {
        Map<String, Set<String>> out = new HashMap<>();
        for (Reservation r : operator.getReservations())
        {
            for (Allocatable a : r.getRequestedAllocatables())
            {
                out.computeIfAbsent(a.getId(), k -> new HashSet<>()).add(r.getId());
            }
        }
        return out;
    }

    @Test
    void followsStoreChangeAndRemoveOfRequests() throws Exception
    {
        Allocatable a = room("PRD128-A");
        Allocatable b = room("PRD128-B");
        Reservation requested = event(a, b);
        requested.setRequestStatus(a, RequestStatus.REQUESTED);
        Reservation plain = event(a);
        facade.storeObjects(new Entity[] { requested, plain });
        assertEquals(Set.of(requested.getId()), operator.openRequestReservationIds(a.getId()));
        assertEquals(Set.of(), operator.openRequestReservationIds(b.getId()));
        assertEquals(scan(), index());

        Reservation edit = facade.edit(requested);
        edit.setRequestStatus(a, null);
        edit.setRequestStatus(b, RequestStatus.REQUESTED);
        facade.store(edit);
        assertEquals(Set.of(), operator.openRequestReservationIds(a.getId()));
        assertEquals(Set.of(requested.getId()), operator.openRequestReservationIds(b.getId()));
        assertEquals(scan(), index());

        facade.remove(facade.edit(edit));
        assertEquals(Set.of(), operator.openRequestReservationIds(b.getId()));
        assertEquals(scan(), index());
    }

    @Test
    void isRebuiltOnReload() throws Exception
    {
        Allocatable a = room("PRD128-A");
        Reservation requested = event(a);
        requested.setRequestStatus(a, RequestStatus.REQUESTED);
        facade.store(requested);

        operator.disconnect();
        operator.connect();

        assertEquals(Set.of(requested.getId()), operator.openRequestReservationIds(a.getId()));
        assertEquals(scan(), index());
    }
}
