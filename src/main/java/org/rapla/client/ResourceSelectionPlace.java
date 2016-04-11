package org.rapla.client;

import java.util.Collection;

import javax.inject.Inject;

import org.rapla.client.ResourceSelectionView.Presenter;
import org.rapla.client.event.AbstractActivityController.Place;
import org.rapla.client.event.PlacePresenter;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.Extension;

@Extension(provides = PlacePresenter.class, id = ResourceSelectionPlace.PLACE_ID)
public class ResourceSelectionPlace implements Presenter, PlacePresenter
{
    public static final String PLACE_ID = "ResSel";
    private final ResourceSelectionView view;
    private final CalendarSelectionModel model;
    private final RaplaFacade facade;
    private final Logger logger;

    @SuppressWarnings("unchecked")
    @Inject
    public ResourceSelectionPlace(@SuppressWarnings("rawtypes") ResourceSelectionView view, CalendarSelectionModel model, RaplaFacade facade, Logger logger)
    {
        this.view = view;
        this.model = model;
        this.facade = facade;
        this.logger = logger;
        view.setPresenter(this);
    }

    @Override
    public Object provideContent()
    {
        updateView(null);
        return view.provideContent();
    }

    @Override
    public void updateView(ModificationEvent event)
    {
        try
        {
            Allocatable[] allocatables = facade.getAllocatables();
            Allocatable[] entries = allocatables;
            Collection<Allocatable> selectedAllocatables = model.getSelectedAllocatablesAsList();
            view.updateContent(entries, selectedAllocatables);
        }
        catch (RaplaException e)
        {
            logger.error("Error updating resources selection: " + e.getMessage(), e);
        }
    }

    @Override
    public void selectionChanged(Collection<Allocatable> selected)
    {
        model.setSelectedObjects(selected);
    }

    @Override
    public void resetPlace()
    {
    }

    @Override
    public void initForPlace(Place place)
    {

    }
}
