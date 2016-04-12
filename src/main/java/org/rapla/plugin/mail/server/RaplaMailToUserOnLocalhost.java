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

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.mail.MailToUserInterface;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;

@DefaultImplementation(context=InjectionContext.server, of=MailToUserInterface.class)
public class RaplaMailToUserOnLocalhost implements MailToUserInterface
{
    @Inject
    MailToUserImpl mail;
    @Inject
    RemoteSession session;
    private final HttpServletRequest request;
    @Inject public RaplaMailToUserOnLocalhost(@Context HttpServletRequest request)
    {
        this.request = request;
    }

    @Override public void sendMail(String username, String subject, String body) throws RaplaException
    {
        if (!session.isAuthentified(request))
        {
            throw new RaplaSecurityException("User needs to be authentified to use the service");
        }
        mail.sendMail(username,subject, body);
    }
}

