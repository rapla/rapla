package org.rapla.server.spring;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.server.RemoteSession;
import org.rapla.server.spring.oauth.external.ExternalProvidersProperties;
import org.rapla.server.spring.oauth.external.ExternalUserResolver;
import org.rapla.server.spring.oauth.external.ProviderConfig;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.slf4j.LoggerFactory;

/**
 * Bridges Spring Security's JWT authentication to Rapla's {@link RemoteSession}.
 *
 * <p>If a {@link JwtAuthenticationToken} is present in the {@link SecurityContextHolder}
 * for the current thread, the JWT is resolved to a Rapla {@link User}:
 * <ul>
 *   <li>If the JWT's {@code iss} matches an enabled external IdP (Microsoft Entra,
 *       Google), dispatch through {@link ExternalUserResolver} (PRD 036).</li>
 *   <li>Otherwise treat as a rapla-locally-issued token and resolve the
 *       {@code sub} claim as a User UUID via the {@link StorageOperator}.</li>
 * </ul>
 * Falls back to a legacy {@link RemoteSession} (header/cookie/query-param token
 * formats) when no Spring Security authentication is present.
 */
public class SpringSecurityRemoteSession implements RemoteSession
{
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SpringSecurityRemoteSession.class);

    private final RemoteSession fallback;
    private final JwtUserResolver jwtUserResolver;

    public SpringSecurityRemoteSession(RemoteSession fallback, StorageOperator operator)
    {
        this(fallback, operator, null, null);
    }

    public SpringSecurityRemoteSession(RemoteSession fallback,
                                       StorageOperator operator,
                                       ExternalProvidersProperties externalProviders,
                                       ExternalUserResolver externalUserResolver)
    {
        this(fallback, new JwtUserResolver(operator, externalProviders, externalUserResolver));
    }

    public SpringSecurityRemoteSession(RemoteSession fallback, JwtUserResolver jwtUserResolver)
    {
        this.fallback = fallback;
        this.jwtUserResolver = jwtUserResolver;
    }

    @Override
    public User checkAndGetUser(HttpServletRequest request) throws RaplaSecurityException
    {
        // When Spring Security holds a JWT for this request, the JWT path is
        // authoritative. A resolver failure (e.g. external IdP user can't be
        // mapped to a rapla account) propagates as a RaplaSecurityException
        // carrying the actual reason — falling back to the legacy session
        // path here would swap the meaningful message for the generic
        // "No user found in session." that the legacy path emits when no
        // header/cookie token is present.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth)
        {
            return resolveJwtOrThrow(jwtAuth.getToken());
        }
        return fallback.checkAndGetUser(request);
    }

    @Override
    public boolean isAuthentified(HttpServletRequest request)
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth)
        {
            try
            {
                return resolveJwtOrThrow(jwtAuth.getToken()) != null;
            }
            catch (RaplaSecurityException ex)
            {
                return false;
            }
        }
        return fallback.isAuthentified(request);
    }

    @Override
    public void logout()
    {
        // Spring-managed JWT — there's no server-side session to invalidate.
        // Fallback may have legacy state to clear.
        fallback.logout();
    }

    /**
     * Resolve the JWT to a rapla User, or throw a {@link RaplaSecurityException}
     * whose message names the actual reason. Delegates to the shared
     * {@link JwtUserResolver} so the REST and GraphQL transports resolve
     * identity identically.
     */
    private User resolveJwtOrThrow(Jwt jwt) throws RaplaSecurityException
    {
        return jwtUserResolver.resolveJwtOrThrow(jwt);
    }
}
