package org.rapla.client;

import java.util.Arrays;
import java.util.Collection;

import javax.inject.Inject;

import org.rapla.client.ActivityManager.Place;
import org.rapla.client.ResourceSelectionView.Presenter;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.Extension;

@Extension(provides = PlacePresenter.class)
public class ResourceSelectionPlace<W> implements Presenter, PlacePresenter
{
    public static final String PLACE_NAME = "ResSel";
    private final ResourceSelectionView<W> view;
    private final CalendarSelectionModel model;
    private final ClientFacade facade;
    private final Logger logger;

    @SuppressWarnings("unchecked")
    @Inject
    public ResourceSelectionPlace(@SuppressWarnings("rawtypes") ResourceSelectionView view, CalendarSelectionModel model, ClientFacade facade, Logger logger)
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
        updateView();
        return view.provideContent();
    }

    @Override
    public void updateView()
    {
        try
        {
            Allocatable[] allocatables = facade.getAllocatables();
            Allocatable[] entries = allocatables;
            Collection<Allocatable> selectedAllocatables = Arrays.asList(model.getSelectedAllocatables());
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
    public boolean isResposibleFor(Place place)
    {
        if (PLACE_NAME.equals(place.getName()))
        {
            return true;
        }
        return false;
    }

}
