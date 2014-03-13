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
import org.rapla.framework.RaplaException;
import org.rapla.storage.UpdateEvent;

import com.google.gwtjsonrpc.common.FutureResult;
import com.google.gwtjsonrpc.common.RemoteJsonService;
import com.google.gwtjsonrpc.common.ResultType;
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
    
	@ResultType(Boolean.class)
    FutureResult<Boolean> canChangePassword();
	@ResultType(VoidResult.class)
	FutureResult<VoidResult> changePassword(String username,String oldPassword,String newPassword);
	@ResultType(VoidResult.class)
	FutureResult<VoidResult> changeName(String username, String newTitle,String newSurename,String newLastname);
	@ResultType(VoidResult.class)
	FutureResult<VoidResult> changeEmail(String username,String newEmail);
	@ResultType(VoidResult.class)
	FutureResult<VoidResult> confirmEmail(String username,String newEmail);
    
	@ResultType(UpdateEvent.class)
    FutureResult<UpdateEvent> getResources() throws RaplaException;
    /** returns the time on the server in string format*/
    
	@ResultType(String.class)
	FutureResult<String> getServerTime();
    
    /** delegates the corresponding method in the StorageOperator. 
     * @param annotationQuery */
    @ResultType(ReservationList.class)
    FutureResult<ReservationList> getReservations(String[] allocatableIds,Date start,Date end, Map<String, String> annotationQuery);

    @ResultType(UpdateEvent.class)
    FutureResult<UpdateEvent> getEntityRecursive(String... id);

    @ResultType(UpdateEvent.class)
    FutureResult<UpdateEvent> refresh(String clientRepoVersion);
    
	@ResultType(VoidResult.class)
	FutureResult<VoidResult> restartServer();

	@ResultType(UpdateEvent.class)
	FutureResult<UpdateEvent> dispatch(UpdateEvent event);
    
	@ResultType(String[].class)
	FutureResult<String[]> getTemplateNames();
    
	@ResultType(String[].class)
	FutureResult<String[]> createIdentifier(String raplaType, int count);

	@ResultType(ConflictList.class)
	FutureResult<ConflictList> getConflicts();
	@ResultType(BindingMap.class)
	FutureResult<BindingMap> getFirstAllocatableBindings(String[] allocatableIds, List<AppointmentImpl> appointments, String[] reservationIds);
	@ResultType(ReservationList.class)
	FutureResult<ReservationList> getAllAllocatableBindings(String[] allocatables, List<AppointmentImpl> appointments, String[] reservationIds);

	@ResultType(Date.class)
    FutureResult<Date> getNextAllocatableDate(String[] allocatableIds, AppointmentImpl appointment,String[] reservationIds, Integer worktimeStartMinutes, Integer worktimeEndMinutes, Integer[] excludedDays, Integer rowsPerHour);
	
    //void logEntityNotFound(String logMessage,String... referencedIds) throws RaplaException;
    
    public static class BindingMap
    {
    	Map<String,List<String>> bindings;
    	BindingMap() {
		}
    	public BindingMap(Map<String,List<String>> bindings)
    	{
    		this.bindings = bindings;
    	}
    	
    	public Map<String,List<String>> get() {
			return bindings;
		}
    }
    
    public static class ReservationList
    {
    	List<ReservationImpl> reservations;
    	ReservationList() {
		}
    	public ReservationList(List<ReservationImpl> reservations)
    	{
    		this.reservations = reservations;
    	}
    	
    	public List<ReservationImpl> get() {
			return reservations;
		}
    }
    
    public static class ConflictList
    {
    	List<ConflictImpl> conflicts;
    	ConflictList()
    	{
    	}
    	public ConflictList(List<ConflictImpl> conflicts)
    	{
    		this.conflicts = conflicts;
    	}
    	
    	public List<ConflictImpl> get() {
			return conflicts;
		}
    
    }

	
}
