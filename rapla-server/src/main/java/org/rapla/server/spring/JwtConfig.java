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
import org.rapla.facade.RaplaFacade;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.spring.oauth.external.ExternalProvidersProperties;
import org.rapla.server.spring.oauth.external.IssuerAwareJwtDecoder;
import org.rapla.server.spring.oauth.external.ProviderConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.rapla.server.spring.web.ApiKeyController;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
@EnableConfigurationProperties(ExternalProvidersProperties.class)
@org.springframework.context.annotation.Conditional(DatasourceConfiguredCondition.class)
public class JwtConfig
{
    /**
     * Resource-server JWT decoder. With no external providers enabled (the
     * default), produces a plain {@link NimbusJwtDecoder} keyed on rapla's
     * own JWKS — behaviour identical to pre-PRD-036. With one or more
     * external providers enabled, wraps each provider's decoder in an
     * {@link IssuerAwareJwtDecoder} that routes per-token by {@code iss}.
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource,
                                 ExternalProvidersProperties externalProviders,
                                 RaplaKeyStorage keyStore,
                                 RaplaFacade facade,
                                 @org.springframework.beans.factory.annotation.Value(
                                         "${rapla.oauth.issuer:}") String localIssuerOverride)
    {
        JwtDecoder base = buildBaseDecoder(jwkSource, externalProviders, localIssuerOverride);
        // PRD 043: outer wrapper dispatches typ=api_key JWTs to their own
        // verification path (per-key public JWK embedded in the JWT header,
        // membership check against RaplaKeyStorage.getAPIKeys for revocation).
        return new ApiKeyJwtDecoder(base, keyStore, facade);
    }

    static JwtDecoder buildBaseDecoder(JWKSource<SecurityContext> jwkSource,
                                       ExternalProvidersProperties externalProviders,
                                       String localIssuerOverride)
    {
        NimbusJwtDecoder local = NimbusJwtDecoder.withPublicKey(extractRsaPublicKey(jwkSource)).build();
        // B1 (security): bar rapla-issued refresh / api_key tokens from the
        // resource-server path. api_key is also intercepted earlier by
        // ApiKeyJwtDecoder; refresh has no other gate, so without this validator
        // a 30-day refresh token authorises every /api/** call (and survives
        // /oauth2/revoke). External-IdP tokens are routed by `iss` to their own
        // decoders and never carry typ=refresh, so they are unaffected.
        OAuth2TokenValidator<Jwt> raplaTokenType = new RaplaTokenTypeValidator();
        local.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), raplaTokenType));
        List<ProviderConfig> enabled = externalProviders.enabledProviders();
        if (enabled.isEmpty())
        {
            return local;
        }
        Map<String, JwtDecoder> byIssuer = new HashMap<>();
        java.util.List<IssuerAwareJwtDecoder.Route> patternRoutes = new java.util.ArrayList<>();
        // Local issuer: when rapla.oauth.issuer is configured we validate
        // against it; otherwise the local decoder accepts any issuer (its
        // tokens are still bound by signature + standard claims).
        String localIssuer = localIssuerOverride == null || localIssuerOverride.isEmpty()
                ? null : localIssuerOverride;
        if (localIssuer != null)
        {
            local.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(localIssuer), raplaTokenType));
            byIssuer.put(localIssuer, local);
        }
        else
        {
            // No configured local issuer: fall back to wildcard local entry under
            // the request-bound issuer "self" sentinel. Tokens minted by rapla
            // SAS carry an `iss` derived from the request — without a stable
            // config value here we can't pin a single map key, so we register
            // ALL local-token issuers seen at runtime by exposing the wildcard
            // route below. This keeps tier-3 tests (which use random local
            // issuers) working without forcing a config change.
            byIssuer.put("__LOCAL_SELF__", local);
        }
        for (ProviderConfig p : enabled)
        {
            NimbusJwtDecoder dec = NimbusJwtDecoder.withJwkSetUri(p.jwksUrl()).build();
            if (p.isMultiTenant())
            {
                // Multi-tenant: skip the strict issuer validator (default
                // accepts any iss); the IssuerAwareJwtDecoder pattern match
                // is the iss check. Signature + standard claims still apply.
                dec.setJwtValidator(JwtValidators.createDefault());
                patternRoutes.add(new IssuerAwareJwtDecoder.Route(p::matchesIssuer, dec));
            }
            else
            {
                // Single-issuer provider: validator pins iss to p.issuer().
                dec.setJwtValidator(JwtValidators.createDefaultWithIssuer(p.issuer()));
                byIssuer.put(p.issuer(), dec);
            }
        }
        return localIssuer == null
                ? new LocalFallbackIssuerAwareDecoder(local, byIssuer, patternRoutes)
                : new IssuerAwareJwtDecoder(byIssuer, patternRoutes);
    }

    /**
     * Variant that falls back to the configured local decoder for any
     * issuer not matching a registered external provider. Used when
     * {@code rapla.oauth.issuer} is unset and the local SAS emits
     * request-derived issuer URLs we can't pre-register at startup.
     */
    private static final class LocalFallbackIssuerAwareDecoder implements JwtDecoder
    {
        private final JwtDecoder local;
        private final IssuerAwareJwtDecoder external;
        private final java.util.Set<String> externalIssuers;
        private final java.util.List<IssuerAwareJwtDecoder.Route> externalPatterns;

        LocalFallbackIssuerAwareDecoder(JwtDecoder local,
                                        Map<String, JwtDecoder> byIssuer,
                                        java.util.List<IssuerAwareJwtDecoder.Route> patternRoutes)
        {
            this.local = local;
            Map<String, JwtDecoder> externalOnly = new HashMap<>(byIssuer);
            externalOnly.remove("__LOCAL_SELF__");
            this.externalIssuers = java.util.Set.copyOf(externalOnly.keySet());
            this.externalPatterns = patternRoutes == null ? java.util.List.of() : java.util.List.copyOf(patternRoutes);
            this.external = (externalOnly.isEmpty() && this.externalPatterns.isEmpty())
                    ? null
                    : new IssuerAwareJwtDecoder(
                            externalOnly.isEmpty() ? Map.of("__UNUSED__", local) : externalOnly,
                            this.externalPatterns);
        }

        @Override
        public Jwt decode(String token)
        {
            String iss = peekIssuer(token);
            if (iss != null && external != null)
            {
                if (externalIssuers.contains(iss)) return external.decode(token);
                for (IssuerAwareJwtDecoder.Route r : externalPatterns)
                {
                    if (r.matcher.test(iss)) return r.decoder.decode(token);
                }
            }
            return local.decode(token);
        }

        private static String peekIssuer(String token)
        {
            if (token == null) return null;
            try
            {
                return com.nimbusds.jwt.JWTParser.parse(token).getJWTClaimsSet().getIssuer();
            }
            catch (java.text.ParseException e)
            {
                return null;
            }
        }
    }

    /**
     * Rejects rapla-issued tokens whose {@code typ} claim is {@code refresh}
     * or {@code api_key} on the resource-server path (B1). Tokens without a
     * {@code typ} claim (or with any other value, e.g. an external IdP's
     * {@code typ=Bearer}) pass — those are gated by issuer routing, not here.
     */
    private static final class RaplaTokenTypeValidator implements OAuth2TokenValidator<Jwt>
    {
        private static final java.util.Set<String> REJECTED_TYPES =
                java.util.Set.of("refresh", ApiKeyController.API_KEY_TYP);

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token)
        {
            Object typ = token.getClaims().get("typ");
            if (typ instanceof String s && REJECTED_TYPES.contains(s))
            {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token",
                        "Token type '" + s + "' is not accepted on the resource server",
                        null));
            }
            return OAuth2TokenValidatorResult.success();
        }
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
            return issue(subject, null, null, expiresInSeconds, "access");
        }

        /**
         * Mint an access token carrying {@code preferred_username} and
         * {@code name} claims. PRD 051 — the SPA toolbar reads
         * {@code preferred_username} for the chip; cross-issuer
         * consistency with Keycloak/Entra tokens which already carry
         * the same OIDC standard claims; covers the refresh-grant +
         * password-grant code paths that bypass Spring AS's encoder
         * (and therefore bypass {@link
         * org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer}).
         *
         * @param subject       rapla User UUID (becomes {@code sub})
         * @param username      rapla username (becomes
         *                      {@code preferred_username}); null/empty
         *                      omitted
         * @param displayName   rapla display name (becomes {@code name});
         *                      null/empty omitted
         */
        public String issueAccessToken(String subject, String username, String displayName,
                                       long expiresInSeconds) throws JOSEException
        {
            return issue(subject, username, displayName, expiresInSeconds, "access");
        }

        public String issueRefreshToken(String subject, long expiresInSeconds) throws JOSEException
        {
            return issue(subject, null, null, expiresInSeconds, "refresh");
        }

        /**
         * Mint an access token carrying an {@code act} claim per
         * RFC 8693 § 4.1 — the token's effective subject is
         * {@code targetUserId} (with display username
         * {@code targetUsername}), but the originating actor is
         * preserved in the {@code act} object for audit/traceability.
         * Used by PRD 051 "switch to user". Same key, same algorithm
         * as {@link #issueAccessToken} — validates against the same
         * JWKS the resource server already trusts.
         *
         * @param targetUserId   the impersonated user's UUID (becomes {@code sub})
         * @param targetUsername the impersonated user's username (becomes {@code username})
         * @param actorUserId    the admin's UUID (becomes {@code act.sub})
         * @param actorUsername  the admin's username (becomes {@code act.username})
         * @param expiresInSeconds TTL in seconds (1 h matches {@code access-token-time-to-live})
         */
        public String issueImpersonationToken(String targetUserId, String targetUsername,
                                              String actorUserId, String actorUsername,
                                              long expiresInSeconds) throws JOSEException
        {
            long now = System.currentTimeMillis();
            java.util.Map<String, Object> actClaim = new java.util.LinkedHashMap<>();
            actClaim.put("sub", actorUserId);
            actClaim.put("username", actorUsername);
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(targetUserId)
                    .issueTime(new Date(now))
                    .expirationTime(new Date(now + expiresInSeconds * 1000))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("typ", "access")
                    .claim("username", targetUsername)
                    .claim("act", actClaim)
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(),
                    claims);
            jwt.sign(signer);
            return jwt.serialize();
        }

        private String issue(String subject, String username, String displayName,
                              long expiresInSeconds, String type) throws JOSEException
        {
            long now = System.currentTimeMillis();
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issueTime(new Date(now))
                    .expirationTime(new Date(now + expiresInSeconds * 1000))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("typ", type);
            if (username != null && !username.isEmpty())
            {
                builder.claim("preferred_username", username);
            }
            if (displayName != null && !displayName.isEmpty())
            {
                builder.claim("name", displayName);
            }
            JWTClaimsSet claims = builder.build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(),
                    claims);
            jwt.sign(signer);
            return jwt.serialize();
        }
    }
}
