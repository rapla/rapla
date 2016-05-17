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
package org.rapla.client.swing.internal.action.user;

import java.awt.Component;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.RaplaAction;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.components.util.Tools;
import org.rapla.entities.User;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.storage.PermissionController;


public class PasswordChangeAction extends RaplaAction {
    
    Object object;
    PopupContext popupContext;
    private final RaplaImages raplaImages;
    private final DialogUiFactoryInterface dialogUiFactory;

    public PasswordChangeAction(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,PopupContext popupContext, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory) {
        super(facade, i18n, raplaLocale, logger);
        this.popupContext = popupContext;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        putValue(NAME, getI18n().format("change.format",getString("password")));
    }

    public void changeObject(Object object) {
        this.object = object;
        update();
    }

    private void update() {
        try {
            if ( object != null && object instanceof  User)
            {
                User selectedUser = (User) object;
                User user = getUser();
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
            User user = getUser();
            boolean showOldPassword = !PermissionController.canAdminUser(user, selectedUser);
            changePassword(selectedUser, showOldPassword);
        } catch (RaplaException ex) {
            dialogUiFactory.showException(ex, popupContext);
        }
    }

    public void changePassword(User user,boolean showOld) throws RaplaException{
        new PasswordChangeActionA(user,showOld).start();
    }

    class PasswordChangeActionA implements Runnable {
        private static final long serialVersionUID = 1L;
        PasswordChangeUI ui;
        DialogInterface dlg;
        User user;
        boolean showOld;

        PasswordChangeActionA(User user,boolean showOld) {
            this.user = user;
            this.showOld = showOld;
            putValue(NAME, getString("change"));
        }
        
        public void start() throws RaplaException
        {
            ui = new PasswordChangeUI(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), showOld);
            dlg = dialogUiFactory.create(popupContext,true,ui.getComponent(),new String[] {getString("change"),getString("cancel")});
            dlg.setDefault(0);
            dlg.setTitle(getI18n().format("change.format",getString("password")));
            dlg.getAction(0).setRunnable(this);
            dlg.getAction(1).setIcon("icon.cancel");
            dlg.start(true);
        }
        
        public void run()
        {
            try {
                char[] oldPassword = showOld ? ui.getOldPassword() : new char[0];
                char[] p1= ui.getNewPassword();
                char[] p2= ui.getPasswordVerification();
                if (!Tools.match(p1,p2))
                    throw new RaplaException(getString("error.passwords_dont_match"));
                getUserModule().changePassword(user , oldPassword, p1);
                dlg.close();
            } catch (RaplaException ex) {
                dialogUiFactory.showException(ex,new SwingPopupContext((Component)dlg, null));
            }
        }

    }


}
