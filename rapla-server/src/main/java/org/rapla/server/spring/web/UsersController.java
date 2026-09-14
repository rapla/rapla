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
import org.rapla.storage.dbrm.UserMe;
import org.rapla.storage.dbrm.UserSummary;
import org.rapla.storage.dbrm.UsersService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * PRD 051 — admin-scope user listing for the SPA's "Switch to user"
 * dialog. Returns {@link UserSummary} DTOs only — narrow wire shape
 * per AGENTS.md §12 (no email, no group list, no preferences).
 *
 * <p>Server-side filter: iterate {@code facade.getUsers()}, keep
 * those passing {@link PermissionController#canAdminUser(User, User)}
 * against the calling user. Includes the caller themselves
 * (self-impersonation is a no-op but harmless and the dropdown stays
 * uniform).
 *
 * <p>An empty response is the "caller has no admin authority" signal
 * the SPA toolbar uses to decide whether to make the username chip
 * clickable.
 */
@RestController
@ConditionalOnBean(RemoteSession.class)
public class UsersController implements UsersService
{
    private final RemoteSession session;
    private final RaplaFacade facade;
    private final HttpServletRequest request;

    public UsersController(RemoteSession session, RaplaFacade facade, HttpServletRequest request)
    {
        this.session = session;
        this.facade = facade;
        this.request = request;
    }

    @Override
    public List<UserSummary> list() throws RaplaException
    {
        // 401 if anonymous. The candidate list must reflect the REAL actor's admin
        // authority: when the caller is impersonating, checkAndGetUser returns the
        // effective (impersonated) user, whose scope is usually narrower/empty — so
        // resolve the original admin from the token's act.sub claim instead, exactly
        // as the impersonation switch authorizes against the real admin.
        session.checkAndGetUser(request);
        User caller = resolveRealActor();
        List<UserSummary> result = new ArrayList<>();
        for (User candidate : facade.getUsers())
        {
            if (PermissionController.canAdminUser(caller, candidate))
            {
                result.add(new UserSummary(candidate.getUsername(), nonNullName(candidate)));
            }
        }
        // Stable order — dropdown picker UX. Alphabetical by username.
        result.sort(Comparator.comparing(UserSummary::getUsername,
                String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Override
    public UserMe me() throws RaplaException
    {
        User caller = session.checkAndGetUser(request);
        return new UserMe(caller.getId(), caller.getUsername(), nonNullName(caller));
    }

    /**
     * The real actor: the original admin ({@code act.sub} on the current token)
     * when impersonating, otherwise the effective authenticated user. Mirrors
     * {@code AuthCookieController.resolveRealActor} so the candidate list and the
     * impersonation-switch authorization use the same identity.
     */
    private User resolveRealActor() throws RaplaException
    {
        String adminSub = actorSub(currentJwt());
        if (adminSub != null)
        {
            User admin = facade.getOperator().tryResolve(adminSub, User.class);
            if (admin == null)
            {
                throw new RaplaSecurityException("impersonation actor not found");
            }
            return admin;
        }
        return session.checkAndGetUser(request);
    }

    private static String actorSub(Jwt jwt)
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

    private static Jwt currentJwt()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth)
        {
            return jwtAuth.getToken();
        }
        return null;
    }

    private static String nonNullName(User u)
    {
        String name = u.getName();
        return name == null ? "" : name;
    }
}
