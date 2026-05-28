package org.rapla.server;

import java.util.Collection;

/**
 * The identity blob handed from an auth store / IdP-claim extractor to
 * {@link UserProvisioner}. PRD 050 Phase 8. Provisioning is a write; building
 * an {@code IdentityClaims} is a read of whatever upstream identity source
 * (LDAP, NTLM, OIDC JWT) — keeps the read/write split clean (AGENTS.md §16).
 *
 * @param username    rapla username key — never null/empty. Lowercase-canonical
 *                    for auto-provisioning; matched case-insensitively for lookup.
 * @param displayName null = "no info, don't touch" (provisioner won't write the field)
 * @param email       null = "no info, don't touch"
 * @param sourceId    e.g. {@code "keycloak"}, {@code "ldap"}, {@code "dhbw-ntlm"}.
 *                    Stamped on {@code User.authenticationSource}; never null.
 * @param groupKeys   group {@link org.rapla.entities.Category} keys to apply on
 *                    new-user creation. {@code null} = "let the provisioner decide"
 *                    (default looks up the JNDI-pref-configured groups).
 */
public record IdentityClaims(
        String username,
        String displayName,
        String email,
        String sourceId,
        Collection<String> groupKeys)
{
    public IdentityClaims
    {
        if (username == null || username.isEmpty())
        {
            throw new IllegalArgumentException("IdentityClaims.username is required");
        }
        if (sourceId == null || sourceId.isEmpty())
        {
            throw new IllegalArgumentException("IdentityClaims.sourceId is required");
        }
    }
}
