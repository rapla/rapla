package org.rapla.server.spring;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Unified token issuance for rapla. Both {@code /auth/login} (this class's
 * {@link JwtIssuer}) and {@code /oauth2/token} (Spring Authorization Server)
 * sign tokens with the same RSA key — Spring's {@link JWKSource}, owned by
 * {@code AuthorizationServerConfig}. The resource server validates both via
 * the single {@link #jwtDecoder} bean.
 *
 * <p>Pre-2026-05-12 history: rapla had a parallel HMAC token path here for
 * legacy {@code /auth/login} tokens, and the decoder was a composite that
 * tried HMAC first then RSA. PRD 026 §5 called that out as cleanup; PRD 029
 * Option A executed it. Single algorithm, single key, single decoder.
 */
@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.file-datasources", name = "raplafile")
public class JwtConfig
{
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource)
    {
        return NimbusJwtDecoder.withPublicKey(extractRsaPublicKey(jwkSource)).build();
    }

    @Bean
    public JwtIssuer jwtIssuer(JWKSource<SecurityContext> jwkSource)
    {
        return new JwtIssuer(jwkSource);
    }

    private static RSAPublicKey extractRsaPublicKey(JWKSource<SecurityContext> jwkSource)
    {
        try
        {
            List<JWK> keys = jwkSource.get(
                    new JWKSelector(new JWKMatcher.Builder().keyType(KeyType.RSA).build()),
                    null);
            if (keys.isEmpty())
            {
                throw new IllegalStateException("No RSA key in JWK set");
            }
            return ((RSAKey) keys.get(0)).toRSAPublicKey();
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Failed to extract RSA public key from JWK set", e);
        }
    }

    public static class JwtIssuer
    {
        private final JWSSigner signer;
        private final String keyId;

        JwtIssuer(JWKSource<SecurityContext> jwkSource)
        {
            try
            {
                List<JWK> keys = jwkSource.get(
                        new JWKSelector(new JWKMatcher.Builder().keyType(KeyType.RSA).build()),
                        null);
                if (keys.isEmpty())
                {
                    throw new IllegalStateException("No RSA key in JWK set");
                }
                RSAKey rsaKey = (RSAKey) keys.get(0);
                this.signer = new RSASSASigner(rsaKey.toRSAPrivateKey());
                this.keyId = rsaKey.getKeyID();
            }
            catch (Exception e)
            {
                throw new IllegalStateException("Failed to initialise RS256 JWT signer", e);
            }
        }

        public String issueAccessToken(String subject, long expiresInSeconds) throws JOSEException
        {
            return issue(subject, expiresInSeconds, "access");
        }

        public String issueRefreshToken(String subject, long expiresInSeconds) throws JOSEException
        {
            return issue(subject, expiresInSeconds, "refresh");
        }

        private String issue(String subject, long expiresInSeconds, String type) throws JOSEException
        {
            long now = System.currentTimeMillis();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issueTime(new Date(now))
                    .expirationTime(new Date(now + expiresInSeconds * 1000))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("typ", type)
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(),
                    claims);
            jwt.sign(signer);
            return jwt.serialize();
        }
    }
}
