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
import java.util.Set;

import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Promise;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;

/** A collection of all module-interfaces
*/
public interface RaplaFacade
{
    CommandScheduler getScheduler();
    /** Methods for quering the various entities of the backend
     */
    StorageOperator getOperator();
    PermissionController getPermissionController();

    <T extends Entity> T tryResolve( ReferenceInfo<T> info);

    <T extends Entity> T resolve( ReferenceInfo<T> info) throws EntityNotFoundException;

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
    Promise<Collection<Reservation>> getReservations(User user,Date start,Date end,ClassificationFilter[] filters);
    Promise<Collection<Reservation>> getReservationsAsync(User user, Allocatable[] allocatables, Date start, Date end, ClassificationFilter[] reservationFilters);




    /**returns all reservations that have allocated at least one Resource or Person that is part of the allocatables array.
     @param allocatables only reservations that allocate at least on element of this array will be returned.
     @param start only reservations beginning after the start-date will be returned (can be null).
     @param end   only reservations beginning before the end-date will be returned (can be null).
     @param filters  you can specify classificationfilters or null for all reservations.
     **/
    Promise<Collection<Reservation>> getReservationsForAllocatable(Allocatable[] allocatables, Date start,Date end,ClassificationFilter[] filters);


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
    Promise<Map<Allocatable, Collection<Appointment>>> getAllocatableBindings(Collection<Allocatable> allocatables, Collection<Appointment> forAppointment);

    /** returns all existing conflicts with the reservation */
    Promise<Collection<Conflict>> getConflicts(Reservation reservation);

    /** returns all existing conflicts that are visible for the user
     conflicts
     */
    Collection<Conflict> getConflicts() throws RaplaException;

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

    /** returns the preferences for the login user
     * @Deprecated use getPreferences(getWorkingUser()) on client and getSystemPreferences on server*/
    @Deprecated
    Preferences getPreferences() throws RaplaException;

    Preferences getSystemPreferences() throws RaplaException;

    /** returns if the user is allowed to exchange the allocatables of this reservation. A user can do it if he has
     * at least admin privileges for one allocatable. He can only exchange or remove or insert allocatables he has admin privileges on.
     * The User cannot change appointments.*/
    boolean canExchangeAllocatables(User user,Reservation reservation);

    Collection<Allocatable> getTemplates() throws RaplaException;

    Promise<Collection<Reservation>> getTemplateReservations(Allocatable name);

    Promise<Date> getNextAllocatableDate(Collection<Allocatable> asList, Appointment appointment, CalendarOptions options);

    boolean canAllocate(CalendarModel model,User user);


    /** All methods that allow modifing the entity-objects.
     */


    /** Creates a new event,  Creates a new event from the first dynamic type found, basically a shortcut to newReservation(getDynamicType(VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].newClassification())
     * This is a convenience method for testing.
     */
    @Deprecated Reservation newReservation() throws RaplaException;

    /** Creates a new resource from the first dynamic type found, basically a shortcut to newAlloctable(getDynamicType(VALUE_CLASSIFICATION_TYPE_RESOURCE)[0].newClassification()).
     * This is a convenience method for testing.
     *  */
    @Deprecated Allocatable newResource() throws RaplaException;




    // client/server
    /** check if the reservation can be saved */
    void checkReservation(Reservation reservation) throws RaplaException;

    /** creates a new Rapla Map. Keep in mind that only RaplaObjects and Strings are allowed as entries for a RaplaMap!*/
    <T> RaplaMap<T> newRaplaMap( Map<String,T> map);
    /** creates an ordered RaplaMap with the entries of the collection as values and their position in the collection from 1..n as keys*/
    <T> RaplaMap<T> newRaplaMap( Collection<T> col);

    CalendarSelectionModel newCalendarModel( User user) throws RaplaException;


    /** Creates a new reservation from the classifcation object and with the passed user as its owner
     * You can create a new classification from a {@link DynamicType} with newClassification method.
     * @see DynamicType#newClassification()
     */
    Reservation newReservation(Classification classification,User user) throws RaplaException;
    Appointment newAppointment(Date startDate,Date endDate) throws RaplaException;
    Appointment newAppointment(Date startDate,Date endDate, User user) throws RaplaException;
    /** @deprecated use newAppointment and change the repeating type of the appointment afterwards*/
    Appointment newAppointment(Date startDate,Date endDate, RepeatingType repeatingType, int repeatingDuration) throws RaplaException;

    /** Creates a new allocatable from the classifcation object and with the passed user as its owner
     * You can create a new classification from a {@link DynamicType} with newClassification method.
     * @see DynamicType#newClassification()*/
    Allocatable newAllocatable( Classification classification, User user) throws RaplaException;
    Allocatable newPeriod(User user) throws RaplaException;

    Category newCategory() throws RaplaException;

    /**
     * @param classificationType @see DynamicTypeAnnotations
     * @return
     * @throws RaplaException
     */
    DynamicType newDynamicType(String classificationType) throws RaplaException;
    Attribute newAttribute(AttributeType attributeType) throws RaplaException;
    User newUser() throws RaplaException;

    /** Clones an entity. The entities will get new identifier and
     won't be equal to the original. The resulting object is not persistant and therefore
     can be editet.
     */
    <T extends Entity> T clone(T obj,User user) throws RaplaException;

    /** This call will be delegated to the {@link org.rapla.storage.StorageOperator}. It
     * returns an editable working copy of an object. Only objects return by this method and new objects are editable.
     * To get the persistant, non-editable version of a working copy use {@link #getPersistant} */
    <T extends Entity> T edit(T obj) throws RaplaException;

    <T extends Entity> Collection<T> edit(Collection<T> list) throws RaplaException;

    /** checks if the user that is logged into the facade is the user that last changed the entites
     *
     * @param entities
     * @param isNew if new is set then this method does not throw an exception if the entities are not found in persistant store
     * @param <T>
     * @return the latest persistant map of the entities
     * @throws RaplaException if the logged in user is not the lastChanged user of any entities. If isNew is false then an exception is also thrown, when an entity is not found in persistant storage
     */
    <T extends Entity> Map<T,T> checklastChanged(Collection<T> entities, boolean isNew) throws RaplaException;

    /** copies a list of reservations to a new beginning. KeepTime specifies if the original time is used or the time of the new beginDate*/
    Collection<Reservation> copy(Collection<Reservation> toCopy, Date beginn, boolean keepTime, User user) throws RaplaException;

    /** Returns the persistant version of a working copy.
     * Throws an {@link org.rapla.entities.EntityNotFoundException} when the
     * object is not found
     * @see #edit
     * @see #clone
     */
    <T extends Entity> T getPersistant(T working) throws RaplaException;

    <T extends Entity> Map<T,T> getPersistant(Collection<T> list) throws RaplaException;

    /** This call will be delegated to the {@link org.rapla.storage.StorageOperator} */
    <T extends Entity> void storeObjects(T[] obj) throws RaplaException;
    /** @see #storeObjects(Entity[]) */
    <T extends Entity> void store(T obj) throws RaplaException;

    /** This call will be delegated to the {@link org.rapla.storage.StorageOperator} */
    <T extends Entity> void removeObjects(T[] obj) throws RaplaException;

    /** @see #removeObjects(Entity[]) */
    <T extends Entity> void remove(T obj) throws RaplaException;

    /** stores and removes objects in the one transaction
     * @throws RaplaException */
    <T extends Entity, S extends Entity> void  storeAndRemove( T[] storedObjects, S[] removedObjects) throws RaplaException;

    <T extends Entity, S extends Entity> void storeAndRemove( T[] storedObjects, S[] removedObjects, User user) throws RaplaException;

    <T extends Entity, S extends Entity> Promise<Void> dispatch( Collection<T> storeList, Collection<ReferenceInfo<S>> removeList, User user);
    /**
     * Does a merge of allocatables. A merge is defined as the given object will be stored if writeable and then 
     * all references to the provided allocatableIds are replaced with the selected allocatable. Afterwards the
     * allocatables with the given allocatableIds are deleted.
     * 
     * @param selectedObject
     *              the winning allocatable, which will replace all references of the allocatableIds
     * @param allocatableIds
     *              the ids for the allocatables to merge into the selectedObject
     */
    void doMerge(Allocatable selectedObject, Set<ReferenceInfo<Allocatable>> allocatableIds, User user) throws RaplaException;

    /**
     *  Refreshes the data that is in the cache (or on the client)
     and notifies all registered {@link ModificationListener ModificationListeners}
     with an update-event.
     There are two types of refreshs.

     <ul>
     <li>Incremental Refresh: Only the changes are propagated</li>
     <li>Full Refresh: The complete data is reread. (Currently disabled in Rapla)</li>
     </ul>

     <p>
     Incremental refreshs are the normal case if you have a client server basis.
     (In a single user system no refreshs are necessary at all).
     The refreshs are triggered in defined intervals if you use the webbased communication
     and automaticaly if you use the old communication layer. You can change the refresh interval
     via the admin options.
     </p>
     <p>
     Of course you can call a refresh anytime you want to synchronize with the server, e.g. if
     you want to ensure you are uptodate before editing. If you are on the server you dont need to refresh.
     </p>


     <strong>WARNING: When using full refresh on a local file storage
     all information will be  changed. So use it
     only if you modify the data from external.
     You better re-get and re-draw all
     the information in the Frontend after a full refresh.
     </strong>

     */
    void refresh() throws RaplaException;
}





