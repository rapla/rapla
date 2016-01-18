package org.rapla.client.edit.reservation.sample;

import com.google.web.bindery.event.shared.EventBus;
import org.rapla.client.edit.reservation.ReservationController;
import org.rapla.client.edit.reservation.sample.ReservationView.Presenter;
import org.rapla.client.event.StopActivityEvent;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

public class ReservationPresenter implements ReservationController, Presenter
{
    public static final String EDIT_ACTIVITY_ID = "edit";

    private final RaplaFacade facade;
    private final Logger logger;
    private final RaplaLocale raplaLocale;
    private final EventBus eventBus;
    private final ReservationView view;

    private Reservation editReservation;
    private Appointment selectedAppointment;
    private boolean isNew;
    private final PermissionController permissionController;
    ClientFacade clientFacade;

    @Inject
    protected ReservationPresenter(RaplaFacade facade, ClientFacade clientFacade,Logger logger, RaplaLocale raplaLocale, EventBus eventBus, ReservationView view)
    {
        this.facade = facade;
        this.clientFacade = clientFacade;
        this.permissionController = facade.getPermissionController();
        this.logger = logger;
        this.raplaLocale = raplaLocale;
        this.eventBus = eventBus;
        this.view = view;
        view.setPresenter(this);
    }

    @Override
    public boolean isVisible()
    {
        return view.isVisible();
    }

    @Override public void edit(final Reservation event, boolean isNew)
    {
        try
        {
            this.isNew = isNew;
            editReservation = facade.edit(event);
            selectedAppointment = editReservation.getAppointments().length > 0 ? editReservation.getAppointments()[0] : null;
            view.show(event);
        }
        catch (RaplaException e)
        {
            logger.error(e.getMessage(), e);
        }
    }

    @Override public void onSaveButtonClicked()
    {
        logger.info("save clicked");
        try
        {
            facade.store(editReservation);
        }
        catch (RaplaException e1)
        {
            logger.error(e1.getMessage(), e1);
        }
        fireEventAndCloseView();
    }

    @Override public void onDeleteButtonClicked()
    {
        logger.info("delete clicked");
        try
        {
            facade.remove(editReservation);
        }
        catch (RaplaException e1)
        {
            logger.error(e1.getMessage(), e1);
        }
        fireEventAndCloseView();
    }

    @Override public void onCancelButtonClicked()
    {
        logger.info("cancel clicked");
        fireEventAndCloseView();
    }

    private void fireEventAndCloseView()
    {
        eventBus.fireEvent(new StopActivityEvent(EDIT_ACTIVITY_ID, editReservation.getId()));
        view.hide();
    }

    @Override public void changeAttribute(Attribute attribute, Object newValue)
    {
        final Classification classification = editReservation.getClassification();
        if (isAllowedToWrite(attribute, classification))
        {
            classification.setValue(attribute, newValue);
        }
        else
        {
            view.showWarning("Not allowed!", "Editing value for " + attribute.getName(raplaLocale.getLocale()));
        }
    }

    private boolean isAllowedToWrite(Attribute attribute, final Classification classification)
    {
        // TODO future
        return true;
    }

    @Override public boolean isDeleteButtonEnabled()
    {
        return isNew;
    }

    @Override public void changeClassification(DynamicType newDynamicType)
    {
        if (newDynamicType != null)
        {
            final Classification newClassification = newDynamicType.newClassification();
            editReservation.setClassification(newClassification);
            view.show(editReservation);
        }
    }

    @Override public Collection<DynamicType> getChangeableReservationDynamicTypes()
    {
        final Collection<DynamicType> creatableTypes = new ArrayList<DynamicType>();
        try
        {
            final DynamicType[] types = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
            final User user = clientFacade.getUser();
            for (DynamicType type : types)
            {
                if (permissionController.canCreate(type, user))
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

    @Override public void newDateClicked()
    {
        Date startDate = new Date();
        Date endDate = new Date();
        try
        {
            Appointment newAppointment = facade.newAppointment(startDate, endDate);
            editReservation.addAppointment(newAppointment);
            this.selectedAppointment = newAppointment;
            view.updateAppointments(newAppointment);
        }
        catch (RaplaException e)
        {
            logger.error("Error creating new appointment: " + e.getMessage(), e);
        }
    }

    @Override public void selectAppointment(Appointment selectedAppointment)
    {
        this.selectedAppointment = selectedAppointment;
        view.updateAppointments(selectedAppointment);
    }

    @Override public void deleteDateClicked()
    {
        editReservation.removeAppointment(selectedAppointment);
        Appointment[] allAppointments = editReservation.getAppointments();
        Appointment newSelectedAppointment = allAppointments.length > 0 ? allAppointments[0] : null;
        selectedAppointment = newSelectedAppointment;
        view.updateAppointments(newSelectedAppointment);
    }

    @Override public void timeChanged(Date startDate, Date endDate)
    {
        if (selectedAppointment != null)
        {
            selectedAppointment.move(startDate, endDate);
        }
    }

    @Override public void allDayEvent(boolean wholeDays)
    {
        if (selectedAppointment != null)
        {
            selectedAppointment.setWholeDays(wholeDays);
            view.updateAppointments(selectedAppointment);
        }
    }

    @Override public void repeating(RepeatingType repeating)
    {
        if (selectedAppointment != null)
        {
            selectedAppointment.setRepeatingEnabled(repeating != null);
            selectedAppointment.getRepeating().setType(repeating);
        }
    }

    @Override public void convertAppointment()
    {
        // TODO implement me
    }

}
