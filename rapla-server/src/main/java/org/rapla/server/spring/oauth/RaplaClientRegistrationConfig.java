package org.rapla.server.spring.oauth;

import org.rapla.server.UserProvisioner;
import org.rapla.server.spring.RefreshSessionService;
import org.rapla.server.spring.oauth.external.ExternalProvidersProperties;
import org.rapla.server.spring.oauth.external.ExternalUserResolver;
import org.rapla.server.spring.oauth.external.ProviderConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

import java.util.ArrayList;
import java.util.List;

/**
 * PRD 072 Phase 1 — builds a Spring {@link ClientRegistrationRepository} from
 * rapla's {@link ExternalProvidersProperties} so the main security chain's
 * {@code oauth2Login()} HEAD can drive the authorize-redirect / state / PKCE /
 * nonce / id_token-validation handshake against each enabled external IdP.
 *
 * <p>One {@link ClientRegistration} per {@code enabledProviders()} entry, keyed
 * by the provider {@code id()} ({@code keycloak} / {@code google} /
 * {@code microsoft}) — that id becomes the {@code registrationId}, so the
 * callback lands at {@code /login/oauth2/code/<id>} and the SSO button links to
 * {@code /oauth2/authorization/<id>}.
 *
 * <p>Auth method per provider:
 * <ul>
 *   <li><b>No client secret</b> (Keycloak public client, Entra "SPA"): PKCE-only,
 *       {@link ClientAuthenticationMethod#NONE}. Reuses the existing public
 *       rapla-app Keycloak client — Keycloak is unchanged, its wildcard redirect
 *       already accepts the new {@code .../login/oauth2/code/keycloak} callback.</li>
 *   <li><b>Client secret set</b> (Google "Web app", Entra "Web"):
 *       {@link ClientAuthenticationMethod#CLIENT_SECRET_BASIC}.</li>
 * </ul>
 *
 * <p>A vanilla rapla deployment (no external IdP) gets an EMPTY repository (a
 * non-null bean is required because {@code spring-security-oauth2-client}'s
 * auto-config hard-depends on a {@link ClientRegistrationRepository}), and
 * {@code SecurityConfig} skips {@code oauth2Login()} when it's empty (see
 * {@link #isEmpty}), so behaviour is unchanged.
 */
@Configuration
public class RaplaClientRegistrationConfig
{
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            ExternalProvidersProperties externalProviders)
    {
        List<ProviderConfig> enabled = externalProviders.enabledProviders();
        List<ClientRegistration> registrations = new ArrayList<>(enabled.size());
        for (ProviderConfig p : enabled)
        {
            registrations.add(toRegistration(p));
        }
        if (registrations.isEmpty())
        {
            // A vanilla deployment with no external IdP should not get
            // oauth2Login(). BUT spring-security-oauth2-client (on the classpath
            // since PRD 072) auto-wires OAuth2AuthorizedClientManager, which
            // HARD-requires a ClientRegistrationRepository bean — a null/absent
            // bean fails context startup. So we always publish a repository; when
            // empty it's an EmptyClientRegistrationRepository, and SecurityConfig
            // checks emptiness (not null) before wiring oauth2Login().
            return new EmptyClientRegistrationRepository();
        }
        return new InMemoryClientRegistrationRepository(registrations);
    }

    /**
     * Returns true when {@code repo} carries no registrations — the signal
     * SecurityConfig uses to skip {@code oauth2Login()} on a vanilla (no
     * external IdP) deployment.
     */
    public static boolean isEmpty(ClientRegistrationRepository repo)
    {
        if (repo == null) return true;
        if (repo instanceof EmptyClientRegistrationRepository) return true;
        if (repo instanceof Iterable<?> it) return !it.iterator().hasNext();
        return false;
    }

    /**
     * Non-null but empty repository. Satisfies the oauth2-client auto-config's
     * hard requirement for a {@code ClientRegistrationRepository} bean while
     * carrying zero registrations (so {@code oauth2Login()} is skipped).
     */
    static final class EmptyClientRegistrationRepository
            implements ClientRegistrationRepository, Iterable<ClientRegistration>
    {
        @Override
        public ClientRegistration findByRegistrationId(String registrationId)
        {
            return null;
        }

        @Override
        public java.util.Iterator<ClientRegistration> iterator()
        {
            return java.util.Collections.emptyIterator();
        }
    }

    /**
     * PRD 072 Phase 1 — the success-handler TAIL bean. Provisions the rapla
     * User and sets the {@code access_token} cookie after a verified external
     * OIDC login. Always created (cheap); only invoked when {@code oauth2Login()}
     * is wired (i.e. when the {@link ClientRegistrationRepository} is non-empty).
     */
    @Bean
    public OidcLoginSuccessHandler oidcLoginSuccessHandler(
            ExternalProvidersProperties externalProviders,
            ExternalUserResolver externalUserResolver,
            UserProvisioner userProvisioner,
            RefreshSessionService refreshSessionService,
            org.rapla.server.spring.CookieAuthSupport cookies,
            @Value("${rapla.oauth.web.login-success-redirect:/app/}") String redirectAfterLogin)
    {
        return new OidcLoginSuccessHandler(externalProviders, externalUserResolver,
                userProvisioner, refreshSessionService, cookies, redirectAfterLogin);
    }

    private static ClientRegistration toRegistration(ProviderConfig p)
    {
        boolean confidential = p.clientSecret() != null && !p.clientSecret().isEmpty();
        // PRD 072 / 036 Phase 3 — TEMPORARY per-provider dev bridge (legacy-callback: true
        // on the provider entry). An IdP whose realm can only whitelist the legacy
        // /app/auth/callback (no admin to add the conformant /login/oauth2/code/{id}) —
        // DHBW Mosbach on localhost — sends /app/auth/callback, and LegacyAppCallbackBridgeFilter
        // 302-redirects the return onto this provider's real /login/oauth2/code/{id}.
        // Conformant per-provider callbacks (mix-up-attack defence) are otherwise the
        // convention. Per-provider, so a second Keycloak keeps its own conformant callback.
        String redirectUri = p.legacyCallback()
                ? "{baseUrl}/app/auth/callback"
                : "{baseUrl}/login/oauth2/code/{registrationId}";
        ClientRegistration.Builder builder = ClientRegistration.withRegistrationId(p.id())
                .clientId(p.clientId())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                // PRD 072 / review SF1: rapla is a broker (identity-only, #7=a) — it
                // verifies the IdP id_token once at login and discards the IdP tokens.
                // It must NOT request a refresh token it will throw away, so strip
                // offline_access from the oauth2Login HEAD scopes (Microsoft's default
                // set includes it; Keycloak/Google defaults don't).
                .scope(p.scopes().stream()
                        .filter(s -> !"offline_access".equalsIgnoreCase(s))
                        .toArray(String[]::new))
                .authorizationUri(p.authorizeUrl())
                .tokenUri(p.tokenUrl())
                .jwkSetUri(p.jwksUrl())
                .userNameAttributeName(usernameAttribute(p))
                .clientName(p.displayName());
        if (confidential)
        {
            builder.clientSecret(p.clientSecret())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        }
        else
        {
            // Public client: PKCE is the security, no secret on the token request.
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        }
        // Pattern-matched (multi-tenant Entra) issuers can't be pinned to a single
        // string; leave issuerUri unset there and rely on signature + jwks. Fixed
        // issuers are set so Spring validates the id_token `iss`.
        if (!p.isMultiTenant() && p.issuer() != null && !p.issuer().isEmpty())
        {
            builder.issuerUri(p.issuer());
        }
        return builder.build();
    }

    private static String usernameAttribute(ProviderConfig p)
    {
        // Prefer the provider's configured username claim; fall back to `sub`.
        String claim = p.usernameClaim();
        return (claim == null || claim.isEmpty()) ? IdTokenClaimNames.SUB : claim;
    }
}
