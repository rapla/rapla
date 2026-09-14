package org.rapla.server.spring.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * PRD 118 D8-3 — fail-closed HTTP surface of the public demo. Every {@code /api} path and the Swing
 * client paths answer 404 unless allowlisted, so a new endpoint stays unreachable on the demo until
 * someone adds it here. A closed path answers identically whether it exists or not.
 */
public class DemoApiAllowlistFilter extends OncePerRequestFilter
{
    static final Set<String> OPEN_PATHS = Set.of("/api/auth/me", "/api/graphql", "/api/graphql/schema",
            "/api/storage/change/name", "/api/storage/profile/capabilities");

    static final List<String> OPEN_PREFIXES = List.of("/api/auth/session", "/api/auth/api-keys", "/api/documents",
            "/api/favorites", "/api/recents", "/api/users");

    private static final List<String> SUSPICIOUS = List.of("..", ";", "//", "\\", "%2e", "%2f", "%5c", "%3b");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
        if (isGuarded(path) && !isOpen(path, request.getRequestURI()))
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean isGuarded(String path)
    {
        return path.equals("/api") || path.startsWith("/api/") || path.equals("/raplaclient")
                || path.equals("/raplaclient.jnlp") || path.equals("/webclient") || path.startsWith("/webclient/");
    }

    static boolean isOpen(String path, String rawUri)
    {
        String raw = rawUri.toLowerCase(Locale.ROOT);
        if (SUSPICIOUS.stream().anyMatch(raw::contains))
        {
            return false;
        }
        return OPEN_PATHS.contains(path)
                || OPEN_PREFIXES.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }
}
