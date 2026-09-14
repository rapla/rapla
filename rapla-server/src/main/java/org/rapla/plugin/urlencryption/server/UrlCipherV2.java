package org.rapla.plugin.urlencryption.server;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * AES-256-GCM for calendar-export URLs (PRD 071 H3), replacing the legacy AES/ECB path.
 *
 * <p><b>Deterministic by design.</b> The export URL is recomputed on demand and never
 * stored, so it must be byte-stable for a given calendar — a random IV would change the
 * URL on every view. The JDK has no {@code AES/GCM-SIV}, so the nonce is derived from the
 * plaintext: {@code IV = HMAC-SHA256(key, plain)[0:12]}. Same plaintext → same IV → same
 * URL; different plaintext → different IV, so GCM's no-nonce-reuse rule holds.
 *
 * <p>A {@code v2:} marker tags the ciphertext so {@link UrlEncryptor#decrypt} can dispatch
 * GCM vs the legacy ECB path (the latter kept forever — old subscriber URLs live in
 * external clients).
 */
public final class UrlCipherV2
{
    public static final String PREFIX = "v2:";

    private static final String GCM_ALG = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private UrlCipherV2() {}

    /** Derive the 256-bit AES key from the system root key (raw bytes). */
    public static byte[] deriveKey(byte[] rootKey) throws Exception
    {
        return MessageDigest.getInstance("SHA-256").digest(rootKey);
    }

    public static boolean isV2(String stored)
    {
        return stored != null && stored.startsWith(PREFIX);
    }

    public static String encrypt(byte[] key, String plain) throws Exception
    {
        byte[] iv = syntheticIv(key, plain);
        Cipher cipher = Cipher.getInstance(GCM_ALG);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return PREFIX + base64().encodeToString(out);
    }

    public static String decrypt(byte[] key, String stored) throws Exception
    {
        byte[] blob = base64().decode(stored.substring(PREFIX.length()).getBytes(StandardCharsets.UTF_8));
        byte[] iv = Arrays.copyOfRange(blob, 0, IV_BYTES);
        byte[] ct = Arrays.copyOfRange(blob, IV_BYTES, blob.length);
        Cipher cipher = Cipher.getInstance(GCM_ALG);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
    }

    private static byte[] syntheticIv(byte[] key, String plain) throws Exception
    {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return Arrays.copyOf(mac.doFinal(plain.getBytes(StandardCharsets.UTF_8)), IV_BYTES);
    }

    private static Base64 base64()
    {
        return new Base64(64, new byte[] {}, true);
    }
}
