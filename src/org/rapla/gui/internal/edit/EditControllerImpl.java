/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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
package org.rapla.gui.internal.edit;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.EditComponent;
import org.rapla.gui.EditController;
import org.rapla.gui.RaplaGUIComponent;

/** This class handles the edit-ui for all entities (except reservations). */
public class EditControllerImpl extends RaplaGUIComponent implements
		EditController {
	Collection<EditDialog<?>> editWindowList = new ArrayList<EditDialog<?>>();

	public EditControllerImpl(RaplaContext sm){
		super(sm);
	}

	void addEditDialog(EditDialog<?> editWindow) {
		editWindowList.add(editWindow);
	}

	void removeEditDialog(EditDialog<?> editWindow) {
		editWindowList.remove(editWindow);
	}

	public <T extends Entity> EditComponent<T> createUI(T obj) throws RaplaException 
	{
		return createUI( obj,false);
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.rapla.gui.edit.IEditController#createUI(org.rapla.entities.
	 * RaplaPersistant)
	 */
	@SuppressWarnings("unchecked")
    public <T extends Entity> EditComponent<T> createUI(T obj, boolean createNew) throws RaplaException {
		RaplaType type = obj.getRaplaType();
		EditComponent<?> ui = null;
		if (Allocatable.TYPE.equals(type)) {
			boolean internal = isInternalType( (Allocatable)obj);
            ui = new AllocatableEditUI(getContext(), internal);
		} else if (DynamicType.TYPE.equals(type)) {
			ui =  new DynamicTypeEditUI(getContext());
		} else if (User.TYPE.equals(type)) {
			ui =  new UserEditUI(getContext());
		} else if (Category.TYPE.equals(type)) {
			ui = new CategoryEditUI(getContext(), createNew);
		} else if (Preferences.TYPE.equals(type)) {
			ui =  new PreferencesEditUI(getContext());
		} else if (Reservation.TYPE.equals(type)) {
            ui =  new ReservationEditUI(getContext());
        }

		if (ui == null) {
			throw new RuntimeException("Can't edit objects of type "
					+ type.toString());
		}
		return (EditComponent<T>)ui;
	}
	
    private boolean isInternalType(Allocatable alloc) {
        DynamicType type = alloc.getClassification().getType();
        String annotation = type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        return annotation != null && annotation.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE);
    }

//	enhancement of the method to deal with arrays
	protected String guessTitle(Object obj) {
		RaplaType raplaType = getRaplaType(obj);
		String title = "";
		if(raplaType != null) {
			title = getString(raplaType.getLocalName());
		}
		
		return title;
	}
	
//	method for determining the consistent RaplaType from different objects 
	protected RaplaType getRaplaType(Object obj){
		Set<RaplaType> types = new HashSet<RaplaType>();
		
//		if the committed object is no array -> wrap object into array
		if (!obj.getClass().isArray()) {
			obj = new Object[] { obj };
		}

//		iterate all committed objects and store RaplayType of the objects in a Set
//		identic typs aren't stored double because of Set
		for (Object o : (Object[]) obj) {
			if (o instanceof Entity) {
				RaplaType type = ((Entity<?>) o).getRaplaType();
				types.add(type);
			}
		}
		
//		check if there is a explicit type, then return this type; otherwise return null
		if (types.size() == 1)
			return types.iterator().next();
		else
			return null;
	}

	public <T extends Entity> void edit(T obj, Component owner) throws RaplaException {
		edit(obj, guessTitle(obj), owner);
	}

	public <T extends Entity> void editNew(T obj, Component owner)
			throws RaplaException {
		edit(obj, guessTitle(obj), owner, true);
	}

	public <T extends Entity> void edit(T[] obj, Component owner) throws RaplaException {
		edit(obj, guessTitle(obj), owner);
	}


	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.rapla.gui.edit.IEditController#edit(org.rapla.entities.Entity,
	 * java.lang.String, java.awt.Component)
	 */
	public <T extends Entity> void edit(T obj, String title, Component owner)
			throws RaplaException {
		edit(obj, title, owner, false);
	}
	
	protected <T extends Entity> void edit(T obj, String title, Component owner,boolean createNew )
				throws RaplaException {
		
		// Hack for 1.6 compiler compatibility
		@SuppressWarnings("cast")
		Entity<?> testObj = (Entity<?>) obj;
		if ( testObj instanceof Reservation)
		{
			getReservationController().edit( (Reservation) testObj );
			return;
		}
		// Lookup if the reservation is already beeing edited
		EditDialog<?> c = null;
		Iterator<EditDialog<?>> it = editWindowList.iterator();
		while (it.hasNext()) {
			c =  it.next();
			List<?> editObj = c.ui.getObjects();
			if (editObj != null && editObj.size() == 1 ) 
			{
			    Object first = editObj.get(0);
			    if (first  instanceof Entity && ((Entity<?>) first).isIdentical(obj))
			    {
			        break;
			    }
			} 
			c = null;
		}

		if (c != null) {
			c.dlg.requestFocus();
			c.dlg.toFront();
		} else {
            editAndOpenDialog( Collections.singletonList( obj),title, owner, createNew);
		}
	}
	
//	method analog to edit(Entity obj, String title, Component owner)

//	however for using with arrays
	public  <T extends Entity> void edit(T[] obj, String title, Component owner)
			throws RaplaException {
		
//		checks if all entities are from the same type; otherwise return
		if(getRaplaType(obj) == null) return;
		
//		if selektion contains only one object start usual Edit dialog
		if(obj.length == 1){
			edit(obj[0], title, owner);
		}
		else
		{
		    editAndOpenDialog(Arrays.asList(obj), title, owner, false);
    	}
	}

    protected <T extends Entity> void editAndOpenDialog(List<T> list, String title, Component owner, boolean createNew) throws RaplaException {
        //		gets for all objects in array a modifiable version and add it to a set to avoid duplication
    	Collection<T> toEdit = getModification().edit( list);
    	if (toEdit.size() > 0) {
        	EditComponent<T> ui = createUI(toEdit.iterator().next(), createNew);
        	EditDialog<T> gui = new EditDialog<T>(getContext(), ui, false);
            gui.start(toEdit, title, owner);
        }
    }
}
