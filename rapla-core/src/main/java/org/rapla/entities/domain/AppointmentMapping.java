package org.rapla.entities.domain;

import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.storage.ReferenceInfo;

import java.util.*;
import java.util.function.Predicate;
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
        return getAllAppointments( null);
    }

    public Set<Appointment> getAllAppointments(Predicate<Appointment> filter) {
        Set<Appointment> result = new LinkedHashSet<>();
        if (appointmentMap != null) {
            for (Collection<Appointment> appointments : appointmentMap.values()) {
                if ( filter != null)
                {
                    appointments.stream().filter(filter).forEach(result::add);
                }
                else
                {
                    result.addAll(appointments);
                }
            }
        }
        return result;
    }

    public Map<ReferenceInfo<Appointment>,Appointment> getAllAppointmentsByReference() {
        Map<ReferenceInfo<Appointment>,Appointment> result = new LinkedHashMap<>();
        if (appointmentMap != null) {
            for (Collection<Appointment> appointments : appointmentMap.values()) {
                for (Appointment appointment : appointments) {
                    result.put(appointment.getReference(),appointment);
                }
            }
        }
        return result;
    }

    public Collection<Reservation> getAllReservations()
    {
        return getAllReservationsFiltered(null);
    }
    public Collection<Reservation> getAllReservationsFiltered(Predicate<Appointment> appointmentFilter)
    {
        final Collection<Appointment> allAppointments = getAllAppointments(appointmentFilter);
        return getAllReservations(allAppointments);
    }

    public static Collection<Reservation> getAllReservations(Collection<Appointment> appointments)
    {
        return appointments.stream().map(Appointment::getReservation).distinct().collect(Collectors.toList());
    }

    public Collection<Appointment> getAppointments(Entity entity) {
        return appointmentMap.get( entity );
    }

    /**
     * Match provenance: the allocatables (restricted to {@code candidates}, or ALL
     * mapped allocatables when {@code candidates} is null) whose belongsTo-resolved
     * binding set contains {@code appointment}. This is the SINGLE primitive behind
     * both Swing's {@code RaplaBuilder.RaplaBlockContext} selected-match grouping and
     * the GraphQL {@code AppointmentBlock.matchedBy} field, so client and server agree
     * on WHY a block is in scope. Result order follows the candidate iteration order.
     */
    public List<Allocatable> getMatchingAllocatables(Appointment appointment, Collection<Allocatable> candidates) {
        Collection<Allocatable> pool = candidates != null ? candidates : allocatables;
        List<Allocatable> out = new ArrayList<>();
        for (Allocatable alloc : pool) {
            Collection<Appointment> appts = appointmentMap.get( alloc );
            if (appts != null && appts.contains( appointment )) {
                out.add( alloc );
            }
        }
        return out;
    }

    public int size() {
        return appointmentMap.size();
    }

    public Iterable<? extends Map.Entry<Entity, Collection<Appointment>>> entrySet() {
        return appointmentMap.entrySet();
    }

}
