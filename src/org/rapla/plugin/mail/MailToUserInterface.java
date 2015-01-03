package org.rapla.plugin.mail;

import javax.jws.WebService;

import org.rapla.framework.RaplaException;
import org.rapla.rest.gwtjsonrpc.common.RemoteJsonService;
@WebService
public interface MailToUserInterface extends RemoteJsonService
{
    void sendMail(String username,String subject, String body) throws RaplaException;
}
