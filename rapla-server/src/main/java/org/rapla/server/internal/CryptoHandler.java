package org.rapla.server.internal;

import org.apache.commons.codec.binary.Base64;
import org.rapla.framework.RaplaException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;


public class CryptoHandler {

	/** Marks an AES-256-GCM ciphertext. Legacy AES/ECB values carry no marker (pure base64). */
	static final String V2_PREFIX = "v2:";

	private static final String GCM_ALG = "AES/GCM/NoPadding";
	private static final int GCM_IV_BYTES = 12;
	private static final int GCM_TAG_BITS = 128;
	private static final String LEGACY_ALG = "AES/ECB/PKCS5Padding";

	private final Base64 base64;
	private final SecretKey gcmKey;       // 256-bit, SHA-256(pepper)
	private final SecretKey legacyKey;    // 128-bit, SHA-1(pepper)[0:16] — decrypt-only
	private final SecureRandom random = new SecureRandom();

	public CryptoHandler(String pepper) throws RaplaException
	{
		try {
			byte[] linebreake = {};
			this.base64 = new Base64(64, linebreake, true);
			byte[] sha256 = MessageDigest.getInstance("SHA-256").digest(pepper.getBytes(StandardCharsets.UTF_8));
			this.gcmKey = new SecretKeySpec(sha256, "AES");
			byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(pepper.getBytes(StandardCharsets.UTF_8));
			this.legacyKey = new SecretKeySpec(Arrays.copyOf(sha1, 16), "AES");
		} catch (Exception e) {
			throw new RaplaException( e.getMessage(),e);
		}
	}

	public String encrypt(String toBeEncrypted) throws RaplaException{
		try {
			byte[] iv = new byte[GCM_IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(GCM_ALG);
			cipher.init(Cipher.ENCRYPT_MODE, gcmKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] ct = cipher.doFinal(toBeEncrypted.getBytes(StandardCharsets.UTF_8));
			byte[] out = new byte[iv.length + ct.length];
			System.arraycopy(iv, 0, out, 0, iv.length);
			System.arraycopy(ct, 0, out, iv.length, ct.length);
			return V2_PREFIX + base64.encodeToString(out);
		} catch (Exception e) {
			throw new RaplaException(e.getMessage(), e);
		}
	}

	public String decrypt(String stored) throws RaplaException{
		try {
			if (stored.startsWith(V2_PREFIX)) {
				byte[] blob = base64.decode(stored.substring(V2_PREFIX.length()).getBytes(StandardCharsets.UTF_8));
				byte[] iv = Arrays.copyOfRange(blob, 0, GCM_IV_BYTES);
				byte[] ct = Arrays.copyOfRange(blob, GCM_IV_BYTES, blob.length);
				Cipher cipher = Cipher.getInstance(GCM_ALG);
				cipher.init(Cipher.DECRYPT_MODE, gcmKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
				return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
			}
			// Legacy AES/ECB — kept indefinitely: deployments update at unknown times.
			Cipher cipher = Cipher.getInstance(LEGACY_ALG);
			cipher.init(Cipher.DECRYPT_MODE, legacyKey);
			return new String(cipher.doFinal(base64.decode(stored.getBytes(StandardCharsets.UTF_8))), StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RaplaException(e.getMessage(), e);
		}
	}


}
