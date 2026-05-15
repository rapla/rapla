package org.rapla.server.spring.oauth.external;

import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.rapla.plugin.jndi.JNDIPlugin;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Resolves an externally-authenticated JWT to a rapla {@link User} entity. The
 * algorithm is the same for all providers (Microsoft Entra, Google, future);
 * provider-specific behaviour (claim names, email-verification guard) flows
 * through the {@link ProviderConfig} passed in.
 *
 * <p>Three-step lookup:
 * <ol>
 *   <li>Match by per-provider external-id stored under
 *       {@code user.preferences["org.rapla.auth.external-id.&lt;providerId&gt;"]}.
 *       Stable across email changes / account renames.</li>
 *   <li>Fall back to match by email. Sets the external-id preference on first
 *       match. For Google we require {@code email_verified=true}; Entra often
 *       doesn't ship a verified flag so we skip that guard when the claim is
 *       absent.</li>
 *   <li>If {@code auto-provision: true} and still no match, create a new rapla
 *       User. v1 limits auto-provision behaviour to "exists with default
 *       groups" — admin can elevate via the user editor afterwards.</li>
 * </ol>
 *
 * <p>See PRD 036 "Why we keep the rapla User" for why we resolve to a rapla
 * entity rather than authenticating against just the token claims.
 */
public class ExternalUserResolver
{
    static final TypedComponentRole<String> ACTIVE_PROVIDER_PREFERENCE =
            new TypedComponentRole<>("org.rapla.auth.provider");

    private final RaplaFacade facade;
    private final Logger logger;

    public ExternalUserResolver(RaplaFacade facade, Logger logger)
    {
        this.facade = facade;
        this.logger = logger;
    }

    public User resolve(Jwt jwt, ProviderConfig provider) throws RaplaException
    {
        String externalId = jwt.getClaimAsString(provider.externalIdClaim());
        if (externalId == null || externalId.isEmpty())
        {
            throw new RaplaSecurityException(
                    "External JWT missing required claim '" + provider.externalIdClaim()
                            + "' for provider " + provider.id());
        }
        enforceHostedDomain(jwt, provider);

        TypedComponentRole<String> externalIdKey =
                new TypedComponentRole<>(provider.externalIdPreferenceKey());

        User byExternalId = findUserByPreference(externalIdKey, externalId);
        if (byExternalId != null) return byExternalId;

        String email = jwt.getClaimAsString(provider.emailClaim());
        if (email != null && !email.isEmpty() && emailIsVerified(jwt))
        {
            User byEmail = findUserByEmail(email);
            if (byEmail != null)
            {
                attachExternalIdToUser(byEmail, externalIdKey, externalId, provider);
                return byEmail;
            }
        }

        if (provider.autoProvision())
        {
            return autoProvisionUser(jwt, provider, externalId, externalIdKey);
        }

        throw new RaplaSecurityException(
                "No rapla user matched external identity from " + provider.id()
                        + " (" + provider.externalIdClaim() + "=" + externalId
                        + ", email=" + email + "); auto-provision is disabled.");
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

    private User findUserByPreference(TypedComponentRole<String> key, String value) throws RaplaException
    {
        for (User user : facade.getUsers())
        {
            try
            {
                String stored = facade.getPreferences(user).getEntryAsString(key, null);
                if (value.equals(stored)) return user;
            }
            catch (RaplaException e)
            {
                logger.warn("Failed to read preferences for user " + user.getUsername() + ": " + e.getMessage());
            }
        }
        return null;
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

    private void attachExternalIdToUser(User user, TypedComponentRole<String> externalIdKey,
                                        String externalId, ProviderConfig provider) throws RaplaException
    {
        Preferences edit = facade.edit(facade.getPreferences(user));
        edit.putEntry(externalIdKey, externalId);
        edit.putEntry(ACTIVE_PROVIDER_PREFERENCE, provider.id());
        facade.store(edit);
    }

    private User autoProvisionUser(Jwt jwt, ProviderConfig provider, String externalId,
                                   TypedComponentRole<String> externalIdKey) throws RaplaException
    {
        String username = jwt.getClaimAsString(provider.usernameClaim());
        if (username == null || username.isEmpty())
        {
            username = jwt.getClaimAsString(provider.emailClaim());
        }
        if (username == null || username.isEmpty())
        {
            throw new RaplaSecurityException(
                    "Cannot auto-provision: neither '" + provider.usernameClaim()
                            + "' nor '" + provider.emailClaim() + "' claim is present");
        }
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
        applyConfiguredGroupsIfPresent(created);
        facade.store(created);

        // Re-fetch via the operator so we can edit attached preferences.
        User persisted = facade.getUser(username);
        Preferences edit = facade.edit(facade.getPreferences(persisted));
        edit.putEntry(externalIdKey, externalId);
        edit.putEntry(ACTIVE_PROVIDER_PREFERENCE, provider.id());
        facade.store(edit);

        logger.info("Auto-provisioned rapla user '" + username + "' from external provider "
                + provider.id() + " (externalId=" + externalId + ")");
        return persisted;
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
