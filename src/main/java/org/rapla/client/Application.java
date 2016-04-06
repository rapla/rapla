package org.rapla.client;

import com.google.web.bindery.event.shared.EventBus;
import org.rapla.client.event.AbstractActivityController;
import org.rapla.client.event.AbstractActivityController.Place;
import org.rapla.client.event.PlaceChangedEvent;
import org.rapla.client.gwt.view.RaplaPopups;
import org.rapla.components.i18n.BundleManager;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Application implements ApplicationView.Presenter
{

    private final Logger logger;
    private final BundleManager bundleManager;
    private final ClientFacade facade;
    private final AbstractActivityController abstractActivityController;
    private final ApplicationView mainView;

    private final EventBus eventBus;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Inject
    public Application(final ApplicationView mainView,EventBus eventBus, Logger logger, BundleManager bundleManager, ClientFacade facade, AbstractActivityController abstractActivityController)
    {
        this.mainView = mainView;
        this.abstractActivityController = abstractActivityController;
        this.bundleManager = bundleManager;
        this.facade = facade;
        this.logger = logger;
        this.eventBus = eventBus;
        mainView.setPresenter(this);
    }

    public void start()
    {
        try
        {
            AbstractActivityController am = abstractActivityController;
            am.init();
            mainView.setLoggedInUser(facade.getUser().getName(bundleManager.getLocale()));
            mainView.updateMenu();
            // Test for the resources
            facade.addModificationListener(new ModificationListener()
            {

                @Override
                public void dataChanged(ModificationEvent evt) throws RaplaException
                {
                    abstractActivityController.updateView(e);
                    // TODO inform activities?
                }
            });
            RaplaPopups.getProgressBar().setPercent(100);
        }
        catch (RaplaException e)
        {
            logger.error(e.getMessage(), e);
        }
    }



    @Override
    public void menuClicked(String action)
    {
        if ("resources".equals(action))
        {
            eventBus.fireEvent(new PlaceChangedEvent(new Place(ResourceSelectionPlace.PLACE_ID, null)));
        }
    }


}
