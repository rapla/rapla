package org.rapla.entities.domain;

import org.rapla.entities.Entity;
import org.rapla.entities.User;

import java.util.*;
import java.util.stream.Collectors;

public class AppointmentMapping {
    private final Map<Entity, Collection<Appointment>> appointmentMap;
    private final Set<Allocatable> allocatables;
    private final Set<User> users;

    public AppointmentMapping() {
        this(new HashMap<>());
    }

    public AppointmentMapping(Map<Entity, Collection<Appointment>> allocatableMap ) {
        this.appointmentMap = allocatableMap;
        this.allocatables = new LinkedHashSet<>();
        this.users = new LinkedHashSet<>();
        for (Entity entity:allocatableMap.keySet()){
            if ( entity.getTypeClass().equals(User.class)) {
                users.add( (User) entity );
            } else {
                allocatables.add( (Allocatable) entity );
            }
        }

    }
    public Set<Allocatable> getAllocatables() {
        return allocatables;
    }

    public Set<User> getUsers() {
        return users;
    }

    public Set<Appointment> getAllAppointments() {
        Set<Appointment> result = new LinkedHashSet<>();
        if (appointmentMap != null) {
            for (Collection<Appointment> appointments : appointmentMap.values()) {
                result.addAll(appointments);
            }
        }
        return result;
    }

    public Collection<Reservation> getAllReservations()
    {
        final Collection<Appointment> allAppointments = getAllAppointments();
        return getAllReservations(allAppointments);
    }

    public static Collection<Reservation> getAllReservations(Collection<Appointment> appointments)
    {
        return appointments.stream().map(Appointment::getReservation).distinct().collect(Collectors.toList());
    }

    public Collection<Appointment> getAppointments(Entity entity) {
        return appointmentMap.get( entity );
    }

    public int size() {
        return appointmentMap.size();
    }

    public Iterable<? extends Map.Entry<Entity, Collection<Appointment>>> entrySet() {
        return appointmentMap.entrySet();
    }
}
