package org.rapla.server.internal;

import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.jndi.JNDIPlugin;
import org.rapla.server.IdentityClaims;
import org.rapla.server.UserProvisioner;
import org.rapla.storage.CachableStorageOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Default {@link UserProvisioner}. Reads/writes via {@link CachableStorageOperator}
 * directly — never via {@code RaplaFacade} (no {@code workingUser} leak risk on
 * the server-singleton facade, matches what
 * {@code RaplaAuthentificationService} already did pre-PRD 050 Phase 8).
 *
 * <p>Per-field rules (universal):
 * <ul>
 *   <li>{@code username} — overwrite when differs (case-sensitive).</li>
 *   <li>{@code displayName} — when claims provide one, overwrite when
 *       {@code !equalsIgnoreCase} (fixes the pre-Phase-8 case-sensitive
 *       inconsistency that caused gratuitous rewrites on every login).</li>
 *   <li>{@code email} — when claims provide one, overwrite when
 *       {@code !equalsIgnoreCase}.</li>
 *   <li>{@code authenticationSource} — stamp {@link IdentityClaims#sourceId()}
 *       when differs. Single canonical stamp site (fixes the pre-Phase-8
 *       behaviour where {@code RaplaAuthentificationService} hardcoded
 *       {@code "ldap"} regardless of which auth store ran).</li>
 *   <li>Groups — only applied on new-user creation (existing users keep
 *       whatever group set an admin curated). Delegates to {@link #resolveGroups}.</li>
 * </ul>
 *
 * <p>Plugin override: subclass and override {@link #resolveGroups(IdentityClaims, Category)}
 * for deployment-specific group sources (e.g. dhbwrapla's AD-role mapping).
 * Override field-policy methods ({@link #shouldOverwriteName}, {@link #shouldOverwriteEmail})
 * when a deployment needs different per-field rules (e.g. DHBW's "email only when empty",
 * "never overwrite name").
 */
public class DefaultUserProvisioner implements UserProvisioner
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultUserProvisioner.class);

    /** Exposed to subclasses (e.g. dhbwrapla's {@code DhbwUserProvisioner})
     *  so plugin provisioners can do their own pre-provision lookups without
     *  duplicating the field. */
    protected final CachableStorageOperator operator;

    public DefaultUserProvisioner(CachableStorageOperator operator)
    {
        this.operator = operator;
    }

    @Override
    public User provision(IdentityClaims claims, boolean autoProvision) throws RaplaException
    {
        // security-audit A0c / PRD 036: when auto-provisioning is disabled for the provider,
        // an unknown identity must NOT create a new rapla user — reject the login instead.
        // An existing user is still resolved/updated by the delegate below.
        if (!autoProvision && lookup(claims.username()) == null)
        {
            throw new org.rapla.storage.RaplaSecurityException(
                    "Auto-provisioning is disabled for this provider and no rapla user exists for '"
                            + claims.username() + "'");
        }
        return provision(claims);
    }

    @Override
    public User provision(IdentityClaims claims) throws RaplaException
    {
        Category userGroupsRoot = operator.getSuperCategory().getCategory(Permission.GROUP_CATEGORY_KEY);

        User existing = lookup(claims.username());
        boolean isNew = existing == null;
        User edit = isNew
                ? createNewUser(claims, userGroupsRoot)
                : (User) operator.editObjects(Collections.singletonList((Entity) existing), null)
                        .values().iterator().next();

        boolean modified = isNew;

        if (!claims.username().equals(edit.getUsername()))
        {
            edit.setUsername(claims.username());
            modified = true;
        }

        if (claims.displayName() != null && !claims.displayName().isEmpty()
                && shouldOverwriteName(edit, claims)
                && !claims.displayName().equalsIgnoreCase(edit.getName()))
        {
            edit.setName(claims.displayName());
            modified = true;
        }

        if (claims.email() != null && !claims.email().isEmpty()
                && shouldOverwriteEmail(edit, claims)
                && !claims.email().equalsIgnoreCase(edit.getEmail()))
        {
            edit.setEmail(claims.email());
            modified = true;
        }

        if (!claims.sourceId().equals(edit.getAuthenticationSource()))
        {
            edit.setAuthenticationSource(claims.sourceId());
            modified = true;
        }

        if (!modified)
        {
            return existing;
        }

        operator.storeAndRemove(Collections.singletonList((Entity) edit), Collections.emptyList(), null);
        LOGGER.info("Provisioned rapla user '{}' (source='{}', new={})",
                claims.username(), claims.sourceId(), isNew);
        return operator.getUser(claims.username());
    }

    /**
     * Look up an existing user by username case-insensitively. Returns null
     * when no match (caller falls into auto-provision).
     */
    private User lookup(String username) throws RaplaException
    {
        try
        {
            return operator.getUser(username);
        }
        catch (RaplaException ex)
        {
            return null;
        }
    }

    /**
     * Construct an editable in-memory User with a fresh id, default groups
     * applied. Caller commits via {@code storeAndRemove}.
     */
    @SuppressWarnings("deprecation")
    private User createNewUser(IdentityClaims claims, Category userGroupsRoot) throws RaplaException
    {
        LocalDateTime now = operator.getCurrentTimestamp();
        UserImpl created = new UserImpl(now, now);
        ReferenceInfo<User> id = operator.createIdentifier(User.class, 1).get(0);
        created.setId(id.getId());
        created.setResolver(operator);

        Collection<Category> groups = resolveGroups(claims, userGroupsRoot);
        if (groups == null || groups.isEmpty())
        {
            // No deployment-specific resolver and no explicit claims-side keys
            // → fall back to the vanilla default groups (mirrors what
            // FacadeImpl.newUser does today).
            if (userGroupsRoot != null)
            {
                for (String key : Permission.DEFAULT_USER_GROUPS)
                {
                    Category g = userGroupsRoot.getCategory(key);
                    if (g != null) created.addGroup(g);
                }
            }
        }
        else
        {
            for (Category g : groups) created.addGroup(g);
        }
        return created;
    }

    /**
     * Resolve groups for a new user. Default reads {@link JNDIPlugin#USERGROUP_CONFIG}
     * from system preferences (the pre-Phase-8 "configured groups" knob from
     * JNDI and external-IdP paths combined). When the claims carry explicit
     * {@code groupKeys}, use those instead. When neither produces a non-empty
     * set, the caller falls back to {@link Permission#DEFAULT_USER_GROUPS}.
     *
     * <p>Subclasses override for deployment-specific sources — e.g. dhbwrapla
     * maps the AD username → group keys via {@code DhbwLdapGroupMapper}.
     *
     * @param groupsRoot the user-groups root category (already resolved). May
     *                   be null for unconfigured caches; subclasses should
     *                   handle.
     * @return the group {@link Category} objects (may be empty; caller falls
     *         back to defaults when so).
     */
    protected Collection<Category> resolveGroups(IdentityClaims claims, Category groupsRoot) throws RaplaException
    {
        if (groupsRoot == null) return Collections.emptyList();

        // Explicit group keys on the claim win (used when an upstream caller
        // already knows the membership — e.g. a JWT 'groups' claim).
        if (claims.groupKeys() != null && !claims.groupKeys().isEmpty())
        {
            List<Category> resolved = new ArrayList<>();
            for (String key : claims.groupKeys())
            {
                Category g = groupsRoot.getCategory(key);
                if (g != null) resolved.add(g);
            }
            return resolved;
        }

        // Fall back to the JNDI-plugin-namespaced configured groups (today's
        // shared knob for vanilla rapla + external IdP defaults).
        Preferences systemPrefs = operator.getPreferences(null, true);
        RaplaMap<Category> configured = systemPrefs.getEntry(JNDIPlugin.USERGROUP_CONFIG);
        if (configured == null || configured.values().isEmpty())
        {
            return Collections.emptyList();
        }
        return new ArrayList<>(configured.values());
    }

    /**
     * Whether to overwrite an existing display name with the claims value.
     * Default: yes (when the claims have one). Subclasses override for
     * deployments where the IdP isn't authoritative for display name
     * (e.g. dhbwrapla intentionally doesn't sync name).
     */
    protected boolean shouldOverwriteName(User existing, IdentityClaims claims)
    {
        return true;
    }

    /**
     * Whether to overwrite an existing email with the claims value.
     * Default: yes (when the claims have one). Subclasses override for
     * deployments that treat a user-set email as authoritative
     * (e.g. dhbwrapla syncs only when the local email is empty).
     */
    protected boolean shouldOverwriteEmail(User existing, IdentityClaims claims)
    {
        return true;
    }

}
