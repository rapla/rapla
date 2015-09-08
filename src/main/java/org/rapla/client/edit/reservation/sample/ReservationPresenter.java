package org.rapla.client.edit.reservation.sample;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

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

    @Inject
    private Logger logger;
    @Inject
    private RaplaLocale raplaLocale;
    @Inject
    private ClientFacade facade;

    @Inject
    private EventBus eventBus;

    private ReservationView<?> view;

    private Reservation tempReservation;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Inject
    public ReservationPresenter(ReservationView view)
    {
        this.view = view;
        view.setPresenter(this);
    }

    boolean isNew;
    private Appointment selectedAppointment;

    @Override
    public void edit(final Reservation event, boolean isNew)
    {
        try
        {
            tempReservation = facade.edit(event);
            selectedAppointment = tempReservation.getAppointments().length > 0 ? tempReservation.getAppointments()[0] : null;
            this.isNew = isNew;
            view.show(tempReservation);
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
            facade.store(tempReservation);
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
        view.hide(reservation);
    }

    @Override
    public void changeAttribute(Reservation reservation, Attribute attribute, Object newValue)
    {
        final Classification classification = tempReservation.getClassification();
        if (isAllowedToWrite(attribute, classification))
        {
            classification.setValue(attribute, newValue);
        }
        else
        {
            view.showWarning("Not allowed!", "Editing value for " + attribute.getName(raplaLocale.getLocale()));
            view.show(tempReservation);
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
        return !isNew;
    }

    @Override
    public void changeClassification(Reservation reservation, DynamicType newDynamicType)
    {
        if (newDynamicType != null)
        {
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
    public void newDateClicked()
    {
        Date startDate = new Date();
        Date endDate = new Date();
        try
        {
            Appointment newAppointment = facade.newAppointment(startDate, endDate);
            this.tempReservation.addAppointment(newAppointment);
            Appointment[] allAppointments = tempReservation.getAppointments();
            selectedAppointment = newAppointment;
            view.updateAppointments(allAppointments, newAppointment);
        }
        catch (RaplaException e)
        {
            logger.error("Error creating new appointment: " + e.getMessage(), e);
        }
    }

    @Override
    public void selectedAppointment(Appointment selectedAppointment)
    {
        this.selectedAppointment = selectedAppointment;
        Appointment[] allAppointments = tempReservation.getAppointments();
        view.updateAppointments(allAppointments, selectedAppointment);
    }

    @Override
    public void deleteDateClicked()
    {
        tempReservation.removeAppointment(selectedAppointment);
        Appointment[] allAppointments = tempReservation.getAppointments();
        Appointment newSelectedAppointment = allAppointments.length > 0 ? allAppointments[0] : null;
        selectedAppointment = newSelectedAppointment;
        view.updateAppointments(allAppointments, newSelectedAppointment);
    }
    
    @Override
    public void timeChanged(Date startDate, Date endDate)
    {
        if(selectedAppointment != null)
        {
            selectedAppointment.move(startDate, endDate);
        }
    }

    @Override
    public void allDayEvent(boolean wholeDays)
    {
        if(selectedAppointment != null)
        {
            selectedAppointment.setWholeDays(wholeDays);
            Appointment[] allAppointments = tempReservation.getAppointments();
            view.updateAppointments(allAppointments, selectedAppointment);
        }
    }

    @Override
    public void repeating(RepeatingType repeating)
    {
        if(selectedAppointment != null)
        {
            selectedAppointment.setRepeatingEnabled(repeating != null);
            selectedAppointment.getRepeating().setType(repeating);
        }
    }

    @Override
    public void convertAppointment()
    {
        if(selectedAppointment != null)
        {
            // TODO implement me
        }
    }
}
