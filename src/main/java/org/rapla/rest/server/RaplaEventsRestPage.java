package org.rapla.rest.server;

import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.domain.permission.PermissionController;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.gwtjsonrpc.RemoteJsonMethod;
import org.rapla.storage.RaplaSecurityException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.*;
import java.util.*;


@Path("events")
@Singleton
@RemoteJsonMethod
public class RaplaEventsRestPage extends AbstractRestPage 
{

	private final PermissionController permissionController;

    @Inject
    public RaplaEventsRestPage(ClientFacade facade, Logger logger, PermissionController permissionController) throws RaplaException {
		super(facade,  logger, true);
        this.permissionController = permissionController;
	}

	private Collection<String> CLASSIFICATION_TYPES = Arrays.asList(new String[] {DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION});

	@GET
    public List<ReservationImpl> list(@QueryParam("user") User user, @QueryParam("start")Date start, @QueryParam("end")Date end, @QueryParam("resources") List<String> resources, @QueryParam("eventTypes") List<String> eventTypes,@QueryParam("attributeFilter") Map<String,String> simpleFilter ) throws Exception
    {
        Collection<Allocatable> allocatables = new ArrayList<Allocatable>();
        for (String id :resources)
        {
            Allocatable allocatable = operator.resolve(id, Allocatable.class);
            allocatables.add( allocatable);
        }
 
        ClassificationFilter[] filters = getClassificationFilter(simpleFilter, CLASSIFICATION_TYPES, eventTypes);
        Map<String, String> annotationQuery = null;
        User owner = null;
        Collection<Reservation> reservations = operator.getReservations(owner, allocatables, start, end, filters, annotationQuery).get();
        List<ReservationImpl> result = new ArrayList<ReservationImpl>();
        for ( Reservation r:reservations)
        {
            EntityResolver entityResolver = getEntityResolver();
            if ( permissionController.canRead(r, user, entityResolver))
            {
                result.add((ReservationImpl) r);
            }
        }
        return result;
    }
    
	@GET
    @Path("{id}")
	public ReservationImpl get(@QueryParam("user") User user, @QueryParam("id") String id) throws RaplaException
    {
        ReservationImpl event = (ReservationImpl) operator.resolve(id, Reservation.class);
        if (!permissionController.canRead(event, user, getEntityResolver()))
        {
            throw new RaplaSecurityException("User " + user + " can't read event " + event);
        }
        return event;
    }
    
	@PUT
    public ReservationImpl update(@QueryParam("user") User user, ReservationImpl event) throws RaplaException
    {
        if (!permissionController.canModify(event, user, getEntityResolver()))
        {
            throw new RaplaSecurityException("User " + user + " can't modify event " + event);
        }
        event.setResolver( operator);
        getModification().store( event);
        ReservationImpl result =(ReservationImpl) getModification().getPersistant( event);
        return result;
    }
    
    @POST
    public ReservationImpl create(@QueryParam("user") User user, ReservationImpl event) throws RaplaException
    {
        event.setResolver( operator);
        if (!getQuery().canCreateReservations(event.getClassification().getType(), user))
        {
            throw new RaplaSecurityException("User " + user + " can't modify event " + event);
        }
        if (event.getId() != null)
        {
            throw new RaplaException("Id has to be null for new events");
        }
        String eventId = operator.createIdentifier(Reservation.TYPE, 1)[0];
        event.setId( eventId);
        event.setCreateDate( operator.getCurrentTimestamp());
        Appointment[] appointments = event.getAppointments();
        String[] appointmentIds = operator.createIdentifier(Appointment.TYPE, 1);
        for ( int i=0;i<appointments.length;i++)
        {
            AppointmentImpl app = (AppointmentImpl)appointments[i];
            String id = appointmentIds[i];
            app.setId(id);
        }
        event.setOwner( user );
        getModification().store( event);
        ReservationImpl result =(ReservationImpl) getModification().getPersistant( event);
        return result;
    }
   
}
