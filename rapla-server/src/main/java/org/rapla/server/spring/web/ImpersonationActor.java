/*--------------------------------------------------------------------------*
 | Copyright (C) 2026, Christopher Kohlhaas                                 |
 *--------------------------------------------------------------------------*/
package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.server.RemoteSession;
import org.rapla.storage.PermissionController;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Map;

/**
 * PRD 131 D10/D11 — who is the actor of an impersonation request, and may he switch to the requested target. The
 * actor is always the logged-in user: the {@code act.sub} of an impersonation token, else the effective user.
 */
final class ImpersonationActor
{
    private ImpersonationActor() {}

    /** The real actor: the original user ({@code act.sub} on the current token) when impersonating, else the effective user. */
    static User realActor(RemoteSession session, RaplaFacade facade, HttpServletRequest request) throws RaplaException
    {
        String actorSub = actorSub(currentJwt());
        if (actorSub != null)
        {
            User actor = facade.getOperator().tryResolve(actorSub, User.class);
            if (actor == null)
            {
                throw new RaplaSecurityException("impersonation actor not found");
            }
            return actor;
        }
        return session.checkAndGetUser(request);
    }

    /** The target if it exists and {@code actor} may switch to it; null for an unknown and a not-permitted target alike. */
    static User permittedTarget(RaplaFacade facade, User actor, String targetUsername)
    {
        User target;
        try
        {
            target = facade.getUser(targetUsername);
        }
        catch (RaplaException ex)
        {
            return null;
        }
        return target != null && PermissionController.canImpersonate(actor, target) ? target : null;
    }

    /** True if the current token is an impersonation token (any {@code act} claim). */
    static boolean isImpersonating()
    {
        Jwt jwt = currentJwt();
        return jwt != null && jwt.getClaims().containsKey("act");
    }

    /** The {@code act.sub} claim of {@code jwt}, or null if the token is not an impersonation token. */
    static String actorSub(Jwt jwt)
    {
        if (jwt == null)
        {
            return null;
        }
        Object act = jwt.getClaims().get("act");
        if (act instanceof Map<?, ?> actMap)
        {
            Object sub = actMap.get("sub");
            return sub != null ? sub.toString() : null;
        }
        return null;
    }

    static Jwt currentJwt()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth)
        {
            return jwtAuth.getToken();
        }
        return null;
    }
}
