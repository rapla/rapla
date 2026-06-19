package org.rapla.server.spring.oauth.external;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.server.IdentityClaims;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.RaplaSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Locale;

/**
 * Resolves an externally-authenticated JWT to a rapla {@link User} entity.
 * Identity is keyed on the rapla username — the same value
 * {@code user.getUsername()} returns, which already drives permissions,
 * ownership, and ACLs. The lookup tries the token's username-bearing claims
 * (upn → preferred_username → email) case-insensitively against stored
 * usernames; if none match, optionally falls back to email-match.
 *
 * <p><strong>PRD 050 Phase 7 — pure lookup, no writes.</strong>
 * Earlier revisions of this class ran {@code syncFromIdp} and
 * {@code autoProvisionUser} inside {@link #resolve(Jwt, ProviderConfig)},
 * turning every authenticated request into a write transaction. That violated
 * AGENTS.md §16 (read APIs don't mutate). Provisioning now lives in
 * {@link org.rapla.server.UserProvisioner}, called from the at-login seam in
 * {@link org.rapla.server.spring.web.OAuthExchangeController}. This class
 * exposes:
 * <ul>
 *   <li>{@link #resolve(Jwt, ProviderConfig)} — pure read for the
 *       resource-server filter path. Returns the matched User or throws
 *       {@link RaplaSecurityException}. No storage writes.</li>
 *   <li>{@link #claimsFor(Jwt, ProviderConfig)} — build the
 *       {@link IdentityClaims} blob the at-login provisioner needs. Called
 *       from the OAuth exchange controller, not from the resource-server.</li>
 * </ul>
 *
 * <p>Tradeoff vs. the previous {@code external-id} preference design (dropped
 * 2026-05-21): an IdP-side username rename produces an orphaned rapla user +
 * a new auto-provisioned one. Same operator burden as the legacy LDAP path —
 * admin renames the rapla user manually.
 */
public class ExternalUserResolver
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalUserResolver.class);
    private final CachableStorageOperator operator;

    public ExternalUserResolver(CachableStorageOperator operator)
    {
        this.operator = operator;
    }

    /**
     * Map the JWT to a rapla {@link User}. <strong>Pure read</strong> —
     * no storage writes, no auto-provisioning. Called from
     * {@code SpringSecurityRemoteSession.resolveJwtOrThrow} on every
     * authenticated request bearing an external-IdP token (AGENTS.md §16:
     * resource-server identity resolution is side-effect-free).
     *
     * <p>Auto-provisioning still happens — but only at the OAuth exchange
     * seam ({@link org.rapla.server.spring.web.OAuthExchangeController}),
     * once per token issuance / refresh. A token presented directly to
     * {@code /api/*} that bypasses {@code /api/auth/oauth/exchange} hits the
     * "No rapla user matched …" branch and the SPA's 401 dialog — which is
     * the intentional policy (provisioning is tied to a deliberate sign-in
     * event).
     */
    public User resolve(Jwt jwt, ProviderConfig provider) throws RaplaException
    {
        enforceHostedDomain(jwt.getClaims(), provider);

        String upn = jwt.getClaimAsString("upn");
        User byUsername = findUserByUsername(upn);
        if (byUsername != null) return byUsername;

        String preferredUsername = jwt.getClaimAsString(provider.usernameClaim());
        byUsername = findUserByUsername(preferredUsername);
        if (byUsername != null) return byUsername;

        String email = jwt.getClaimAsString(provider.emailClaim());
        if (email != null && !email.isEmpty() && emailIsVerified(jwt))
        {
            byUsername = findUserByUsername(email);
            if (byUsername != null) return byUsername;

            User byEmail = findUserByEmail(email);
            if (byEmail != null) return byEmail;
        }

        throw new RaplaSecurityException(
                "No rapla user matched external identity from " + provider.id()
                        + " (upn=" + upn
                        + ", " + provider.usernameClaim() + "=" + preferredUsername
                        + ", email=" + email + ")");
    }

    /**
     * Translate the JWT into an {@link IdentityClaims} blob the at-login
     * provisioner can act on. Pure: this is the read side of the auth-store
     * pattern ({@code AuthenticationStore.extractClaims}) for the OIDC path.
     *
     * <p>Used by the OAuth exchange controller after the IdP returns a fresh
     * access token. Username is canonicalised to lowercase — the stored form
     * for provisioned users, so case differences across IdPs don't fragment
     * a single human into multiple rapla rows.
     */
    public IdentityClaims claimsFor(Jwt jwt, ProviderConfig provider) throws RaplaSecurityException
    {
        return claimsFor(jwt.getClaims(), provider);
    }

    /**
     * PRD 072 Phase 1 — {@link IdentityClaims} from a generic claims map. Used
     * by the server-side {@code oauth2Login()} success-handler path, which holds
     * an {@code OidcIdToken}/{@code OidcUser} (already verified by Spring's head)
     * rather than a resource-server {@link Jwt}. Pure read; no storage writes.
     */
    public IdentityClaims claimsFor(java.util.Map<String, Object> claims, ProviderConfig provider) throws RaplaSecurityException
    {
        enforceHostedDomain(claims, provider);

        // upn → usernameClaim → emailClaim, matching the resolve() priority.
        String username = asString(claims, "upn");
        if (username == null || username.isEmpty())
        {
            username = asString(claims, provider.usernameClaim());
        }
        if (username == null || username.isEmpty())
        {
            username = asString(claims, provider.emailClaim());
        }
        if (username == null || username.isEmpty())
        {
            throw new RaplaSecurityException(
                    "Cannot extract identity claims: neither 'upn', '"
                            + provider.usernameClaim() + "' nor '"
                            + provider.emailClaim() + "' claim is present");
        }
        username = username.toLowerCase(Locale.ROOT);

        String displayName = asString(claims, "name");
        if (displayName == null || displayName.isEmpty())
        {
            String given = asString(claims, "given_name");
            String family = asString(claims, "family_name");
            if (given != null || family != null)
            {
                displayName = ((given == null ? "" : given) + " "
                        + (family == null ? "" : family)).trim();
            }
        }

        String email = asString(claims, provider.emailClaim());
        if (email != null && email.isEmpty()) email = null;

        return new IdentityClaims(username, displayName, email, provider.id(), null);
    }

    private static String asString(java.util.Map<String, Object> claims, String key)
    {
        if (claims == null || key == null) return null;
        Object v = claims.get(key);
        return v == null ? null : v.toString();
    }

    private void enforceHostedDomain(java.util.Map<String, Object> claims, ProviderConfig provider) throws RaplaSecurityException
    {
        String configured = provider.hostedDomain();
        if (configured == null || configured.isEmpty()) return;
        String hd = asString(claims, "hd");
        if (hd == null)
        {
            // Some Entra deployments don't expose 'hd' even on single-tenant —
            // fall back to comparing the email domain.
            String email = asString(claims, provider.emailClaim());
            if (email == null || !email.toLowerCase(Locale.ROOT).endsWith(
                    "@" + configured.toLowerCase(Locale.ROOT)))
            {
                throw new RaplaSecurityException(
                        "External identity from " + provider.id()
                                + " is not in the configured hosted-domain '" + configured + "'");
            }
            return;
        }
        if (!configured.equalsIgnoreCase(hd))
        {
            throw new RaplaSecurityException(
                    "External identity from " + provider.id()
                            + " is not in the configured hosted-domain '" + configured
                            + "' (received hd='" + hd + "')");
        }
    }

    /**
     * Google sets {@code email_verified=true|false}. Entra typically omits the
     * claim — when absent we trust the email (Entra controls the directory).
     */
    private boolean emailIsVerified(Jwt jwt)
    {
        Boolean verified = jwt.getClaimAsBoolean("email_verified");
        return verified == null || verified;
    }

    private User findUserByUsername(String candidate) throws RaplaException
    {
        if (candidate == null || candidate.isEmpty()) return null;
        try
        {
            return operator.getUser(candidate);
        }
        catch (RaplaException ex)
        {
            return null;
        }
    }

    private User findUserByEmail(String email) throws RaplaException
    {
        for (User user : operator.getUsers())
        {
            String userEmail = user.getEmail();
            if (userEmail != null && email.equalsIgnoreCase(userEmail))
            {
                return user;
            }
        }
        return null;
    }
}
