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
package org.rapla.gui.internal.action.user;
import java.awt.Component;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import org.rapla.components.util.Tools;
import org.rapla.entities.User;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaAction;
import org.rapla.gui.toolkit.DialogUI;


public class PasswordChangeAction extends RaplaAction {
    
    Object object;
    Component parent;

    public PasswordChangeAction(RaplaContext context,Component parent) {
        super( context);
        this.parent = parent;
        putValue(NAME, getI18n().format("change.format",getString("password")));
    }


    public void changeObject(Object object) {
        this.object = object;
        update();
    }

    private void update() {
        User user = null;
        try {
            user = getUser();
                setEnabled(object != null && (isAdmin() || user.equals(object)));
        } catch (RaplaException ex) {
            setEnabled(false);
            return;
        }

    }

    public void actionPerformed(ActionEvent evt) {
        try {
            if (object == null)
                return;
            changePassword((User) object, !getUser().isAdmin());
        } catch (RaplaException ex) {
            showException(ex, this.parent);
        }
    }

    public void changePassword(User user,boolean showOld) throws RaplaException{
        new PasswordChangeActionA(user,showOld).start();
    }

    class PasswordChangeActionA extends AbstractAction {
        private static final long serialVersionUID = 1L;
        PasswordChangeUI ui;
        DialogUI dlg;
        User user;
        boolean showOld;

        PasswordChangeActionA(User user,boolean showOld) {
            this.user = user;
            this.showOld = showOld;
            putValue(NAME, getString("change"));
        }
        
        public void start() throws RaplaException
        {
            ui = new PasswordChangeUI(getContext(),showOld);
            dlg = DialogUI.create(getContext(),parent,true,ui.getComponent(),new String[] {getString("change"),getString("cancel")});
            dlg.setDefault(0);
            dlg.setTitle(getI18n().format("change.format",getString("password")));
            dlg.getButton(0).setAction(this);
            dlg.getButton(1).setIcon(getIcon("icon.cancel"));
            dlg.start();
        }

        public void actionPerformed(ActionEvent evt) {
            try {
                char[] oldPassword = showOld ? ui.getOldPassword() : new char[0];
                char[] p1= ui.getNewPassword();
                char[] p2= ui.getPasswordVerification();
                if (!Tools.match(p1,p2))
                    throw new RaplaException(getString("error.passwords_dont_match"));
                getUserModule().changePassword(user , oldPassword, p1);
                dlg.close();
            } catch (RaplaException ex) {
                showException(ex,dlg);
            }
        }
    }


}
