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
import org.rapla.client.PopupContext;
import org.rapla.client.menu.RaplaObjectActions;
import org.rapla.client.swing.RaplaAction;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

import javax.inject.Provider;
import java.util.Collection;

public class RaplaObjectAction extends RaplaAction {
    RaplaObjectActions actions;
	// edit selection (several
	// editable entities)

    private final RaplaImages raplaImages;

    public RaplaObjectAction(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, PopupContext popupContext, RaplaImages raplaImages, Provider<RaplaObjectActions> actions)  {
        super(facade, i18n, raplaLocale, logger);
        this.actions = actions.get();
        this.actions.setPopupContext( popupContext);
        this.raplaImages = raplaImages;
    }
    

    public RaplaObjectAction setNew(Class<? extends RaplaObject> raplaType) {
        actions.setNew(raplaType);
        putValue(NAME, getString("new"));
        putValue(SMALL_ICON, raplaImages.getIconFromKey("icon.new"));
        update();
        return this;
    }

    public RaplaObjectAction setNewClassificationType(String classificationType)
    {
        RaplaObjectAction result = setNew(DynamicType.class);
        actions.setClassificationType( classificationType);
        return result;
    }



    public RaplaObjectAction setDelete(Entity<?> object) {
        actions.setDelete(object);
        putValue(NAME, getString("delete"));
        putValue(SMALL_ICON, raplaImages.getIconFromKey("icon.delete"));
        changeObject(object);
        return this;
    }

    public RaplaObjectAction setDeleteSelection(Collection<Entity<?>> selection) {
        actions.setDeleteSelection( selection);
        putValue(NAME, getString("delete_selection"));
        putValue(SMALL_ICON, raplaImages.getIconFromKey("icon.delete"));
        update();
        return this;
    }

    public RaplaObjectAction setView(Entity<?> object) {
        actions.setView(object);
        putValue(NAME, getString("view"));
        putValue(SMALL_ICON, raplaImages.getIconFromKey("icon.help"));
        changeObject(object);
        return this;
    }

    public RaplaObjectAction setEdit(Entity<?> object) {
        actions.setEdit( object );
        putValue(NAME, getString("edit"));
        putValue(SMALL_ICON, raplaImages.getIconFromKey("icon.edit"));
        changeObject(object);
        return this;
    }
    
 // method for setting a selection as a editable selection
 	// (cf. setEdit() and setDeleteSelection())
 	public RaplaObjectAction setEditSelection(Collection<Entity<?>> selection) {
        actions.setEditSelection( selection);
 		putValue(NAME, getString("edit"));
 		putValue(SMALL_ICON, raplaImages.getIconFromKey("icon.edit"));
        update();
 		return this;
 	}

    public void changeObject(Entity<?> object) {
        actions.changeObject( object);
        if (actions.isDelete()) {
            if (object == null)
                putValue(NAME, getString("delete"));
            else
                putValue(NAME, getI18n().format("delete.format",getName(object)));
        }
        update();
    }


    protected void update() {
        setEnabled( actions.isEnabled());
    }


    public void actionPerformed() {
        actions.actionPerformed();
    }


	public void setPerson(boolean b) {
		actions.setPerson( b);
	}

    public DynamicType[] guessTypes() throws RaplaException {
        return actions.guessTypes();
    }
}
