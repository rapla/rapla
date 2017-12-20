package org.rapla.client.swing;

import org.rapla.client.CalendarPlacePresenter;
import org.rapla.client.event.AbstractActivityController;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@DefaultImplementation(context=InjectionContext.swing, of=AbstractActivityController.class)
public class SwingActivityController extends AbstractActivityController
{

    public static final String MERGE_ALLOCATABLES = "merge";


    //    private final EditController editController;
//    private final ClientFacade facade;
//    private final CalendarSelectionModel model;
//    private final MergeController mergeController;

    @Inject
    public SwingActivityController(ApplicationEventBus eventBus, Logger logger)
    {
        super(eventBus, logger);
    }


    @Override protected boolean isPlace(ApplicationEvent activity)
    {
        final String applicationEventId = activity.getApplicationEventId();
        switch(applicationEventId)
        {
            case CalendarPlacePresenter.PLACE_ID:
                return true;
        }
        return false;
    }

    @Override protected void parsePlaceAndActivities() throws RaplaException
    {
        activities.add(new ApplicationEvent("cal", "Standard", null, null));
    }

    @Override protected void updateHistroryEntry()
    {

    }



}
