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

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.mail.MailToUserInterface;
import org.rapla.server.RemoteSession;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

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
        final User user = session.checkAndGetUser(request);
        mail.sendMail(username,subject, body);
    }
}

