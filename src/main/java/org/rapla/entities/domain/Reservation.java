/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org .       |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.entities.domain;

import jsinterop.annotations.JsType;
import org.rapla.entities.Annotatable;
import org.rapla.entities.Named;
import org.rapla.entities.Ownable;
import org.rapla.entities.Timestamp;
import org.rapla.entities.dynamictype.Classifiable;

import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.stream.Stream;

/** The <code>Reservation</code> interface is the central interface of
 *  Rapla.  Objects implementing this interface are the courses or
 *  events to be scheduled.  A <code>Reservation</code> consist
 *  of a group of appointments and a set of allocated
 *  resources (rooms, notebooks, ..) and persons.
 *  By default all resources and persons are allocated on every appointment.
 *  If you want to associate allocatable objects to special appointments
 *  use Restrictions.
 *
 *  @see Classifiable
 *  @see Appointment
 *  @see Allocatable
 */
@JsType
public interface Reservation extends EntityPermissionContainer<Reservation>,Classifiable,Named,Ownable,Timestamp, Annotatable,Comparable
{
    int MAX_RESERVATION_LENGTH = 100;

    void addAppointment(Appointment appointment);
    void removeAppointment(Appointment appointment);
    /** returns all appointments that are part off the reservation.*/
    
    Collection<Appointment> getSortedAppointments();

    Stream<Appointment> getAppointmentStream();
    
    Appointment[] getAppointments();
   /** Restrict an allocation to one ore more appointments.
    *  By default all objects of a reservation are allocated
    *  on every appointment. Restrictions allow to model
    *  relations between allocatables and appointments.
    *  A resource or person is restricted if its connected to
    *  one or more appointments instead the whole reservation.
    */
    void setRestriction(Allocatable alloc,Appointment[] appointments);
    
    void setRestrictionForAppointment(Appointment appointment, Allocatable[] restrictedAllocatables);
    
    Appointment[] getRestriction(Allocatable alloc);

    /** returns all appointments for an allocatable. This are either the restrictions, if there are any or all appointments
     * @see #getRestriction
     * @see #getAppointments*/
    Appointment[] getAppointmentsFor(Allocatable alloc);

    /** find an appointment in the reservation that equals the specified appointment. This is usefull if you have the
     * persistant version of an appointment and want to discover the editable appointment in the working copyReservations of a reservation.
     * This does only work with persistant appointments, that have an id.*/
    Appointment findAppointment(Appointment appointment);

    void addAllocatable(Allocatable allocatable);
    void removeAllocatable(Allocatable allocatable);
    Allocatable[] getAllocatables();

    Allocatable[] getRestrictedAllocatables(Appointment appointment);

    /** get all allocatables that are allocated on the appointment, restricted and non restricted ones*/
    Stream<Allocatable> getAllocatablesFor(Appointment appointment);
    
    /** returns if an the reservation has allocated the specified object. */
    boolean hasAllocated(Allocatable alloc);

    /** returns if the allocatable is reserved on the specified appointment. */
    boolean hasAllocatedOn(Allocatable alloc,Appointment appointment);

    /** returns all persons that are associated with the reservation.
        Need not necessarily to be users of the System.
    */
    Allocatable[] getPersons();

    /** returns all resources that are associated with the reservation. */
    Allocatable[] getResources();
    
   

    Reservation[] RESERVATION_ARRAY = new Reservation[0];

	/** returns the first (in time) start of all appointments. Returns null when the reservation has no appointments*/
	Date getFirstDate();
	
	/** returns the last (in time) maxEnd of all appointments. Returns null when one appointment has no end*/
	Date getMaxEnd();

	String format(Locale locale, String annotationName);

	String formatAppointment(Locale locale, String annotationName, Appointment appointment);
	
	String formatAppointmentBlock(Locale locale, String annotationName, AppointmentBlock block);

	int indexOf(Appointment a1);
	
}







