/*--------------------------------------------------------------------------*
 | Copyright (C) 2026, Christopher Kohlhaas                                 |
 *--------------------------------------------------------------------------*/
package org.rapla.server.spring.web;

import com.nimbusds.jose.JOSEException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.server.RemoteSession;
import org.rapla.server.spring.CookieAuthSupport;
import org.rapla.server.spring.JwtConfig;
import org.rapla.server.spring.RefreshSessionService;
import org.rapla.storage.PermissionController;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.AuthCookieService;
import org.rapla.storage.dbrm.IdentityResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PRD 072 Phase 2 — cookie-credential (model A) auth endpoints for the browser
 * surfaces. Routing/verbs/params come from {@link AuthCookieService}
 * (rapla-core); this controller carries only logic. The existing Bearer-shaped
 * {@link ImpersonationController} stays for the Swing/API path.
 *
 * <p>§12 (data-leak): {@link #me()} returns ONLY the caller's own identity —
 * there is no parameter to request another user, and the identity is read from
 * the caller's own validated token.
 */
@RestController
@ConditionalOnBean(RemoteSession.class)
public class AuthCookieController implements AuthCookieService
{
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("rapla");
    private static final long IMPERSONATION_TTL_SECONDS = 3600L;

    private final RemoteSession session;
    private final RaplaFacade facade;
    private final JwtConfig.JwtIssuer jwtIssuer;
    private final RefreshSessionService refreshSessionService;
    private final CookieAuthSupport cookies;
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    public AuthCookieController(RemoteSession session,
                               RaplaFacade facade,
                               JwtConfig.JwtIssuer jwtIssuer,
                               RefreshSessionService refreshSessionService,
                               CookieAuthSupport cookies,
                               HttpServletRequest request,
                               HttpServletResponse response)
    {
        this.session = session;
        this.facade = facade;
        this.jwtIssuer = jwtIssuer;
        this.refreshSessionService = refreshSessionService;
        this.cookies = cookies;
        this.request = request;
        this.response = response;
    }

    @Override
    public IdentityResponse me() throws RaplaException
    {
        // checkAndGetUser resolves the EFFECTIVE user (the impersonation target
        // when an act-claim token is presented). 401 without a valid credential.
        User user = session.checkAndGetUser(request);

        // Impersonation state comes from the act-claim on the caller's own token.
        Jwt jwt = currentJwt();
        boolean impersonating = false;
        String actor = null;
        String target = null;
        if (jwt != null)
        {
            Object act = jwt.getClaims().get("act");
            if (act instanceof Map<?, ?> actMap)
            {
                Object actorUsername = actMap.get("username");
                if (actorUsername != null)
                {
                    impersonating = true;
                    actor = actorUsername.toString();
                    target = user.getUsername();
                }
            }
        }

        List<String> roles = new ArrayList<>();
        if (user.isAdmin())
        {
            roles.add("admin");
        }
        for (Category group : user.getGroupList())
        {
            roles.add(group.getKey());
        }

        return new IdentityResponse(user.getUsername(), user.getName(), user.isAdmin(),
                roles, impersonating, actor, target);
    }

    @Override
    public void refresh() throws RaplaException
    {
        String presented = CookieAuthSupport.readCookie(request, CookieAuthSupport.REFRESH_TOKEN_COOKIE);
        if (presented == null)
        {
            throw new RaplaSecurityException("no refresh token");
        }
        // validate() throws RaplaSecurityException (→ 401) on any failure.
        RefreshSessionService.ValidatedRefresh validated = refreshSessionService.validate(presented);
        String accessToken;
        try
        {
            accessToken = refreshSessionService.issueAccessToken(validated.user());
        }
        catch (JOSEException e)
        {
            throw new RaplaException("Failed to mint access token: " + e.getMessage(), e);
        }
        cookies.setAccessTokenCookie(response, accessToken, RefreshSessionService.ACCESS_TOKEN_TTL_SECONDS);
        // Single-slot, non-sliding: the refresh token is NOT rotated.
    }

    @Override
    public void impersonateSwitch(String targetUsername) throws RaplaException
    {
        // 401 if anonymous. The EFFECTIVE user is the impersonation target when an
        // act-claim token is presented (a chained switch).
        session.checkAndGetUser(request);
        // Review B2: the REAL actor must be the original admin, not the effective
        // (impersonated) user. If the current token already carries an act claim
        // (already impersonating), resolve the admin from act.sub so canAdminUser +
        // the audit log + the new act-claim name the real admin — no actor laundering,
        // no admin-check run against the impersonated user's permissions.
        User actor = resolveRealActor();

        User target;
        try
        {
            target = facade.getUser(targetUsername);
        }
        catch (RaplaException ex)
        {
            throw new TargetNotFoundException(targetUsername);
        }
        if (target == null)
        {
            throw new TargetNotFoundException(targetUsername);
        }

        if (!PermissionController.canAdminUser(actor, target))
        {
            // 403 — the actor IS authenticated, just not authorised to admin
            // this target. A dedicated 403 type (not RaplaSecurityException,
            // which the global handler maps to 401) so refresh's invalid-token
            // path keeps its 401.
            throw new ImpersonationForbiddenException();
        }

        String token;
        try
        {
            token = jwtIssuer.issueImpersonationToken(
                    target.getId(), target.getUsername(),
                    actor.getId(), actor.getUsername(),
                    IMPERSONATION_TTL_SECONDS);
        }
        catch (JOSEException ex)
        {
            throw new RaplaException("Failed to sign impersonation token: " + ex.getMessage(), ex);
        }

        AUDIT_LOG.info("Impersonation: actor=" + actor.getUsername() + " (uuid=" + actor.getId()
                + ") target=" + target.getUsername() + " (uuid=" + target.getId() + ")");

        cookies.setAccessTokenCookie(response, token, IMPERSONATION_TTL_SECONDS);
    }

    @Override
    public void impersonateEnd() throws RaplaException
    {
        // Review B1: restore the admin from the act.sub claim on the CURRENT
        // impersonation token — NOT the refresh cookie. The refresh cookie is
        // Path=/api/auth/refresh, so a real browser never sends it to
        // /api/auth/impersonate/end → depending on it made end() always 401.
        session.checkAndGetUser(request); // 401 if anonymous
        String adminSub = actorSub(currentJwt());
        if (adminSub == null)
        {
            // Not impersonating — there is no admin session to restore.
            throw new RaplaSecurityException("not impersonating");
        }
        User admin = facade.getOperator().tryResolve(adminSub, User.class);
        if (admin == null)
        {
            throw new RaplaSecurityException("impersonation actor not found");
        }
        String accessToken;
        try
        {
            accessToken = refreshSessionService.issueAccessToken(admin);
        }
        catch (JOSEException e)
        {
            throw new RaplaException("Failed to mint admin access token: " + e.getMessage(), e);
        }
        cookies.setAccessTokenCookie(response, accessToken, RefreshSessionService.ACCESS_TOKEN_TTL_SECONDS);
    }

    @Override
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout()
    {
        // Sign-out is unconditional and idempotent: clear the cookies regardless
        // of whether the presented access token is still valid (you must be able
        // to log out with an expired token). /api/auth/** is permitAll, so the
        // bearer-validation gate never blocks this. Spring's default LogoutFilter
        // (POST /logout, clears JSESSIONID only) is unaware of these cookies.
        cookies.clearAuthCookies(response);
        HttpSession httpSession = request.getSession(false);
        if (httpSession != null)
        {
            httpSession.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    /**
     * The real actor for an impersonation operation: the original admin (the
     * {@code act.sub} on the current token) when the caller is already
     * impersonating, otherwise the effective authenticated user. Review B2 —
     * prevents a chained switch from laundering the admin out of the chain.
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

    /** The original-admin UUID from the current token's {@code act.sub} claim, or null if not impersonating. */
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

    @ResponseStatus(HttpStatus.NOT_FOUND)
    static final class TargetNotFoundException extends RuntimeException
    {
        TargetNotFoundException(String username)
        {
            super("Target user not found: " + username);
        }
    }

    /**
     * 403 carrier for "actor authenticated but not authorised to admin this
     * target". Distinct from {@link RaplaSecurityException} (mapped to 401 by
     * {@link RaplaExceptionHandler}) so the refresh endpoint's invalid-token
     * path keeps its 401. Body intentionally empty — surfacing the
     * can_admin_parent scope would leak group structure (see
     * {@link ImpersonationController}).
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    static final class ImpersonationForbiddenException extends RuntimeException
    {
        ImpersonationForbiddenException()
        {
            super("Not authorized to impersonate the requested target");
        }
    }
}
