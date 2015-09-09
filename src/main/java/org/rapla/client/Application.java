package org.rapla.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.rapla.client.ActivityManager.Activity;
import org.rapla.client.ActivityManager.Place;
import org.rapla.client.edit.reservation.ReservationController;
import org.rapla.client.event.PlaceChangedEvent;
import org.rapla.components.i18n.BundleManager;
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
    private Set<ActivityPresenter> activityPresenters;

    private final EventBus eventBus;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Inject
    public Application(ApplicationView mainView, EventBus eventBus)
    {
        this.mainView = mainView;
        this.eventBus = eventBus;
        mainView.setPresenter(this);
    }

    @Inject
    private void setActivities(Set<ActivityPresenter> activityPresenters)
    {
        this.activityPresenters = activityPresenters;
    }

    @Inject
    private void setPlaces(Set<PlacePresenter> placePresenters)
    {
        this.placePresenters = new ArrayList<PlacePresenter>(placePresenters);
    }

    public void start()
    {
        try
        {
            ActivityManager am = activityManager.get();
            am.init();
            mainView.setLoggedInUser(facade.getUser().getName(bundleManager.getLocale()));
            mainView.updateMenu();
            // Test for the resources
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

    public void selectPlace(Place place)
    {
        if (place != null)
        {
            for (PlacePresenter placePresenter : placePresenters)
            {
                if (placePresenter.isResposibleFor(place))
                {
                    this.actualPlacePresenter = placePresenter;
                    mainView.updateContent((W) actualPlacePresenter.provideContent());
                    break;
                }
            }
        }
        else
        {
            actualPlacePresenter = placePresenters.get(0);
            actualPlacePresenter.resetPlace();
            mainView.updateContent((W) actualPlacePresenter.provideContent());
        }
    }

    @Override
    public void menuClicked(String action)
    {
        if ("resources".equals(action))
        {
            eventBus.fireEvent(new PlaceChangedEvent(new Place(ResourceSelectionPlace.PLACE_NAME, null)));
        }
    }

    public boolean startActivity(Activity activity)
    {
        if (activity != null)
        {
            for (ActivityPresenter activityPresenter : activityPresenters)
            {
                if (activityPresenter.startActivity(activity))
                {
                    return true;
                }
            }
        }
        return false;
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
