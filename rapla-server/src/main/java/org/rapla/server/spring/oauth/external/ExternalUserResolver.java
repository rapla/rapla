package org.rapla.server.spring.oauth.external;

import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.plugin.jndi.JNDIPlugin;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Resolves an externally-authenticated JWT to a rapla {@link User} entity.
 * Identity is keyed on the rapla username — the same value
 * {@code user.getUsername()} returns, which already drives permissions,
 * ownership, and ACLs. The lookup tries the token's username-bearing claims
 * (upn → preferred_username → email) case-insensitively against stored
 * usernames; if none match, optionally falls back to email-match; if still
 * no match, auto-provisions a new user.
 *
 * <p>Tradeoff vs. the previous {@code external-id} preference design (dropped
 * 2026-05-21): an IdP-side username rename produces an orphaned rapla user +
 * a new auto-provisioned one. Same operator burden as the legacy LDAP path —
 * admin renames the rapla user manually. Wins: no first-login fragility for
 * CSV-imported users, no realm-rotation breakage, no hidden state in
 * preferences, less code.
 */
public class ExternalUserResolver
{
    private final RaplaFacade facade;
    private final Logger logger;

    public ExternalUserResolver(RaplaFacade facade, Logger logger)
    {
        this.facade = facade;
        this.logger = logger;
    }

    public User resolve(Jwt jwt, ProviderConfig provider) throws RaplaException
    {
        enforceHostedDomain(jwt, provider);

        // Try each candidate as a username (case-insensitive — LocalCache.getUser
        // already does the equalsIgnoreCase fallback). Stop at first hit.
        String upn = jwt.getClaimAsString("upn");
        User byUsername = findUserByUsername(upn);
        if (byUsername != null) return syncFromIdp(byUsername, jwt, provider);

        String preferredUsername = jwt.getClaimAsString(provider.usernameClaim());
        byUsername = findUserByUsername(preferredUsername);
        if (byUsername != null) return syncFromIdp(byUsername, jwt, provider);

        String email = jwt.getClaimAsString(provider.emailClaim());
        // Allow the email claim as a username fallback only for IdPs where the
        // email is treated as the username (email-as-username deployments).
        // Caller controls this via the emailIsVerified guard for Google.
        if (email != null && !email.isEmpty() && emailIsVerified(jwt))
        {
            byUsername = findUserByUsername(email);
            if (byUsername != null) return syncFromIdp(byUsername, jwt, provider);

            User byEmail = findUserByEmail(email);
            if (byEmail != null) return syncFromIdp(byEmail, jwt, provider);
        }

        if (provider.autoProvision())
        {
            return autoProvisionUser(jwt, provider);
        }

        throw new RaplaSecurityException(
                "No rapla user matched external identity from " + provider.id()
                        + " (upn=" + upn
                        + ", " + provider.usernameClaim() + "=" + preferredUsername
                        + ", email=" + email + "); auto-provision is disabled.");
    }

    /**
     * PRD 050: on every successful external-IdP match,
     * <ol>
     *   <li>stamp the user's {@code authenticationSource} if it's not already set —
     *       gates future self password / name / email change attempts at the
     *       server and hides the UI buttons;</li>
     *   <li>refresh name + email from the JWT claims so the local rapla copy
     *       stays in sync with the IdP-side values (the IdP is authoritative
     *       for externally-authenticated users — mirrors what dhbwrapla's
     *       {@code DhbwNtlmAuthStore.initUser} does for the NTLM/LDAP path).</li>
     * </ol>
     * Idempotent — when nothing changed, no write to storage. The marker
     * itself is sticky: once set (e.g. by a previous OAuth login), it
     * survives later authentication paths; the only way back to local is
     * the admin "disconnect from external auth" action.
     */
    private User syncFromIdp(User user, Jwt jwt, ProviderConfig provider) throws RaplaException
    {
        String expectedSource = provider.id();
        String currentSource = user.getAuthenticationSource();
        String idpName = readDisplayName(jwt);
        String idpEmail = jwt.getClaimAsString(provider.emailClaim());

        boolean sourceNeedsUpdate = !expectedSource.equals(currentSource);
        boolean nameNeedsUpdate = idpName != null && !idpName.isEmpty() && !idpName.equals(user.getName());
        boolean emailNeedsUpdate = idpEmail != null && !idpEmail.isEmpty() && !idpEmail.equals(user.getEmail());

        if (!sourceNeedsUpdate && !nameNeedsUpdate && !emailNeedsUpdate)
        {
            return user;
        }

        User edit = facade.edit(user);
        if (sourceNeedsUpdate)
        {
            edit.setAuthenticationSource(expectedSource);
            logger.info("Stamped authentication-source='" + expectedSource + "' on rapla user '"
                    + user.getUsername() + "' (external login from " + provider.id() + ")");
        }
        // Only refresh name/email when the IdP is the authoritative source.
        // If currentSource is null and we just stamped it, this login is the
        // promotion event — sync. If currentSource was non-null and matched,
        // the IdP claims are authoritative going forward; sync. If
        // currentSource was non-null and DIFFERENT (e.g. user has ldap stamp
        // but is suddenly logging in via keycloak), the existing source wins
        // — don't overwrite name/email until admin disconnects first.
        boolean canSync = currentSource == null || expectedSource.equals(currentSource);
        if (canSync)
        {
            if (nameNeedsUpdate)
            {
                edit.setName(idpName);
                logger.info("Synced name from " + provider.id() + " for rapla user '" + user.getUsername() + "'");
            }
            if (emailNeedsUpdate)
            {
                edit.setEmail(idpEmail);
                logger.info("Synced email from " + provider.id() + " for rapla user '" + user.getUsername() + "'");
            }
        }
        facade.store(edit);
        return facade.getUser(user.getUsername());
    }

    /**
     * Same priority as {@link #autoProvisionUser} for the display name:
     * {@code name} → {@code given_name} + {@code family_name} → null
     * (don't fall back to username on refresh — username is already there).
     */
    private String readDisplayName(Jwt jwt)
    {
        String displayName = jwt.getClaimAsString("name");
        if (displayName != null && !displayName.isEmpty()) return displayName;
        String given = jwt.getClaimAsString("given_name");
        String family = jwt.getClaimAsString("family_name");
        if (given == null && family == null) return null;
        return ((given == null ? "" : given) + " " + (family == null ? "" : family)).trim();
    }

    private void enforceHostedDomain(Jwt jwt, ProviderConfig provider) throws RaplaSecurityException
    {
        String configured = provider.hostedDomain();
        if (configured == null || configured.isEmpty()) return;
        String hd = jwt.getClaimAsString("hd");
        if (hd == null)
        {
            // Some Entra deployments don't expose 'hd' even on single-tenant —
            // fall back to comparing the email domain.
            String email = jwt.getClaimAsString(provider.emailClaim());
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
            return facade.getUser(candidate);
        }
        catch (RaplaException ex)
        {
            // facade.getUser throws on unknown name; that's a miss, not a
            // failure — continue to the next candidate.
            return null;
        }
    }

    private User findUserByEmail(String email) throws RaplaException
    {
        String normalized = email.toLowerCase(Locale.ROOT);
        for (User user : facade.getUsers())
        {
            String userEmail = user.getEmail();
            if (userEmail != null && normalized.equals(userEmail.toLowerCase(Locale.ROOT)))
            {
                return user;
            }
        }
        return null;
    }

    private User autoProvisionUser(Jwt jwt, ProviderConfig provider) throws RaplaException
    {
        // Same priority as the lookup: upn → usernameClaim → emailClaim.
        // The chosen value is lowercased so the stored form is case-canonical
        // — display surfaces (user lists, permission editors, REST DTOs) show
        // the literal stored case, and this prevents "Christopher.Kohlhaas"
        // vs "christopher.kohlhaas" visual drift across logins from IdPs that
        // disagree on case.
        String username = jwt.getClaimAsString("upn");
        if (username == null || username.isEmpty())
        {
            username = jwt.getClaimAsString(provider.usernameClaim());
        }
        if (username == null || username.isEmpty())
        {
            username = jwt.getClaimAsString(provider.emailClaim());
        }
        if (username == null || username.isEmpty())
        {
            throw new RaplaSecurityException(
                    "Cannot auto-provision: neither 'upn', '" + provider.usernameClaim()
                            + "' nor '" + provider.emailClaim() + "' claim is present");
        }
        username = username.toLowerCase(Locale.ROOT);
        String displayName = jwt.getClaimAsString("name");
        if (displayName == null || displayName.isEmpty())
        {
            String given = jwt.getClaimAsString("given_name");
            String family = jwt.getClaimAsString("family_name");
            if (given != null || family != null)
            {
                displayName = ((given == null ? "" : given) + " "
                        + (family == null ? "" : family)).trim();
            }
            else
            {
                displayName = username;
            }
        }
        String email = jwt.getClaimAsString(provider.emailClaim());

        User created = facade.newUser();
        created.setUsername(username);
        created.setName(displayName);
        if (email != null) created.setEmail(email);
        // PRD 050: stamp the external IdP marker at provisioning so the user
        // is immediately gated from self password / name / email changes.
        created.setAuthenticationSource(provider.id());
        applyConfiguredGroupsIfPresent(created);
        facade.store(created);

        logger.info("Auto-provisioned rapla user '" + username + "' from external provider "
                + provider.id());
        return facade.getUser(username);
    }

    /**
     * If the admin has configured {@link JNDIPlugin#USERGROUP_CONFIG} in system
     * preferences (the LDAP-era knob: "groups assigned to externally-authed
     * users"), use those groups in place of {@code FacadeImpl.newUser()}'s
     * defaults. Otherwise leave the defaults alone. The preference key is
     * plugin-namespaced for historical reasons; renaming is tracked separately.
     */
    private void applyConfiguredGroupsIfPresent(User user) throws RaplaException
    {
        RaplaMap<Category> configured = facade.getSystemPreferences().getEntry(JNDIPlugin.USERGROUP_CONFIG);
        if (configured == null || configured.values().isEmpty()) return;
        for (Category existing : new ArrayList<>(user.getGroupList()))
        {
            user.removeGroup(existing);
        }
        for (Category g : configured.values())
        {
            user.addGroup(g);
        }
    }
}
