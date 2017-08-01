package org.rapla.server.internal;

import org.apache.commons.codec.binary.Base64;
import org.rapla.framework.RaplaException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;


public class CryptoHandler {
	
    String syncEncryptionAlg = "AES/ECB/PKCS5Padding";
    private Cipher encryptionCipher;
    private Cipher decryptionCipher;
    private Base64 base64;
	private static final String ENCODING = "UTF-8";

	public CryptoHandler( String pepper) throws RaplaException
	{
		try {
	        byte[] linebreake = {};
			this.base64 = new Base64(64, linebreake, true);
	        initializeCiphers( pepper);
		} catch (Exception e) {
			throw new RaplaException( e.getMessage(),e);
		}
	}

	 private void initializeCiphers(String pepper) throws InvalidKeyException, UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException {
    	byte[] key = pepper.getBytes("UTF-8");
    	MessageDigest sha = MessageDigest.getInstance("SHA-1");
    	key = sha.digest(key);
    	key = Arrays.copyOf(key, 16); // use only first 128 bit
        Key specKey = new SecretKeySpec(key, "AES");
		this.encryptionCipher = Cipher.getInstance(syncEncryptionAlg);
        this.encryptionCipher.init(Cipher.ENCRYPT_MODE, specKey);

        this.decryptionCipher = Cipher.getInstance(syncEncryptionAlg);
        this.decryptionCipher.init(Cipher.DECRYPT_MODE, specKey);
    }

	
	public  String encrypt(String toBeEncrypted) throws RaplaException{
		try {
			return base64.encodeToString(this.encryptionCipher.doFinal(toBeEncrypted.getBytes(ENCODING)));
		} catch (Exception e) {
			throw new RaplaException(e.getMessage(), e);
		}
	}

	public  String decrypt(String toBeDecryptedBase64) throws RaplaException{
		try {
			return new String(this.decryptionCipher.doFinal(base64.decode(toBeDecryptedBase64.getBytes(ENCODING))));
		} catch (Exception e) {
			throw new RaplaException(e.getMessage(), e);
		}
	}
	

}
