package org.rapla.plugin.urlencryption.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UrlCipherV2Test
{
    private static byte[] key() throws Exception
    {
        return UrlCipherV2.deriveKey("a-system-root-key".getBytes("UTF-8"));
    }

    @Test
    public void roundTrips() throws Exception
    {
        byte[] k = key();
        String plain = "user=homer&file=Export";
        String enc = UrlCipherV2.encrypt(k, plain);
        assertTrue(enc.startsWith("v2:"), enc);
        assertEquals(plain, UrlCipherV2.decrypt(k, enc));
    }

    @Test
    public void isDeterministicSoTheUrlStaysStable() throws Exception
    {
        byte[] k = key();
        String plain = "user=homer&file=Export";
        // same plaintext → byte-identical URL on every generation (no random IV)
        assertEquals(UrlCipherV2.encrypt(k, plain), UrlCipherV2.encrypt(k, plain));
        // different plaintext → different ciphertext
        assertNotEquals(UrlCipherV2.encrypt(k, plain), UrlCipherV2.encrypt(k, "user=monty&file=Export"));
    }

    @Test
    public void detectsV2Marker() throws Exception
    {
        assertTrue(UrlCipherV2.isV2(UrlCipherV2.encrypt(key(), "x")));
        assertFalse(UrlCipherV2.isV2("AbCdLegacyEcbBase64NoMarker"));
        assertFalse(UrlCipherV2.isV2(null));
    }

    @Test
    public void tamperedCiphertextIsRejected() throws Exception
    {
        byte[] k = key();
        String enc = UrlCipherV2.encrypt(k, "user=homer&file=Export");
        String tampered = enc.substring(0, enc.length() - 2) + (enc.endsWith("A") ? "B" : "A");
        // GCM auth tag fails on any modification (unlike the malleable ECB path)
        assertThrows(Exception.class, () -> UrlCipherV2.decrypt(k, tampered));
    }
}
