package org.rapla.server.spring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

/**
 * PRD 072 Phase 2 — promotes the {@code access_token} cookie to a synthetic
 * {@code Authorization: Bearer} header for requests that carry the cookie but
 * NO {@code Authorization} header, so Spring's resource-server
 * {@code BearerTokenAuthenticationFilter} authenticates the browser credential
 * with zero changes to the decoder chain (same {@code typ}-rejection applies).
 *
 * <p>Ordering is load-bearing (review B1): this filter is installed AFTER
 * {@link org.springframework.security.web.csrf.CsrfFilter} and BEFORE the
 * resource-server bearer filter. That way:
 * <ul>
 *   <li>{@code CsrfFilter} (and {@link CookieAuthCsrfMatcher}) see the ORIGINAL
 *       request — cookie present, no {@code Authorization} header — so the
 *       cookie-auth CSRF rule fires for mutating requests.</li>
 *   <li>The resource server's own {@code Not[BearerTokenRequestMatcher]} CSRF
 *       exclusion also sees the original request (header-only resolver) and does
 *       NOT exclude cookie requests from CSRF.</li>
 *   <li>The resource-server bearer filter sees the promoted header and
 *       authenticates the cookie token.</li>
 * </ul>
 * The header always wins: a request that already carries {@code Authorization}
 * is passed through untouched (Swing / iCal / API-keys unchanged).
 */
public class CookieToBearerFilter extends OncePerRequestFilter
{
    private final JwtDecoder decoder;
    private final CookieAuthSupport cookies;

    public CookieToBearerFilter(JwtDecoder decoder, CookieAuthSupport cookies)
    {
        this.decoder = decoder;
        this.cookies = cookies;
    }

    /**
     * PRD 118 D8-11 — only a cookie that still verifies is promoted. One that doesn't (signed by a
     * rotated key, expired, malformed) would fail the bearer filter on EVERY path, {@code /login}
     * included; instead the request stays anonymous and the stale access cookie is expired. The
     * refresh cookie is left alone so an expired access token can still be refreshed.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        if (request.getHeader("Authorization") == null)
        {
            String cookieToken = CookieAuthSupport.readCookie(request, CookieAuthSupport.ACCESS_TOKEN_COOKIE);
            if (cookieToken != null)
            {
                if (verifies(cookieToken))
                {
                    chain.doFilter(new BearerHeaderRequestWrapper(request, cookieToken), response);
                    return;
                }
                response.addHeader("Set-Cookie", cookies.buildAccessCookie("", 0).toString());
            }
        }
        chain.doFilter(request, response);
    }

    private boolean verifies(String token)
    {
        try
        {
            decoder.decode(token);
            return true;
        }
        catch (JwtException e)
        {
            return false;
        }
    }

    private static final class BearerHeaderRequestWrapper extends HttpServletRequestWrapper
    {
        private final String bearerValue;

        BearerHeaderRequestWrapper(HttpServletRequest request, String token)
        {
            super(request);
            this.bearerValue = "Bearer " + token;
        }

        @Override
        public String getHeader(String name)
        {
            if ("Authorization".equalsIgnoreCase(name))
            {
                return bearerValue;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name)
        {
            if ("Authorization".equalsIgnoreCase(name))
            {
                return Collections.enumeration(Collections.singletonList(bearerValue));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames()
        {
            Enumeration<String> original = super.getHeaderNames();
            java.util.List<String> names = new java.util.ArrayList<>();
            boolean hasAuth = false;
            while (original.hasMoreElements())
            {
                String n = original.nextElement();
                if ("Authorization".equalsIgnoreCase(n)) hasAuth = true;
                names.add(n);
            }
            if (!hasAuth) names.add("Authorization");
            return Collections.enumeration(names);
        }
    }
}
