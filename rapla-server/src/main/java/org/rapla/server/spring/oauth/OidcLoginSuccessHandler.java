package org.rapla.server.spring.oauth;

import com.nimbusds.jose.JOSEException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.server.IdentityClaims;
import org.rapla.server.UserProvisioner;
import org.rapla.server.spring.RefreshSessionService;
import org.rapla.server.spring.oauth.external.ExternalProvidersProperties;
import org.rapla.server.spring.oauth.external.ExternalUserResolver;
import org.rapla.server.spring.oauth.external.ProviderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * PRD 072 Phase 1 — the success-handler TAIL of the server-side
 * {@code oauth2Login()} flow. Spring's oauth2Login HEAD has already done the
 * authorize-redirect / state / PKCE / nonce / id_token signature+claims
 * validation, so by the time we run the external identity is verified-once.
 *
 * <p>This handler then:
 * <ol>
 *   <li>maps the {@code registrationId} ({@code keycloak}/{@code google}/
 *       {@code microsoft}) back to its {@link ProviderConfig},</li>
 *   <li>translates the verified {@link OidcUser} claims to an
 *       {@link IdentityClaims} blob (pure read — AGENTS.md §16),</li>
 *   <li>provisions / resolves the rapla {@link User} via {@link UserProvisioner}
 *       (the single OIDC write seam — PRD 050),</li>
 *   <li>mints a rapla access token (+ persists the refresh session) via
 *       {@link RefreshSessionService#issueAndPersist},</li>
 *   <li>sets the rapla {@code access_token} cookie (HttpOnly, Secure,
 *       SameSite=Lax). The external IdP's tokens are discarded — rapla issues
 *       its own session token.</li>
 * </ol>
 *
 * <p>Phase 2 (separate PRD work) adds the reactive-401 cookie refresh,
 * {@code /api/auth/me}, and cookie impersonation. This handler only sets the
 * cookie.
 */
public class OidcLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcLoginSuccessHandler.class);

    private final ExternalProvidersProperties externalProviders;
    private final ExternalUserResolver externalUserResolver;
    private final UserProvisioner userProvisioner;
    private final RefreshSessionService refreshSessionService;
    private final org.rapla.server.spring.CookieAuthSupport cookies;
    private final String redirectAfterLogin;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public OidcLoginSuccessHandler(ExternalProvidersProperties externalProviders,
                                   ExternalUserResolver externalUserResolver,
                                   UserProvisioner userProvisioner,
                                   RefreshSessionService refreshSessionService,
                                   org.rapla.server.spring.CookieAuthSupport cookies,
                                   String redirectAfterLogin)
    {
        this.externalProviders = externalProviders;
        this.externalUserResolver = externalUserResolver;
        this.userProvisioner = userProvisioner;
        this.refreshSessionService = refreshSessionService;
        this.cookies = cookies;
        this.redirectAfterLogin = (redirectAfterLogin == null || redirectAfterLogin.isEmpty())
                ? "/app/" : redirectAfterLogin;
        // Browser logins (SPA/explorers) have no saved request → land on /app/.
        // The Swing-SSO broker flow DOES have a saved request (the original
        // /oauth2/authorize), which SavedRequestAwareAuthenticationSuccessHandler
        // resumes so the loopback authorization_code is issued instead of dumping
        // the browser into the Angular app.
        setDefaultTargetUrl(this.redirectAfterLogin);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException
    {
        String registrationId = registrationIdOf(authentication);
        Map<String, Object> claims = claimsOf(authentication);
        if (registrationId == null || claims == null)
        {
            // Not an external OIDC login (e.g. a form-login that somehow routed
            // here). Nothing to provision; fall through to the default redirect.
            super.onAuthenticationSuccess(request, response, authentication);
            return;
        }
        ProviderConfig provider = externalProviders.byId(registrationId).orElse(null);
        if (provider == null)
        {
            LOGGER.warn("oauth2Login succeeded for unknown provider registrationId '{}'; no rapla session issued", registrationId);
            super.onAuthenticationSuccess(request, response, authentication);
            return;
        }

        try
        {
            IdentityClaims identity = externalUserResolver.claimsFor(claims, provider);
            User user = userProvisioner.provision(identity);
            // PRD 072 (Swing-SSO broker fix): re-establish the SecurityContext as the
            // rapla user (UUID principal), matching the password-grant convention
            // (raplaAuthenticationProvider). Otherwise the Authorization Server issues
            // Swing's loopback authorization_code/tokens with sub = the external OIDC
            // username, which the rapla token generators + /api (resolve sub as a rapla
            // UUID) cannot resolve → invalid_grant on the loopback token exchange.
            reAuthenticateAsRaplaUser(user, request, response);
            RefreshSessionService.IssuedTokens tokens = refreshSessionService.issueAndPersist(user);
            cookies.setAccessTokenCookie(response, tokens.accessToken(), tokens.expiresIn());
            // PRD 072 Phase 2 — also set the path-scoped refresh_token cookie so
            // the browser can do the reactive-401 refresh against /api/auth/session/refresh.
            cookies.setRefreshTokenCookie(response, tokens.refreshToken(),
                    RefreshSessionService.REFRESH_TOKEN_TTL_SECONDS);
        }
        catch (RaplaException | JOSEException e)
        {
            LOGGER.warn("Could not establish a rapla session after {} login: {}", registrationId, e.getMessage());
            // No cookie set — the SPA's first /api call will 401 and bounce to /login.
        }
        // Resume the saved request (Swing /oauth2/authorize) if present, else /app/.
        super.onAuthenticationSuccess(request, response, authentication);
    }

    /**
     * Replace the OIDC {@link Authentication} (whose name is the external
     * username) with one whose principal name is the rapla user UUID, and
     * persist it to the session — so the Authorization Server's subsequent
     * loopback {@code authorization_code}/token issuance uses {@code sub = UUID}
     * (mirrors {@code raplaAuthenticationProvider} for the password grant). Same
     * authorities shape (the {@link FactorGrantedAuthority} supplies the
     * {@code auth_time} the OIDC ID-token generator requires).
     */
    private void reAuthenticateAsRaplaUser(User user, HttpServletRequest request, HttpServletResponse response)
    {
        UsernamePasswordAuthenticationToken raplaAuth = new UsernamePasswordAuthenticationToken(
                user.getId(), null,
                List.of(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY),
                        new SimpleGrantedAuthority(user.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(raplaAuth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private static String registrationIdOf(Authentication authentication)
    {
        if (authentication instanceof OAuth2AuthenticationToken oauthToken)
        {
            return oauthToken.getAuthorizedClientRegistrationId();
        }
        return null;
    }

    private static Map<String, Object> claimsOf(Authentication authentication)
    {
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser)
        {
            // OIDC only: the id_token's signature/iss/aud were verified by the
            // oauth2Login HEAD, so these claims are trustworthy.
            return oidcUser.getClaims();
        }
        // Review N2: NO non-OIDC OAuth2User fallback. Provisioning a rapla user
        // off unverified userinfo attributes is a weaker-trust path; rapla is a
        // broker that trusts a verified id_token only. A non-OIDC provider returns
        // null here → no session, no cookie (fail-closed) until such a provider is
        // deliberately supported with its own verification.
        return null;
    }
}
