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
import org.rapla.client.menu.MenuInterface;
import org.rapla.client.menu.MenuItemFactory;
import org.rapla.client.menu.RaplaObjectActions;
import org.rapla.client.swing.RaplaAction;
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
    private RaplaResources i18n;


    public RaplaObjectAction(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, PopupContext popupContext,  Provider<RaplaObjectActions> actions)  {
        super(facade, i18n, raplaLocale, logger);
        this.i18n = i18n;
        this.actions = actions.get();
        this.actions.setPopupContext( popupContext);
    }
    

    public RaplaObjectAction setNew(Class<? extends RaplaObject> raplaType) {
        actions.setNew(raplaType);
        putValue(NAME, getString("new"));
        setIcon( i18n.getIcon("icon.new"));
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
        setIcon(i18n.getIcon("icon.delete"));
        changeObject(object);
        return this;
    }

    public RaplaObjectAction setDeleteSelection(Collection<Entity<?>> selection) {
        actions.setDeleteSelection( selection);
        putValue(NAME, getString("delete_selection"));
        setIcon( i18n.getIcon("icon.delete"));
        update();
        return this;
    }

    public RaplaObjectAction setView(Entity<?> object) {
        actions.setView(object);
        putValue(NAME, getString("view"));
        setIcon(i18n.getIcon("icon.help"));
        changeObject(object);
        return this;
    }

    public RaplaObjectAction setEdit(Entity<?> object) {
        actions.setEdit( object );
        putValue(NAME, getString("edit"));
        setIcon(i18n.getIcon("icon.edit"));
        changeObject(object);
        return this;
    }
    
 // method for setting a selection as a editable selection
 	// (cf. setEdit() and setDeleteSelection())
 	public RaplaObjectAction setEditSelection(Collection<Entity<?>> selection) {
        actions.setEditSelection( selection);
 		putValue(NAME, getString("edit"));
 		setIcon(i18n.getIcon("icon.edit"));
        update();
 		return this;
 	}

    public void changeObject(Entity<?> object) {
        actions.changeObject( object);
        if (actions.isDelete()) {
            if (object == null)
                putValue(NAME, i18n.getString("delete"));
            else
                putValue(NAME, i18n.format("delete.format",getName(object)));
        }
        update();
    }

    @Override
    public RaplaObjectAction addTo(MenuInterface menu, MenuItemFactory menuItemFactory) {
        return (RaplaObjectAction) super.addTo(menu, menuItemFactory);
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
