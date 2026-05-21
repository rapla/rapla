package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.urlencryption.UrlEncryption;
import org.rapla.plugin.urlencryption.server.UrlEncryptor;
import org.rapla.server.RemoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean(RemoteSession.class)
@ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.urlencryption", matchIfMissing = true)
public class UrlEncryptionController implements UrlEncryption
{
    private final UrlEncryptor urlEncryptor;
    private final HttpServletRequest request;

    public UrlEncryptionController(UrlEncryptor urlEncryptor, HttpServletRequest request)
    {
        this.urlEncryptor = urlEncryptor;
        this.request = request;
    }

    @Override
    public String encrypt(String plain) throws RaplaException
    {
        return urlEncryptor.encrypt(plain, request);
    }
}
