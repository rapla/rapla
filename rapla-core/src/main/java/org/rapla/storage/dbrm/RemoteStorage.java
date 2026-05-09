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
import java.util.List;
import java.util.Map;

import java.time.LocalDateTime;
@HttpExchange("/storage")
public interface RemoteStorage
{
    String USER_WAS_NOT_AUTHENTIFIED = "User was not authentified";

    @GetExchange("/change/canchangepassword")
    boolean canChangePassword() throws RaplaException;

    @PostExchange("/change/password")
    void changePassword(@RequestBody PasswordPost job) throws RaplaException;

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

    @GetExchange("/resources")
    UpdateEvent getResources() throws RaplaException;

    /** delegates the corresponding method in the StorageOperator. */
    @PostExchange("/queryAppointments")
    AppointmentMap queryAppointments(@RequestBody QueryAppointments job) throws RaplaException;

    class QueryAppointments
    {
        private String[] ownerIds;
        private String[] resources;
        private LocalDateTime start;
        private LocalDateTime end;
        private Map<String, String> annotations;
        private boolean requestsOnly = false;

        public QueryAppointments(String[] ownerIds, String[] resources, LocalDateTime start, LocalDateTime end, Map<String, String> annotations, boolean requestsOnly)
        {
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

        public LocalDateTime getStart()
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

        public LocalDateTime getEnd()
        {
            return end;
        }

        public Map<String, String> getAnnotations()
        {
            return annotations;
        }
    }

    @PostExchange("/entity/recursiveSync")
    UpdateEvent getEntityRecursive(@RequestParam(value = "errorIfNotFound", required = false)Boolean errorIfNotFound, @RequestBody UpdateEvent.SerializableReferenceInfo[] infos) throws RaplaException;

    @PostExchange("/entity/dependent")
    UpdateEvent getEntityDependencies(@RequestParam(value = "errorIfNotFound", required = false)Boolean errorIfNotFound, @RequestBody UpdateEvent.SerializableReferenceInfo[] infos) throws RaplaException;

    @PostExchange("/refreshAllEvents")
    UpdateEvent refreshAllEvents(@RequestParam(value = "lastValidated", required = false) String lastSyncedTime) throws RaplaException;

    @PostExchange("/refresh")
    UpdateEvent refresh(@RequestParam(value = "lastValidated", required = false) String lastValidated) throws RaplaException;

    @PostExchange("/restart")
    void restartServer() throws RaplaException;

    @PostExchange("/dispatch")
    UpdateEvent dispatch(@RequestBody UpdateEvent event) throws RaplaException;

    @PostExchange("/identifier")
    List<String> createIdentifier(@RequestParam(value = "raplaType", required = false) String raplaType, @RequestParam(value = "count", required = false) int count) throws RaplaException;

    @GetExchange("/conflicts")
    List<ConflictImpl> getConflicts() throws RaplaException;

    @PostExchange("/allocatable/bindings/first")
    BindingMap getFirstAllocatableBindings(@RequestBody AllocatableBindingsRequest job) throws RaplaException;

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
    List<ReservationImpl> getAllAllocatableBindings(@RequestBody AllocatableBindingsRequest job) throws RaplaException;

    @PostExchange("/allocatable/date/next")
    LocalDateTime getNextAllocatableDate(@RequestBody NextAllocatableDateRequest job) throws RaplaException;

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
    UpdateEvent doMerge(@RequestBody MergeRequest job, @RequestParam(value = "lastSynched", required = false) String lastSyncedTime) throws RaplaException;

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
