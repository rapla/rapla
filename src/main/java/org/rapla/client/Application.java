package org.rapla.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.rapla.client.ActivityManager.Place;
import org.rapla.client.edit.reservation.ReservationController;
import org.rapla.client.event.DetailSelectEvent;
import org.rapla.components.i18n.BundleManager;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;

import com.google.web.bindery.event.shared.EventBus;

@Singleton
public class Application<W> implements ApplicationView.Presenter
{

    @Inject
    Logger logger;

    @Inject
    BundleManager bundleManager;
    @Inject
    ClientFacade facade;
    @Inject
    private Provider<ReservationController> controller;
    @Inject
    private Provider<ActivityManager> activityManager;
    private ApplicationView<W> mainView;
    private PlacePresenter actualPlacePresenter;
    private List<PlacePresenter> placePresenters;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Inject
    public Application(ApplicationView mainView, EventBus eventBus)
    {
        this.mainView = mainView;
        mainView.setPresenter(this);
    }

    @Inject
    private void setPlaces(Set<PlacePresenter> placePresenters)
    {
        this.placePresenters = new ArrayList<PlacePresenter>(placePresenters);
        this.actualPlacePresenter = this.placePresenters.get(0);
    }

    public void start()
    {
        try
        {
            ActivityManager am = activityManager.get();
            am.init();
            Place place = am.getPlace();
            if(place != null)
            {
                for (PlacePresenter placePresenter : placePresenters)
                {
                    if(placePresenter.isResposibleFor(place))
                    {
                        this.actualPlacePresenter = placePresenter;
                        break;
                    }
                }
            }
            mainView.setLoggedInUser(facade.getUser().getName(bundleManager.getLocale()));
            mainView.updateMenu();
            // Test for the resources
            mainView.updateContent((W) actualPlacePresenter.provideContent());
            facade.addModificationListener(new ModificationListener()
            {

                @Override
                public void dataChanged(ModificationEvent evt) throws RaplaException
                {
                    actualPlacePresenter.updateView();
                }
            });
            ((FacadeImpl) facade).setCachingEnabled(false);
        }
        catch (RaplaException e)
        {
            logger.error(e.getMessage(), e);
        }
    }

    public void detailsRequested(DetailSelectEvent e)
    {
        final Entity<?> selectedObject = e.getSelectedObject();
        if (selectedObject == null || !(selectedObject instanceof Reservation))
        {
            logger.error("Should not happen");
            return;
        }
        logger.info("Editing Object: " + selectedObject.getId());
        // edit an existing reservation
        try
        {
            final Reservation event = (Reservation) selectedObject;
            final boolean readOnly = event.isReadOnly();
            Reservation editableEvent = event.isReadOnly() ? facade.edit(event) : event;
            ReservationController reservationController = controller.get();
            final boolean isNew = !readOnly;
            reservationController.edit(editableEvent, isNew);
        }
        catch (RaplaException e1)
        {
            logger.error(e1.getMessage(), e1);
        }
    }

    //    @Override
    //    public void addClicked()
    //    {
    //        logger.info("Add clicked");
    //        try
    //        {
    //            Reservation newEvent = facade.newReservation();
    //            final Date selectedDate = facade.today();
    //            final Date time = new Date(DateTools.MILLISECONDS_PER_MINUTE * calendarOptions.getWorktimeStartMinutes());
    //            final Date startDate = raplaLocale.toDate(selectedDate, time);
    //            final Classification classification = newEvent.getClassification();
    //            final Attribute first = classification.getType().getAttributes()[0];
    //            classification.setValue(first, "Test");
    //
    //            final Date endDate = new Date(startDate.getTime() + DateTools.MILLISECONDS_PER_HOUR);
    //            final Appointment newAppointment = facade.newAppointment(startDate, endDate);
    //            newEvent.addAppointment(newAppointment);
    //            final Allocatable[] resources = facade.getAllocatables();
    //            newEvent.addAllocatable(resources[0]);
    //            eventBus.fireEvent(new DetailSelectEvent(newEvent, null));
    //        }
    //        catch (RaplaException e1)
    //        {
    //            logger.error(e1.getMessage(), e1);
    //        }
    //    }
    //
}
