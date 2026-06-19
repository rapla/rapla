package org.rapla.server.spring;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Set;

/**
 * PRD 072 Phase 2 (review B1) — fires CSRF protection ONLY for
 * cookie-authenticated mutating requests.
 *
 * <p>A request needs the {@code X-XSRF-TOKEN} double-submit token when ALL of:
 * <ul>
 *   <li>it is a state-changing method ({@code POST}/{@code PUT}/{@code PATCH}/
 *       {@code DELETE}); safe methods are always exempt,</li>
 *   <li>it carries the {@code access_token} cookie (the browser credential), AND</li>
 *   <li>it has NO {@code Authorization} header.</li>
 * </ul>
 *
 * <p>Everything else is exempt:
 * <ul>
 *   <li>Header-Bearer requests (Swing / iCal / API-keys) — an attacker's page
 *       cannot set an {@code Authorization} header cross-site, so they need no
 *       CSRF token.</li>
 *   <li>The CURRENT SPA's plain session-cookie / {@code JSESSIONID} POSTs that
 *       carry no {@code access_token} cookie — left untouched so the existing
 *       flows keep working until they migrate to the cookie model.</li>
 * </ul>
 */
public class CookieAuthCsrfMatcher implements RequestMatcher
{
    private static final Set<String> SAFE_METHODS =
            Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(),
                    HttpMethod.TRACE.name(), HttpMethod.OPTIONS.name());

    @Override
    public boolean matches(HttpServletRequest request)
    {
        if (SAFE_METHODS.contains(request.getMethod()))
        {
            return false;
        }
        boolean hasAuthorizationHeader = request.getHeader("Authorization") != null;
        if (hasAuthorizationHeader)
        {
            return false;
        }
        return CookieAuthSupport.readCookie(request, CookieAuthSupport.ACCESS_TOKEN_COOKIE) != null;
    }
}
