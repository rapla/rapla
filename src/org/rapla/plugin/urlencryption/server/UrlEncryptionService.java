package org.rapla.plugin.urlencryption.server;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.plugin.urlencryption.UrlEncryption;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;

/**
 * This class provides functionality to encrypt URL parameters to secure the resource export.
 * The class runs on the server and implements the Interface UrlEncryption which provides
 * encryption service to all clients and some minor utilities.
 *
 * @author Jonas Kohlbrenner
 */
public class UrlEncryptionService extends RaplaComponent implements UrlEncryption, RemoteMethodFactory<UrlEncryption> {
    @Deprecated
	private static TypedComponentRole<String> KEY_PREFERENCE_ENTRY = new TypedComponentRole<String>("org.rapla.plugin.urlencryption.urlEncKey");
    @Deprecated
    private static String KEY_ATTRIBUTE_NAME = "urlEncKey";
    String syncEncryptionAlg = "AES/ECB/PKCS5Padding";
    private byte[] encryptionKey;

    private Cipher encryptionCipher;
    private Cipher decryptionCipher;

    private Base64 base64;

    /**
     * Initializes the Url encryption plugin.
     * Checks whether an encryption key exists or not, reads an existing one from the configuration file
     * or generates a new one. The decryption and encryption ciphers are also initialized here.
     *
     * @param context
     * @param config
     * @throws RaplaException
     */
    public UrlEncryptionService(RaplaContext context, Configuration config) throws RaplaException, InvalidKeyException {
        super(context);
        byte[] linebreake = {};
        this.base64 = new Base64(64, linebreake, true);

        // Try to read the encryption key from the plugin configuration file.
    	Preferences preferences = getQuery().getSystemPreferences();

    	// first we try the old key entry
		String keyEntry = preferences.getEntryAsString(KEY_PREFERENCE_ENTRY, null);
		boolean testingOldConfig = ( keyEntry == null);
		if ( testingOldConfig)
		{
			keyEntry = config.getAttribute(UrlEncryptionService.KEY_ATTRIBUTE_NAME, null);
			
		}
		// now use the system private key 
		if ( keyEntry == null)
		{
		    RaplaKeyStorage keyStore = getContext().lookup(RaplaKeyStorage.class);
		    keyEntry = keyStore.getRootKeyBase64();
		}

		{
			this.encryptionKey = this.base64.decode(keyEntry);
            if (this.encryptionKey == null || this.encryptionKey.equals(""))
            	throw new InvalidKeyException("Empty key string found!");

            this.initializeCiphers(this.encryptionKey);
		}
    }


    /**
     * Initializes the encryption an decryption Cipher so they can be used.
     *
     * @param encryptionKey
     */
    private void initializeCiphers(byte[] encryptionKey) throws InvalidKeyException {
        try {
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
        } catch (NoSuchAlgorithmException e) {
            // AES Algorithm does not exist here
            getLogger().error("AES Algorithm does not exist here");
        } catch (NoSuchPaddingException e) {
            // AES/ECB/PKCS5 Padding missing
        	getLogger().error("AES/ECB/PKCS5 Padding missing");
        }
    }

    /**
     * Encrypts any text using the generated encryption key.
     *
     * @param plain Plain text
     * @return String The encrypted result or null in case of an exception
     */
    public synchronized String encrypt(String plain) {
        try {
            return this.base64.encodeToString(this.encryptionCipher.doFinal(plain.getBytes()));
        } catch (IllegalBlockSizeException e) {
            // Something went wrong while converting the plain String into byte array
        } catch (BadPaddingException e) {
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
    public synchronized String decrypt(String encrypted) throws Exception {
        try {
            return new String(this.decryptionCipher.doFinal(this.base64.decode(encrypted.getBytes())));
        } catch (Exception e) {
            throw new Exception("The provided URL is not valid.");
        }
    }

    public UrlEncryption createService(RemoteSession remoteSession) 
    {
        return this;
    }
}
