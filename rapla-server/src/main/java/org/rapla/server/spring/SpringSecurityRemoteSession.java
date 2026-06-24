package org.rapla.server.spring;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.server.RemoteSession;
import org.rapla.server.spring.oauth.external.ExternalUserResolver;
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
 * <p>Identity comes solely from the {@link JwtAuthenticationToken} that the
 * Spring Security resource-server filter chain places in the
 * {@link SecurityContextHolder} for the current request (a Bearer header, or the
 * {@code access_token} cookie promoted by {@link CookieToBearerFilter}). The JWT
 * is resolved to a Rapla {@link User} via the shared {@link JwtUserResolver}:
 * <ul>
 *   <li>If the JWT's {@code iss} matches an enabled external IdP (Microsoft Entra,
 *       Google), dispatch through {@link ExternalUserResolver} (PRD 036).</li>
 *   <li>Otherwise treat as a rapla-locally-issued token and resolve the
 *       {@code sub} claim as a User UUID via the {@link StorageOperator}.</li>
 * </ul>
 *
 * <p>When no JWT is present the request is unauthenticated — there is no longer a
 * legacy header/cookie/query-param HMAC-token fallback (the rapla-custom
 * {@code userId$signature} token is no longer minted by anything; all auth flows
 * issue RSA JWTs via the OAuth2 Authorization Server).
 */
public class SpringSecurityRemoteSession implements RemoteSession
{
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SpringSecurityRemoteSession.class);

    private final JwtUserResolver jwtUserResolver;

    public SpringSecurityRemoteSession(JwtUserResolver jwtUserResolver)
    {
        this.jwtUserResolver = jwtUserResolver;
    }

    @Override
    public User checkAndGetUser(HttpServletRequest request) throws RaplaSecurityException
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth)
        {
            return resolveJwtOrThrow(jwtAuth.getToken());
        }
        throw new RaplaSecurityException("No authenticated user — a valid JWT (Bearer header or access_token cookie) is required.");
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
        return false;
    }

    @Override
    public void logout()
    {
        // Spring-managed JWT — there's no server-side session to invalidate.
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
