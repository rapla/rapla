package org.rapla.server.spring.web;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.mail.MailConfigService;
import org.rapla.server.RemoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean({MailConfigService.class, RemoteSession.class})
@RequestMapping(value = "/api/mail/config", produces = "application/json")
public class MailConfigController
{
    private final MailConfigService service;

    public MailConfigController(MailConfigService service)
    {
        this.service = service;
    }

    @GetMapping("/external")
    public boolean isExternalConfigEnabled() throws RaplaException
    {
        return service.isExternalConfigEnabled();
    }

    @PostMapping
    public void testMail(@RequestBody DefaultConfiguration config,
                         @RequestParam(value = "defaultSender", required = false) String defaultSender) throws RaplaException
    {
        service.testMail(config, defaultSender);
    }

    @GetMapping
    public DefaultConfiguration getConfig() throws RaplaException
    {
        return service.getConfig();
    }
}
