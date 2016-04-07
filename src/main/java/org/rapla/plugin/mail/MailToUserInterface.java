package org.rapla.plugin.mail;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.rapla.framework.RaplaException;

@Path("mail/send")
public interface MailToUserInterface 
{
    @POST
    void sendMail(@QueryParam("username")String username,@HeaderParam("subject")String subject, String body) throws RaplaException;
}
