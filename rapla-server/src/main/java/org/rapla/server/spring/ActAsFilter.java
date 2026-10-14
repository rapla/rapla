package org.rapla.server.spring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.storage.PermissionController;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PRD 131 D7/D12 — per-tab act-as. Runs right after the resource-server bearer filter. A cookie-authenticated request
 * under {@code /api} must say who it acts as: header {@code X-Rapla-Act-As} = {@code self} or a user id ({@code GET
 * /api/documents/**} may use the query parameter {@code actAs} instead and defaults to {@code self}). A permitted id
 * replaces the authentication with the shape of an impersonation token ({@code sub} = target, {@code act} = actor), so
 * nothing downstream reads the header. A Bearer request (API key, Swing) never acts as anybody: the header or
 * parameter there is rejected, as is any id on a token that already carries {@code act}.
 *
 * <p>Unknown and not-permitted ids answer identically (403, empty body, AGENTS.md section 12). Public spec documents
 * stay reachable by plain links (GraphiQL's SDL link).
 */
public class ActAsFilter extends OncePerRequestFilter
{
    public static final String HEADER = "X-Rapla-Act-As";
    public static final String PARAMETER = "actAs";
    private static final String SELF = "self";

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("rapla");
    private static final int AUDITED_MAX = 4096;

    private final Map<String, Boolean> audited = java.util.Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest)
        {
            return size() > AUDITED_MAX;
        }
    });

    private final JwtUserResolver jwtUserResolver;
    private final StorageOperator operator;
    private final boolean impersonationEnabled;

    public ActAsFilter(JwtUserResolver jwtUserResolver, StorageOperator operator, boolean impersonationEnabled)
    {
        this.jwtUserResolver = jwtUserResolver;
        this.operator = operator;
        this.impersonationEnabled = impersonationEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
        if (!path.startsWith("/api/"))
        {
            chain.doFilter(request, response);
            return;
        }
        boolean get = "GET".equals(request.getMethod());
        boolean documentGet = get && (path.equals("/api/documents") || path.startsWith("/api/documents/"));
        List<String> parameters = actAsParameters(request);
        if (!parameters.isEmpty() && (!documentGet || parameters.size() > 1))
        {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (get && (path.equals("/api/graphql/schema") || path.startsWith("/api/v3/api-docs")))
        {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader(HEADER);
        if (request.getAttribute(CookieToBearerFilter.COOKIE_CREDENTIAL) == null)
        {
            boolean bearer = request.getHeader("Authorization") != null;
            if (bearer && (header != null || !parameters.isEmpty()))
            {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken actorAuthentication))
        {
            chain.doFilter(request, response);
            return;
        }
        String actAs;
        if (documentGet)
        {
            if (header != null && !parameters.isEmpty())
            {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            actAs = !parameters.isEmpty() ? parameters.get(0) : header != null ? header : SELF;
        }
        else
        {
            actAs = header;
        }
        if (actAs == null || actAs.isBlank())
        {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (!SELF.equals(actAs))
        {
            int rejected = actAs(actorAuthentication, actAs);
            if (rejected != 0)
            {
                response.setStatus(rejected);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** Replaces the authentication by the target's; returns 0, or the HTTP status that rejects the request. */
    private int actAs(JwtAuthenticationToken actorAuthentication, String targetId)
    {
        Jwt actorJwt = actorAuthentication.getToken();
        if (actorJwt.getClaims().containsKey("act") || ApiKeyScopeContextInitializer.isApiKeyPrincipal())
        {
            return HttpServletResponse.SC_BAD_REQUEST;
        }
        if (!impersonationEnabled)
        {
            return HttpServletResponse.SC_FORBIDDEN;
        }
        User actor;
        try
        {
            actor = jwtUserResolver.resolveJwtOrThrow(actorJwt);
        }
        catch (RaplaSecurityException e)
        {
            return HttpServletResponse.SC_UNAUTHORIZED;
        }
        Entity found = operator.tryResolve(targetId, Entity.class);
        if (!(found instanceof User target) || !PermissionController.canImpersonate(actor, target))
        {
            return HttpServletResponse.SC_FORBIDDEN;
        }
        if (audited.put(actorJwt.getId() + "|" + target.getId(), Boolean.TRUE) == null)
        {
            AUDIT_LOG.info("Impersonation (act-as): actor=" + actor.getUsername() + " (uuid=" + actor.getId()
                    + ") target=" + target.getUsername() + " (uuid=" + target.getId() + ")");
        }
        Map<String, Object> act = new LinkedHashMap<>();
        act.put("sub", actor.getId());
        act.put("username", actor.getUsername());
        Jwt impersonation = Jwt.withTokenValue(actorJwt.getTokenValue())
                .headers(headers -> headers.putAll(actorJwt.getHeaders()))
                .subject(target.getId())
                .issuedAt(actorJwt.getIssuedAt())
                .expiresAt(actorJwt.getExpiresAt())
                .jti(UUID.randomUUID().toString())
                .claim("typ", "access")
                .claim("username", target.getUsername())
                .claim("act", act)
                .build();
        JwtAuthenticationToken replaced = new JwtAuthenticationToken(impersonation, actorAuthentication.getAuthorities(), target.getId());
        replaced.setDetails(actorAuthentication.getDetails());
        SecurityContextHolder.getContext().setAuthentication(replaced);
        return 0;
    }

    private static List<String> actAsParameters(HttpServletRequest request)
    {
        String query = request.getQueryString();
        List<String> values = new ArrayList<>();
        if (query == null)
        {
            return values;
        }
        for (String pair : query.split("&"))
        {
            int eq = pair.indexOf('=');
            try
            {
                if (PARAMETER.equals(URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8)))
                {
                    values.add(eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
                }
            }
            catch (IllegalArgumentException malformed)
            {
                // a malformed pair cannot be the parameter; the application rejects it on its own
            }
        }
        return values;
    }
}
