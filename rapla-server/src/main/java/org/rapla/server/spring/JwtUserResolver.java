package org.rapla.server.spring;

import org.rapla.entities.User;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.server.spring.oauth.external.ExternalProvidersProperties;
import org.rapla.server.spring.oauth.external.ExternalUserResolver;
import org.rapla.server.spring.oauth.external.ProviderConfig;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Single source of truth for "given a validated JWT (or the current
 * {@link SecurityContextHolder} authentication), which rapla {@link User} is
 * the caller?". Used by BOTH transport layers so they can't drift:
 * <ul>
 *   <li>REST — {@link SpringSecurityRemoteSession} delegates
 *       {@link #resolveJwtOrThrow(Jwt)}.</li>
 *   <li>GraphQL — the resolvers call {@link #resolveCurrentUserOrNull()}.</li>
 * </ul>
 *
 * <p>The resolution routes by the JWT {@code iss} claim: a token from an
 * enabled external IdP (Keycloak / Microsoft Entra / Google) is mapped via
 * {@link ExternalUserResolver} (which matches on upn → preferred_username →
 * email, since an external username need not equal the rapla username); a
 * rapla-locally-issued token resolves its {@code sub} claim as a rapla User
 * UUID. Before this class existed, the GraphQL path did a naive
 * {@code operator.getUser(preferred_username)} that silently failed for every
 * external-IdP token — e.g. a Keycloak login whose {@code preferred_username}
 * is "christopher.kohlhaas" while the rapla username is the full email — so
 * GraphQL treated authenticated external users as anonymous.
 */
public class JwtUserResolver
{
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtUserResolver.class);

    private final StorageOperator operator;
    private final ExternalProvidersProperties externalProviders;
    private final ExternalUserResolver externalUserResolver;

    public JwtUserResolver(StorageOperator operator,
                           ExternalProvidersProperties externalProviders,
                           ExternalUserResolver externalUserResolver)
    {
        this.operator = operator;
        this.externalProviders = externalProviders;
        this.externalUserResolver = externalUserResolver;
    }

    /**
     * Resolve the current thread's Spring Security authentication to a rapla
     * {@link User}, returning {@code null} for anonymous / unresolvable
     * callers (never throws). Designed for the GraphQL resolvers, which model
     * "no caller" as {@code null} rather than an exception.
     *
     * <p>Handles three authentication shapes:
     * <ul>
     *   <li>{@link JwtAuthenticationToken} — the production resource-server
     *       path; routed through {@link #resolveJwtOrThrow(Jwt)}.</li>
     *   <li>Any other authenticated principal (form login, test
     *       {@code @WithMockUser}) — resolved by {@code getName()} against
     *       {@code operator.getUser(name)}.</li>
     *   <li>Anonymous / unauthenticated — {@code null}.</li>
     * </ul>
     */
    public User resolveCurrentUserOrNull()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated())
        {
            return null;
        }
        if (auth instanceof JwtAuthenticationToken jwtAuth)
        {
            try
            {
                return resolveJwtOrThrow(jwtAuth.getToken());
            }
            catch (RaplaException ex)
            {
                LOGGER.debug("JWT could not be resolved to a rapla user: {}", ex.getMessage());
                return null;
            }
        }
        String name = auth.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name))
        {
            return null;
        }
        try
        {
            return operator.getUser(name);
        }
        catch (RaplaException ex)
        {
            return null;
        }
    }

    /**
     * Resolve a validated JWT to a rapla {@link User}, or throw a
     * {@link RaplaSecurityException} whose message names the actual reason.
     * The authoritative resolution used by the REST resource-server path.
     */
    public User resolveJwtOrThrow(Jwt jwt) throws RaplaSecurityException
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
