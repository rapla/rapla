package org.rapla.server.spring.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rapla.server.spring.RaplaServerProperties;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * PRD 118 D8-3/D8-3a — fail-closed HTTP surface. Every guarded path answers 404 unless it is an open path or lies
 * under an open prefix ({@code rapla.api-allowlist.*}), so a new endpoint stays unreachable until the configuration
 * opens it. A closed path answers identically whether it exists or not. The guarded set and the raw-URI veto are code,
 * not configuration: a narrowed guard list would make {@code /api} fail-open (D8-3a review R1).
 */
public class DemoApiAllowlistFilter extends OncePerRequestFilter
{
    private static final List<String> SUSPICIOUS = List.of("..", ";", "//", "\\", "%2e", "%2f", "%5c", "%3b");
    private static final List<String> GUARDED_PATHS = List.of("/raplaclient", "/raplaclient.jnlp");
    private static final List<String> GUARDED_PREFIXES = List.of("/api", "/webclient");

    private final List<String> openPaths;
    private final List<String> openPrefixes;

    public DemoApiAllowlistFilter(RaplaServerProperties.ApiAllowlist lists)
    {
        this.openPaths = List.copyOf(lists.getOpenPaths());
        this.openPrefixes = List.copyOf(lists.getOpenPrefixes());
    }

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

    private static boolean underPrefix(String path, String prefix)
    {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    boolean isGuarded(String path)
    {
        return GUARDED_PATHS.contains(path) || GUARDED_PREFIXES.stream().anyMatch(prefix -> underPrefix(path, prefix));
    }

    boolean isOpen(String path, String rawUri)
    {
        String raw = rawUri.toLowerCase(Locale.ROOT);
        if (SUSPICIOUS.stream().anyMatch(raw::contains))
        {
            return false;
        }
        return openPaths.contains(path) || openPrefixes.stream().anyMatch(prefix -> underPrefix(path, prefix));
    }

    /**
     * Fails the start on an entry that would open too much or could never match: empty, {@code /}, {@code /api},
     * {@code /api/}, wildcards, a suspicious token, or anything that is neither below {@code /api/} nor a guarded
     * non-API path.
     */
    public static void validate(RaplaServerProperties.ApiAllowlist lists)
    {
        validate("open-paths", lists.getOpenPaths());
        validate("open-prefixes", lists.getOpenPrefixes());
    }

    private static void validate(String property, List<String> entries)
    {
        for (String entry : entries)
        {
            if (!isValidEntry(entry))
            {
                throw new IllegalStateException("rapla.api-allowlist." + property + ": invalid entry \"" + (entry == null ? "" : entry)
                        + "\" (must name a path below /api/ or a guarded non-API path; no wildcards, no '/', '/api', '..', ';', '//', encodings)");
            }
        }
    }

    private static boolean isValidEntry(String entry)
    {
        if (entry == null || entry.isBlank() || entry.contains("*"))
        {
            return false;
        }
        String lower = entry.toLowerCase(Locale.ROOT);
        if (SUSPICIOUS.stream().anyMatch(lower::contains))
        {
            return false;
        }
        boolean belowApi = entry.startsWith("/api/") && entry.length() > "/api/".length() && !entry.endsWith("/");
        boolean guardedNonApi = !entry.equals("/api") && (GUARDED_PATHS.contains(entry) || GUARDED_PREFIXES.contains(entry));
        return belowApi || guardedNonApi;
    }
}
