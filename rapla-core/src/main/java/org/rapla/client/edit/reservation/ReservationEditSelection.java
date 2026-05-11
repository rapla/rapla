package org.rapla.client.edit.reservation;

import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentStartComparator;
import org.rapla.entities.domain.Reservation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure-Java state container for the reservation-edit selection tier
 * (the data that {@code AllocatableSelection} mutates across an edit
 * session: mutable reservations, their pre-edit baselines, the flattened
 * appointment list, the original-reservation lookup).
 * <p>
 * No Swing imports, no facade. The Swing tier holds one of these as a
 * field and asks it questions instead of doing the work inline; tests
 * can exercise the same state transitions without booting a dialog.
 * <p>
 * The "binding map" ({@code allocatable → conflicting appointments}) is
 * NOT held here — it's recomputed by {@link AllocationConflictModel} or
 * a facade-backed loader and passed in per query. This class only
 * tracks the reservation-side state.
 */
public final class ReservationEditSelection
{
    private Collection<Reservation> mutableReservations = Collections.emptyList();
    private Collection<Reservation> originalReservations = Collections.emptyList();
    private Appointment[] appointments = new Appointment[0];

    /**
     * Replaces the edit-session state. {@code appointments} is recomputed
     * as the flattened union over {@code mutable.getAppointments()}.
     */
    public void setReservations(Collection<Reservation> mutable, Collection<Reservation> original)
    {
        this.mutableReservations = mutable == null ? Collections.emptyList() : mutable;
        this.originalReservations = original == null ? Collections.emptyList() : original;
        this.appointments = flattenAppointments(this.mutableReservations);
    }

    public Collection<Reservation> mutableReservations()
    {
        return mutableReservations;
    }

    public Collection<Reservation> originalReservations()
    {
        return originalReservations;
    }

    public Appointment[] appointments()
    {
        return appointments;
    }

    /**
     * Distinct allocatables currently used by any mutable reservation
     * (set semantics, iteration order matches first-seen order).
     */
    public Set<Allocatable> allocatedAllocatables()
    {
        Set<Allocatable> result = new LinkedHashSet<>();
        for (Reservation r : mutableReservations)
        {
            result.addAll(Arrays.asList(r.getAllocatables()));
        }
        return result;
    }

    /**
     * Match a mutable reservation back to its pre-edit baseline by id.
     * Returns {@code null} when the reservation is newly created (no
     * original counterpart).
     */
    public Reservation findMatchingOriginal(Reservation reservation)
    {
        if (reservation == null || originalReservations.isEmpty())
        {
            return null;
        }
        for (Reservation original : originalReservations)
        {
            if (Objects.equals(original, reservation))
            {
                return original;
            }
        }
        return null;
    }

    /**
     * For one allocatable, collect every appointment-restriction across all
     * mutable reservations. When a reservation has no explicit restriction
     * for the allocatable, falls back to all appointments-for that
     * allocatable. Order: reservation iteration order, then restriction
     * order within each reservation. Duplicates kept (legacy semantics).
     */
    public List<Appointment> appointmentsRestricting(Allocatable allocatable)
    {
        List<Appointment> out = new ArrayList<>();
        for (Reservation r : mutableReservations)
        {
            Appointment[] restriction = r.getRestriction(allocatable);
            if (restriction.length == 0)
            {
                restriction = r.getAppointmentsFor(allocatable);
            }
            out.addAll(Arrays.asList(restriction));
        }
        return out;
    }

    /**
     * For one allocatable, return the union of restriction-appointments
     * (no fallback to all-for-allocatable). Mirrors
     * {@code AllocatableSelection.getRestriction(Allocatable)}.
     */
    public List<Appointment> explicitRestriction(Allocatable allocatable)
    {
        List<Appointment> out = new ArrayList<>();
        for (Reservation r : mutableReservations)
        {
            out.addAll(Arrays.asList(r.getRestriction(allocatable)));
        }
        return out;
    }

    /**
     * Flatten reservations to a single appointment array sorted by start time.
     * Matches the legacy {@code AllocatableSelection.setAppointments(...)}
     * behaviour — reservations contribute their sorted appointments, the
     * combined list is sorted again across reservations.
     */
    private static Appointment[] flattenAppointments(Collection<Reservation> reservations)
    {
        List<Appointment> all = new ArrayList<>();
        for (Reservation r : reservations)
        {
            all.addAll(r.getSortedAppointments());
        }
        all.sort(new AppointmentStartComparator());
        return all.toArray(new Appointment[0]);
    }
}
