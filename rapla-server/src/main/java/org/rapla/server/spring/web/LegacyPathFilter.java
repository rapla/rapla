package org.rapla.server.spring.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * PRD 109 — keeps the URLs of a Rapla 2.0 deployment alive after the move to Rapla 3.
 *
 * <p>Rapla 2.0 ran under a servlet context path ({@code /wochenplan}, {@code /btz}, …);
 * PRD 031 Phase 1 moved Rapla 3 to the root. Published calendar links (embedded in
 * customer web pages, many carrying the {@code UrlEncryptor} {@code key}/{@code salt}
 * pair) and the index bookmark must keep answering under the old prefix; everything else
 * is 301'd onto the canonical path.
 *
 * <p>Two constraints that must not be relaxed:
 * <ul>
 *   <li><b>Wrap, never forward.</b> The rewrite continues the <em>same</em> filter chain
 *       with a path-rewritten request, so the Spring Security chain downstream still sees
 *       the canonical path and applies its matchers. A {@code RequestDispatcher.forward}
 *       would skip that chain (registered for the REQUEST/ERROR dispatch) and make
 *       {@code ${prefix}/rapla/internal_*} reachable without authentication.</li>
 *   <li><b>The query string is passed through raw.</b> {@code UrlEncryptor} returns
 *       {@code <blob>&salt=<salt>}; re-encoding or re-assembling it breaks decryption.</li>
 * </ul>
 *
 * <p>The mapping is a code constant on purpose (PRD 109 D4): a configurable list is one
 * {@code /api/**} entry away from duplicating the whole live URL space under a second
 * prefix, with it the OAuth redirect-URI checks, the rate-limit path comparisons, the
 * cookie path and the CSP assumptions.
 */
public class LegacyPathFilter extends OncePerRequestFilter
{
    /** Paths (prefix already stripped) answered verbatim — no redirect, URL stays put. */
    private static final Map<String, String> VERBATIM = Map.of(
            "/", "/",
            "/index", "/index",
            "/rapla/index", "/index",
            "/rapla/calendar", "/rapla/calendar",
            "/rapla/calendar.csv", "/rapla/calendar.csv",
            "/rapla/internal_calendar", "/rapla/internal_calendar",
            "/rapla/internal_calendar.csv", "/rapla/internal_calendar.csv");

    /** Rapla 3 serves the launcher at the root, without the {@code /rapla/} segment. */
    private static final Map<String, String> REDIRECT_TARGETS = Map.of(
            "/rapla/raplaclient.jnlp", "/raplaclient.jnlp");

    private final String prefix;

    public LegacyPathFilter(String legacyContextPath)
    {
        String normalized = legacyContextPath == null ? "" : legacyContextPath.trim();
        while (normalized.endsWith("/"))
        {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.isEmpty() && !normalized.startsWith("/"))
        {
            normalized = "/" + normalized;
        }
        this.prefix = normalized;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        final String uri = request.getRequestURI();
        if (prefix.isEmpty() || !(uri.equals(prefix) || uri.startsWith(prefix + "/")))
        {
            chain.doFilter(request, response);
            return;
        }

        String rest = uri.substring(prefix.length());
        if (rest.isEmpty())
        {
            rest = "/";
        }

        final String verbatim = VERBATIM.get(rest);
        if (verbatim != null)
        {
            chain.doFilter(new RewrittenRequest(request, verbatim), response);
            return;
        }

        final String target = REDIRECT_TARGETS.getOrDefault(rest, rest);
        final String query = request.getQueryString();
        response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        response.setHeader(HttpHeaders.LOCATION, query == null ? target : target + "?" + query);
    }

    private static final class RewrittenRequest extends HttpServletRequestWrapper
    {
        private final String path;

        RewrittenRequest(HttpServletRequest request, String path)
        {
            super(request);
            this.path = path;
        }

        @Override
        public String getRequestURI()
        {
            return path;
        }

        @Override
        public String getServletPath()
        {
            return path;
        }

        @Override
        public String getPathInfo()
        {
            return null;
        }

        @Override
        public StringBuffer getRequestURL()
        {
            StringBuffer url = new StringBuffer(getScheme()).append("://").append(getServerName());
            int port = getServerPort();
            boolean defaultPort = ("http".equals(getScheme()) && port == 80) || ("https".equals(getScheme()) && port == 443);
            if (!defaultPort && port > 0)
            {
                url.append(':').append(port);
            }
            return url.append(path);
        }
    }
}
