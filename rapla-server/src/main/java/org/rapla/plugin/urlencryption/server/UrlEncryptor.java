package org.rapla.plugin.urlencryption.server;

import org.apache.commons.codec.binary.Base64;
import org.jetbrains.annotations.NotNull;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.plugin.urlencryption.UrlEncryption;
import org.rapla.plugin.urlencryption.UrlEncryptionPlugin;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.RemoteSession;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.HttpServletRequest;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyException;
import java.security.NoSuchAlgorithmException;

public class UrlEncryptor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(UrlEncryptor.class);

    @Deprecated
    private static final TypedComponentRole<String> KEY_PREFERENCE_ENTRY = new TypedComponentRole<>("org.rapla.plugin.urlencryption.urlEncKey");
    private static final int NUM_SHUFFELS = 2;
    private final String syncEncryptionAlg = "AES/ECB/PKCS5Padding";

    private Cipher encryptionCipher;
    private Cipher decryptionCipher;
    private byte[] rootKeyBytes;   // raw system root key for the v2 (AES-256-GCM) key derivation

    private final RaplaFacade facade;
    private final RaplaKeyStorage keyStore;
    private final RemoteSession session;

    @Autowired
    public UrlEncryptor(RaplaFacade facade, RaplaKeyStorage keyStore, RemoteSession session)
    {
        super();
        this.facade = facade;
        this.keyStore = keyStore;
        this.session = session;
    }

    /**
     * Encrypts any text using the generated encryption key.
     *
     * @param plain Plain text
     * @return String The encrypted result or null in case of an exception
     */
    public synchronized String encrypt(String plain, HttpServletRequest request, String algo) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        return encrypt(plain, user.getId(), algo);
    }

    @NotNull
    public synchronized String encrypt(String plain, String userId, String algo) throws RaplaException
    {
        final Base64 base64 = initForRequest();
        if (UrlEncryptionPlugin.ALGO_V2.equals(algo))
        {
            // New export → authenticated, deterministic AES-256-GCM.
            try
            {
                return UrlCipherV2.encrypt(UrlCipherV2.deriveKey(rootKeyBytes), plain);
            }
            catch (Exception e)
            {
                throw new RaplaException(e.getMessage(), e);
            }
        }
        // Legacy AES/ECB — keeps existing export URLs byte-stable (algo "true"/null).
        final String salt = Integer.toString(userId.hashCode());
        try
        {
            String valueToEnc = null;
            String eValue = plain;
            for(int i = 0; i < NUM_SHUFFELS; i++)
            {
                valueToEnc = salt + eValue;
                byte[] encValue = this.encryptionCipher.doFinal(valueToEnc.getBytes());
                eValue = base64.encodeAsString(encValue);
            }
            return eValue + "&" + UrlEncryption.ENCRYPTED_SALT_PARAMETER_NAME + "=" + salt;
        }
        catch (IllegalBlockSizeException e)
        {
            // Something went wrong while converting the plain String into byte array
        }
        catch (BadPaddingException e)
        {
            // Something went wrong while initializing the used cipher
        }
        return "";
    }

    /**
     * Decrypts the provided string using the encryption key defined at the plugins initialization.
     *
     * @param encrypted Encrypted string
     * @return String Plain string
     * @throws Exception If the String could't be decrypted.
     */
    public synchronized String decrypt(String encrypted, String salt) throws Exception
    {
        final Base64 base64 = initForRequest();
        if (UrlCipherV2.isV2(encrypted))
        {
            // v2 (AES-256-GCM) — salt param is unused; the marker drives dispatch.
            return UrlCipherV2.decrypt(UrlCipherV2.deriveKey(rootKeyBytes), encrypted);
        }
        // Legacy AES/ECB — kept forever: old subscriber URLs live in external clients.
        try
        {
            String dValue = null;
            String valueToDecrypt = encrypted;
            for(int i = 0; i < NUM_SHUFFELS; i++)
            {
                byte[] decordedValue = base64.decode(valueToDecrypt);
                byte[] decValue = decryptionCipher.doFinal(decordedValue);
                dValue = new String(decValue).substring(salt.length());
                valueToDecrypt = dValue;
            }
            return dValue;
        }
        catch (Exception e)
        {
            throw new Exception("The provided URL is not valid." + e.getMessage());
        }
    }

    private Base64 initForRequest() throws RaplaException
    {
        byte[] linebreake = {};
        Base64 base64 = new Base64(64, linebreake, true);

        // Try to read the encryption key from the plugin configuration file.
        Preferences preferences = facade.getSystemPreferences();

        // first we try the old key entry
        String keyEntry = preferences.getEntryAsString(KEY_PREFERENCE_ENTRY, null);
        boolean testingOldConfig = (keyEntry == null);
        if (testingOldConfig)
        {
            //FIXME does not work with 1.7 configurations
            //keyEntry = config.getAttribute(UrlEncryptionService.KEY_ATTRIBUTE_NAME, null);

        }
        // now use the system private key 
        if (keyEntry == null)
        {
            keyEntry = keyStore.getRootKeyBase64();
        }
        try
        {
            byte[] encryptionKey = base64.decode(keyEntry);
            if (encryptionKey == null || encryptionKey.equals(""))
                throw new InvalidKeyException("Empty key string found!");

            this.rootKeyBytes = encryptionKey;
            this.initializeCiphers(encryptionKey);
            return base64;
        }
        catch (KeyException e)
        {
            throw new RaplaException(e.getMessage(), e);
        }
    }

    /**
     * Initializes the encryption an decryption Cipher so they can be used.
     *
     */
    private void initializeCiphers(byte[] encryptionKey) throws InvalidKeyException
    {
        try
        {
            byte[] encryptionKey2;
            // We just use the first 16 bytes from the private key for encrypting the url
            if (encryptionKey.length > 16)
            {
                encryptionKey2 = new byte[16];
                System.arraycopy(encryptionKey, 0, encryptionKey2, 0, 16);
            }
            else
            {
                encryptionKey2 = encryptionKey;
            }
            Key specKey = new SecretKeySpec(encryptionKey2, "AES");
            this.encryptionCipher = Cipher.getInstance(syncEncryptionAlg);
            this.encryptionCipher.init(Cipher.ENCRYPT_MODE, specKey);

            this.decryptionCipher = Cipher.getInstance(syncEncryptionAlg);
            this.decryptionCipher.init(Cipher.DECRYPT_MODE, specKey);
        }
        catch (NoSuchAlgorithmException e)
        {
            // AES Algorithm does not exist here
            LOGGER.error("AES Algorithm does not exist here");
        }
        catch (NoSuchPaddingException e)
        {
            // AES/ECB/PKCS5 Padding missing
            LOGGER.error("AES/ECB/PKCS5 Padding missing");
        }
    }
}
