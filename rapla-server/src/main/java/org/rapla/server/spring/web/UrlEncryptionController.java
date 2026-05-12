package org.rapla.server.spring.web;

import org.rapla.framework.RaplaException;
import org.rapla.plugin.urlencryption.UrlEncryption;
import org.rapla.server.RemoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean({UrlEncryption.class, RemoteSession.class})
@RequestMapping(value = "/urlencryption", produces = "application/json")
public class UrlEncryptionController
{
    private final UrlEncryption service;

    public UrlEncryptionController(UrlEncryption service)
    {
        this.service = service;
    }

    @PostMapping
    public String encrypt(@RequestBody String plain) throws RaplaException
    {
        return service.encrypt(plain);
    }
}
