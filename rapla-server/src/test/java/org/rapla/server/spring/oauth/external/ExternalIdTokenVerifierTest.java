package org.rapla.server.spring.oauth.external;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tier-1 unit test for {@link ExternalIdTokenVerifier} — proves the verifier
 * validates signature + iss + exp + <b>aud</b> on an external id_token, without
 * a live IdP. The verifier is subclassed so {@code decoderFor} resolves the
 * provider's JWKS via a {@code withPublicKey} decoder over a throwaway RSA key
 * (the same {@link ExternalIdTokenVerifier#validatorsFor} stack production uses).
 *
 * <p>The {@code aud}-pin cases are the security-critical ones: a token whose
 * signature + issuer are valid but whose audience is some OTHER relying party
 * must be rejected (otherwise rapla would mint a session off a token minted for
 * a different client).
 */
class ExternalIdTokenVerifierTest
{
    private static final String ISSUER = "https://keycloak.example.com/realms/dhbw";
    private static final String RAPLA_CLIENT_ID = "rapla-client";

    private static RSAKey signingKey;
    private static ProviderConfig provider;
    private static ExternalIdTokenVerifier verifier;

    @BeforeAll
    static void setup() throws Exception
    {
        signingKey = new RSAKeyGenerator(2048).keyID("idp-key").generate();
        provider = keycloakProvider();
        // Override decoderFor: validate the signature with the test public key
        // (no JWKS HTTP fetch) but keep the production validator stack (iss/exp/aud).
        verifier = new ExternalIdTokenVerifier()
        {
            @Override
            protected JwtDecoder decoderFor(ProviderConfig p)
            {
                NimbusJwtDecoder dec;
                try
                {
                    dec = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }
                dec.setJwtValidator(ExternalIdTokenVerifier.validatorsFor(p));
                return dec;
            }
        };
    }

    @Test
    void validIdTokenPassesAndReturnsClaims()
    {
        String token = idToken(ISSUER, List.of(RAPLA_CLIENT_ID), 3600);
        Jwt jwt = verifier.verify(token, provider);
        assertEquals(ISSUER, jwt.getIssuer().toString());
        assertEquals("homer", jwt.getClaimAsString("preferred_username"));
    }

    @Test
    void wrongAudienceIsRejected()
    {
        // Signature + issuer valid, but aud is a DIFFERENT relying party.
        String token = idToken(ISSUER, List.of("some-other-client"), 3600);
        assertThrows(JwtException.class, () -> verifier.verify(token, provider));
    }

    @Test
    void multiAudienceContainingRaplaClientIsAccepted()
    {
        String token = idToken(ISSUER, List.of("other-client", RAPLA_CLIENT_ID), 3600);
        Jwt jwt = verifier.verify(token, provider);
        assertEquals("homer", jwt.getClaimAsString("preferred_username"));
    }

    @Test
    void wrongIssuerIsRejected()
    {
        String token = idToken("https://evil.example.com", List.of(RAPLA_CLIENT_ID), 3600);
        assertThrows(JwtException.class, () -> verifier.verify(token, provider));
    }

    @Test
    void expiredTokenIsRejected()
    {
        String token = idToken(ISSUER, List.of(RAPLA_CLIENT_ID), -120);
        assertThrows(JwtException.class, () -> verifier.verify(token, provider));
    }

    @Test
    void wrongSignatureIsRejected() throws Exception
    {
        RSAKey foreignKey = new RSAKeyGenerator(2048).keyID("foreign").generate();
        long now = System.currentTimeMillis();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("external-user")
                .issuer(ISSUER)
                .audience(RAPLA_CLIENT_ID)
                .issueTime(new Date(now))
                .expirationTime(new Date(now + 3600_000))
                .claim("preferred_username", "homer")
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("idp-key").build(), claims);
        jwt.sign(new RSASSASigner(foreignKey.toRSAPrivateKey()));
        String token = jwt.serialize();
        assertThrows(JwtException.class, () -> verifier.verify(token, provider));
    }

    private static String idToken(String issuer, List<String> audience, long expiresInSeconds)
    {
        try
        {
            long now = System.currentTimeMillis();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("external-user")
                    .issuer(issuer)
                    .audience(audience)
                    .issueTime(new Date(now))
                    .expirationTime(new Date(now + expiresInSeconds * 1000))
                    .claim("preferred_username", "homer")
                    .claim("name", "Homer Simpson")
                    .claim("email", "homer@example.com")
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(signingKey.toRSAPrivateKey()));
            return jwt.serialize();
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static ProviderConfig keycloakProvider()
    {
        return new ProviderConfig(
                "keycloak", ExternalProviderId.KEYCLOAK,
                "Keycloak", "keycloak", 15, true,
                RAPLA_CLIENT_ID, "",
                ISSUER,
                ISSUER + "/protocol/openid-connect/auth",
                ISSUER + "/protocol/openid-connect/token",
                ISSUER + "/protocol/openid-connect/certs",
                ISSUER + "/protocol/openid-connect/logout",
                "",
                List.of("openid", "profile", "email"),
                new LinkedHashMap<>(),
                "preferred_username", "email", "sub",
                "", true, false, false);
    }
}
