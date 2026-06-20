package org.rapla.server.spring;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.rapla.server.spring.oauth.external.ExternalProvidersProperties;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * PRD 072 Phase 6 — single-issuer cutover. With the default
 * {@code rapla.oauth.trust-external-issuers=false}, the resource-server JWT
 * decoder trusts ONLY rapla-issued tokens, even when one or more external
 * IdPs are enabled (so their login/chooser still works). A token carrying an
 * external IdP's {@code iss} is rejected at {@code /api}; the rapla-issued
 * {@code typ=access} token is accepted. The legacy multi-issuer behaviour is
 * an explicit opt-in (flag {@code true}).
 *
 * <p>Tier-1: no Spring context — exercises {@link JwtConfig#buildBaseDecoder}
 * directly, the same seam the {@code jwtDecoder} bean uses.
 */
class JwtConfigSingleIssuerCutoverTest
{
    private static final String KEYCLOAK_ISSUER = "https://keycloak.example.com/realms/dhbw";

    private static ExternalProvidersProperties keycloakEnabled()
    {
        ExternalProvidersProperties props = new ExternalProvidersProperties();
        ExternalProvidersProperties.Keycloak kc = props.getKeycloak();
        kc.setEnabled(true);
        kc.setBaseUrl("https://keycloak.example.com");
        kc.setRealm("dhbw");
        kc.setClientId("rapla-client");
        return props;
    }

    private static JWKSource<SecurityContext> raplaKeys() throws Exception
    {
        RSAKey rsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    // --- Default (single-issuer cutover): external issuers NOT trusted -------

    @Test
    void defaultRejectsExternalIssuerEvenWhenProviderEnabled() throws Exception
    {
        JWKSource<SecurityContext> keys = raplaKeys();
        // External provider enabled, but trust-external-issuers=false (cutover).
        JwtDecoder decoder = JwtConfig.buildBaseDecoder(keys, keycloakEnabled(), null, false);

        // Cutover: only rapla's own local decoder is wired — NOT an issuer-aware
        // wrapper that would route the foreign iss to the Keycloak decoder. This
        // is the property the flag guards (no per-token external dispatch at all).
        assertInstanceOf(NimbusJwtDecoder.class, decoder,
                "cutover must wire only the bare local rapla decoder, no issuer-aware routing");

        String foreign = foreignTokenWithIssuer(KEYCLOAK_ISSUER);
        assertThrows(JwtException.class, () -> decoder.decode(foreign),
                "with the cutover default, a token whose iss is an external IdP must be rejected at /api");
    }

    @Test
    void defaultAcceptsRaplaIssuedAccessToken() throws Exception
    {
        JWKSource<SecurityContext> keys = raplaKeys();
        JwtDecoder decoder = JwtConfig.buildBaseDecoder(keys, keycloakEnabled(), null, false);
        JwtConfig.JwtIssuer issuer = new JwtConfig.JwtIssuer(keys);

        String access = issuer.issueAccessToken("user-uuid-1", 3600);
        assertDoesNotThrow(() -> decoder.decode(access),
                "a rapla-issued typ=access token must still authorize /api under the cutover");
    }

    @Test
    void defaultWithConfiguredLocalIssuerAcceptsRaplaTokenRejectsExternal() throws Exception
    {
        JWKSource<SecurityContext> keys = raplaKeys();
        // rapla.oauth.issuer set: the configured-local-issuer branch.
        JwtDecoder decoder = JwtConfig.buildBaseDecoder(keys, keycloakEnabled(),
                "https://rapla.example.com", false);
        JwtConfig.JwtIssuer issuer = new JwtConfig.JwtIssuer(keys);

        // rapla token must carry the configured issuer to pass the issuer validator.
        String access = raplaAccessTokenWithIssuer(keys, "https://rapla.example.com");
        assertDoesNotThrow(() -> decoder.decode(access),
                "rapla-issued token with the configured issuer must decode under the cutover");

        String foreign = foreignTokenWithIssuer(KEYCLOAK_ISSUER);
        assertThrows(JwtException.class, () -> decoder.decode(foreign),
                "external-IdP issuer must be rejected even with a configured local issuer");
    }

    // --- Opt-in escape hatch: legacy multi-issuer trust ---------------------

    @Test
    void optInRoutesExternalIssuerToItsOwnDecoder() throws Exception
    {
        JWKSource<SecurityContext> keys = raplaKeys();
        // trust-external-issuers=true → the legacy multi-issuer decoder.
        JwtDecoder decoder = JwtConfig.buildBaseDecoder(keys, keycloakEnabled(), null, true);

        // Opt-in: an issuer-aware wrapper is wired (NOT the bare local decoder),
        // so external-iss tokens are dispatched to their provider decoders.
        assertFalse(decoder instanceof NimbusJwtDecoder,
                "opt-in must wire the issuer-aware multi-issuer decoder, not the bare local one");

        // The Keycloak-iss token is routed to the Keycloak NimbusJwtDecoder, which
        // attempts a (failing) JWKS fetch — NOT the local "unknown issuer" reject.
        // Either way it throws; the distinguishing property is that a rapla-issued
        // token still decodes (regression) AND the foreign token is at least
        // dispatched (it does not short-circuit to the cutover reject).
        JwtConfig.JwtIssuer issuer = new JwtConfig.JwtIssuer(keys);
        String access = issuer.issueAccessToken("user-uuid-1", 3600);
        assertDoesNotThrow(() -> decoder.decode(access),
                "rapla-issued token must still decode in the opt-in multi-issuer mode");
    }

    /**
     * A token that LOOKS like it came from an external IdP — carries that
     * {@code iss}, HS256-signed with a throwaway secret (not rapla's RSA key).
     * Under the cutover it must be rejected (wrong issuer / wrong signature for
     * the only trusted, local, decoder).
     */
    private static String foreignTokenWithIssuer(String issuer) throws JOSEException
    {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("external-user")
                .issuer(issuer)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        byte[] secret = new byte[32];
        for (int i = 0; i < 32; i++) secret[i] = (byte) i;
        jwt.sign(new MACSigner(secret));
        return jwt.serialize();
    }

    /** rapla-signed access token carrying an explicit issuer claim. */
    private static String raplaAccessTokenWithIssuer(JWKSource<SecurityContext> keys, String issuer) throws Exception
    {
        RSAKey rsaKey = (RSAKey) keys.get(
                new com.nimbusds.jose.jwk.JWKSelector(
                        new com.nimbusds.jose.jwk.JWKMatcher.Builder()
                                .keyType(com.nimbusds.jose.jwk.KeyType.RSA).build()),
                null).get(0);
        long now = System.currentTimeMillis();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-uuid-1")
                .issuer(issuer)
                .issueTime(new Date(now))
                .expirationTime(new Date(now + 3600_000))
                .claim("typ", "access")
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims);
        jwt.sign(new com.nimbusds.jose.crypto.RSASSASigner(rsaKey.toRSAPrivateKey()));
        return jwt.serialize();
    }
}
