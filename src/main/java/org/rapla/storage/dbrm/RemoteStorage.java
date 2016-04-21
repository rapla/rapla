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

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.RaplaException;
import org.rapla.storage.UpdateEvent;

@Path("storage")
public interface RemoteStorage
{
    String USER_WAS_NOT_AUTHENTIFIED = "User was not authentified";

    @GET
    @Path("change/canchangepassword")
    boolean canChangePassword() throws RaplaException;

    @POST
    @Path("change/password")
    void changePassword(PasswordPost job) throws RaplaException;

    public static class PasswordPost
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

    @POST
    @Path("change/name")
    void changeName(@QueryParam("username") String username, @QueryParam("title") String newTitle, @QueryParam("surename") String newSurename,
            String newLastname) throws RaplaException;

    @POST
    @Path("change/email")
    void changeEmail(@QueryParam("username") String username, String newEmail) throws RaplaException;

    @POST
    @Path("confirm/email")
    void confirmEmail(@QueryParam("username") String username, String newEmail) throws RaplaException;

    @GET
    @Path("resources")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    UpdateEvent getResources() throws RaplaException;

    /** delegates the corresponding method in the StorageOperator. */
    //    FutureResult<List<ReservationImpl>> getReservations(@WebParam(name="resources")String[] allocatableIds,@WebParam(name="start")Date start,@WebParam(name="end")Date end, @WebParam(name="annotations")Map<String, String> annotationQuery);
    @POST
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    AppointmentMap queryAppointments(QueryAppointments job) throws RaplaException;

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class QueryAppointments
    {
        private String[] resources;
        private Date start;
        private Date end;
        private Map<String, String> annotations;

        public QueryAppointments(String[] resources, Date start, Date end, Map<String, String> annotations)
        {
            super();
            this.resources = resources;
            this.start = start;
            this.end = end;
            this.annotations = annotations;
        }

        public QueryAppointments()
        {
        }

        public String[] getResources()
        {
            return resources;
        }

        public Date getStart()
        {
            return start;
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

    @POST
    @Path("entity/recursive")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    UpdateEvent getEntityRecursive(UpdateEvent.SerializableReferenceInfo... infos) throws RaplaException;

    @GET
    @Path("refresh")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    UpdateEvent refresh(@QueryParam("lastSynched") String lastSyncedTime) throws RaplaException;

    @POST
    @Path("restart")
    void restartServer() throws RaplaException;

    @POST
    @Path("dispatch")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    UpdateEvent dispatch(UpdateEvent event) throws RaplaException;

    //	@ResultType(value=String.class,container=List.class)
    //	FutureResult<List<String>> getTemplateNames();

    @GET
    @Path("identifier")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    List<String> createIdentifier(@QueryParam("raplaType") String raplaType, @QueryParam("count") int count) throws RaplaException;

    @GET
    @Path("conflicts")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    List<ConflictImpl> getConflicts() throws RaplaException;

    @POST
    @Path("allocatable/bindings/first")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    BindingMap getFirstAllocatableBindings(AllocatableBindingsRequest job) throws RaplaException;

    public static class AllocatableBindingsRequest
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

    @POST
    @Path("allocatable/bindings/all")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    List<ReservationImpl> getAllAllocatableBindings(AllocatableBindingsRequest job) throws RaplaException;

    @POST
    @Path("allocatable/date/next")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    Date getNextAllocatableDate(NextAllocatableDateRequest job) throws RaplaException;

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class NextAllocatableDateRequest
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

    @GET
    @Path("user")
    @Produces({ MediaType.APPLICATION_JSON })
    String getUsername(@QueryParam("userId") String userId) throws RaplaException;

    //void logEntityNotFound(String logMessage,String... referencedIds) throws RaplaException;

    @POST
    @Path("merge")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    void doMerge(MergeRequest job) throws RaplaException;

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class MergeRequest
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

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
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
