package org.rapla.server.spring.oauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * PRD 072 — TEMPORARY dev-only DHBW bridge, server-side variant. DHBW Keycloak
 * only whitelists the legacy {@code /app/auth/callback} redirect (no admin to add
 * the conformant {@code /login/oauth2/code/keycloak}). Spring's per-provider
 * callback convention (mix-up-attack defence, OAuth 2.0 Security BCP) is kept:
 * the keycloak {@code ClientRegistration} SENDS the registered
 * {@code /app/auth/callback}, and this filter 302-redirects that callback onto
 * Spring's real {@code /login/oauth2/code/keycloak} endpoint (query preserved).
 * Spring validates {@code state} (not the request path) and uses the SAVED
 * redirect_uri ({@code /app/auth/callback}) for the token call, so it still
 * matches what DHBW issued the code for.
 *
 * <p>The ng-serve proxy already does this rewrite on :4200 (the SPA dev origin),
 * but the Swing-SSO browser hits the server directly on :8051 where there is no
 * proxy — hence this server-side counterpart. Gated by
 * {@code rapla.oauth.web.dhbw-legacy-callback}; remove once DHBW registers the
 * conformant redirect URI.
 */
public final class LegacyKeycloakCallbackBridgeFilter extends OncePerRequestFilter
{
    static final String LEGACY_PATH = "/app/auth/callback";
    static final String TARGET_PATH = "/login/oauth2/code/keycloak";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        if (LEGACY_PATH.equals(request.getRequestURI()))
        {
            String query = request.getQueryString();
            response.sendRedirect(query == null ? TARGET_PATH : TARGET_PATH + "?" + query);
            return;
        }
        chain.doFilter(request, response);
    }
}
