package org.rapla.client.edit.reservation.sample;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.inject.Inject;

import org.rapla.client.edit.reservation.ReservationController;
import org.rapla.client.edit.reservation.sample.ReservationView.Presenter;
import org.rapla.client.event.DetailEndEvent;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;

import com.google.web.bindery.event.shared.EventBus;

public class ReservationPresenter implements ReservationController, Presenter
{

    private static class ReservationInfo
    {
        boolean isNew;
        private Appointment selectedAppointment;
        private Reservation editReservation;
    }

    private final Map<String, ReservationInfo> reservationMap = new LinkedHashMap<String, ReservationInfo>();

    @Inject
    private Logger logger;
    @Inject
    private RaplaLocale raplaLocale;
    @Inject
    private ClientFacade facade;

    @Inject
    private EventBus eventBus;

    private ReservationView<?> view;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Inject
    public ReservationPresenter(ReservationView view)
    {
        this.view = view;
        view.setPresenter(this);
    }

    @Override
    public void edit(final Reservation event, boolean isNew)
    {
        try
        {
            final ReservationInfo ri = new ReservationInfo();
            ri.isNew = isNew;
            ri.editReservation = facade.edit(event);
            ri.selectedAppointment = ri.editReservation.getAppointments().length > 0 ? ri.editReservation.getAppointments()[0] : null;
            reservationMap.put(event.getId(), ri);
            view.show(event);
        }
        catch (RaplaException e)
        {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void onSaveButtonClicked(Reservation reservation)
    {
        logger.info("save clicked");
        try
        {
            final ReservationInfo ri = reservationMap.get(reservation.getId());
            final Reservation editReservation = ri.editReservation;
            facade.store(editReservation);
        }
        catch (RaplaException e1)
        {
            logger.error(e1.getMessage(), e1);
        }
        fireEventAndCloseView(reservation);
    }

    @Override
    public void onDeleteButtonClicked(final Reservation reservation)
    {
        logger.info("delete clicked");
        try
        {
            final ReservationInfo ri = reservationMap.get(reservation.getId());
            final Reservation tempReservation = ri.editReservation;
            facade.remove(tempReservation);
        }
        catch (RaplaException e1)
        {
            logger.error(e1.getMessage(), e1);
        }
        fireEventAndCloseView(reservation);
    }

    @Override
    public void onCancelButtonClicked(final Reservation reservation)
    {
        logger.info("cancel clicked");
        fireEventAndCloseView(reservation);
    }

    private void fireEventAndCloseView(final Reservation reservation)
    {
        eventBus.fireEvent(new DetailEndEvent(reservation));
        reservationMap.remove(reservation.getId());
        view.hide(reservation);
    }

    @Override
    public void changeAttribute(Reservation reservation, Attribute attribute, Object newValue)
    {
        final ReservationInfo ri = reservationMap.get(reservation.getId());
        final Reservation editReservation = ri.editReservation;
        final Classification classification = editReservation.getClassification();
        if (isAllowedToWrite(attribute, classification))
        {
            classification.setValue(attribute, newValue);
        }
        else
        {
            view.showWarning("Not allowed!", "Editing value for " + attribute.getName(raplaLocale.getLocale()));
            view.show(editReservation);
        }
    }

    private boolean isAllowedToWrite(Attribute attribute, final Classification classification)
    {
        // TODO future
        return true;
    }

    @Override
    public boolean isDeleteButtonEnabled(final Reservation reservation)
    {
        final ReservationInfo ri = reservationMap.get(reservation.getId());
        return !ri.isNew;
    }

    @Override
    public void changeClassification(Reservation reservation, DynamicType newDynamicType)
    {
        if (newDynamicType != null)
        {
            final ReservationInfo ri = reservationMap.get(reservation.getId());
            final Reservation tempReservation = ri.editReservation;
            final Classification newClassification = newDynamicType.newClassification();
            tempReservation.setClassification(newClassification);
            view.show(tempReservation);
        }
    }

    @Override
    public Collection<DynamicType> getChangeableReservationDynamicTypes()
    {
        final Collection<DynamicType> creatableTypes = new ArrayList<DynamicType>();
        try
        {
            final DynamicType[] types = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
            final User user = facade.getUser();
            for (DynamicType type : types)
            {
                if (PermissionContainer.Util.canCreate(type, user))
                {
                    creatableTypes.add(type);
                }
            }
        }
        catch (RaplaException e)
        {
            logger.warn(e.getMessage(), e);
        }
        return creatableTypes;
    }

    @Override
    public void newDateClicked(Reservation reservation)
    {
        Date startDate = new Date();
        Date endDate = new Date();
        try
        {
            final ReservationInfo ri = reservationMap.get(reservation.getId());
            final Reservation tempReservation = ri.editReservation;
            Appointment newAppointment = facade.newAppointment(startDate, endDate);
            tempReservation.addAppointment(newAppointment);
            Appointment[] allAppointments = tempReservation.getAppointments();
            ri.selectedAppointment = newAppointment;
            view.updateAppointments(tempReservation, allAppointments, newAppointment);
        }
        catch (RaplaException e)
        {
            logger.error("Error creating new appointment: " + e.getMessage(), e);
        }
    }

    @Override
    public void selectAppointment(Reservation reservation, Appointment selectedAppointment)
    {
        final ReservationInfo ri = reservationMap.get(reservation.getId());
        final Reservation tempReservation = ri.editReservation;
        ri.selectedAppointment = selectedAppointment;
        Appointment[] allAppointments = tempReservation.getAppointments();
        view.updateAppointments(tempReservation, allAppointments, selectedAppointment);
    }

    @Override
    public void deleteDateClicked(Reservation reservation)
    {
        final ReservationInfo ri = reservationMap.get(reservation.getId());
        final Reservation tempReservation = ri.editReservation;
        tempReservation.removeAppointment(ri.selectedAppointment);
        Appointment[] allAppointments = tempReservation.getAppointments();
        Appointment newSelectedAppointment = allAppointments.length > 0 ? allAppointments[0] : null;
        ri.selectedAppointment = newSelectedAppointment;
        view.updateAppointments(tempReservation, allAppointments, newSelectedAppointment);
    }

    @Override
    public void timeChanged(Reservation reservation, Date startDate, Date endDate)
    {
        final ReservationInfo ri = reservationMap.get(reservation.getId());
        final Reservation tempReservation = ri.editReservation;
        if (ri.selectedAppointment != null)
        {
            ri.selectedAppointment.move(startDate, endDate);
        }
    }

    @Override
    public void allDayEvent(Reservation reservation, boolean wholeDays)
    {
        final ReservationInfo ri = reservationMap.get(reservation.getId());
        final Reservation tempReservation = ri.editReservation;
        if (ri.selectedAppointment != null)
        {
            ri.selectedAppointment.setWholeDays(wholeDays);
            Appointment[] allAppointments = tempReservation.getAppointments();
            view.updateAppointments(tempReservation, allAppointments, ri.selectedAppointment);
        }
    }

    @Override
    public void repeating(Reservation reservation, RepeatingType repeating)
    {
        final ReservationInfo ri = reservationMap.get(reservation.getId());
        if (ri.selectedAppointment != null)
        {
            ri.selectedAppointment.setRepeatingEnabled(repeating != null);
            ri.selectedAppointment.getRepeating().setType(repeating);
        }
    }

    @Override
    public void convertAppointment(Reservation reservation)
    {
        // TODO implement me
    }
}
