package org.rapla.plugin.urlencryption.server;

import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.urlencryption.UrlEncryption;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

/**
 * This class provides functionality to encrypt URL parameters to secure the resource export.
 * The class runs on the server and implements the Interface UrlEncryption which provides
 * encryption service to all clients and some minor utilities.
 *
 * @author Jonas Kohlbrenner
*/
@DefaultImplementation(of = UrlEncryption.class, context = InjectionContext.server)
@Singleton
public class UrlEncryptionService implements UrlEncryption
{
    @Inject
    UrlEncryptor urlEncryptor;
    
    private final HttpServletRequest request;

    /**
     * Initializes the Url encryption plugin.
     * Checks whether an encryption key exists or not, reads an existing one from the configuration file
     * or generates a new one. The decryption and encryption ciphers are also initialized here.
     *
     * @throws RaplaException
     */
    @Inject
    public UrlEncryptionService(@Context HttpServletRequest request) throws RaplaInitializationException
    {
        this.request = request;//, InvalidKeyException {
    }

    /**
     * Encrypts any text using the generated encryption key.
     *
     * @param plain Plain text
     * @return String The encrypted result or null in case of an exception
     */
    @Override
    public String encrypt(String plain) throws RaplaException
    {
        return urlEncryptor.encrypt(plain, request);
    }

}
