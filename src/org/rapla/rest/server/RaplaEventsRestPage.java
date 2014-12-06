package org.rapla.rest.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.jws.WebParam;
import javax.jws.WebService;

import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.servletpages.RaplaPageGenerator;
import org.rapla.storage.RaplaSecurityException;


@WebService
public class RaplaEventsRestPage extends AbstractRestPage implements RaplaPageGenerator
{
    public RaplaEventsRestPage(RaplaContext context) throws RaplaException 
    {
        super(context);
    }
    
    private Collection<String> CLASSIFICATION_TYPES = Arrays.asList(new String[] {DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION});

    
    public List<ReservationImpl> list(@WebParam(name="user") User user, @WebParam(name="start")Date start, @WebParam(name="end")Date end, @WebParam(name="resources") List<String> resources, @WebParam(name="eventTypes") List<String> eventTypes,@WebParam(name="attributeFilter") Map<String,String> simpleFilter ) throws RaplaException
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
        Collection<Reservation> reservations = operator.getReservations(owner, allocatables, start, end, filters, annotationQuery);
        List<ReservationImpl> result = new ArrayList<ReservationImpl>();
        for ( Reservation r:reservations)
        {
            EntityResolver entityResolver = getEntityResolver();
            if ( canRead(r, user, entityResolver ))
            {
                result.add((ReservationImpl) r);
            }
        }
        return result;
    }
    
    public ReservationImpl get(@WebParam(name="user") User user, @WebParam(name="id")String id) throws RaplaException
    {
        ReservationImpl event = (ReservationImpl) operator.resolve(id, Reservation.class);
        if (!canRead(event, user, getEntityResolver() ))
        {
            throw new RaplaSecurityException("User " + user + " can't read event " + event);
        }
        return event;
    }
    
    public ReservationImpl update(@WebParam(name="user") User user, ReservationImpl event) throws RaplaException
    {
        if (!canModify(event, user, getEntityResolver()))
        {
            throw new RaplaSecurityException("User " + user + " can't modify event " + event);
        }
        event.setResolver( operator);
        getModification().store( event);
        ReservationImpl result =(ReservationImpl) getModification().getPersistant( event);
        return result;
    }
    
    public ReservationImpl create(@WebParam(name="user") User user, ReservationImpl event) throws RaplaException
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
