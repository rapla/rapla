package org.rapla.client.internal;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class PkceUtilTest
{
    @Test
    public void randomUrlSafeIsUrlSafeAndNonEmpty()
    {
        String s = PkceUtil.randomUrlSafe(32);
        assertTrue("verifier should be at least 43 chars (RFC 7636)", s.length() >= 43);
        assertFalse("must not contain padding '='", s.contains("="));
        assertTrue("must be URL-safe base64", s.matches("[A-Za-z0-9_-]+"));
    }

    @Test
    public void randomUrlSafeProducesUniqueValues()
    {
        String a = PkceUtil.randomUrlSafe(32);
        String b = PkceUtil.randomUrlSafe(32);
        assertNotEquals(a, b);
    }

    @Test
    public void codeChallengeIsBase64UrlOfSha256() throws Exception
    {
        String verifier = "verifier-abc-123_~";
        byte[] expected = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        String expectedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(expected);
        assertEquals(expectedChallenge, PkceUtil.codeChallenge(verifier));
    }
}
