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
package org.rapla.client.swing.internal.edit;

import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.EditDialogFactoryInterface;
import org.rapla.client.dialog.EditDialogInterface;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaType;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** This class handles the edit-ui for all entities (except reservations). */

@DefaultImplementation(of=EditController.class, context = InjectionContext.swing)
@Singleton
public class EditControllerImpl implements
		EditController {
	Collection<EditDialogInterface<?>> editWindowList = new ArrayList<EditDialogInterface<?>>();
	private final EditDialogFactoryInterface editDialogFactory;
	private final ReservationController reservationController;
	private final RaplaFacade facade;
    private final RaplaResources i18n;

    @Inject
	public EditControllerImpl(EditDialogFactoryInterface editDialogFactory, ReservationController reservationController,
            RaplaFacade facade, RaplaResources i18n)
    {
        super();
        this.editDialogFactory = editDialogFactory;
        this.reservationController = reservationController;
        this.facade = facade;
        this.i18n = i18n;
    }

    @Override
	public <T extends Entity> void edit(T obj, PopupContext popupContext) throws RaplaException {
        String title = null;
        EditCallback<List<T>> callback = null;
        List<T> list = Collections.singletonList(obj);
        editAndOpenDialog(list, title, popupContext, callback);
	}

	public <T extends Entity> void edit(final T obj, final String title,final PopupContext popupContext, final EditController.EditCallback<T> callback) throws RaplaException {
        final EditCallback<List<T>> listCallback;
        if ( callback != null)
        {
            listCallback = new EditCallback<List<T>>()
            {
                @Override public void onFailure(Throwable e)
                {
                    callback.onFailure(e);
                }

                @Override public void onSuccess(List<T> editObject)
                {
                    callback.onSuccess(editObject.get(0));
                }

                @Override public void onAbort()
                {
                    callback.onAbort();
                }
            };
        }
        else
        {
            listCallback = null;
        }
        edit(Collections.singletonList(obj), title, popupContext, listCallback);
	}

	public <T extends Entity> void edit(List<T> list, String title,PopupContext popupContext, EditController.EditCallback<List<T>> callback) throws RaplaException {

		//		if selektion contains only one object start usual Edit dialog
        editAndOpenDialog(list, title, popupContext, callback);
	}


	void addEditDialog(EditDialogInterface editWindow) {
		editWindowList.add(editWindow);
	}

	void removeEditDialog(EditDialogInterface editWindow) {
		editWindowList.remove(editWindow);
	}

//	enhancement of the method to deal with arrays
	private String guessTitle(Collection obj) {
        Class<? extends Entity> raplaType = getRaplaType(obj);
		String title = "";
		if(raplaType != null) {
            String localname = RaplaType.getLocalName(raplaType);
			title = i18n.getString(localname);
		}

		return title;
	}

//	method for determining the consistent RaplaType from different objects
	protected Class<? extends Entity> getRaplaType(Collection obj){
		Set<Class<? extends Entity>> types = new HashSet<Class<? extends Entity>>();


//		iterate all committed objects and store RaplayType of the objects in a Set
//		identic typs aren't stored double because of Set
		for (Object o : obj) {
			if (o instanceof Entity) {
                final Class<? extends Entity> type = ((Entity) o).getTypeClass();
				types.add(type);
			}
		}

//		check if there is a explicit type, then return this type; otherwise return null
		if (types.size() == 1)
			return types.iterator().next();
		else
			return null;
	}

    private <T extends Entity> void editAndOpenDialog(List<T> list, String title, PopupContext popupContext, EditCallback<List<T>> callback) throws RaplaException {
        if( list.size() == 0)
        {
            throw new RaplaException("Empty list not allowed. You must have at least one entity to edit.");
        }
        if(title == null)
        {
            title = guessTitle(list);

        }
        //		checks if all entities are from the same type; otherwise return
        if(getRaplaType(list) == null)
        {
            if (callback != null)
            {
                callback.onAbort();
            }
            return;
        }

        if ( list.size() == 1)
         {
             Entity<?> testObj = (Entity<?>) list.get(0);
             if ( testObj instanceof Reservation)
             {
                 reservationController.edit((Reservation) testObj);
                 return;
             }
             // Lookup if the entity (not a reservation) is already beeing edited
             EditDialogInterface c = null;
             Iterator<EditDialogInterface<?>> it = editWindowList.iterator();
             while (it.hasNext()) {
                 c =  it.next();
                 List<?> editObj = c.getObjects();
                 if (editObj != null && editObj.size() == 1 )
                 {
                     Object first = editObj.get(0);
                     if (first  instanceof Entity && ((Entity<?>) first).isIdentical(testObj))
                     {
                         break;
                     }
                 }
                 c = null;
             }

             if (c != null)
             {
                 c.getDialog().requestFocus();
                 c.getDialog().toFront();
                 return;
             }
         }
        //		gets for all objects in array a modifiable version and add it to a set to avoid duplication
    	Collection<T> toEdit = facade.edit(list);
    	if (toEdit.size() > 0) {
        	EditDialogInterface<T> gui = editDialogFactory.create(this);
            gui.start(toEdit, title, popupContext, callback);
        }
    }
}
