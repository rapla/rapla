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
import org.rapla.plugin.mail.MailPlugin;
import org.rapla.plugin.mail.MailToUserInterface;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;

public class RaplaMailToUserOnLocalhost extends RaplaComponent implements MailToUserInterface, RemoteMethodFactory<MailToUserInterface>
{
        public RaplaMailToUserOnLocalhost( RaplaContext context)  {
            super( context );
        }
        
        public void sendMail(String userName,String subject, String body) throws RaplaException {
            User recipientUser = getQuery().getUser( userName );
            // O.K. We need to generate the mail
            String recipientEmail = recipientUser.getEmail();
            if (recipientEmail == null || recipientEmail.trim().length() == 0) {
                getLogger().warn("No email address specified for user "  
                                 + recipientUser.getUsername()
                                 + " Can't send mail.");
                return;
            }


            final MailInterface mail = getContext().lookup(MailInterface.class);
            ClientFacade facade =  getContext().lookup(ClientFacade.class);
            Preferences prefs = facade.getSystemPreferences();
            final String defaultSender = prefs.getEntryAsString( MailPlugin.DEFAULT_SENDER_ENTRY, "");
            mail.sendMail( defaultSender, recipientEmail,subject, body);
            getLogger().getChildLogger("mail").info("Email send to user " + userName);
        }


        public MailToUserInterface createService(RemoteSession remoteSession) {
            return this;
        }

		
  
}

