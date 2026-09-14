package org.rapla.server.internal;

import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CryptoHandlerTest
{
    private static final String PEPPER = "a-test-root-key-base64-ish-string";

    @Test
    public void gcmRoundTrips() throws Exception
    {
        CryptoHandler handler = new CryptoHandler(PEPPER);
        String plain = "homer:s3cr3t-exchange-pw";
        String encrypted = handler.encrypt(plain);
        assertEquals(plain, handler.decrypt(encrypted));
    }

    @Test
    public void newCiphertextIsGcmNotEcb() throws Exception
    {
        // GCM uses a random IV → encrypting the same plaintext twice yields different
        // ciphertext (ECB would be byte-identical). Locks "we are no longer on ECB".
        CryptoHandler handler = new CryptoHandler(PEPPER);
        String plain = "homer:s3cr3t-exchange-pw";
        assertNotEquals(handler.encrypt(plain), handler.encrypt(plain));
        // ...and both still decrypt back to the same cleartext.
        assertEquals(plain, handler.decrypt(handler.encrypt(plain)));
    }

    @Test
    public void decryptsLegacyEcbCiphertext() throws Exception
    {
        // A value written by the OLD AES/ECB/SHA-1 code must keep decrypting forever
        // (deployments update at unknown times; we never re-key the store).
        String plain = "monty:legacy-pw";
        String legacy = legacyEcbEncrypt(PEPPER, plain);
        CryptoHandler handler = new CryptoHandler(PEPPER);
        assertEquals(plain, handler.decrypt(legacy));
    }

    @Test
    public void legacyValueHasNoVersionMarkerNewValueDoes() throws Exception
    {
        // The version marker is what lets decrypt dispatch GCM vs legacy. A legacy
        // blob must be recognisably un-tagged; a fresh blob must be tagged.
        String plain = "homer:pw";
        String legacy = legacyEcbEncrypt(PEPPER, plain);
        CryptoHandler handler = new CryptoHandler(PEPPER);
        String fresh = handler.encrypt(plain);
        assertTrue(fresh.startsWith(CryptoHandler.V2_PREFIX), "fresh ciphertext must carry the v2 marker");
        assertTrue(!legacy.startsWith(CryptoHandler.V2_PREFIX), "legacy ciphertext must not carry the marker");
    }

    /** Reproduces the exact pre-H3 scheme: SHA-1(pepper)[0:16] key, AES/ECB/PKCS5Padding, url-safe base64. */
    private static String legacyEcbEncrypt(String pepper, String plain) throws Exception
    {
        byte[] key = MessageDigest.getInstance("SHA-1").digest(pepper.getBytes(StandardCharsets.UTF_8));
        key = Arrays.copyOf(key, 16);
        Key specKey = new SecretKeySpec(key, "AES");
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, specKey);
        Base64 base64 = new Base64(64, new byte[] {}, true);
        return base64.encodeToString(c.doFinal(plain.getBytes("UTF-8")));
    }
}
