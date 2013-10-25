package org.rapla.plugin.mail;

import javax.jws.WebService;

import org.rapla.framework.RaplaException;
@WebService
public interface MailToUserInterface
{
    void sendMail(String username,String subject, String body) throws RaplaException;
}
