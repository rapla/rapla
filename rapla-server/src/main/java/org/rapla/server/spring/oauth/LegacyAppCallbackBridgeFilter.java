package org.rapla.server.spring.oauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * PRD 072 / 036 Phase 3 — TEMPORARY dev bridge, server-side variant. An external
 * IdP whose realm can only whitelist the legacy {@code /app/auth/callback}
 * redirect (no admin to add the conformant {@code /login/oauth2/code/{id}}) — DHBW
 * Mosbach on localhost — is marked with {@code legacy-callback: true} on its
 * provider entry. Its {@code ClientRegistration} then SENDS {@code /app/auth/callback},
 * and this filter 302-redirects that callback onto Spring's real per-registration
 * endpoint {@code /login/oauth2/code/{registrationId}} (query preserved). Spring
 * validates {@code state} (not the request path) and uses the SAVED redirect_uri
 * ({@code /app/auth/callback}) for the token call, so it still matches what the IdP
 * issued the code for.
 *
 * <p>The target registration is provided at construction (the single enabled
 * provider with {@code legacy-callback: true}); see {@code SecurityConfig}. The
 * ng-serve proxy already does this rewrite on :4200 (the SPA dev origin), but the
 * Swing-SSO browser hits the server directly on :8051 where there is no proxy —
 * hence this server-side counterpart. Per-provider; remove once the IdP registers
 * the conformant redirect URI.
 */
public final class LegacyAppCallbackBridgeFilter extends OncePerRequestFilter
{
    static final String LEGACY_PATH = "/app/auth/callback";

    private final String targetPath;

    public LegacyAppCallbackBridgeFilter(String targetRegistrationId)
    {
        this.targetPath = "/login/oauth2/code/" + targetRegistrationId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        if (LEGACY_PATH.equals(request.getRequestURI()))
        {
            String query = request.getQueryString();
            response.sendRedirect(query == null ? targetPath : targetPath + "?" + query);
            return;
        }
        chain.doFilter(request, response);
    }
}
