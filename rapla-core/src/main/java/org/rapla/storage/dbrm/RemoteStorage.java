/*--------------------------------------------------------------------------*
 | Copyright (C) 2023    Christopher Kohlhaas                               |
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

import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.RaplaException;
import org.rapla.scheduler.Promise;
import org.rapla.storage.UpdateEvent;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;
import java.util.Date;
import java.util.List;
import java.util.Map;

@HttpExchange("/storage")
public interface RemoteStorage
{
    String USER_WAS_NOT_AUTHENTIFIED = "User was not authentified";

    @GetExchange("/change/canchangepassword")
    boolean canChangePassword() throws RaplaException;

    @PostExchange("/change/password")
    void changePassword(PasswordPost job) throws RaplaException;

    class PasswordPost
    {
        private String username;
        private String oldPassword;
        private String newPassword;

        public PasswordPost()
        {
        }

        public PasswordPost(String username, String oldPassword, String newPassword)
        {
            super();
            this.username = username;
            this.oldPassword = oldPassword;
            this.newPassword = newPassword;
        }

        public String getUsername()
        {
            return username;
        }

        public String getOldPassword()
        {
            return oldPassword;
        }

        public String getNewPassword()
        {
            return newPassword;
        }
    }

    @PostExchange("/change/name")
    void changeName(@RequestParam(value = "username", required = false) String username, @RequestParam(value = "title", required = false) String newTitle, @RequestParam(value = "surename", required = false) String newSurename,
            String newLastname) throws RaplaException;

    @PostExchange("/change/email")
    void changeEmail(@RequestParam(value = "username", required = false) String username, String newEmail) throws RaplaException;

    @PostExchange("/confirm/email")
    void confirmEmail(@RequestParam(value = "username", required = false) String username, String newEmail) throws RaplaException;

    @GetExchange("/resourcesSync")
    UpdateEvent getResourcesSync() throws RaplaException;

    @GetExchange("/resources")
    Promise<UpdateEvent> getResources();

    /** delegates the corresponding method in the StorageOperator. */
    //    FutureResult<List<ReservationImpl>> getReservations(@WebParam(name="resources")String[] allocatableIds,@WebParam(name="start")Date start,@WebParam(name="end")Date end, @WebParam(name="annotations")Map<String, String> annotationQuery);
    @PostExchange
    Promise<AppointmentMap> queryAppointments(QueryAppointments job) throws RaplaException;

    class QueryAppointments
    {
        private String[] ownerIds;
        private String[] resources;
        private Date start;
        private Date end;
        private Map<String, String> annotations;
        private boolean requestsOnly = false;

        public QueryAppointments(String[] ownerIds, String[] resources, Date start, Date end, Map<String, String> annotations, boolean requestsOnly)
        {
            super();
            this.resources = resources;
            this.ownerIds = ownerIds;
            this.start = start;
            this.end = end;
            this.annotations = annotations;
            this.requestsOnly = requestsOnly;
        }

        public QueryAppointments()
        {
        }


        public String[] getOwnerIds() {
            return ownerIds;
        }
        public String[] getResources()
        {
            return resources;
        }

        public Date getStart()
        {
            return start;
        }

        public boolean isRequestsOnly()
        {
            return requestsOnly;
        }

        public void setRequestsOnly(boolean requestsOnly) {
            this.requestsOnly = requestsOnly;
        }

        public Date getEnd()
        {
            return end;
        }

        public Map<String, String> getAnnotations()
        {
            return annotations;
        }
    }

    @PostExchange("/entity/recursiveSync")
    UpdateEvent getEntityRecursive(@RequestParam(value = "errorIfNotFound", required = false)Boolean errorIfNotFound,UpdateEvent.SerializableReferenceInfo... infos) throws RaplaException;

    @PostExchange("/entity/dependent")
    Promise<UpdateEvent> getEntityDependencies(@RequestParam(value = "errorIfNotFound", required = false)Boolean errorIfNotFound,UpdateEvent.SerializableReferenceInfo... infos);

    @PostExchange("/refreshSync")
    UpdateEvent refreshSync(@RequestParam(value = "lastValidated", required = false) String lastSyncedTime) throws RaplaException;

    @PostExchange("/refreshSyncAllEvents")
    UpdateEvent refreshSyncAllEvents(@RequestParam(value = "lastValidated", required = false) String lastSyncedTime) throws RaplaException;

    @PostExchange("/refresh")
    Promise<UpdateEvent> refresh(@RequestParam(value = "lastValidated", required = false) String lastValidated);

    @PostExchange("/restart")
    Promise<Void> restartServer();

    @PostExchange("/dispatchSync")
    UpdateEvent store(UpdateEvent event) throws RaplaException;

    @PostExchange("/dispatch")
    Promise<UpdateEvent> dispatch(UpdateEvent event);

    //	@ResultType(value=String.class,container=List.class)
    //	FutureResult<List<String>> getTemplateNames();

    @PostExchange("/identifierSync")
    List<String> createIdentifierSync(@RequestParam(value = "raplaType", required = false) String raplaType, @RequestParam(value = "count", required = false) int count) throws RaplaException;

    @PostExchange("/identifier")
    Promise<List<String>> createIdentifier(@RequestParam(value = "raplaType", required = false) String raplaType, @RequestParam(value = "count", required = false) int count);

    @GetExchange("/conflicts")
    Promise<List<ConflictImpl>> getConflicts() ;

    @PostExchange("/allocatable/bindings/first")
    Promise<BindingMap> getFirstAllocatableBindings(AllocatableBindingsRequest job);

    class AllocatableBindingsRequest
    {
        private String[] allocatableIds;
        private List<AppointmentImpl> appointments;
        private String[] reservationIds;

        public AllocatableBindingsRequest()
        {
        }

        public AllocatableBindingsRequest(String[] allocatableIds, List<AppointmentImpl> appointments, String[] reservationIds)
        {
            super();
            this.allocatableIds = allocatableIds;
            this.appointments = appointments;
            this.reservationIds = reservationIds;
        }

        public String[] getAllocatableIds()
        {
            return allocatableIds;
        }

        public List<AppointmentImpl> getAppointments()
        {
            return appointments;
        }

        public String[] getReservationIds()
        {
            return reservationIds;
        }
    }

    @PostExchange("/allocatable/bindings/all")
    Promise<List<ReservationImpl>> getAllAllocatableBindings(AllocatableBindingsRequest job);

    @PostExchange("/allocatable/date/next")
    Promise<Date> getNextAllocatableDate(NextAllocatableDateRequest job);

    class NextAllocatableDateRequest
    {
        private String[] allocatableIds;
        private AppointmentImpl appointment;
        private String[] reservationIds;
        private Integer worktimeStartMinutes;
        private Integer worktimeEndMinutes;
        private Integer[] excludedDays;
        Integer rowsPerHour;

        public NextAllocatableDateRequest()
        {
            // TODO Auto-generated constructor stub
        }

        public NextAllocatableDateRequest(String[] allocatableIds, AppointmentImpl appointment, String[] reservationIds, Integer worktimeStartMinutes,
                Integer worktimeEndMinutes, Integer[] excludedDays, Integer rowsPerHour)
        {
            super();
            this.allocatableIds = allocatableIds;
            this.appointment = appointment;
            this.reservationIds = reservationIds;
            this.worktimeStartMinutes = worktimeStartMinutes;
            this.worktimeEndMinutes = worktimeEndMinutes;
            this.excludedDays = excludedDays;
            this.rowsPerHour = rowsPerHour;
        }

        public String[] getAllocatableIds()
        {
            return allocatableIds;
        }

        public AppointmentImpl getAppointment()
        {
            return appointment;
        }

        public String[] getReservationIds()
        {
            return reservationIds;
        }

        public Integer getWorktimeStartMinutes()
        {
            return worktimeStartMinutes;
        }

        public Integer getWorktimeEndMinutes()
        {
            return worktimeEndMinutes;
        }

        public Integer[] getExcludedDays()
        {
            return excludedDays;
        }

        public Integer getRowsPerHour()
        {
            return rowsPerHour;
        }
    }

    @GetExchange("/user")
    String getUsername(@RequestParam(value = "userId", required = false) String userId) throws RaplaException;

    //void logEntityNotFound(String logMessage,String... referencedIds) throws RaplaException;

    @PostExchange("/merge")
    Promise<UpdateEvent> doMerge(MergeRequest job, @RequestParam(value = "lastSynched", required = false) String lastSyncedTime);

    class MergeRequest
    {
        private AllocatableImpl allocatable;
        private String[] allocatableIds;


        public MergeRequest()
        {
        }

        public MergeRequest(AllocatableImpl allocatable, String[] allocatableIds)
        {
            super();
            this.allocatable = allocatable;
            this.allocatableIds = allocatableIds;
        }

        public AllocatableImpl getAllocatable()
        {
            return allocatable;
        }

        public String[] getAllocatableIds()
        {
            return allocatableIds;
        }
    }

    class BindingMap
    {
        Map<String, List<String>> bindings;

        BindingMap()
        {
        }

        public BindingMap(Map<String, List<String>> bindings)
        {
            this.bindings = bindings;
        }

        public Map<String, List<String>> get()
        {
            return bindings;
        }
    }



}
