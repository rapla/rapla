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
package org.rapla.client.swing.internal.action;

import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.internal.DeleteUndo;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.RaplaAction;
import org.rapla.client.swing.images.RaplaImages;
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
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.storage.PermissionController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class RaplaObjectAction extends RaplaAction {
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

    protected int type;
    boolean isPerson;
    protected Entity<?> object;
    List<Entity<?>> objectList;
    protected Class<? extends RaplaObject> raplaType;
    private final PopupContext popupContext;
    protected final EditController editController;
    private final InfoFactory infoFactory;
    private final RaplaImages raplaImages;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final PermissionController permissionController;

    public RaplaObjectAction(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, EditController editController, InfoFactory infoFactory, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory) {
        this(facade, i18n, raplaLocale, logger, null, editController, infoFactory, raplaImages, dialogUiFactory);
    }

    public RaplaObjectAction(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, PopupContext popupContext, EditController editController, InfoFactory infoFactory, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory)  {
        super(facade, i18n, raplaLocale, logger);
        this.editController = editController;
        this.popupContext = popupContext;
        this.infoFactory = infoFactory;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        this.permissionController = facade.getRaplaFacade().getPermissionController();
    }
    
    protected PopupContext getPopupContext()
    {
        return popupContext;
    }

    public RaplaObjectAction setNew(Class<? extends RaplaObject> raplaType) {
        this.raplaType = raplaType;
        this.type = NEW;
        putValue(NAME, getString("new"));
        putValue(SMALL_ICON, raplaImages.getIconFromKey("icon.new"));
        update();
        return this;
    }

    public RaplaObjectAction setDelete(Entity<?> object) {
        this.type = DELETE;
        putValue(NAME, getString("delete"));
        putValue(SMALL_ICON, raplaImages.getIconFromKey("icon.delete"));
        changeObject(object);
        return this;
    }

    public RaplaObjectAction setDeleteSelection(Collection<Entity<?>> selection) {
        this.type = DELETE_SELECTION;
        putValue(NAME, getString("delete_selection"));
        putValue(SMALL_ICON, raplaImages.getIconFromKey("icon.delete"));
        this.objectList = new ArrayList<Entity<?>>(selection);
        update();
        return this;
    }

    public RaplaObjectAction setView(Entity<?> object) {
        this.type = VIEW;
        putValue(NAME, getString("view"));
        putValue(SMALL_ICON, raplaImages.getIconFromKey("icon.help"));
        changeObject(object);
        return this;
    }

    public RaplaObjectAction setEdit(Entity<?> object) {
        this.type = EDIT;
        putValue(NAME, getString("edit"));
        putValue(SMALL_ICON, raplaImages.getIconFromKey("icon.edit"));
        changeObject(object);
        return this;
    }
    
 // method for setting a selection as a editable selection
 	// (cf. setEdit() and setDeleteSelection())
 	public RaplaObjectAction setEditSelection(Collection<Entity<?>> selection) {
 		this.type = EDIT_SELECTION;
 		putValue(NAME, getString("edit"));
 		putValue(SMALL_ICON, raplaImages.getIconFromKey("icon.edit"));
 		this.objectList = new ArrayList<Entity<?>>(selection);
        update();
 		return this;
 	}

    public void changeObject(Entity<?> object) {
        this.object = object;
        if (type == DELETE) {
            if (object == null)
                putValue(NAME, getString("delete"));
            else
                putValue(NAME, getI18n().format("delete.format",getName(object)));
        }
        update();
    }


    protected void update() {
        boolean enabled = true;
        User user = null;
        try
        {
            user = getUser();
        }
        catch (RaplaException e)
        {
            enabled = false;
            setEnabled( false);
            return;
        }

        if (type == EDIT || type == DELETE) {
            enabled = permissionController.canModify(object, user);

        } else if (type == NEW ) {
            enabled = (raplaType != null && raplaType == Allocatable.class && permissionController.isRegisterer(null, user)) || isAdmin();
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
        setEnabled(enabled);
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


    protected Entity<? extends Entity<?>> newEntity(Class<? extends RaplaObject> raplaType) throws RaplaException {
        RaplaFacade m = getFacade();
        final User user = getUser();
        if ( Reservation.class == raplaType )
        {
            DynamicType type = guessType();
            final Classification newClassification = type.newClassification();
            Reservation newReservation = m.newReservation( newClassification, user);
            return newReservation;
        }
        if ( Allocatable.class == raplaType )
        {
        	DynamicType type = guessType();
            final Classification newClassification = type.newClassification();
            Allocatable allocatable = m.newAllocatable( newClassification, user );
        	return allocatable ;
        }
       if ( Category.class ==  raplaType)
            return m.newCategory(); //will probably never happen
       if ( User.class ==  raplaType )
    	   return m.newUser();
       if ( Period.class == raplaType )
             return m.newPeriod(user);
       throw new RaplaException("Can't create Entity for " + raplaType + "!");
    }


    /** guesses the DynamicType for the new object depending on the selected element.
        <li>If the selected element is a DynamicType-Folder the DynamicType will be returned.
        <li>If the selected element has a Classification the appropriatie DynamicType will be returned.
        <li>else the first matching DynamicType for the passed classificationType will be returned.
        <li>null if none of the above criterias matched.
    */
    public DynamicType guessType() throws RaplaException {
        DynamicType[] types = guessTypes();
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
        DynamicType[] dynamicTypes = getQuery().getDynamicTypes( classificationType );
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
    private Category createNewNodeAt(Category parent) throws RaplaException {
        Category newCategory = getFacade().newCategory();
        newCategory.setKey(createNewKey(parent.getCategories()));
        newCategory.getName().setName(getI18n().getLang(), getString("new_category") );
        getFacade().edit(parent).addCategory(newCategory);
        getLogger().debug(" new category " + newCategory + " added to " + parent);
        return newCategory;
    }

	protected  void newEntity() throws RaplaException {
        final Entity<? extends Entity> obj;
    	if ( Category.class == raplaType ) {
        	obj = createNewNodeAt((Category)object);
        } else {
			obj = newEntity(raplaType);
        }
        editController.edit(obj, popupContext);
    }

	protected void edit() throws RaplaException {
        editController.edit(object, popupContext);
    }

    protected void delete() throws RaplaException {
        if (object == null)
            return;
        Entity<?>[] objects = new Entity[] {  object};
        DialogInterface dlg = infoFactory.createDeleteDialog( objects, popupContext);
        dlg.start(true);
        if (dlg.getSelectedIndex() != 0)
            return;
        List<Entity<?>> singletonList = Arrays.asList( objects);
        delete(singletonList);
    }

    protected void deleteSelection() throws RaplaException 
    {
        if (objectList == null || objectList.size() == 0)
            return;
        DialogInterface dlg = infoFactory.createDeleteDialog(objectList.toArray(), popupContext);
        dlg.start(true);
        if (dlg.getSelectedIndex() != 0)
            return;
		delete(objectList);
    }

	protected void delete(Collection<Entity<?>>  objects) throws RaplaException 
	{
		Collection<Entity<?>> entities = new ArrayList<Entity<?>>();    
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
        DeleteUndo<? extends Entity<?>> deleteCommand = new DeleteUndo(getFacade(),getI18n(), entities, getUser());
	    if ( undoable)
	    {
	    	getUpdateModule().getCommandHistory().storeAndExecute(deleteCommand);
	    }
	    else
	    {
	    	deleteCommand.execute();
	    }	
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

}
