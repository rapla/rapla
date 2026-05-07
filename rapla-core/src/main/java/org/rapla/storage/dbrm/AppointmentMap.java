package org.rapla.storage.dbrm;

import org.rapla.components.util.Assert;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentMapping;
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
    private final Map<String, Set<String>> entityIdToAppointmentIds;
    private transient EntityResolver resolver;

    public AppointmentMap()
    {
        this.entityIdToAppointmentIds = new LinkedHashMap<>();
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

    public AppointmentMap(AppointmentMapping mapping)
    {
        this.entityIdToAppointmentIds = new LinkedHashMap<>();
        this.reservations = new LinkedHashSet<>();
        for (Map.Entry<Entity, Collection<Appointment>> entry : mapping.entrySet())
        {
            final Entity key = entry.getKey();
            Assert.notNull( key);
            final String allocatableId = key.getId();
            Set<String> ids = entityIdToAppointmentIds.get(allocatableId);
            if (ids == null)
            {
                ids = new LinkedHashSet<>();
                entityIdToAppointmentIds.put(allocatableId, ids);
            }
            final Collection<Appointment> value = entry.getValue();
            for (Appointment app : value)
            {
                final ReservationImpl reservation = (ReservationImpl) app.getReservation();
                final String appId = app.getId();
                if (reservation.getAppointmentStream().map( Appointment::getId ).anyMatch( appId::equals))
                {
                    reservations.add(reservation);
                    ids.add(appId);
                }
            }
        }
    }

    public AppointmentMapping getResult(ClassificationFilter[] filters)
    {
        Map<String, Appointment> appointmentIdToAppointment = new LinkedHashMap<>();
        for (ReservationImpl reservation : reservations)
        {
            final Appointment[] appointments = reservation.getAppointments();
            for (Appointment app : appointments)
            {
                final String appId = app.getId();
                appointmentIdToAppointment.put(appId, app);
            }
        }
        Map<Entity, Collection<Appointment>> appointmentMap = new LinkedHashMap<>();

        for (Map.Entry<String, Set<String>> entry : entityIdToAppointmentIds.entrySet())
        {
            final String key = entry.getKey();
            final Set<String> value = entry.getValue();
            Entity entity = resolver.tryResolve(key, Allocatable.class);
            if ( entity == null) {
                entity = resolver.tryResolve(key, User.class);
            }
            if (entity != null)
            {
                Collection<Appointment> appointments = appointmentMap.get(entity);
                if (appointments == null)
                {
                    appointments = new LinkedHashSet<>();
                    appointmentMap.put(entity, appointments);
                }
                for (String appointmentId : value)
                {
                    Appointment app = appointmentIdToAppointment.get(appointmentId);
                    if ( app != null)
                    {
                        // if app == null that means an appointment is no longer in the reservation
                        Assert.notNull(app);
                        Reservation reservation = app.getReservation();
                        Assert.notNull(reservation);
                        if (filters != null && !ClassificationFilter.Util.matches(filters, reservation))
                        {
                            continue;
                        }
                        appointments.add(app);
                    }
                }
            }
        }
        final AppointmentMapping result = new AppointmentMapping(appointmentMap);
        return result;

    }

    @Override public String toString()
    {
        return "AppointmentMap{" +
                "reservations=" + reservations +
                ", allocatableIdToAppointmentIds=" + entityIdToAppointmentIds +
                '}';
    }
    
}
