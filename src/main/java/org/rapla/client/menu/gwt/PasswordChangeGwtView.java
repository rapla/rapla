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
package org.rapla.client.menu.gwt;

import org.rapla.client.menu.PasswordChangeView;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;

@DefaultImplementation(of=PasswordChangeView.class,context = InjectionContext.gwt)
public class PasswordChangeGwtView
    implements
    PasswordChangeView
{

    @Inject
    public PasswordChangeGwtView()
    {

    }
    @Override
    public void dontShowOldPassword()
    {
        throw new UnsupportedOperationException();
    }

    public Object getComponent() {
        return null;
    }

    @Override
    public char[] getOldPassword() {
        throw new UnsupportedOperationException();
    }

    @Override
    public char[] getNewPassword() {
        throw new UnsupportedOperationException();
    }

    @Override
    public char[] getPasswordVerification() {
        throw new UnsupportedOperationException();
    }
}









