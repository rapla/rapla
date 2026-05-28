package org.rapla.server;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;

/**
 * Owns the rapla-side write that materialises an {@link IdentityClaims}
 * into a {@link User} row — find-or-create the user, apply per-field
 * comparison rules, stamp {@code authenticationSource}, resolve groups,
 * single {@code storeAndRemove}. PRD 050 Phase 8.
 *
 * <p>Provisioning lives behind an SPI so plugins replace it via a
 * {@code @Bean} (matches {@link AuthenticationStore}). Rapla ships
 * {@code DefaultUserProvisioner} as {@code @ConditionalOnMissingBean};
 * dhbwrapla's {@code DhbwUserProvisioner} subclasses it to swap in
 * AD-role-mapped groups and DHBW-specific per-field policy.
 *
 * <p><strong>Call only from at-login seams</strong> — OAuth token exchange
 * (once per code-grant + once per refresh-grant), password-grant
 * authentication, JNDI/NTLM authentication. Not from the resource-server
 * request path; that's AGENTS.md §16 (read APIs don't mutate).
 */
public interface UserProvisioner
{
    /**
     * Find the existing rapla user for {@code claims.username()} or auto-provision
     * a new one. Apply per-field rules (case-insensitive comparisons for name
     * and email, stamp {@code authenticationSource} from {@link IdentityClaims#sourceId()},
     * resolve groups). Idempotent — when nothing changed, no storage write.
     *
     * @return the post-provisioning {@link User} (re-read from cache).
     */
    User provision(IdentityClaims claims) throws RaplaException;
}
