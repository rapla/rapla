package org.rapla.plugin.mail.server;

import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.plugin.mail.MailPlugin;

import org.springframework.beans.factory.annotation.Autowired;

public class MailToUserImpl
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MailToUserImpl.class);

    final MailInterface mail;
    final RaplaFacade facade;

    @Autowired
    public MailToUserImpl(final MailInterface mail, final RaplaFacade facade)
    {
        this.mail = mail;
        this.facade = facade;
    }

    public void sendMailToUser(String userName, String subject, String body) throws RaplaException
    {
        User recipientUser = facade.getUser(userName);
        // O.K. We need to generate the mail
        String recipientEmail = recipientUser.getEmail();
        if (recipientEmail == null || recipientEmail.trim().length() == 0)
        {
            LOGGER.warn("No email address specified for user {} Can't send mail.", recipientUser.getUsername());
            return;
        }

        sendMailToEmail( recipientEmail, subject, body );

    }

    public void sendMailToEmail( String recipientEmail,String subject, String body) throws RaplaException {
        Preferences prefs = facade.getSystemPreferences();
        final String defaultSender = prefs.getEntryAsString(MailPlugin.DEFAULT_SENDER_ENTRY, "");
        mail.sendMail(defaultSender, recipientEmail, subject, body);
        LOGGER.info("Email send to user {}", recipientEmail);
    }
}
