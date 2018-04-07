package org.rapla.rest.server;

import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaException;
import org.rapla.rest.PATCH;
import org.rapla.scheduler.Promise;
import org.rapla.server.PromiseWait;
import org.rapla.server.RemoteSession;
import org.rapla.server.internal.SecurityManager;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.PermissionController;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Path("events") public class RaplaEventsRestPage
{
    @Inject RaplaFacade facade;
    @Inject RemoteSession session;
    @Inject SecurityManager securityManager;
    private final HttpServletRequest request;
    @Inject CachableStorageOperator operator;
    @Inject PromiseWait promiseWait;

    @Inject public RaplaEventsRestPage(@Context HttpServletRequest request)
    {
        this.request = request;
    }

    private Collection<String> CLASSIFICATION_TYPES = Arrays.asList(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);

    @GET @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML }) public List<ReservationImpl> list(@QueryParam("start") Date start,
            @QueryParam("end") Date end, @QueryParam("resources") List<String> resources, @QueryParam("eventTypes") Collection<String> eventTypes,
            @QueryParam("attributeFilter") Map<String, String> simpleFilter) throws Exception
    {
        final User user = session.checkAndGetUser(request);
        Collection<Allocatable> allocatables = new ArrayList<>();
        for (String id : resources)
        {
            Allocatable allocatable = facade.resolve(new ReferenceInfo<Allocatable>(id, Allocatable.class));
            allocatables.add(allocatable);
        }

        final ClassificationFilter[] filters = RaplaResourcesRestPage.getClassificationFilter(facade, simpleFilter, CLASSIFICATION_TYPES, eventTypes);
        final Map<String, String> annotationQuery = null;
        final User owner = null;
        final Promise<Map<Allocatable, Collection<Appointment>>> promise = operator
                .queryAppointments(owner, allocatables, start, end, filters, annotationQuery);
        final Map<Allocatable, Collection<Appointment>> appMap = promiseWait.waitForWithRaplaException(promise, 20000);
        final List<ReservationImpl> result = new ArrayList<>();
        final Collection<Reservation> reservations = CalendarModelImpl.getAllReservations(appMap);
        PermissionController permissionController = facade.getPermissionController();
        for (Reservation r : reservations)
        {
            if (permissionController.canRead(r, user))
            {
                result.add((ReservationImpl) r);
            }
        }
        return result;
    }

    @GET @Path("{id}") @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML }) public ReservationImpl get(@PathParam("id") String id)
            throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        final StorageOperator operator = facade.getOperator();
        ReservationImpl event = (ReservationImpl) operator.resolve(id, Reservation.class);
        securityManager.checkRead(user, event);
        return event;
    }

    @PATCH @Path("{id}") @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML }) public ReservationImpl patch(@PathParam("id") String id,ReservationImpl event) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        setResolver(event);
        securityManager.checkWritePermissions(user, event);
        facade.store(event);
        ReservationImpl result = facade.getPersistant(event);
        return result;
    }

    protected void setResolver(ReservationImpl event)
    {
        final StorageOperator operator = facade.getOperator();
        event.setResolver(operator);
    }

    @PUT @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML }) public ReservationImpl update(ReservationImpl event) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        setResolver(event);
        securityManager.checkWritePermissions(user, event);
        facade.store(event);
        ReservationImpl result = facade.getPersistant(event);
        return result;
    }

    @DELETE @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML }) public boolean delete(@PathParam("id") String id) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        final Reservation event = facade.tryResolve(new ReferenceInfo<Reservation>(id, Reservation.class));
        if ( event == null)
        {
            return false;
        }
        securityManager.checkDeletePermissions(user, event);
        facade.remove(event);
        return true;
    }

    @POST @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML }) public ReservationImpl create(ReservationImpl event) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        setResolver( event);
        if (!facade.getPermissionController().canCreate(event.getClassification().getType(), user))
        {
            throw new RaplaSecurityException("User " + user + " can't modify event " + event);
        }
        if (event.getId() != null)
        {
            throw new RaplaException("Id has to be null for new events");
        }
        ReferenceInfo<Reservation> eventId = operator.createIdentifier(Reservation.class, 1).get(0);
        event.setId(eventId.getId());
        Appointment[] appointments = event.getAppointments();
        List<ReferenceInfo<Appointment>> appointmentIds = operator.createIdentifier(Appointment.class, 1);
        for (int i = 0; i < appointments.length; i++)
        {
            AppointmentImpl app = (AppointmentImpl) appointments[i];
            String id = appointmentIds.get(i).getId();
            app.setId(id);
        }
        event.setOwner(user);
        facade.storeAndRemove(new Entity[] { event }, Entity.ENTITY_ARRAY, user);
        ReservationImpl result = facade.getPersistant(event);
        return result;
    }

}
