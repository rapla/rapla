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
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.components.i18n.I18nIcon;
import org.rapla.components.util.Tools;
import org.rapla.entities.User;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import javax.inject.Provider;

public class PasswordChangeAction {
    
    Object object;
    PopupContext popupContext;
    private final RaplaResources i18n;
    private final DialogUiFactoryInterface dialogUiFactory;
    final private MenuItemFactory menuItemFactory;
    final private ClientFacade clientFacade;
    String name;

    boolean enabled;
    I18nIcon icon;
    private final Provider<PasswordChangeView> view;

    @Inject
    public PasswordChangeAction( RaplaResources i18n,DialogUiFactoryInterface dialogUiFactory, MenuItemFactory menuItemFactory, ClientFacade clientFacade, Provider<PasswordChangeView> view) {
        this.i18n = i18n;
        this.dialogUiFactory = dialogUiFactory;
        name = i18n.format("change.format",i18n.getString("password"));
        this.menuItemFactory = menuItemFactory;
        this.clientFacade = clientFacade;
        this.view = view;
    }

    public void changeObject(Object object) {
        this.object = object;
        update();
    }

    public I18nIcon getIcon()
    {
        return icon;
    }

    public String getName()
    {
        return name;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }


    public PasswordChangeAction setPopupContext(PopupContext popupContext)
    {
        this.popupContext = popupContext;
        return this;
    }

    private void update() {
        try {
            if ( object != null && object instanceof  User)
            {
                User selectedUser = (User) object;
                User user = clientFacade.getUser();
                setEnabled(PermissionController.canAdminUser(user, selectedUser) || user.equals(selectedUser));
            }
            else
            {
                setEnabled( false);
            }
        } catch (RaplaException ex) {
            setEnabled(false);
            return;
        }

    }

    public void actionPerformed() {
        try {
            if (object == null)
                return;
            User selectedUser = (User) object;
            User user = clientFacade.getUser();
            boolean showOldPassword = !PermissionController.canAdminUser(user, selectedUser) || user.equals( selectedUser);
            changePassword(selectedUser, showOldPassword);
        } catch (RaplaException ex) {
            dialogUiFactory.showException(ex, popupContext);
        }
    }

    public void changePassword(User user,boolean showOld) throws RaplaException{
        PasswordChangeView ui = view.get();
        DialogInterface dlg = dialogUiFactory
                .createContentDialog(popupContext, ui.getComponent(), new String[] { i18n.getString("change"), i18n.getString("cancel") });
        dlg.setDefault(0);
        dlg.setTitle(i18n.format("change.format",i18n.getString("password")));
        dlg.getAction(0).setRunnable( ()
        ->
                {
                    PopupContext dlgPopupContext = dialogUiFactory.createPopupContext( ui);
                    try {
                        char[] oldPassword = showOld ? ui.getOldPassword() : new char[0];
                        char[] p1= ui.getNewPassword();
                        char[] p2= ui.getPasswordVerification();
                        if (!Tools.match(p1,p2))
                            throw new RaplaException(i18n.getString("error.passwords_dont_match"));
                        clientFacade.changePassword(user , oldPassword, p1);
                        dlg.close();
                    } catch (RaplaException ex) {
                        dialogUiFactory.showException(ex,dlgPopupContext);
                    }
                }
        );
        dlg.getAction(1).setIcon(i18n.getIcon("icon.cancel"));
        dlg.start(true);
    }

    public IdentifiableMenuEntry createMenuEntry()
    {
        final Consumer<PopupContext> action = (context) -> actionPerformed();
        return menuItemFactory.createMenuItem(getName(), getIcon(), isEnabled() ? action : null);
    }


}
