package org.rapla.server.spring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * PRD 072 Phase 2/3 — materialize the {@code XSRF-TOKEN} cookie on every request.
 *
 * <p>Spring Security defers CSRF-token loading: with
 * {@code CookieCsrfTokenRepository.withHttpOnlyFalse()} the {@code XSRF-TOKEN}
 * cookie is written only when the token is actually read AND the response is
 * committed. Nothing in rapla reads it (the {@code /login} form has no hidden
 * {@code _csrf} field; the explorer pages + the SPA shell are static GETs), so the
 * cookie was never set — and the browser's double-submit header
 * ({@code X-XSRF-TOKEN}) could never be populated, making every cookie-auth
 * mutating POST (GraphiQL's introspection, the SPA's GraphQL mutations) 403.
 *
 * <p>This filter saves the token to the repository <b>eagerly and explicitly</b>
 * (not via the deferred-on-commit path, which proved non-deterministic under
 * Spring Security 7): if the request carries no {@code XSRF-TOKEN} cookie, it
 * generates one and writes it now, so the cookie is present before the first POST.
 * On a request that already carries the cookie it is a no-op. The repository is
 * stateless (double-submit), so on a later POST {@code CsrfFilter} re-reads the
 * same value from the request cookie and validates it against the header.
 */
public final class CsrfCookieFilter extends OncePerRequestFilter
{
    private final CookieCsrfTokenRepository repository;

    public CsrfCookieFilter(CookieCsrfTokenRepository repository)
    {
        this.repository = repository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        CsrfToken token = repository.loadToken(request);
        if (token == null)
        {
            token = repository.generateToken(request);
            repository.saveToken(token, request, response);
        }
        // Publish THIS materialized token as the request attribute so anything that
        // renders a CSRF field (the server-side /login form) reads the SAME value
        // that's in the XSRF-TOKEN cookie. Without this the controller would pull
        // the deferred CsrfToken, which generates its OWN value (a second, mismatched
        // XSRF-TOKEN cookie) → the form's _csrf never matches → cookie-auth POST /login
        // 403s whenever an access_token cookie is present (CookieAuthCsrfMatcher).
        request.setAttribute(CsrfToken.class.getName(), token);
        request.setAttribute(token.getParameterName(), token);
        chain.doFilter(request, response);
    }
}
