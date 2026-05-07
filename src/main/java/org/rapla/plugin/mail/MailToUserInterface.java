package org.rapla.plugin.mail;

import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/mail/send")
public interface MailToUserInterface
{
    @PostExchange
    void sendMail(@RequestParam("username") String username,
                  @RequestHeader("subject") String subject,
                  @RequestBody String body) throws RaplaException;
}
