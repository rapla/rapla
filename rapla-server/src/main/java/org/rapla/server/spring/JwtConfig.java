package org.rapla.server.spring;

import org.rapla.components.util.DateTools;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.rapla.server.RaplaKeyStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.time.LocalDateTime;
@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.file-datasources", name = "raplafile")
public class JwtConfig
{
    @Bean
    public JwtDecoder jwtDecoder(RaplaKeyStorage keyStorage)
    {
        byte[] secretBytes = deriveHmacSecret(keyStorage.getRootKeyBase64());
        SecretKeySpec key = new SecretKeySpec(secretBytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public JwtIssuer jwtIssuer(RaplaKeyStorage keyStorage)
    {
        byte[] secretBytes = deriveHmacSecret(keyStorage.getRootKeyBase64());
        return new JwtIssuer(secretBytes);
    }

    private static byte[] deriveHmacSecret(String rootKeyBase64)
    {
        byte[] decoded;
        try
        {
            decoded = Base64.getDecoder().decode(rootKeyBase64);
        }
        catch (IllegalArgumentException e)
        {
            decoded = Base64.getUrlDecoder().decode(rootKeyBase64);
        }
        if (decoded.length >= 32)
        {
            return decoded;
        }
        byte[] expanded = new byte[32];
        for (int i = 0; i < 32; i++)
        {
            expanded[i] = decoded[i % decoded.length];
        }
        return expanded;
    }

    public static class JwtIssuer
    {
        private final JWSSigner signer;

        JwtIssuer(byte[] secretBytes)
        {
            try
            {
                this.signer = new MACSigner(secretBytes);
            }
            catch (JOSEException e)
            {
                throw new IllegalStateException("Failed to initialise HS256 JWT signer", e);
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
                    .issueTime(new java.util.Date(now))
                    .expirationTime(new java.util.Date(now + expiresInSeconds * 1000))
                    .jwtID(java.util.UUID.randomUUID().toString())
                    .claim("typ", type)
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(signer);
            return jwt.serialize();
        }
    }
}
