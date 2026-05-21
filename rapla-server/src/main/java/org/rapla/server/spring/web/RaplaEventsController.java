package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentMapping;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.rest.RaplaEventsService;
import org.rapla.server.RemoteSession;
import org.rapla.server.internal.SecurityManager;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.PermissionController;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@ConditionalOnBean(RemoteSession.class)
public class RaplaEventsController implements RaplaEventsService
{
    private static final Collection<String> CLASSIFICATION_TYPES =
            Arrays.asList(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);

    private final RaplaFacade facade;
    private final RemoteSession session;
    private final SecurityManager securityManager;
    private final CachableStorageOperator operator;
    private final org.rapla.storage.SyncStorageOperator syncOperator;
    private final HttpServletRequest request;

    public RaplaEventsController(RaplaFacade facade,
                                 RemoteSession session,
                                 SecurityManager securityManager,
                                 CachableStorageOperator operator,
                                 org.rapla.storage.SyncStorageOperator syncOperator,
                                 HttpServletRequest request)
    {
        this.facade = facade;
        this.session = session;
        this.securityManager = securityManager;
        this.operator = operator;
        this.syncOperator = syncOperator;
        this.request = request;
    }

    @Override
    public List<ReservationImpl> list(LocalDateTime start, LocalDateTime end,
                                       List<String> resources, List<String> owners,
                                       List<String> eventTypes,
                                       Map<String, String> attributeFilter) throws Exception
    {
        final User user = session.checkAndGetUser(request);
        if (resources == null) resources = Collections.emptyList();
        Collection<Allocatable> allocatables = new ArrayList<>();
        for (String id : resources)
        {
            allocatables.add(facade.resolve(new ReferenceInfo<>(id, Allocatable.class)));
        }

        Collection<User> ownerEntities = new ArrayList<>();
        if (owners != null)
        {
            for (String id : owners)
            {
                ownerEntities.add(facade.resolve(new ReferenceInfo<>(id, User.class)));
            }
        }

        final ClassificationFilter[] filters = ClassificationFilterUtil.getClassificationFilter(
                facade, attributeFilter, CLASSIFICATION_TYPES, eventTypes);
        final Map<String, String> annotationQuery = null;
        final User ownerForQuery = null;
        final AppointmentMapping appMap = syncOperator.queryAppointmentsSync(
                ownerForQuery, allocatables, ownerEntities, start, end, filters, annotationQuery, false);
        final List<ReservationImpl> result = new ArrayList<>();
        final Collection<Reservation> reservations = appMap.getAllReservations();
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

    @Override
    public ReservationImpl get(String id) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        final StorageOperator op = facade.getOperator();
        ReservationImpl event = (ReservationImpl) op.resolve(id, Reservation.class);
        securityManager.checkRead(user, event);
        return event;
    }

    @Override
    public ReservationImpl patch(String id, ReservationImpl event) throws Exception
    {
        final User user = session.checkAndGetUser(request);
        setResolver(event);
        securityManager.checkWritePermissions(user, event);
        facade.store(event);
        return facade.getPersistent(event);
    }

    @Override
    public ReservationImpl update(ReservationImpl event) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        setResolver(event);
        securityManager.checkWritePermissions(user, event);
        facade.store(event);
        return facade.getPersistent(event);
    }

    @Override
    public boolean delete(String id) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        final Reservation event = facade.tryResolve(new ReferenceInfo<>(id, Reservation.class));
        if (event == null)
        {
            return false;
        }
        securityManager.checkDeletePermissions(user, event);
        facade.remove(event);
        return true;
    }

    @Override
    public ReservationImpl create(ReservationImpl event) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        setResolver(event);
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
        List<ReferenceInfo<Appointment>> appointmentIds = operator.createIdentifier(Appointment.class, appointments.length);
        for (int i = 0; i < appointments.length; i++)
        {
            AppointmentImpl app = (AppointmentImpl) appointments[i];
            app.setId(appointmentIds.get(i).getId());
        }
        event.setOwner(user);
        facade.storeAndRemove(new Entity[] { event }, Entity.ENTITY_ARRAY, user);
        return facade.getPersistent(event);
    }

    private void setResolver(ReservationImpl event)
    {
        event.setResolver(facade.getOperator());
    }
}
