/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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
/** A Facade Interface for manipulating the stored data.
 *  This abstraction allows Rapla to store the data
 *  in many ways. <BR>
 *  Currently implemented are the storage in an XML-File
 *  ,the storage in an SQL-DBMS and storage over a
 *  network connection.
 *  @see org.rapla.storage.dbsql.DBOperator
 *  @see org.rapla.storage.dbfile.XMLOperator
 *  @see org.rapla.storage.dbrm.RemoteOperator
 *  @author Christopher Kohlhaas
 */
package org.rapla.storage;

import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.Conflict;
import org.rapla.facade.PeriodModel;
import org.rapla.framework.RaplaException;
import org.rapla.scheduler.Promise;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface StorageOperator extends EntityResolver {
	int MAX_DEPENDENCY = 20;
	   
	String UNRESOLVED_RESOURCE_TYPE = "rapla:unresolvedResource";
	String ANONYMOUSEVENT_TYPE = "rapla:anonymousEvent";
	String DEFAULT_USER_TYPE = "rapla:defaultUser";
	String PERIOD_TYPE = "rapla:period";
	String RAPLA_TEMPLATE = "rapla:template";


    String getUsername(ReferenceInfo<User> userId) throws RaplaException;
    boolean isConnected();
    /** Refreshes the data. This could be helpful if the storage
     * operator uses a cache and does not support "Active Monitoring"
     * of the original data */
    void refresh() throws RaplaException;

    Promise<Void> refreshAsync();

    void disconnect() throws RaplaException;

    /** should return a clone of the object. <strong>Never</strong> edit the
        original, <strong>always</strong> edit the object returned by editObject.*/
    Map<Entity,Entity> editObjects(Collection<Entity> objList, User user) throws RaplaException;

    /** should return a clone of the object. <strong>Never</strong> edit the
     original, <strong>always</strong> edit the object returned by editObject.
     The Map keys are the original Versions of the Object and the values are the editable versions
     */
    Promise<Map<Entity,Entity>> editObjectsAsync(Collection<Entity> objList, User user, boolean checkLastChanged);

    /** if an id is not found and throwEntityNotFound is set to false then the resulting map does not contain an entry for the missing id*/
    <T extends Entity> Promise<Map<ReferenceInfo<T>,T>> getFromIdAsync(Collection<ReferenceInfo<T>> idSet, boolean throwEntityNotFound);
    
    Map<Entity,Entity> getPersistant(Collection<? extends Entity> entity) throws RaplaException;

    //Promise<Map<Entity,Entity>> getPersistantAsync(Collection<? extends Entity> entity);
    /** Stores and/or removes entities and specifies a user that is responsible for the changes.
     * Notifies  all registered StorageUpdateListeners after a successful
     storage.*/
    <T extends Entity, S extends Entity> void storeAndRemove(Collection<T> storeObjects,Collection<ReferenceInfo<S>> removeObjects,User user) throws RaplaException;

    <T extends Entity, S extends Entity> Promise<Void> storeAndRemoveAsync(Collection<T> storeObjects,Collection<ReferenceInfo<S>> removeObjects,User user);

    <T extends Entity> List<ReferenceInfo<T>> createIdentifier(Class<T> raplaType, int count) throws RaplaException;

    <T extends Entity> Promise<List<ReferenceInfo<T>>> createIdentifierAsync(Class<T> raplaType, int count);

    Collection<User> getUsers() throws RaplaException;

	Collection<DynamicType> getDynamicTypes() throws RaplaException;

    /** returns the user or null if a user with the given username was not found. */
    User getUser(String username) throws RaplaException;
    Preferences getPreferences(User user, boolean createIfNotNull) throws RaplaException;

    /** returns the reservations of the specified user, sorted by name.
     * @param allocatables 
     * @param reservationFilters 
     * @param annotationQuery */
    Promise<Map<Allocatable,Collection<Appointment>>> queryAppointments(User user, Collection<Allocatable> allocatables, Date start, Date end,  ClassificationFilter[] reservationFilters, Map<String, String> annotationQuery);

    Promise<Map<Allocatable, Collection<Appointment>>> queryAppointments( User user,Collection<Allocatable> allocatables, Date start, Date end, ClassificationFilter[] reservationFilters, String templateId);

	Collection<Allocatable> getAllocatables(ClassificationFilter[] filters) throws RaplaException;

    Category getSuperCategory();

    /** changes the password for the user */
    void changePassword(User user,char[] oldPassword,char[] newPassword) throws RaplaException;

    /** changes the name for the passed user. If a person is connected then all three fields are used. Otherwise only lastname is used*/
	void changeName(User user,String title, String firstname, String surname) throws RaplaException;

	 /** changes the name for the user */ 
	void changeEmail(User user,String newEmail) throws RaplaException;

	void confirmEmail(User user, String newEmail) throws RaplaException;

	boolean canChangePassword() throws RaplaException;


    /** returns the beginning of the current day. Uses getCurrentTimstamp. */
    Date today();

    /** returns the date and time in seconds for creation. Server time will be used if in client/server mode. Note that this is always the utc time */ 
    Date getCurrentTimestamp();
    
    boolean supportsActiveMonitoring();

    Promise<Map<Allocatable, Collection<Appointment>>> getFirstAllocatableBindings(Collection<Allocatable> allocatables, Collection<Appointment> appointments, Collection<Reservation> ignoreList);
    
    Promise<Map<Allocatable, Map<Appointment,Collection<Appointment>>>> getAllAllocatableBindings(Collection<Allocatable> allocatables, Collection<Appointment> appointments, Collection<Reservation> ignoreList);

    Promise<Date> getNextAllocatableDate(Collection<Allocatable> allocatables,Appointment appointment, Collection<Reservation> ignoreList, Integer worktimeStartMinutes,Integer worktimeEndMinutes, Integer[] excludedDays, Integer rowsPerHour);
    
    Promise<Collection<Conflict>> getConflicts(User user);

    Promise<Collection<Conflict>> getConflicts(Reservation reservation);

    PermissionController getPermissionController();
	//Collection<String> getTemplateNames() throws RaplaException;

    FunctionFactory getFunctionFactory(String functionName);
 //   List<Allocatable> queryDependent(Collection<Allocatable> allocatables);
    Promise<Allocatable> doMerge(Allocatable selectedObject, Set<ReferenceInfo<Allocatable>> allocatableIds, User user);

    Collection<Allocatable> getDependent(Collection<Allocatable> allocatables);

    /** returns an Interface for accessing the periods
     * @throws RaplaException */
    PeriodModel getPeriodModelFor(String key) throws RaplaException;

}