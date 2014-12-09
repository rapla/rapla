/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.facade;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaException;
import org.rapla.rest.gwtjsonrpc.common.FutureResult;
/** Methods for quering the various entities of the backend
*/

public interface QueryModule
{
    /** returns all DynamicTypes matching the specified classification
        possible keys are reservation, person and resource.
        @see org.rapla.entities.dynamictype.DynamicTypeAnnotations
      */
    DynamicType[] getDynamicTypes(String classificationType) throws RaplaException;

    /** returns the DynamicType with the passed elementKey */
    DynamicType getDynamicType(String elementKey) throws RaplaException;

    /** returns The root category.   */
    Category getSuperCategory();

    /** returns The category that contains all the user-groups of rapla   */
    Category getUserGroupsCategory() throws RaplaException;

    /** returns all users  */
    User[] getUsers() throws RaplaException;

    /** returns the user with the specified username */
    User getUser(String username) throws RaplaException;

    /** returns all allocatables that match the passed ClassificationFilter. If null all readable allocatables are returned*/
    Allocatable[] getAllocatables(ClassificationFilter[] filters) throws RaplaException;
    
    /** returns all readable allocatables, same as getAllocatables(null)*/
    Allocatable[] getAllocatables() throws RaplaException;

    /** returns the reservations of the specified user in the specified interval
     @param user  A user-object or null for all users
     @param start only reservations beginning after the start-date will be returned (can be null).
     @param end   only reservations beginning before the end-date will be returned (can be null).
     @param filters  you can specify classificationfilters or null for all reservations .
     */
    Reservation[] getReservations(User user,Date start,Date end,ClassificationFilter[] filters) throws RaplaException;

    /**returns all reservations that have allocated at least one Resource or Person that is part of the allocatables array.
     @param allocatables only reservations that allocate at least on element of this array will be returned.
     @param start only reservations beginning after the start-date will be returned (can be null).
     @param end   only reservations beginning before the end-date will be returned (can be null).

    **/
    Reservation[] getReservations(Allocatable[] allocatables,Date start,Date end) throws RaplaException;
    
    Reservation[] getReservationsForAllocatable(Allocatable[] allocatables, Date start,Date end,ClassificationFilter[] filters) throws RaplaException;
    
	List<Reservation> getReservations(Collection<Conflict> conflicts) throws RaplaException;

    /** returns all available periods */
    Period[] getPeriods() throws RaplaException;

    /** returns an Interface for accessing the periods
     * @throws RaplaException */
    PeriodModel getPeriodModel() throws RaplaException;

    /** returns the current date in GMT+0 Timezone. If rapla operates
        in multi-user mode, the date should be calculated from the
        server date.
     */
    Date today();


    
    /** returns all allocatables from the set of passed allocatables, that are already allocated by different parallel reservations at the time-slices, that are described by the appointment */
    public FutureResult<Map<Allocatable, Collection<Appointment>>> getAllocatableBindings(Collection<Allocatable> allocatables,Collection<Appointment> forAppointment);
    
    /** returns all allocatables, that are already allocated by different parallel reservations at the time-slices, that are described by the appointment 
     * @deprecated use {@link #getAllocatableBindings(Collection,Collection)} instead
     * */
    @Deprecated
    Allocatable[] getAllocatableBindings(Appointment appointment) throws RaplaException;

    /** returns all existing conflicts with the reservation */
    Conflict[] getConflicts(Reservation reservation) throws RaplaException;

    /** returns all existing conflicts that are visible for the user
        conflicts
     */
    Conflict[] getConflicts() throws RaplaException;

    /** returns if the user has the permissions to change/create an
        allocation on the passed appointment. Changes of an
        existing appointment that are in an permisable
        timeframe are allowed. Example: The extension of an exisiting appointment,
        doesn't affect allocations in the past and should not create a
        conflict with the permissions.
     */
    //boolean hasPermissionToAllocate( Appointment appointment, Allocatable allocatable );

    /** returns the preferences for the passed user, must be admin todo this. creates a new prefence object if not set*/
    Preferences getPreferences(User user) throws RaplaException;

    /** returns the preferences for the passed user, must be admin todo this.*/
    Preferences getPreferences(User user, boolean createIfNotNull) throws RaplaException;

    /** returns the preferences for the login user */
    Preferences getPreferences() throws RaplaException;
    
    Preferences getSystemPreferences() throws RaplaException;

    /** returns if the user is allowed to exchange the allocatables of this reservation. A user can do it if he has
     * at least admin privileges for one allocatable. He can only exchange or remove or insert allocatables he has admin privileges on.
     * The User cannot change appointments.*/
    boolean canExchangeAllocatables(Reservation reservation);
    
    boolean canCreateReservations(DynamicType type, User user);
    
	public Collection<Allocatable> getTemplates() throws RaplaException;
	
	public Collection<Reservation> getTemplateReservations(Allocatable name) throws RaplaException;

	FutureResult<Date> getNextAllocatableDate(Collection<Allocatable> asList, Appointment appointment, CalendarOptions options) throws RaplaException;


}





