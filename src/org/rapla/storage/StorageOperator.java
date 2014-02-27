/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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

import java.util.Collection;
import java.util.Date;
import java.util.Map;

import org.rapla.ConnectInfo;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.Template;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;

public interface StorageOperator extends EntityResolver {
    void connect() throws RaplaException;
    void connect(ConnectInfo connectInfo) throws RaplaException;
    boolean isConnected();
    /** Refreshes the data. This could be helpfull if the storage
     * operator uses a cache and does not support "Active Monitoring"
     * of the original data */
    void refresh() throws RaplaException;
    void disconnect() throws RaplaException;

    /** should return a clone of the object. <strong>Never</strong> edit the
        original, <strong>always</strong> edit the object returned by editObject.*/
    Collection<Entity> editObjects(Collection<Entity> obj, User user) throws RaplaException;

    Map<Entity,Entity> getPersistant(Collection<? extends Entity> entity) throws RaplaException;
    /** Stores and/or removes entities and specifies a user that is responsible for the changes.
     * Notifies  all registered StorageUpdateListeners after a successful
     storage.*/
    void storeAndRemove(Collection<Entity> storeObjects,Collection<Entity> removeObjects,User user) throws RaplaException;

    String[] createIdentifier(RaplaType raplaType, int count) throws RaplaException;

    <T extends RaplaObject> Collection<T> getObjects(Class<T> raplaType) throws RaplaException;

    /** returns all the objects (except reservations)that are visible for the current user */
    Collection<Entity>getVisibleEntities(User user) throws RaplaException;

    /** returns the user or null if a user with the given username was not found. */
    User getUser(String username) throws RaplaException;
    Preferences getPreferences(User user, boolean createIfNotNull) throws RaplaException;

    /** returns the reservations of the specified user, sorted by name.
     * @param allocatables */
    Collection<Reservation> getReservations(User user,Collection<Allocatable> allocatables, Date start,Date end) throws RaplaException;

    Category getSuperCategory();

    /** changes the password for the user */
    void changePassword(User user,char[] oldPassword,char[] newPassword) throws RaplaException;

    /** changes the name for the passed user. If a person is connected then all three fields are used. Otherwise only lastname is used*/
	void changeName(User user,String title, String firstname, String surname) throws RaplaException;

	 /** changes the name for the user */ 
	void changeEmail(User user,String newEmail) throws RaplaException;

	void confirmEmail(User user, String newEmail) throws RaplaException;

	boolean canChangePassword() throws RaplaException;

    void addStorageUpdateListener(StorageUpdateListener updateListener);
    void removeStorageUpdateListener(StorageUpdateListener updateListener);

    /** returns the beginning of the current day. Uses getCurrentTimstamp. */
    Date today();

    /** returns the date and time in seconds for creation. Server time will be used if in client/server mode. Note that this is not the system time. Its the time converted to rapla time (GMT+0) 
     * see {@link RaplaLocale.toRaplaTime} 
     * @throws RaplaException */
    Date getCurrentTimestamp();
    
    boolean supportsActiveMonitoring();

    Map<Allocatable,Collection<Appointment>> getFirstAllocatableBindings(Collection<Allocatable> allocatables, Collection<Appointment> appointments, Collection<Reservation> ignoreList) throws RaplaException;
    
    Map<Allocatable, Map<Appointment,Collection<Appointment>>> getAllAllocatableBindings(Collection<Allocatable> allocatables, Collection<Appointment> appointments, Collection<Reservation> ignoreList) throws RaplaException;

    Date getNextAllocatableDate(Collection<Allocatable> allocatables,Appointment appointment, Collection<Reservation> ignoreList, Integer worktimeStartMinutes,Integer worktimeEndMinutes, Integer[] excludedDays, Integer rowsPerHour) throws RaplaException; 
    
    Collection<Conflict> getConflicts(User user) throws RaplaException;

    /**@deprecated will be replaced by direct accessors */
    @Deprecated
    Map<String, Template> getTemplateMap();

}