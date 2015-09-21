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
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.mail.MailPlugin;
import org.rapla.plugin.mail.MailToUserInterface;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;

import javax.inject.Inject;

@DefaultImplementation(of = MailToUserInterface.class, context = InjectionContext.server) public class RaplaMailToUserOnLocalhost implements MailToUserInterface
{

    final MailInterface mail;
    final ClientFacade facade;
    final Logger logger;

    @Inject public RaplaMailToUserOnLocalhost(final MailInterface mail, final ClientFacade facade, final RemoteSession session, final Logger logger)
            throws RaplaSecurityException
    {
        this(mail, facade, logger);
        if (!session.isAuthentified())
        {
            throw new RaplaSecurityException("User needs to be authentified to use the service");
        }
    }

    public RaplaMailToUserOnLocalhost(final MailInterface mail, final ClientFacade facade, final Logger logger)
    {
        this.mail = mail;
        this.facade = facade;
        this.logger = logger;
    }

    public void sendMail(String userName, String subject, String body) throws RaplaException
    {
        User recipientUser = facade.getUser(userName);
        // O.K. We need to generate the mail
        String recipientEmail = recipientUser.getEmail();
        if (recipientEmail == null || recipientEmail.trim().length() == 0)
        {
            logger.warn("No email address specified for user " + recipientUser.getUsername() + " Can't send mail.");
            return;
        }

        Preferences prefs = facade.getSystemPreferences();
        final String defaultSender = prefs.getEntryAsString(MailPlugin.DEFAULT_SENDER_ENTRY, "");
        mail.sendMail(defaultSender, recipientEmail, subject, body);
        logger.getChildLogger("mail").info("Email send to user " + userName);
    }

}

