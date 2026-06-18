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
        // The Spring HTTP-interface proxy sends String bodies as application/json,
        // wrapping the value in JSON quotes (e.g. "user=admin&file=Export" with
        // literal "). Strip a single surrounding pair before encrypting so the
        // ciphertext matches what text/plain callers (old client, direct API) produce.
        String sanitized = plain;
        if (sanitized != null && sanitized.length() >= 2
                && sanitized.charAt(0) == '"' && sanitized.charAt(sanitized.length() - 1) == '"')
        {
            sanitized = sanitized.substring(1, sanitized.length() - 1);
        }
        return urlEncryptor.encrypt(sanitized, request);
    }
}
