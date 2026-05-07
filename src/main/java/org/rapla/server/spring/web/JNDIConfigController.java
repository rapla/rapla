package org.rapla.server.spring.web;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.jndi.internal.JNDIConfig;
import org.rapla.scheduler.sync.SynchronizedCompletablePromise;
import org.rapla.server.RemoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean({JNDIConfig.class, RemoteSession.class})
@RequestMapping("/jndi")
public class JNDIConfigController
{
    private final JNDIConfig service;

    public JNDIConfigController(JNDIConfig service)
    {
        this.service = service;
    }

    @PostMapping
    public boolean test(@RequestBody JNDIConfig.MailTestRequest job) throws Exception
    {
        return SynchronizedCompletablePromise.waitFor(service.test(job), 30000, null);
    }

    @GetMapping
    public DefaultConfiguration getConfig() throws RaplaException
    {
        return service.getConfig();
    }
}
