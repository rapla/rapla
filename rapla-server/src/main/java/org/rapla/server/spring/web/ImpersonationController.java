/*--------------------------------------------------------------------------*
 | Copyright (C) 2026, Christopher Kohlhaas                                 |
 *--------------------------------------------------------------------------*/
package org.rapla.server.spring.web;

import com.nimbusds.jose.JOSEException;
import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.server.RemoteSession;
import org.rapla.server.spring.JwtConfig;
import org.rapla.storage.PermissionController;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.ImpersonationResponse;
import org.rapla.storage.dbrm.ImpersonationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * PRD 051 — admin "switch to user" endpoint. Mints a rapla-SAS-signed
 * access token whose effective subject is the impersonated target,
 * carrying an {@code act} claim that names the admin actor.
 *
 * <p>Authorization runs every call: the controller resolves the actor
 * from the incoming Bearer (any issuer rapla accepts via the
 * multi-issuer decoder + {@link RemoteSession#checkAndGetUser}), then
 * checks {@link PermissionController#canAdminUser(User, User)} against
 * the target. No server-side per-impersonation state is persisted —
 * each call is independent, and the issued token has no refresh-token
 * counterpart. See PRD 051 § "Token renewal model".
 */
@RestController
@ConditionalOnBean(RemoteSession.class)
public class ImpersonationController implements ImpersonationService
{
    /** Matches rapla-SAS's {@code access-token-time-to-live: 1h} default
     *  (PRD 041, {@code application.yml:95}). Renewal is via another
     *  call to this endpoint; no refresh-token. */
    private static final long IMPERSONATION_TTL_SECONDS = 3600L;

    private final RemoteSession session;
    private final RaplaFacade facade;
    private final JwtConfig.JwtIssuer jwtIssuer;
    private final HttpServletRequest request;
    private final Logger logger;

    public ImpersonationController(RemoteSession session,
                                    RaplaFacade facade,
                                    JwtConfig.JwtIssuer jwtIssuer,
                                    HttpServletRequest request,
                                    Logger logger)
    {
        this.session = session;
        this.facade = facade;
        this.jwtIssuer = jwtIssuer;
        this.request = request;
        this.logger = logger;
    }

    @Override
    public ImpersonationResponse impersonate(String targetUsername) throws RaplaException
    {
        // 1. Resolve the actor from the incoming Bearer. Goes through
        //    SpringSecurityRemoteSession.resolveJwtOrThrow → works
        //    uniformly for rapla-SAS, Keycloak, Entra, and Google tokens
        //    via the existing ExternalUserResolver pipeline.
        User actor = session.checkAndGetUser(request);

        // 2. Resolve the target by username. canAdminUser's scoping
        //    means we want a 404 (not 403) for "unknown user" — leaking
        //    "this user exists but you can't admin them" via differing
        //    status codes would let an admin enumerate users outside
        //    their group-admin scope. So look up first, 404 if absent.
        User target;
        try
        {
            target = facade.getUser(targetUsername);
        }
        catch (RaplaException ex)
        {
            // facade.getUser throws when the name isn't found.
            throw new TargetNotFoundException(targetUsername);
        }
        if (target == null)
        {
            throw new TargetNotFoundException(targetUsername);
        }

        // 3. Authorize. PRD 051 § "Rapla's group-administration policy
        //    — the authorization rule": global admin OR group-admin
        //    whose can_admin_parent scope intersects the target's groups.
        if (!PermissionController.canAdminUser(actor, target))
        {
            throw new RaplaSecurityException(
                    "User '" + actor.getUsername() + "' is not authorized to impersonate '"
                            + target.getUsername() + "'");
        }

        // 4. Mint. Same key, same algorithm, same JWKS as any other
        //    rapla-SAS access token — validates against the existing
        //    resource-server decoder without special-casing.
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

        // 5. Audit. One line per issuance, including renewals — the
        //    renewal cadence is the audit cadence. Lands wherever ops
        //    collects rapla logs.
        logger.info("Impersonation: actor=" + actor.getUsername() + " (uuid=" + actor.getId()
                + ") target=" + target.getUsername() + " (uuid=" + target.getId() + ")");

        return new ImpersonationResponse(token, "Bearer", IMPERSONATION_TTL_SECONDS);
    }

    /**
     * 404 carrier for "target_username not found". Extends
     * {@link RuntimeException} (not {@link RaplaException}) so the
     * global {@code @ExceptionHandler(RaplaException.class)} in
     * {@link RaplaExceptionHandler} doesn't gobble it into a 500;
     * the {@code @ResponseStatus} resolver gets first crack.
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    static final class TargetNotFoundException extends RuntimeException
    {
        TargetNotFoundException(String username)
        {
            super("Target user not found: " + username);
        }
    }

    /**
     * RaplaSecurityException → 403 for impersonation specifically. The
     * generic mapping in the global exception handler returns 401 for
     * RaplaSecurityException; that's wrong here because the actor IS
     * authenticated — they just aren't authorised to admin this target.
     */
    @ExceptionHandler(RaplaSecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void handleAuthorizationFailure(RaplaSecurityException ex)
    {
        // Body is intentionally empty — the controller's own audit log
        // captures the failed attempt; the client doesn't need details
        // beyond the 403 status (and surfacing "user X is not an admin
        // of user Y" would leak the can_admin_parent scope structure).
    }
}
