/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 ?, Christopher Kohlhaas                               |
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
package org.rapla.storage.dbrm;

import java.util.Date;

import javax.jws.WebService;

import org.rapla.entities.RaplaType;
import org.rapla.entities.domain.Appointment;
import org.rapla.framework.RaplaException;
import org.rapla.storage.UpdateEvent;
@WebService
public interface RemoteStorage  {
  //  RemoteMethod GET_RESOURCES = new RemoteMethod("getResources");
//    RemoteMethod GET_RESERVATIONS= new RemoteMethod("getReservations", new String[] {"start","end"});
    //RemoteMethod GET_ENTITY_RECURSIVE= new RemoteMethod("getEntityRecursive", new String[] {"id"});
    //RemoteMethod DISPATCH= new RemoteMethod("dispatch", new String[] {"evt"});
    //RemoteMethod CREATE_IDENTIFIER= new RemoteMethod("createIdentifier", new String[] {"raplaType"});
    
    // These Methods belong to the server
    //RemoteMethod RESTART_SERVER = new RemoteMethod("restartServer",new String[] { });
    //RemoteMethod REFRESH = new RemoteMethod("refresh",new String[] {"clientRepositoryVersion" });
    //RemoteMethod LOGIN = new RemoteMethod("login",new String[] { "username", "password"});
    //RemoteMethod CHECK_SERVER_VERSION = new RemoteMethod("checkServerVersion",new String[] {"clientVersion" });
	final String USER_WAS_NOT_AUTHENTIFIED = "User was not authentified";
    
    void authenticate(String username,String password) throws RaplaException;
    boolean canChangePassword() throws RaplaException;
    void changePassword(String username,String oldPassword,String newPassword) throws RaplaException;
    void changeName(String username, String newTitle,String newSurename,String newLastname) throws RaplaException;
    void changeEmail(String username,String newEmail) throws RaplaException;
    void confirmEmail(String username,String newEmail) throws RaplaException;
    
    EntityList getResources() throws RaplaException;
    /** returns the time on the server in string format*/
    String getServerTime() throws RaplaException;
    /** delegates the corresponding method in the StorageOperator. */
    EntityList getReservations(String[] allocatableIds,Date start,Date end) throws RaplaException;

    EntityList getEntityRecursive(String... id) throws RaplaException;

    UpdateEvent refresh(String clientRepoVersion) throws RaplaException;
    
    void restartServer() throws RaplaException;
    UpdateEvent dispatch(UpdateEvent event) throws RaplaException;
    
    String[] createIdentifier(RaplaType raplaType, int count) throws RaplaException;

    EntityList getConflicts() throws RaplaException;
    Integer[][] getFirstAllocatableBindings(String[] allocatables, Appointment[] appointments, String[] reservationIds) throws RaplaException;
    EntityList getAllAllocatableBindings(String[] allocatables, Appointment[] appointments, String[] reservationIds) throws RaplaException;

    Date getNextAllocatableDate(String[] allocatableIds, Appointment appointment,String[] reservationIds, Integer worktimeStartMinutes, Integer worktimeEndMinutes, Integer[] excludedDays, Integer rowsPerHour) throws RaplaException;
	
	void logEntityNotFound(String logMessage,String... referencedIds) throws RaplaException;
}
