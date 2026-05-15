package org.rapla.server.spring.oauth.external;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssuerAwareJwtDecoderTest
{
    private static final String LOCAL = "http://localhost:8051";
    private static final String MICROSOFT = "https://login.microsoftonline.com/tenant-123/v2.0";
    private static final String GOOGLE = "https://accounts.google.com";

    @Test
    void routesToLocalDecoderForLocalIssuer() throws Exception
    {
        CountingDecoder local = new CountingDecoder();
        CountingDecoder microsoft = new CountingDecoder();
        IssuerAwareJwtDecoder dec = new IssuerAwareJwtDecoder(Map.of(
                LOCAL, local,
                MICROSOFT, microsoft));

        dec.decode(unsignedTokenWithIssuer(LOCAL));

        assertEquals(1, local.calls.get());
        assertEquals(0, microsoft.calls.get());
    }

    @Test
    void routesToMicrosoftDecoderForMicrosoftIssuer() throws Exception
    {
        CountingDecoder local = new CountingDecoder();
        CountingDecoder microsoft = new CountingDecoder();
        IssuerAwareJwtDecoder dec = new IssuerAwareJwtDecoder(Map.of(
                LOCAL, local,
                MICROSOFT, microsoft));

        dec.decode(unsignedTokenWithIssuer(MICROSOFT));

        assertEquals(0, local.calls.get());
        assertEquals(1, microsoft.calls.get());
    }

    @Test
    void coexistsWithAllThreeIssuers() throws Exception
    {
        CountingDecoder local = new CountingDecoder();
        CountingDecoder microsoft = new CountingDecoder();
        CountingDecoder google = new CountingDecoder();
        IssuerAwareJwtDecoder dec = new IssuerAwareJwtDecoder(Map.of(
                LOCAL, local,
                MICROSOFT, microsoft,
                GOOGLE, google));

        dec.decode(unsignedTokenWithIssuer(LOCAL));
        dec.decode(unsignedTokenWithIssuer(MICROSOFT));
        dec.decode(unsignedTokenWithIssuer(GOOGLE));

        assertEquals(1, local.calls.get());
        assertEquals(1, microsoft.calls.get());
        assertEquals(1, google.calls.get());
    }

    @Test
    void rejectsUnknownIssuer() throws Exception
    {
        CountingDecoder local = new CountingDecoder();
        IssuerAwareJwtDecoder dec = new IssuerAwareJwtDecoder(Map.of(LOCAL, local));

        String token = unsignedTokenWithIssuer("https://evil.example.com");

        BadJwtException ex = assertThrows(BadJwtException.class, () -> dec.decode(token));
        assertEquals(0, local.calls.get());
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("evil.example.com"));
    }

    @Test
    void rejectsTokenWithoutIssuerClaim() throws Exception
    {
        CountingDecoder local = new CountingDecoder();
        IssuerAwareJwtDecoder dec = new IssuerAwareJwtDecoder(Map.of(LOCAL, local));

        String token = unsignedTokenWithIssuer(null);

        assertThrows(BadJwtException.class, () -> dec.decode(token));
        assertEquals(0, local.calls.get());
    }

    @Test
    void rejectsMalformedToken()
    {
        CountingDecoder local = new CountingDecoder();
        IssuerAwareJwtDecoder dec = new IssuerAwareJwtDecoder(Map.of(LOCAL, local));

        assertThrows(BadJwtException.class, () -> dec.decode("not.a.jwt"));
        assertThrows(BadJwtException.class, () -> dec.decode(""));
        assertEquals(0, local.calls.get());
    }

    @Test
    void rejectsNullToken()
    {
        CountingDecoder local = new CountingDecoder();
        IssuerAwareJwtDecoder dec = new IssuerAwareJwtDecoder(Map.of(LOCAL, local));

        assertThrows(BadJwtException.class, () -> dec.decode(null));
    }

    @Test
    void delegatesSignatureValidationToInnerDecoder() throws Exception
    {
        CountingDecoder local = new CountingDecoder();
        local.errorOnNext = new BadJwtException("bad sig");

        IssuerAwareJwtDecoder dec = new IssuerAwareJwtDecoder(Map.of(LOCAL, local));

        BadJwtException ex = assertThrows(BadJwtException.class,
                () -> dec.decode(unsignedTokenWithIssuer(LOCAL)));
        assertEquals("bad sig", ex.getMessage());
        assertEquals(1, local.calls.get(), "inner decoder must have been called");
    }

    @Test
    void requiresNonEmptyMap()
    {
        assertThrows(IllegalArgumentException.class, () -> new IssuerAwareJwtDecoder(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new IssuerAwareJwtDecoder(null));
    }

    /**
     * Builds a JWT with the given issuer claim, signed with HS256 using a
     * fixed secret. The decoder under test doesn't actually verify the
     * signature here (we route to a stub) — we just need a parseable token
     * structure.
     */
    private static String unsignedTokenWithIssuer(String issuer) throws JOSEException
    {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject("user-id")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(60)));
        if (issuer != null) claims = claims.issuer(issuer);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        byte[] secret = new byte[32];
        for (int i = 0; i < 32; i++) secret[i] = (byte) i;
        jwt.sign(new MACSigner(secret));
        return jwt.serialize();
    }

    private static final class CountingDecoder implements JwtDecoder
    {
        final AtomicInteger calls = new AtomicInteger();
        RuntimeException errorOnNext;

        @Override
        public Jwt decode(String token)
        {
            calls.incrementAndGet();
            if (errorOnNext != null)
            {
                RuntimeException toThrow = errorOnNext;
                errorOnNext = null;
                throw toThrow;
            }
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "HS256")
                    .claim("iss", "stub")
                    .claim("sub", "user-id")
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(60))
                    .build();
        }

        @SuppressWarnings("unused")
        void unusedAssertSameToken(Jwt jwt, Jwt other) { assertSame(jwt, other); }
    }
}
