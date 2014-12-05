/*--------------------------------------------------------------------------*
 | Copyright (C) 2013 Gereon Fassbender, Christopher Kohlhaas               |
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
import java.util.Map;

import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaException;

/** All methods that allow modifing the entity-objects.
*/

public interface ModificationModule {
    /** check if the reservation can be saved */
    void checkReservation(Reservation reservation) throws RaplaException;
    /** creates a new Rapla Map. Keep in mind that only RaplaObjects and Strings are allowed as entries for a RaplaMap!*/
    <T> RaplaMap<T> newRaplaMap( Map<String,T> map);
    /** creates an ordered RaplaMap with the entries of the collection as values and their position in the collection from 1..n as keys*/
    <T> RaplaMap<T> newRaplaMap( Collection<T> col);

    CalendarSelectionModel newCalendarModel( User user) throws RaplaException;

    /** Creates a new event,  Creates a new event from the first dynamic type found, basically a shortcut to newReservation(getDynamicType(VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].newClassification()) 
     * This is a convenience method for testing.
     */
    Reservation newReservation() throws RaplaException;
    /** Shortcut for newReservation(classification,getUser()*/
    Reservation newReservation(Classification classification) throws RaplaException;
    /** Creates a new reservation from the classifcation object and with the passed user as its owner 
     * You can create a new classification from a {@link DynamicType} with newClassification method.
     * @see DynamicType#newClassification()
     */
    Reservation newReservation(Classification classification,User user) throws RaplaException;
    Appointment newAppointment(Date startDate,Date endDate) throws RaplaException;
    Appointment newAppointment(Date startDate,Date endDate, User user) throws RaplaException;
    /** @deprecated use newAppointment and change the repeating type of the appointment afterwards*/ 
    Appointment newAppointment(Date startDate,Date endDate, RepeatingType repeatingType, int repeatingDuration) throws RaplaException;
    /** Creates a new resource from the first dynamic type found, basically a shortcut to newAlloctable(getDynamicType(VALUE_CLASSIFICATION_TYPE_RESOURCE)[0].newClassification()).
     * This is a convenience method for testing.
     *  */
    Allocatable newResource() throws RaplaException;
    /** Creates a new person resource,  Creates a new resource from the first dynamic type found, basically a shortcut to newAlloctable(getDynamicType(VALUE_CLASSIFICATION_TYPE_PERSON)[0].newClassification()) 
     * This is a convenience method for testing.
     */
    Allocatable newPerson() throws RaplaException;
    /** Creates a new allocatable from the classifcation object and with the passed user as its owner 
     * You can create a new classification from a {@link DynamicType} with newClassification method.
     * @see DynamicType#newClassification()*/
    Allocatable newAllocatable( Classification classification, User user) throws RaplaException;
    
    /** Shortcut for newAllocatble(classification,getUser()*/
    Allocatable newAllocatable( Classification classification) throws RaplaException;
    
    Allocatable newPeriod() throws RaplaException;
    Category newCategory() throws RaplaException;
    Attribute newAttribute(AttributeType attributeType) throws RaplaException;
    DynamicType newDynamicType(String classificationType) throws RaplaException;
    User newUser() throws RaplaException;

    /** Clones an entity. The entities will get new identifier and
     won't be equal to the original. The resulting object is not persistant and therefore
     can be editet.
     */
    <T extends Entity> T clone(T obj) throws RaplaException;

    /** This call will be delegated to the {@link org.rapla.storage.StorageOperator}. It
     * returns an editable working copy of an object. Only objects return by this method and new objects are editable.
     * To get the persistant, non-editable version of a working copy use {@link #getPersistant} */
    <T extends Entity> T edit(T obj) throws RaplaException;

    <T extends Entity> Collection<T> edit(Collection<T> list) throws RaplaException;
    
    /** Returns the persistant version of a working copy.
     * Throws an {@link org.rapla.entities.EntityNotFoundException} when the
     * object is not found
     * @see #edit
     * @see #clone
     */
    <T extends Entity> T getPersistant(T working) throws RaplaException;
    
    <T extends Entity> Map<T,T> getPersistant(Collection<T> list) throws RaplaException;

    /** This call will be delegated to the {@link org.rapla.storage.StorageOperator} */
    void storeObjects(Entity<?>[] obj) throws RaplaException;
    /** @see #storeObjects(Entity[]) */
    void store(Entity<?> obj) throws RaplaException;
    /** This call will be delegated to the {@link org.rapla.storage.StorageOperator} */
    void removeObjects(Entity<?>[] obj) throws RaplaException;
    /** @see #removeObjects(Entity[]) */
    void remove(Entity<?> obj) throws RaplaException;

    /** stores and removes objects in the one transaction
     * @throws RaplaException */
    void storeAndRemove( Entity<?>[] storedObjects, Entity<?>[] removedObjects) throws RaplaException;
    
    void setTemplate(Allocatable template);
    
    Allocatable getTemplate();

    CommandHistory getCommandHistory();

    
}





