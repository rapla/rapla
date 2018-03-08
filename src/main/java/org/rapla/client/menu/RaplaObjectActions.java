/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas, Bettina Lademann                |
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
package org.rapla.client.menu;

import jsinterop.annotations.JsType;
import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DeleteDialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.internal.DeleteUndo;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import java.util.*;

@JsType
public class RaplaObjectActions extends RaplaComponent{
    public final static int DELETE = 1;
    public final static int COPY = 2;
    public final static int PASTE = 3;
    public final static int CUT = 4;
    public final static int NEW = 5;
    public final static int EDIT = 6;
    public final static int VIEW = 7;
    public final static int DELETE_SELECTION = 8;
    public final static int EDIT_SELECTION = 9; // new attribute to define a
	// edit selection (several
	// editable entities)
    String classificationType;
    protected int type;
    boolean isPerson;
    protected Entity<?> object;
    List<Entity<?>> objectList;
    protected Class<? extends RaplaObject> raplaType;
    PopupContext popupContext;
    ClientFacade clientFacade;
    protected final EditController editController;
    private final InfoFactory infoFactory;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final DeleteDialogInterface deleteDialogInterface;
    private final PermissionController permissionController;

    @Inject
    public RaplaObjectActions(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,  EditController editController, InfoFactory infoFactory, DialogUiFactoryInterface dialogUiFactory, DeleteDialogInterface deleteDialogInterface)  {
        super(facade.getRaplaFacade(), i18n, raplaLocale, logger);
        this.clientFacade = facade;
        this.editController = editController;
        this.infoFactory = infoFactory;
        this.dialogUiFactory = dialogUiFactory;
        this.permissionController = facade.getRaplaFacade().getPermissionController();
        this.deleteDialogInterface = deleteDialogInterface;
    }
    
    protected PopupContext getPopupContext()
    {
        return popupContext;
    }


    public RaplaObjectActions setNew(Class<? extends RaplaObject> raplaType) {
        this.raplaType = raplaType;
        this.type = NEW;
        return this;
    }

    public void setPopupContext(PopupContext popupContext)
    {
        this.popupContext = popupContext;
}

    public void setClassificationType(String classificationType) {
        this.classificationType = classificationType;
    }

    public RaplaObjectActions setDelete(Entity<?> object) {
        this.type = DELETE;
        changeObject(object);
        return this;
    }

    public RaplaObjectActions setDeleteSelection(Collection<Entity<?>> selection) {
        this.type = DELETE_SELECTION;
        this.objectList = new ArrayList<Entity<?>>(selection);
        isEnabled();
        return this;
    }

    public RaplaObjectActions setView(Entity<?> object) {
        this.type = VIEW;
        changeObject(object);
        return this;
    }

    public RaplaObjectActions setEdit(Entity<?> object) {
        this.type = EDIT;
        changeObject(object);
        return this;
    }
    
 // method for setting a selection as a editable selection
 	// (cf. setEdit() and setDeleteSelection())
 	public RaplaObjectActions setEditSelection(Collection<Entity<?>> selection) {
 		this.type = EDIT_SELECTION;
 		this.objectList = new ArrayList<Entity<?>>(selection);
 		return this;
 	}

    public void changeObject(Entity<?> object) {
        this.object = object;
    }


    public boolean isEnabled() {
        boolean enabled = true;
        User user = null;
        try
        {
            user = clientFacade.getUser();
        }
        catch (RaplaException e)
        {
            return false;
        }

        if (type == EDIT || type == DELETE) {
            enabled = permissionController.canModify(object, user);

        } else if (type == NEW ) {
            enabled = (raplaType != null && raplaType == Allocatable.class && permissionController.isRegisterer(null, user)) || user.isAdmin();
        } else if (type == EDIT_SELECTION || type == DELETE_SELECTION) {
            if (objectList != null && objectList.size() > 0 ) {
                Iterator<Entity<?>> it = objectList.iterator();
                while (it.hasNext()) {
                    if (!permissionController.canModify(it.next(), user)){
                        enabled = false;
                        break;
                    }
                }
            } else {
                enabled = false;
            }
        }
        return enabled;
    }


    public void actionPerformed() {
        try {
            switch (type) {
            case DELETE: delete();break;
            case DELETE_SELECTION: deleteSelection();break;
            case EDIT: edit();break;
         // EditSelection() as reaction of actionPerformed (click on the edit
         			// button)
         	case EDIT_SELECTION:editSelection();break;
            case NEW: newEntity();break;
            case VIEW: view();break;
            }
        } catch (RaplaException ex) {
            dialogUiFactory.showException(ex,popupContext);
        } // end of try-catch
    }

    public void view() throws RaplaException {
        infoFactory.showInfoDialog(object,popupContext);
    }


    protected Promise<? extends Entity> newEntityAsyc(Class<? extends RaplaObject> raplaType){
        RaplaFacade m = getFacade();
        try
        {
            final User user = clientFacade.getUser();
            if (Reservation.class == raplaType)
            {
                DynamicType type = guessType(object);
                final Classification newClassification = type.newClassification();
                Promise<Reservation> newReservation = m.newReservationAsync(newClassification);
                return newReservation;
            }
            final Entity entity;
            if (Allocatable.class == raplaType)
            {
                DynamicType type = guessType(object);
                final Classification newClassification = type.newClassification();
                Allocatable allocatable = m.newAllocatable(newClassification, user);
                entity = allocatable;
            }
            else if (Category.class == raplaType)
                entity = m.newCategory(); //will probably never happen
            else if (User.class == raplaType)
                entity= m.newUser();
            else if (Period.class == raplaType)
                entity = m.newPeriod(user);
            else if (DynamicType.class == raplaType)
                entity = m.newDynamicType( classificationType);
            else
                throw new RaplaException("Can't createInfoDialog Entity for " + raplaType + "!");
            return new ResolvedPromise<>(entity);
        } catch (Exception ex)
        {
            return new ResolvedPromise<>(ex);
        }

    }


    /** guesses the DynamicType for the new object depending on the selected element.
        <li>If the selected element is a DynamicType-Folder the DynamicType will be returned.
        <li>If the selected element has a Classification the appropriatie DynamicType will be returned.
        <li>else the first matching DynamicType for the passed classificationType will be returned.
        <li>null if none of the above criterias matched.
    */
    private DynamicType guessType(Object object) throws RaplaException {
        DynamicType[] types = guessTypesFor(object);
        if ( types.length > 0)
        {
            return types[0];
        }
        else
        {
            return null;
        }
    }

    public DynamicType[] guessTypes() throws RaplaException {
        return guessTypesFor( object);
    }

    public DynamicType[] guessTypesFor(Object object) throws RaplaException {
        DynamicType dynamicType = null;
        getLogger().debug("Guessing DynamicType from " + object);
        if (object instanceof DynamicType)
            dynamicType = (DynamicType) object;

        if (object instanceof Classifiable) {
            Classification classification= ((Classifiable) object).getClassification();
            dynamicType = classification.getType();
        }
        if (dynamicType != null)
        {
            return new DynamicType[] {dynamicType};
        }
        String classificationType = null;
        if ( Reservation.class ==  raplaType) {
            classificationType = DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION;
        } else  if ( Allocatable.class == raplaType ) {
            if ( isPerson ) {
                classificationType = DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON;
            } else {
                classificationType = DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE;
            }
        }
        DynamicType[] dynamicTypes = getFacade().getDynamicTypes( classificationType );
        return dynamicTypes;
      
    }

    private String createNewKey(Category[] subCategories) {
        int max = 1;
        for (int i=0;i<subCategories.length;i++) {
            String key = subCategories[i].getKey();
            if (key.length()>1
                && key.charAt(0) =='c'
                && Character.isDigit(key.charAt(1))
                )
                {
                    try {
                        int value = Integer.valueOf(key.substring(1)).intValue();
                        if (value >= max)
                            max = value + 1;
                    } catch (NumberFormatException ex) {
                    }
                }
        }
        return "c" + (max);
    }

    // creates a new Category
    private Promise<Category> createNewNodeAt(Category parent) throws RaplaException {
        Category newCategory = getFacade().newCategory();
        newCategory.setKey(createNewKey(parent.getCategories()));
        newCategory.getName().setName(getI18n().getLang(), getString("new_category") );
        return getFacade().editAsync(parent).thenApply((editableCategore)->{editableCategore.addCategory(newCategory);return  newCategory;});
    }

	protected  void newEntity() throws RaplaException {
    	if ( Category.class == raplaType ) {
            createNewNodeAt((Category) object).thenAccept(category -> editController.edit(category, popupContext));
        } else if ( Category.class == raplaType ) {
            DynamicType newDynamicType = getFacade().newDynamicType(classificationType);
            editController.edit(newDynamicType, popupContext);
        } else {
            handleException(newEntityAsyc(raplaType).thenAccept(entity->editController.edit( entity, popupContext)));
        }
    }

	protected void edit()  {
        editController.edit(object, popupContext);
    }

    protected void delete()  {
        if (object == null)
            return;
        delete(Collections.singletonList( object));
    }

    protected void deleteSelection()
    {
        if (objectList == null || objectList.size() == 0)
            return;
        delete(objectList);
    }

    private void delete(List<Entity<?>> list)
    {
        handleException(deleteDialogInterface.showDeleteDialog(popupContext,list.toArray()).thenCompose((result->result ? delete((Collection<Entity<?>>) list): ResolvedPromise.VOID_PROMISE)));
    }

    protected Promise<Void> delete(Collection<Entity<?>>  objects) throws RaplaException
	{
		Collection<Entity<?>> entities = new ArrayList<>();
	    boolean undoable = true;
	    for ( Entity<?> obj: objects)
	    {
	    	entities.add(  obj);
	    	Class raplaType = obj.getTypeClass();
			if ( raplaType == User.class || raplaType == DynamicType.class)
			{
				undoable = false;
			}
	    }
	    @SuppressWarnings({ "rawtypes", "unchecked" })
        DeleteUndo<? extends Entity<?>> deleteCommand = new DeleteUndo(getFacade(),getI18n(), entities, clientFacade.getUser());
        final Promise<Void> promise;
	    if ( undoable)
	    {
            promise = clientFacade.getCommandHistory().storeAndExecute(deleteCommand);
	    }
	    else
	    {
	    	promise = deleteCommand.execute();
	    }
        return promise;
	}

    protected Promise handleException(Promise promise)
    {
        return promise.exceptionally(ex->
                dialogUiFactory.showException((Throwable)ex,popupContext)
        );
    }
	

 // action which is executed by clicking on the edit button (after
 	// actionPerformed)
 	// chances the selection list in an array and commits it on EditController
    protected void editSelection() throws RaplaException {
 		if (objectList == null || objectList.size() == 0)
 			return;
        List<Entity> list = new ArrayList<Entity>(objectList);
        editController.edit(list, popupContext);
 	}

	public void setPerson(boolean b) {
		isPerson = b;
	}


    public boolean isDelete() {
        return type == DELETE;
    }
}
