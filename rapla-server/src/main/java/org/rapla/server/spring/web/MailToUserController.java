package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.mail.server.MailToUserImpl;
import org.rapla.server.RemoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean(RemoteSession.class)
@RequestMapping(value = "/mail/send", produces = "application/json")
public class MailToUserController
{
    private final MailToUserImpl mailToUser;
    private final RemoteSession session;

    public MailToUserController(MailToUserImpl mailToUser, RemoteSession session)
    {
        this.mailToUser = mailToUser;
        this.session = session;
    }

    @PostMapping
    public void sendMail(HttpServletRequest request,
                         @RequestParam("username") String username,
                         @RequestHeader(value = "subject", required = false) String subject,
                         @RequestBody(required = false) String body) throws RaplaException
    {
        session.checkAndGetUser(request);
        mailToUser.sendMailToUser(username, subject, body);
    }
}
