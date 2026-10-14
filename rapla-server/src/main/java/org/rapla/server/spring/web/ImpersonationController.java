/*--------------------------------------------------------------------------*
 | Copyright (C) 2026, Christopher Kohlhaas                                 |
 *--------------------------------------------------------------------------*/
package org.rapla.server.spring.web;

import com.nimbusds.jose.JOSEException;
import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.server.RemoteSession;
import org.rapla.server.spring.ApiKeyScopeContextInitializer;
import org.rapla.server.spring.JwtConfig;
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
 * checks {@link org.rapla.storage.PermissionController#canImpersonate(User, User)}
 * against the target. No server-side per-impersonation state is persisted —
 * each call is independent, and the issued token has no refresh-token
 * counterpart. See PRD 051 § "Token renewal model".
 */
@RestController
@ConditionalOnBean(RemoteSession.class)
public class ImpersonationController implements ImpersonationService
{
    /** Audit log routed to the named "rapla" category so the
     *  {@code ImpersonationControllerTest} ListAppender keeps capturing
     *  impersonation events after the per-class LOGGER migration.
     *  See PRD 053 § "Test-contract caveat surfaced". */
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("rapla");
    /** Matches rapla-SAS's {@code access-token-time-to-live: 1h} default
     *  (PRD 041, {@code application.yml:95}). Renewal is via another
     *  call to this endpoint; no refresh-token. */
    private static final long IMPERSONATION_TTL_SECONDS = 3600L;

    private final RemoteSession session;
    private final RaplaFacade facade;
    private final JwtConfig.JwtIssuer jwtIssuer;
    private final HttpServletRequest request;
    private final boolean impersonationEnabled;

    public ImpersonationController(RemoteSession session,
                                    RaplaFacade facade,
                                    JwtConfig.JwtIssuer jwtIssuer,
                                    HttpServletRequest request,
                                    @org.springframework.beans.factory.annotation.Value("${rapla.auth.impersonation.enabled:true}") boolean impersonationEnabled)
    {
        this.session = session;
        this.facade = facade;
        this.jwtIssuer = jwtIssuer;
        this.request = request;
        this.impersonationEnabled = impersonationEnabled;
    }

    @Override
    public ImpersonationResponse impersonate(String targetUsername) throws RaplaException
    {
        // security-audit A0c / PRD 051: impersonation can be disabled entirely for a
        // deployment (rapla.auth.impersonation.enabled=false) — the kill switch.
        if (!impersonationEnabled)
        {
            throw new RaplaSecurityException("Impersonation is disabled (rapla.auth.impersonation.enabled=false)");
        }
        // 1. Resolve the actor from the incoming Bearer. Goes through
        //    SpringSecurityRemoteSession.resolveJwtOrThrow → works
        //    uniformly for rapla-SAS, Keycloak, Entra, and Google tokens
        //    via the existing ExternalUserResolver pipeline.
        User actor = session.checkAndGetUser(request);
        if (ApiKeyScopeContextInitializer.isApiKeyPrincipal())
        {
            throw new RaplaSecurityException("api keys cannot impersonate");
        }
        // PRD 131 D10: no chains. A client switches with the actor's own token; an impersonation token never starts another one.
        if (ImpersonationActor.isImpersonating())
        {
            throw new RaplaSecurityException("an impersonation token cannot start another impersonation");
        }

        // 2. Resolve + authorize the target (PRD 131 D11: canImpersonate). An unknown and a not-permitted target
        //    get the identical 403 — differing answers would let an actor enumerate users outside his right.
        User target = ImpersonationActor.permittedTarget(facade, actor, targetUsername);
        if (target == null)
        {
            throw new RaplaSecurityException("Not authorized to impersonate the requested target");
        }

        // 3. Mint. Same key, same algorithm, same JWKS as any other
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

        // 4. Audit. One line per issuance, including renewals — the
        //    renewal cadence is the audit cadence. Lands wherever ops
        //    collects rapla logs.
        AUDIT_LOG.info("Impersonation: actor=" + actor.getUsername() + " (uuid=" + actor.getId()
                + ") target=" + target.getUsername() + " (uuid=" + target.getId() + ")");

        return new ImpersonationResponse(token, "Bearer", IMPERSONATION_TTL_SECONDS);
    }

    /**
     * RaplaSecurityException → 403 for impersonation specifically. The
     * generic mapping in the global exception handler returns 401 for
     * RaplaSecurityException; that's wrong here because the actor IS
     * authenticated — they just aren't authorised to switch to this target.
     */
    @ExceptionHandler(RaplaSecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void handleAuthorizationFailure(RaplaSecurityException ex)
    {
        // Body is intentionally empty — the controller's own audit log
        // captures the failed attempt; the client doesn't need details
        // beyond the 403 status (and surfacing "user X may not switch to
        // user Y" would leak the can_admin_parent / impersonated_by structure).
    }
}
