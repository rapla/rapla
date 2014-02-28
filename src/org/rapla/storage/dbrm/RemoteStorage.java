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
import java.util.List;
import java.util.Map;

import javax.jws.WebService;

import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.storage.UpdateEvent;

import com.google.gwtjsonrpc.common.RemoteJsonService;
import com.google.gwtjsonrpc.common.VoidResult;
@WebService
public interface RemoteStorage extends RemoteJsonService {
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
    
    FutureResult<VoidResult> authenticate(String username,String password);
    FutureResult<Boolean> canChangePassword();
    FutureResult<VoidResult> changePassword(String username,String oldPassword,String newPassword);
    FutureResult<VoidResult> changeName(String username, String newTitle,String newSurename,String newLastname);
    FutureResult<VoidResult> changeEmail(String username,String newEmail);
    FutureResult<VoidResult> confirmEmail(String username,String newEmail);
    
    FutureResult<UpdateEvent> getResources();
    /** returns the time on the server in string format*/
    FutureResult<String> getServerTime();
    /** delegates the corresponding method in the StorageOperator. */
    FutureResult<List<ReservationImpl>> getReservations(String[] allocatableIds,Date start,Date end);

    FutureResult<UpdateEvent> getEntityRecursive(String... id);

    FutureResult<UpdateEvent> refresh(String clientRepoVersion);
    
    FutureResult<VoidResult> restartServer();
    FutureResult<UpdateEvent> dispatch(UpdateEvent event);
    
    FutureResult<String[]> createIdentifier(String raplaType, int count);

    FutureResult<List<ConflictImpl>> getConflicts();
    FutureResult<Map<String,List<String>>> getFirstAllocatableBindings(String[] allocatableIds, List<AppointmentImpl> appointments, String[] reservationIds);
    //List<ConflictImpl> getAllAllocatableBindings(String[] allocatables, Appointment[] appointments, String[] reservationIds);

    FutureResult<Date> getNextAllocatableDate(String[] allocatableIds, AppointmentImpl appointment,String[] reservationIds, Integer worktimeStartMinutes, Integer worktimeEndMinutes, Integer[] excludedDays, Integer rowsPerHour);
	
    FutureResult<VoidResult> logEntityNotFound(String logMessage,String... referencedIds);
}
