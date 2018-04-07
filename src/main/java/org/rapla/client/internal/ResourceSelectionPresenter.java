/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.client.internal;

import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.CalendarEventBus;
import org.rapla.client.event.CalendarRefreshEvent;
import org.rapla.client.internal.ResourceSelectionView.Presenter;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public class ResourceSelectionPresenter implements Presenter
{
    protected final CalendarSelectionModel model;

    private final EditController editController;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final CalendarEventBus eventBus;
    private final ResourceSelectionView view;
    private final ClientFacade facade;
    private final Logger logger;
    private PresenterChangeCallback callback;
    private CommandScheduler scheduler;

    @Inject
    public ResourceSelectionPresenter(ClientFacade facade, Logger logger, CalendarSelectionModel model, EditController editController,
            DialogUiFactoryInterface dialogUiFactory, CalendarEventBus eventBus, ResourceSelectionView view, CommandScheduler scheduler)
            throws RaplaInitializationException
    {
        this.facade = facade;
        this.logger = logger;
        this.view = view;
        this.model = model;
        this.eventBus = eventBus;
        this.editController = editController;
        this.dialogUiFactory = dialogUiFactory;
        this.scheduler = scheduler;
        view.setPresenter(this);

        try
        {
            updateMenu();
            ClassificationFilter[] filter = model.getAllocatableFilter();
            Collection<Object> selectedObjects = new ArrayList<>(model.getSelectedObjects());
            view.update(filter, model, selectedObjects);
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }
    }

    @Override
    public void updateFilters(ClassificationFilter[] filters) throws RaplaException
    {
        model.setAllocatableFilter(filters);
        ClassificationFilter[] filter = model.getAllocatableFilter();
        Collection<Object> selectedObjects = new ArrayList<>(model.getSelectedObjects());
        view.update(filter, model, selectedObjects);
        applyFilter();
    }

    public void setCallback(PresenterChangeCallback callback)
    {
        this.callback = callback;
    }

    private RaplaFacade getRaplaFacade()
    {
        return facade.getRaplaFacade();
    }


    @Override
    public void mouseOverResourceSelection()
    {
        try
        {
            if (!facade.isSessionActive())
            {
                return;
            }
            updateMenu();
        }
        catch (Exception ex)
        {
            PopupContext popupContext = dialogUiFactory.createPopupContext(view);
            dialogUiFactory.showException(ex, popupContext);
        }
    }

    @Override
    public void selectResource(Object focusedObject)
    {
        if (focusedObject == null || !(focusedObject instanceof RaplaObject))
            return;
        // System.out.println(focusedObject.toString());
        Class type = ((RaplaObject) focusedObject).getTypeClass();
        if (type == User.class || type == Allocatable.class)
        {
            Entity entity = (Entity) focusedObject;
            PopupContext popupContext = dialogUiFactory.createPopupContext(view);
            PermissionController permissionController = getRaplaFacade().getPermissionController();
            try
            {
                final User user = facade.getUser();
                if (permissionController.canModify(entity, user))
                {
                    editController.edit(entity, popupContext);
                }
            }
            catch (RaplaException e)
            {
                logger.error("Error getting user in resource selection: " + e.getMessage(), e);
            }
        }
    }

    public CalendarSelectionModel getModel()
    {
        return model;
    }

    public void dataChanged(ModificationEvent evt) throws RaplaException
    {
        if (evt == null || evt.isModified())
        {
            ClassificationFilter[] filter = model.getAllocatableFilter();
            Collection<Object> selectedObjects = new ArrayList<>(model.getSelectedObjects());
            view.update(filter, model, selectedObjects);
        }
        // No longer needed here as directly done in RaplaClientServiceImpl
        // ((CalendarModelImpl) model).dataChanged( evt);
    }

    boolean treeListenersEnabled = true;

    @Override
    public void updateSelectedObjects(Collection<Object> elements)
    {
        try
        {
            HashSet<Object> selectedElements = new HashSet<>(elements);
            getModel().setSelectedObjects(selectedElements);
            updateMenu();
            applyFilter();
        }
        catch (RaplaException ex)
        {
            PopupContext popupContext = dialogUiFactory.createPopupContext(view);
            dialogUiFactory.showException(ex, popupContext);
        }
    }

    @Override
    public void applyFilter()
    {
        eventBus.publish(new CalendarRefreshEvent());
    }

    public void updateMenu() throws RaplaException
    {
        Collection<?> list = getModel().getSelectedObjects();
        Object focusedObject = null;
        if (list.size() == 1)
        {
            focusedObject = list.iterator().next();
        }
        view.updateMenu(list, focusedObject);
    }

    public RaplaWidget provideContent()
    {
        return view;
    }

    @Override
    public void treeSelectionChanged()
    {
        callback.onChange();
    }

    public void closeFilterButton()
    {
        view.closeFilterButton();
    }

}
