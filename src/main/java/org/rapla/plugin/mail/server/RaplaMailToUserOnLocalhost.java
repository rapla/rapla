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
package org.rapla.plugin.mail.server;

import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.mail.MailToUserInterface;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;

import javax.inject.Inject;

@DefaultImplementation(of = MailToUserInterface.class, context = InjectionContext.server)
public class RaplaMailToUserOnLocalhost implements MailToUserInterface
{

    final MailToUserImpl mail;
    final RemoteSession session;
    @Inject public RaplaMailToUserOnLocalhost(final MailToUserImpl mail, final RemoteSession session)
            throws RaplaSecurityException
    {
        this.mail = mail;
        this.session = session;
        if (!session.isAuthentified())
        {
            throw new RaplaSecurityException("User needs to be authentified to use the service");
        }
    }

    @Override public void sendMail(String username, String subject, String body) throws RaplaException
    {
        mail.sendMail(username,subject, body);
    }
}

