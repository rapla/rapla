package org.rapla.server.spring;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.logger.Logger;
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
    private final RemoteSession fallback;
    private final StorageOperator operator;
    private final Logger logger;
    private final ExternalProvidersProperties externalProviders;
    private final ExternalUserResolver externalUserResolver;

    public SpringSecurityRemoteSession(RemoteSession fallback, StorageOperator operator, Logger logger)
    {
        this(fallback, operator, logger, null, null);
    }

    public SpringSecurityRemoteSession(RemoteSession fallback,
                                       StorageOperator operator,
                                       Logger logger,
                                       ExternalProvidersProperties externalProviders,
                                       ExternalUserResolver externalUserResolver)
    {
        this.fallback = fallback;
        this.operator = operator;
        this.logger = logger;
        this.externalProviders = externalProviders;
        this.externalUserResolver = externalUserResolver;
    }

    @Override
    public User checkAndGetUser(HttpServletRequest request) throws RaplaSecurityException
    {
        User user = jwtUser();
        if (user != null)
        {
            return user;
        }
        return fallback.checkAndGetUser(request);
    }

    @Override
    public boolean isAuthentified(HttpServletRequest request)
    {
        return jwtUser() != null || fallback.isAuthentified(request);
    }

    @Override
    public Logger getLogger()
    {
        return logger;
    }

    @Override
    public void logout()
    {
        // Spring-managed JWT — there's no server-side session to invalidate.
        // Fallback may have legacy state to clear.
        fallback.logout();
    }

    private User jwtUser()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth))
        {
            return null;
        }
        Jwt jwt = jwtAuth.getToken();

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
                catch (Exception ex)
                {
                    logger.warn("External JWT (iss=" + issuer + ") could not be resolved to a Rapla user: " + ex.getMessage());
                    return null;
                }
            }
        }

        String subject = jwt.getSubject();
        if (subject == null)
        {
            return null;
        }
        try
        {
            return operator.resolve(new ReferenceInfo<>(subject, User.class));
        }
        catch (Exception ex)
        {
            logger.warn("JWT subject " + subject + " could not be resolved to a Rapla user: " + ex.getMessage());
            return null;
        }
    }
}
