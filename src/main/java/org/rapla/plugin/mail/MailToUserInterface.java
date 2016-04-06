package org.rapla.plugin.mail;

import org.rapla.framework.RaplaException;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

@Path("mail/send")
public interface MailToUserInterface 
{
    @POST
    void sendMail(@QueryParam("username")String username,@HeaderParam("subject")String subject, String body) throws RaplaException;
}
