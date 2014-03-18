package org.rapla.server.internal;

import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.rapla.framework.RaplaException;

/**
 * This class is used for en- and decrypting the password of a user.<br>
 * <b>Note: </b>The String in <i>PEPPER</i> should be changed to a random string before building
 * the application for a productive environment!
 * 
 * @author lutz
 *
 */
public class CryptoHandler {
	private static final String ENCODING = "ISO-8859-1";
	String pepper;
	public CryptoHandler( String pepper)
	{
		this.pepper = pepper;
	}

	/**
	 * This method encrypts a passed {@link String}
	 * 
	 * @param toBeEncrypted : {@link String} which needs to be encrypted
	 * @param additive : {@link String} which is permanently associated with toBeEncrypted
	 * @return {@link String}
	 * @throws Exception
	 */
	public  String encrypt(String toBeEncrypted, String additive) throws RaplaException{
		return crypt(toBeEncrypted, additive, Cipher.ENCRYPT_MODE);
	}
	/**
	 * This method decrypts a passed {@link String}
	 * 
	 * @param toDeEncrypted : {@link String} which needs to be decrypted
	 * @param additive : {@link String} which is permanently associated with toDeEncrypted
	 * @return {@link String}
	 * @throws Exception
	 */
	public  String decrypt(String toBeDecrypted, String additive) throws RaplaException{
		return crypt(toBeDecrypted, additive, Cipher.DECRYPT_MODE);
	}
	/**
	 * This method contains the en- and decryption logic - it uses <b>AES-128Bit</b>
	 * 
	 * @param toBeEncrypted : {@link String}
	 * @param additive : {@link String}
	 * @param cryptMode : {@link Integer}
	 * @return {@link String}
	 * @throws Exception
	 */
	private  String crypt(String toBeEncrypted , String additive, int cryptMode) throws RaplaException{
		try
		{
			String returnVal = null;
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			
			byte[] key = pepper.getBytes(ENCODING);
			byte[] additiveBytes = additive.getBytes(ENCODING);
			key = sha.digest(key);
			additiveBytes = sha.digest(additiveBytes);	
	
			byte[] sessionKey =Arrays.copyOf(key, 16); // use only first 128 bit			
			byte[] iv =Arrays.copyOf(additiveBytes, 16); // use only first 128 bit		
			byte[] plaintext = toBeEncrypted.getBytes(ENCODING);
			
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(cryptMode, new SecretKeySpec(sessionKey, "AES"), new IvParameterSpec(iv));
			byte[]resultBytes = cipher.doFinal(plaintext);		
			returnVal =  new String(resultBytes, ENCODING);
	//		returnVal = resultBytes.toString();
			return returnVal;
		} catch ( Exception ex)
		{
			throw new RaplaException(ex.getMessage(), ex);
		}
		
	}

}
