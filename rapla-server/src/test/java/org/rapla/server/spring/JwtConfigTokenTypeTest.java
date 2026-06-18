package org.rapla.server.spring;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.server.spring.oauth.external.ExternalProvidersProperties;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * B1 (security): the resource-server JWT decoder must reject rapla-issued
 * tokens whose {@code typ} is {@code refresh} or {@code api_key}. The documented
 * guarantee (docs/authentication.md) is "refresh and api_key are rejected on the
 * resource-server path", but before this fix only {@code api_key} was special-cased
 * (by {@link ApiKeyJwtDecoder}); a {@code typ=refresh} token — RS256-signed by the
 * same key, 30 d TTL — sailed straight through the base decoder and authorised any
 * {@code /api/**} request, immune to {@code /oauth2/revoke}.
 */
class JwtConfigTokenTypeTest
{
    private JwtDecoder decoder;
    private JwtConfig.JwtIssuer issuer;

    @BeforeEach
    void setUp() throws Exception
    {
        RSAKey rsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        // No external providers, no configured local issuer: the plain local decoder path.
        decoder = JwtConfig.buildBaseDecoder(jwkSource, new ExternalProvidersProperties(), null);
        issuer = new JwtConfig.JwtIssuer(jwkSource);
    }

    @Test
    void accessTokenIsAccepted() throws Exception
    {
        String access = issuer.issueAccessToken("user-uuid-1", 3600);
        assertDoesNotThrow(() -> decoder.decode(access),
                "a normal typ=access token must still decode");
    }

    @Test
    void refreshTokenIsRejectedOnResourceServer() throws Exception
    {
        String refresh = issuer.issueRefreshToken("user-uuid-1", 3600);
        assertThrows(JwtException.class, () -> decoder.decode(refresh),
                "a typ=refresh token must NOT be accepted as an access token on the resource server");
    }
}
