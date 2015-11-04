package org.rapla.plugin.mail;

import org.rapla.framework.RaplaException;
import org.rapla.jsonrpc.common.RemoteJsonMethod;
@RemoteJsonMethod
public interface MailToUserInterface 
{
    void sendMail(String username,String subject, String body) throws RaplaException;
}
