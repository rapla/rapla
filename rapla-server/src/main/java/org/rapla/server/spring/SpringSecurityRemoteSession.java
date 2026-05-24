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
    private final StorageOperator operator;
    private final ExternalProvidersProperties externalProviders;
    private final ExternalUserResolver externalUserResolver;

    public SpringSecurityRemoteSession(RemoteSession fallback, StorageOperator operator)
    {
        this(fallback, operator, null, null);
    }

    public SpringSecurityRemoteSession(RemoteSession fallback,
                                       StorageOperator operator,
                                       ExternalProvidersProperties externalProviders,
                                       ExternalUserResolver externalUserResolver)
    {
        this.fallback = fallback;
        this.operator = operator;
        this.externalProviders = externalProviders;
        this.externalUserResolver = externalUserResolver;
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
     * whose message names the actual reason (e.g. "name already taken",
     * "auto-provision is disabled"). Callers receive the concrete failure
     * cause instead of a generic 401 message.
     */
    private User resolveJwtOrThrow(Jwt jwt) throws RaplaSecurityException
    {
        // External-issuer dispatch (PRD 036). If `iss` matches an enabled
        // external provider, the JWT's claims describe an external identity
        // that must be mapped to a rapla User via ExternalUserResolver — the
        // `sub` claim is the IdP's user identifier, NOT a rapla UUID.
        if (externalProviders != null && externalUserResolver != null)
        {
            String issuer = jwt.getClaimAsString("iss");
            ProviderConfig provider = externalProviders.byIssuer(issuer).orElse(null);
            if (provider != null)
            {
                try
                {
                    return externalUserResolver.resolve(jwt, provider);
                }
                catch (RaplaSecurityException ex)
                {
                    LOGGER.warn("External JWT (iss={}) could not be resolved to a Rapla user: {}", issuer, ex.getMessage());
                    throw ex;
                }
                catch (RaplaException ex)
                {
                    LOGGER.warn("External JWT (iss={}) could not be resolved to a Rapla user: {}", issuer, ex.getMessage());
                    throw new RaplaSecurityException(ex.getMessage(), ex);
                }
            }
        }

        String subject = jwt.getSubject();
        if (subject == null)
        {
            throw new RaplaSecurityException("JWT has no subject and no matching external provider for issuer "
                    + jwt.getClaimAsString("iss"));
        }
        try
        {
            return operator.resolve(new ReferenceInfo<>(subject, User.class));
        }
        catch (RaplaException ex)
        {
            LOGGER.warn("JWT subject {} could not be resolved to a Rapla user: {}", subject, ex.getMessage());
            throw new RaplaSecurityException("JWT subject '" + subject + "' could not be resolved: " + ex.getMessage(), ex);
        }
    }
}
