package org.rapla.server.spring;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PRD 072 #7 (broker / model A): the rapla refresh token is the <b>absolute,
 * non-sliding session cap</b> for IdP-brokered logins — rapla owns the session,
 * an IdP-side account disable is caught only at the next forced re-federation,
 * which this cap bounds. The cap was lowered 30 d → <b>21 d</b> (2026-06-19) to
 * tighten that worst-case revoke-propagation window.
 *
 * <p>Asserts the issued {@code typ=refresh} token's real lifetime ({@code exp - iat}),
 * so the test catches both a changed constant and a constant that stops being
 * wired into issuance.
 */
class RefreshTokenTtlTest
{
    private JwtConfig.JwtIssuer issuer;

    @BeforeEach
    void setUp() throws Exception
    {
        RSAKey rsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        issuer = new JwtConfig.JwtIssuer(jwkSource);
    }

    @Test
    void refreshTokenLifetimeIs21Days() throws Exception
    {
        String refresh = issuer.issueRefreshToken("user-uuid-1", RefreshSessionService.REFRESH_TOKEN_TTL_SECONDS);

        SignedJWT jwt = SignedJWT.parse(refresh);
        Date iat = jwt.getJWTClaimsSet().getIssueTime();
        Date exp = jwt.getJWTClaimsSet().getExpirationTime();
        Duration lifetime = Duration.between(iat.toInstant(), exp.toInstant());

        assertEquals(Duration.ofDays(21), lifetime,
                "refresh-token absolute cap must be 21 days (PRD 072 #7 IdP-revoke window)");
    }
}
