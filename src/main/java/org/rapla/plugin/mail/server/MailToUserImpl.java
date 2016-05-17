package org.rapla.plugin.mail.server;

import javax.inject.Inject;

import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.plugin.mail.MailPlugin;

public class MailToUserImpl
{

    final MailInterface mail;
    final RaplaFacade facade;
    final Logger logger;

    @Inject
    public MailToUserImpl(final MailInterface mail, final RaplaFacade facade, final Logger logger)
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
