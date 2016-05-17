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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import javax.inject.Inject;

import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.MenuContext;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.CalendarRefreshEvent;
import org.rapla.client.internal.ResourceSelectionView.Presenter;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.MenuFactory;
import org.rapla.client.swing.SwingMenuContext;
import org.rapla.client.swing.internal.FilterEditButton.FilterEditButtonFactory;
import org.rapla.client.swing.internal.MenuFactoryImpl;
import org.rapla.client.swing.internal.RaplaMenuBarContainer;
import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.client.swing.toolkit.RaplaPopupMenu;
import org.rapla.client.RaplaWidget;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.logger.Logger;
import org.rapla.storage.PermissionController;

import com.google.web.bindery.event.shared.EventBus;

public class ResourceSelectionPresenter implements Presenter
{
    protected final CalendarSelectionModel model;

    private final EditController editController;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final RaplaMenuBarContainer menuBar;
    private final EventBus eventBus;
    private final ResourceSelectionView view;
    private final ClientFacade facade;
    private final Logger logger;
    private final RaplaResources i18n;

    private final MenuFactory menuFactory;
    private PresenterChangeCallback callback;

    @Inject
    public ResourceSelectionPresenter(RaplaMenuBarContainer menuBar, ClientFacade facade, Logger logger, RaplaResources i18n, MenuFactory menuFactory,
            CalendarSelectionModel model, EditController editController, InfoFactory infoFactory, DialogUiFactoryInterface dialogUiFactory, FilterEditButtonFactory filterEditButtonFactory,
            EventBus eventBus, ResourceSelectionView view)
                    throws RaplaInitializationException
    {
        this.facade = facade;
        this.logger = logger;
        this.i18n = i18n;
        this.menuFactory = menuFactory;
        this.view = view;
        this.menuBar = menuBar;
        this.model = model;
        this.eventBus = eventBus;
        this.editController = editController;
        this.dialogUiFactory = dialogUiFactory;
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
    public void updateFilters(ClassificationFilter[] filters)
    {
        model.setAllocatableFilter(filters);
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
    public boolean moveCategory(Category categoryToMove, Category targetCategory)
    {
        try
        {
            final Collection<Category> categoriesToStore = new ArrayList<>();
            final Category categoryToMoveEdit = getRaplaFacade().edit(categoryToMove);
            final Category targetParentCategoryEdit = getRaplaFacade().edit(targetCategory.getParent());
            if (!targetParentCategoryEdit.hasCategory(categoryToMoveEdit))
            {
                // remove from old parent
                final Category moveCategoryParent = getRaplaFacade().edit(categoryToMove.getParent());
                moveCategoryParent.removeCategory(categoryToMoveEdit);
                categoriesToStore.add(moveCategoryParent);
            }
            final Collection<Category> categories = getRaplaFacade().edit(Arrays.asList(targetParentCategoryEdit.getCategories()));
            for (Category category : categories)
            {
                targetParentCategoryEdit.removeCategory(category);
            }
            for (Category category : categories)
            {
                if (category.equals(targetCategory))
                {
                    targetParentCategoryEdit.addCategory(categoryToMoveEdit);
                }
                else if (category.equals(categoryToMoveEdit))
                {
                    continue;
                }
                targetParentCategoryEdit.addCategory(category);
            }
            categoriesToStore.add(targetParentCategoryEdit);
            categoriesToStore.add(categoryToMoveEdit);
            getRaplaFacade().storeObjects(categoriesToStore.toArray(Entity.ENTITY_ARRAY));
            return true;
        }
        catch (Exception e)
        {
            dialogUiFactory.showError(e, null);
            return false;
        }
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
            PopupContext popupContext = null; // new SwingPopupContext(getComponent(), null)
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

            // FIXME get informations
            PopupContext popupContext = null;//new SwingPopupContext(getComponent(), null)
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
                logger.error("Error getting user in resource selection: "+e.getMessage(), e);
            }
        }
    }
    
    public CalendarSelectionModel getModel()
    {
        return model;
    }

    public void dataChanged(ModificationEvent evt) throws RaplaException
    {
        if (evt != null && evt.isModified())
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
    public void showTreePopup(PopupContext popupContext, Object selectedObject, MenuContext menuContext)
    {
        try
        {
            RaplaPopupMenu menu = new RaplaPopupMenu();

            RaplaMenu newMenu = new RaplaMenu("new");
            newMenu.setText(i18n.getString("new"));
            // TODO extract interface
            SwingMenuContext swingMenuContext = ((SwingMenuContext) menuContext);
            boolean addNewReservationMenu = selectedObject instanceof Allocatable || selectedObject instanceof DynamicType;
            ((MenuFactoryImpl) getMenuFactory()).addNew(newMenu, swingMenuContext, null, addNewReservationMenu);

            getMenuFactory().addObjectMenu(menu, swingMenuContext, "EDIT_BEGIN");
            newMenu.setEnabled(newMenu.getMenuComponentCount() > 0);
            menu.insertAfterId(newMenu, "EDIT_BEGIN");
            view.showMenu(menu, swingMenuContext);
        }
        catch (RaplaException ex)
        {
            dialogUiFactory.showException(ex, popupContext);
        }
    }

    
    @Override
    public void updateSelectedObjects(Collection<Object> elements)
    {
        try
        {
            HashSet<Object> selectedElements = new HashSet<Object>(elements);
            getModel().setSelectedObjects(selectedElements);
            updateMenu();
            applyFilter();
        }
        catch(RaplaException ex)
        {
            PopupContext popupContext = null;
            dialogUiFactory.showException(ex, popupContext);
        }
    }

    @Override
    public void applyFilter()
    {
        eventBus.fireEvent(new CalendarRefreshEvent());

    }

    public void updateMenu() throws RaplaException
    {
        RaplaMenu editMenu = menuBar.getEditMenu();
        RaplaMenu newMenu = menuBar.getNewMenu();

        editMenu.removeAllBetween("EDIT_BEGIN", "EDIT_END");
        newMenu.removeAll();

        Collection<?> list = getModel().getSelectedObjects();
        Object focusedObject = null;
        if (list.size() == 1)
        {
            focusedObject = list.iterator().next();
        }

        SwingMenuContext menuContext = new SwingMenuContext(focusedObject);
        menuContext.setSelectedObjects(list);
        if (view.hasFocus())
        {
            getMenuFactory().addObjectMenu(editMenu, menuContext, "EDIT_BEGIN");
        }
        ((MenuFactoryImpl) getMenuFactory()).addNew(newMenu, menuContext, null, true);
        newMenu.setEnabled(newMenu.getMenuComponentCount() > 0);
    }

    public MenuFactory getMenuFactory()
    {
        return menuFactory;
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
