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
import org.rapla.server.spring.ApiKeyScopeContextInitializer;
import org.rapla.server.spring.RefreshSessionService;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.AuthCookieService;
import org.rapla.storage.dbrm.IdentityResponse;
import org.rapla.storage.dbrm.ImpersonationTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
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

    private final RemoteSession session;
    private final RaplaFacade facade;
    private final RefreshSessionService refreshSessionService;
    private final CookieAuthSupport cookies;
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    private final boolean impersonationEnabled;

    public AuthCookieController(RemoteSession session,
                               RaplaFacade facade,
                               RefreshSessionService refreshSessionService,
                               CookieAuthSupport cookies,
                               HttpServletRequest request,
                               HttpServletResponse response,
                               @org.springframework.beans.factory.annotation.Value("${rapla.auth.impersonation.enabled:true}") boolean impersonationEnabled,
                               @org.springframework.beans.factory.annotation.Value("${rapla.demo.banner:}") String demoBanner,
                               RequestLanguage requestLanguage)
    {
        this.session = session;
        this.facade = facade;
        this.refreshSessionService = refreshSessionService;
        this.cookies = cookies;
        this.request = request;
        this.response = response;
        this.impersonationEnabled = impersonationEnabled;
        this.demoBanner = demoBanner == null || demoBanner.isBlank() ? null : demoBanner;
        this.requestLanguage = requestLanguage;
    }

    /** PRD 118 D8-8 — {@code rapla.demo.banner}, shown by the SPA shell; null outside the demo. */
    private final String demoBanner;
    private final RequestLanguage requestLanguage;

    @Override
    public IdentityResponse me() throws RaplaException
    {
        // checkAndGetUser resolves the EFFECTIVE user (the impersonation target
        // when an act-claim token is presented). 401 without a valid credential.
        User user = session.checkAndGetUser(request);

        // Impersonation state comes from the act-claim on the caller's own token.
        Jwt jwt = ImpersonationActor.currentJwt();
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

        IdentityResponse identity = new IdentityResponse(user.getId(), user.getUsername(), user.getName(), user.isAdmin(),
                roles, impersonating, actor, target);
        identity.setDemoBanner(demoBanner);
        identity.setLanguage(requestLanguage.resolve(request, user).getLanguage());
        return identity;
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
        cookies.setAccessTokenCookie(response, accessToken, RefreshSessionService.ACCESS_TOKEN_TTL_SECONDS,
                CookieAuthSupport.isRemembered(request));
        // Single-slot, non-sliding: the refresh token is NOT rotated.
    }

    @Override
    public ImpersonationTarget impersonateSwitch(String targetUsername) throws RaplaException
    {
        // security-audit A0c / PRD 051: honour the same impersonation kill switch as
        // ImpersonationController (rapla.auth.impersonation.enabled=false).
        if (!impersonationEnabled)
        {
            throw new RaplaSecurityException("Impersonation is disabled (rapla.auth.impersonation.enabled=false)");
        }
        // 401 if anonymous. The EFFECTIVE user is the act-as target when this tab already acts as one (a chained switch).
        session.checkAndGetUser(request);
        if (ApiKeyScopeContextInitializer.isApiKeyPrincipal())
        {
            throw new ImpersonationForbiddenException();
        }
        // Review B2 / PRD 131 D10: the REAL actor is the logged-in user (act.sub when already impersonating), so
        // a chain A -> B -> C is checked as A -> C and the audit log names A.
        User actor = ImpersonationActor.realActor(session, facade, request);

        // An unknown and a not-permitted target get the identical 403 (AGENTS.md section 12).
        User target = ImpersonationActor.permittedTarget(facade, actor, targetUsername);
        if (target == null)
        {
            throw new ImpersonationForbiddenException();
        }

        AUDIT_LOG.info("Impersonation: actor=" + actor.getUsername() + " (uuid=" + actor.getId()
                + ") target=" + target.getUsername() + " (uuid=" + target.getId() + ")");

        return new ImpersonationTarget(target.getId(), target.getUsername());
    }

    @Override
    public void impersonateEnd() throws RaplaException
    {
        session.checkAndGetUser(request); // 401 if anonymous
        if (!ImpersonationActor.isImpersonating())
        {
            throw new RaplaSecurityException("not impersonating");
        }
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
        //
        // Server-side revoke: resolve the user from the path-scoped refresh_token
        // cookie (this endpoint lives under /api/auth/session precisely so that
        // cookie reaches it) and clear the session. peekUser works off the durable
        // refresh token, so logout revokes correctly even when the access token
        // has already expired. clearSession is per-user (single-token-per-user) —
        // a deliberate "log out everywhere" property.
        String refreshToken = CookieAuthSupport.readCookie(request, CookieAuthSupport.REFRESH_TOKEN_COOKIE);
        if (refreshToken != null)
        {
            User user = refreshSessionService.peekUser(refreshToken);
            if (user != null)
            {
                try
                {
                    refreshSessionService.clearSession(user);
                }
                catch (RaplaException ex)
                {
                    AUDIT_LOG.warn("logout: could not clear server-side session for {}: {}",
                            user.getUsername(), ex.getMessage());
                }
            }
        }
        cookies.clearAuthCookies(response);
        HttpSession httpSession = request.getSession(false);
        if (httpSession != null)
        {
            httpSession.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    /**
     * 403 carrier for "actor authenticated but not authorised to switch to this
     * target" (and for an unknown target, identically). Distinct from {@link RaplaSecurityException} (mapped to 401 by
     * {@link RaplaExceptionHandler}) so the refresh endpoint's invalid-token
     * path keeps its 401. Body intentionally empty — surfacing the
     * can_admin_parent / impersonated_by scope would leak group structure (see
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
