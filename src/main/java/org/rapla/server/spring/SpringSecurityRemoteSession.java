package org.rapla.server.spring;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.logger.Logger;
import org.rapla.server.RemoteSession;
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
 * for the current thread, the JWT's {@code sub} claim is resolved to a Rapla
 * {@link User} via the {@link StorageOperator}. Otherwise this delegates to a
 * fallback session (which can use the legacy header/cookie/query-param token
 * formats — useful while the migration is in flight).
 */
public class SpringSecurityRemoteSession implements RemoteSession
{
    private final RemoteSession fallback;
    private final StorageOperator operator;
    private final Logger logger;

    public SpringSecurityRemoteSession(RemoteSession fallback, StorageOperator operator, Logger logger)
    {
        this.fallback = fallback;
        this.operator = operator;
        this.logger = logger;
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
