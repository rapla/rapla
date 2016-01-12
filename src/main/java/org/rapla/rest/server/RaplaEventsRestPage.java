package org.rapla.rest.server;

import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.jsonrpc.common.RemoteJsonMethod;
import org.rapla.server.RemoteSession;
import org.rapla.storage.PermissionController;
import org.rapla.storage.RaplaSecurityException;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;


@Path("events")
@RemoteJsonMethod
public class RaplaEventsRestPage extends AbstractRestPage 
{

    private final User user;

    @Inject
    public RaplaEventsRestPage(ClientFacade facade,   RemoteSession session) throws RaplaException {
		super(facade);
        user = session.getUser();
	}

	private Collection<String> CLASSIFICATION_TYPES = Arrays.asList(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);

	@GET
    public List<ReservationImpl> list( @QueryParam("start")Date start, @QueryParam("end")Date end, @QueryParam("resources") List<String> resources, @QueryParam("eventTypes") List<String> eventTypes,@QueryParam("attributeFilter") Map<String,String> simpleFilter ) throws Exception
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
        PermissionController permissionController = facade.getPermissionController();
        for ( Reservation r:reservations)
        {
            if ( permissionController.canRead(r, user))
            {
                result.add((ReservationImpl) r);
            }
        }
        return result;
    }
    
	@GET
    @Path("{id}")
	public ReservationImpl get( @PathParam("id") String id) throws RaplaException
    {
        ReservationImpl event = (ReservationImpl) operator.resolve(id, Reservation.class);
        PermissionController permissionController = facade.getPermissionController();
        if (!permissionController.canRead(event, user))
        {
            throw new RaplaSecurityException("User " + user + " can't read event " + event);
        }
        return event;
    }
    
	@PUT
    public ReservationImpl update( ReservationImpl event) throws RaplaException
    {
        PermissionController permissionController = facade.getPermissionController();
        if (!permissionController.canModify(event, user))
        {
            throw new RaplaSecurityException("User " + user + " can't modify event " + event);
        }
        event.setResolver( operator);
        getModification().store( event);
        ReservationImpl result = getModification().getPersistant( event);
        return result;
    }
    
    @POST
    public ReservationImpl create( ReservationImpl event) throws RaplaException
    {
        event.setResolver( operator);
        if (!facade.getPermissionController().canCreate(event.getClassification().getType(), user))
        {
            throw new RaplaSecurityException("User " + user + " can't modify event " + event);
        }
        if (event.getId() != null)
        {
            throw new RaplaException("Id has to be null for new events");
        }
        String eventId = operator.createIdentifier(Reservation.class, 1)[0];
        event.setId( eventId);
        event.setCreateDate( operator.getCurrentTimestamp());
        Appointment[] appointments = event.getAppointments();
        String[] appointmentIds = operator.createIdentifier(Appointment.class, 1);
        for ( int i=0;i<appointments.length;i++)
        {
            AppointmentImpl app = (AppointmentImpl)appointments[i];
            String id = appointmentIds[i];
            app.setId(id);
        }
        event.setOwner( user );
        getModification().store( event);
        ReservationImpl result = getModification().getPersistant( event);
        return result;
    }
   
}
