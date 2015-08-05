package org.rapla.client.edit.reservation.sample;

import java.util.ArrayList;
import java.util.Collection;

import javax.inject.Inject;

import org.rapla.client.edit.reservation.ReservationController;
import org.rapla.client.edit.reservation.sample.ReservationView.Presenter;
import org.rapla.client.event.DetailEndEvent;
import org.rapla.entities.User;
import org.rapla.entities.domain.PermissionContainer;
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

    Reservation event;
    boolean isNew;

    @Override
    public void edit(final Reservation event, boolean isNew)
    {
        try
        {
            tempReservation = facade.edit(event);
            this.isNew = isNew;
            User user = facade.getUser();
            view.show(event, user);
            this.event = event;
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
        eventBus.fireEvent(new DetailEndEvent(event));
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
            try
            {
                final User user = facade.getUser();
                view.show(tempReservation, user);
            }
            catch (RaplaException e)
            {
                logger.error(e.getMessage(), e);
            }
        }
    }

    private boolean isAllowedToWrite(Attribute attribute, final Classification classification)
    {
        final String id = attribute.getKey();
        final Attribute localAttribute = classification.getAttribute(id);
        return localAttribute != null ? !localAttribute.isReadOnly() : false;
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
            try
            {
                User user = facade.getUser();
                view.show(tempReservation, user);
            }
            catch (RaplaException e)
            {
                logger.error(e.getMessage(), e);
            }
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
}
