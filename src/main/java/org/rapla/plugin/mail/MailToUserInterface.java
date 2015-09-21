package org.rapla.plugin.mail;

import javax.jws.WebService;

import org.rapla.framework.RaplaException;
import org.rapla.gwtjsonrpc.RemoteJsonMethod;
import org.rapla.gwtjsonrpc.common.RemoteJsonService;
@WebService
@RemoteJsonMethod
public interface MailToUserInterface extends RemoteJsonService
{
    void sendMail(String username,String subject, String body) throws RaplaException;
}
