package org.rapla.server.spring.oauth.external;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies an EXTERNAL OIDC provider's {@code id_token} on its own trust chain,
 * INDEPENDENT of the resource-server {@code jwtDecoder} bean. After the PRD 072
 * Phase 6 single-issuer cutover that bean trusts ONLY rapla-issued tokens (by
 * default), so it can no longer decode an external IdP token. Both the BFF
 * exchange ({@link org.rapla.server.spring.web.OAuthExchangeController}) and the
 * RFC 8693 token-exchange endpoint need to validate a finished external
 * id_token before re-minting a rapla token — that is what this component does.
 *
 * <p>Per provider a {@link NimbusJwtDecoder} is built from the provider's JWKS
 * URI ({@link ProviderConfig#jwksUrl()}) — signature comes from there — and the
 * following claim validators are attached:
 * <ul>
 *   <li><b>exp / nbf</b> — Spring's default timestamp validators
 *       ({@link JwtValidators#createDefault()});</li>
 *   <li><b>iss</b> — exact-equals against {@link ProviderConfig#issuer()} for a
 *       single-issuer provider, or the multi-tenant pattern
 *       ({@link ProviderConfig#matchesIssuer}) for a pattern provider;</li>
 *   <li><b>aud</b> — SECURITY-CRITICAL: the token's {@code aud} MUST contain
 *       rapla's configured {@code client_id} for this provider
 *       ({@link ProviderConfig#clientId()}). Without this an attacker could
 *       present an id_token minted for a DIFFERENT relying party (same IdP,
 *       different audience) and have rapla mint a session for it.</li>
 * </ul>
 *
 * <p>Decoders are cached per provider id (each holds a refreshing JWKS source).
 */
public class ExternalIdTokenVerifier
{
    private final ConcurrentHashMap<String, JwtDecoder> decoders = new ConcurrentHashMap<>();

    /**
     * Decode + validate the external {@code id_token} for {@code provider}.
     *
     * @return the verified {@link Jwt} (signature + iss + exp + aud all checked).
     * @throws org.springframework.security.oauth2.jwt.JwtException on any
     *         signature, issuer, expiry, or audience failure (the caller maps
     *         this to a 401 {@code invalid_token} without leaking the reason).
     */
    public Jwt verify(String idToken, ProviderConfig provider)
    {
        JwtDecoder decoder = decoders.computeIfAbsent(provider.id(), id -> decoderFor(provider));
        return decoder.decode(idToken);
    }

    /**
     * Build the per-provider decoder. Package-visible + overridable so tests can
     * substitute a {@code withPublicKey} decoder over a throwaway RSA key
     * (no live JWKS endpoint), while production uses the provider's JWKS URI.
     * The validator stack ({@link #validatorsFor}) is shared between both.
     */
    protected JwtDecoder decoderFor(ProviderConfig provider)
    {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(provider.jwksUrl()).build();
        decoder.setJwtValidator(validatorsFor(provider));
        return decoder;
    }

    /**
     * The default (exp/nbf) + iss + aud validator stack for {@code provider}.
     * Shared by the production JWKS decoder and the test public-key decoder so
     * the audience/issuer pin is identical on both paths.
     */
    public static OAuth2TokenValidator<Jwt> validatorsFor(ProviderConfig provider)
    {
        OAuth2TokenValidator<Jwt> issuer = provider.isMultiTenant()
                ? new IssuerPatternValidator(provider)
                : JwtValidators.createDefaultWithIssuer(provider.issuer());
        return new DelegatingOAuth2TokenValidator<>(issuer, audienceValidator(provider));
    }

    /**
     * {@code aud} MUST contain rapla's configured {@code client_id} for this
     * provider. Rejects a token minted for another audience even when its
     * signature + issuer are valid.
     */
    private static OAuth2TokenValidator<Jwt> audienceValidator(ProviderConfig provider)
    {
        String expectedAudience = provider.clientId();
        return new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud ->
                aud != null && aud.contains(expectedAudience));
    }

    /**
     * For multi-tenant providers (Entra {@code common}/{@code organizations})
     * the {@code iss} carries the user's home-tenant GUID, so an exact-equals
     * issuer validator can't be used; match the configured pattern instead.
     * {@link JwtValidators#createDefault()} still applies exp/nbf.
     */
    private static final class IssuerPatternValidator implements OAuth2TokenValidator<Jwt>
    {
        private final OAuth2TokenValidator<Jwt> timestamps = JwtValidators.createDefault();
        private final ProviderConfig provider;

        IssuerPatternValidator(ProviderConfig provider) { this.provider = provider; }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token)
        {
            OAuth2TokenValidatorResult base = timestamps.validate(token);
            if (base.hasErrors()) return base;
            String iss = token.getIssuer() == null ? null : token.getIssuer().toString();
            if (!provider.matchesIssuer(iss))
            {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        OAuth2ErrorCodes.INVALID_TOKEN, "The iss claim is not valid", null));
            }
            return OAuth2TokenValidatorResult.success();
        }
    }
}
