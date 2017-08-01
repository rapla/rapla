package org.rapla.storage.dbrm;

import org.rapla.components.util.Assert;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ReferenceInfo;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class AppointmentMap
{
    private Set<ReservationImpl> reservations;
    private Map<String, Set<String>> allocatableIdToAppointmentIds;
    private transient EntityResolver resolver;

    public AppointmentMap()
    {

    }

    public void init(EntityResolver resolver)
    {
        this.resolver = resolver;
        for ( ReservationImpl reservation:reservations)
        {
            reservation.setResolver( resolver);
            reservation.setReadOnly();
        }
    }

    public AppointmentMap(Map<Allocatable, Collection<Appointment>> map)
    {
        this.allocatableIdToAppointmentIds = new LinkedHashMap<>();
        this.reservations = new LinkedHashSet<>();
        for (Map.Entry<Allocatable, Collection<Appointment>> entry : map.entrySet())
        {
            final Allocatable key = entry.getKey();
            Assert.notNull( key);
            final String allocatableId = key.getId();
            Set<String> ids = allocatableIdToAppointmentIds.get(allocatableId);
            if (ids == null)
            {
                ids = new LinkedHashSet<>();
                allocatableIdToAppointmentIds.put(allocatableId, ids);
            }
            final Collection<Appointment> value = entry.getValue();
            for (Appointment app : value)
            {
                reservations.add((ReservationImpl) app.getReservation());
                ids.add(app.getId());
            }
        }
    }

    public Map<Allocatable, Collection<Appointment>> getResult(ClassificationFilter[] filters)
    {
        Map<String, Appointment> appointmentIdToAppointment = new LinkedHashMap<>();
        for (ReservationImpl reservation : reservations)
        {
            final Appointment[] appointments = reservation.getAppointments();
            for (Appointment app : appointments)
            {
                appointmentIdToAppointment.put(app.getId(), app);
            }
        }
        final LinkedHashMap<Allocatable, Collection<Appointment>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : allocatableIdToAppointmentIds.entrySet())
        {
            final String key = entry.getKey();
            final Set<String> value = entry.getValue();
            final Allocatable allocatable = resolver.tryResolve(new ReferenceInfo<Allocatable>(key, Allocatable.class));
            if (allocatable != null)
            {
                Collection<Appointment> appointments = result.get(allocatable);
                if (appointments == null)
                {
                    appointments = new LinkedHashSet<>();
                    result.put(allocatable, appointments);
                }
                for (String appointmentId : value)
                {
                    Appointment app = appointmentIdToAppointment.get(appointmentId);
                    Assert.notNull(app);
                    Reservation reservation = app.getReservation();
                    Assert.notNull( reservation);
                    if (filters != null && !ClassificationFilter.Util.matches(filters, reservation))
                    {
                        continue;
                    }
                    appointments.add(app);
                }
            }
        }return result;

    }

    @Override public String toString()
    {
        return "AppointmentMap{" +
                "reservations=" + reservations +
                ", allocatableIdToAppointmentIds=" + allocatableIdToAppointmentIds +
                '}';
    }
    
}
