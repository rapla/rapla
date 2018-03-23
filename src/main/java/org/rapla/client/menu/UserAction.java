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

import io.reactivex.functions.Consumer;
import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.UserClientService;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.components.i18n.I18nIcon;
import org.rapla.entities.User;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import javax.inject.Provider;

public class UserAction  {
    Object object;
    public final int NEW = 1;
    public final int SWITCH_TO_USER = 3;
    int type = NEW;
    private PopupContext popupContext;
    private final UserClientService service;
    private final EditController editController;
    RaplaResources i18n;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final MenuItemFactory menuItemFactory;
    private final Provider<PasswordChangeAction> passwordChangeAction;
    private String name;
    private I18nIcon icon;
    private boolean enabled;
    ClientFacade clientFacade;
    Logger logger;

    @Inject
    public UserAction(ClientFacade facade, RaplaResources i18n, Logger logger, UserClientService service, EditController editController,
            DialogUiFactoryInterface dialogUiFactory, MenuItemFactory menuItemFactory, Provider<PasswordChangeAction> passwordChangeAction) {
        this.clientFacade = facade;
        this.i18n = i18n;
        this.logger = logger;
        this.service = service;
        this.editController = editController;
        this.dialogUiFactory = dialogUiFactory;
        this.menuItemFactory = menuItemFactory;
        this.passwordChangeAction = passwordChangeAction;
    }

    public UserAction setPopupContext(PopupContext popupContext)
    {
        this.popupContext = popupContext;
        return this;
    }

    public UserAction setNew() {
        type = NEW;
        name = i18n.getString("user");
        icon = i18n.getIcon("icon.new");
        update();
        return this;
    }

    public UserAction setSwitchToUser() {
        type = SWITCH_TO_USER;
        if (service.canSwitchBack()) {
            name = i18n.getString("switch_back");
        } else {
            name = i18n.getString("switch_to");
        }
        return this;
    }

    public void changeObject(Object object) {
        this.object = object;
        update();
    }

    private void update() {
        User user = null;
        try {
            user = clientFacade.getUser();
            final boolean admin = PermissionController.canAdminUsers( user );
            if (type == NEW) {
                setEnabled(admin);
            } else if (type == SWITCH_TO_USER) {
                setEnabled(service.canSwitchBack() || (object != null && admin && !user.equals(object)));
            }
        } catch (RaplaException ex) {
            setEnabled(false);
            return;
        }

    }

    public void actionPerformed() {
        try {
            if (type == SWITCH_TO_USER) {
            	if (service.canSwitchBack()) {
                    service.switchTo(null);
                    //putValue(NAME, getString("switch_to"));
                } else if ( object != null ){
                    service.switchTo((User) object);
                   // putValue(NAME, getString("switch_back"));
                }
            } else if (type == NEW) {
                User newUser = clientFacade.getRaplaFacade().newUser();
                // createInfoDialog new user dialog and show password dialog if user is created successfully
                editController.edit( newUser, popupContext);//,new EditController.EditCallback<User>()
/*
                FIXME call change password dialog after new user
                    {
                            @Override public void onFailure(Throwable e)
                            {
                                dialogUiFactory.showException( e, popupContext);
                            }

                            @Override public void onSuccess(User editObject)
                            {
                                object = editObject;
                                if (getUserModule().canChangePassword())
                                {
                                    try
                                    {
                                        changePassword(editObject, false);
                                    }
                                    catch (RaplaException e)
                                    {
                                        onFailure(e);
                                    }
                                }
                            }

                            @Override public void onAbort()
                            {
                            }
                        }
                );
*/
            }
        } catch (RaplaException ex) {
            dialogUiFactory.showException(ex, popupContext);
        }
    }

    public void changePassword(User user, boolean showOld) throws RaplaException
    {
        final PasswordChangeAction passwordChangeAction = this.passwordChangeAction.get();
        passwordChangeAction.setPopupContext( popupContext);
        passwordChangeAction.changePassword(user, showOld);
    }

    public IdentifiableMenuEntry createMenuEntry()
    {
        final Consumer<PopupContext> action = (context) -> actionPerformed();
        return menuItemFactory.createMenuItem(getName(), getIcon(), isEnabled() ? action : null);
    }

    public String getName()
    {
        return name;
    }

    public I18nIcon getIcon()
    {
        return icon;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }
}
