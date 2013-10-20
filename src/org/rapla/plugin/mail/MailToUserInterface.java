package org.rapla.plugin.mail;

import org.rapla.framework.RaplaException;

public interface MailToUserInterface
{
    void sendMail(String username,String subject, String body) throws RaplaException;
}
