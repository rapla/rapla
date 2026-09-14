package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.urlencryption.UrlEncryption;
import org.rapla.plugin.urlencryption.server.UrlEncryptor;
import org.rapla.server.RemoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    public String encrypt(String plain, String algo) throws RaplaException
    {
        return urlEncryptor.encrypt(plain, request, algo);
    }

    /** Authenticated but not allowed to mint for that calendar → 403 (the global handler says 401). */
    @ExceptionHandler(RaplaSecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void handleForbidden(RaplaSecurityException ex)
    {
    }
}
