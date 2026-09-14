/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.server;
import org.rapla.framework.RaplaException;


/**
 * Pluggable authentication source — vanilla rapla's JNDI/LDAP plugin and
 * dhbwrapla's NTLM-bridge are the two impls in tree today.
 *
 * <p>Two-phase contract: {@link #authenticate} verifies credentials,
 * {@link #extractClaims} returns the identity blob for provisioning.
 * Provisioning itself (find-or-create User, mutate fields, store) lives in
 * {@link UserProvisioner} since PRD 050 Phase 8 — the auth store no longer
 * mutates User entities directly. AGENTS.md §16: this is a read interface;
 * no writes from impls.
 */
public interface AuthenticationStore {
    boolean isEnabled();

    /** returns, if the user can be authenticated. */
    boolean authenticate(String username, String password) throws RaplaException;

    /**
     * Translate the authenticated credentials into rapla's identity blob.
     * Called after {@link #authenticate} returned true. Must be side-effect-free
     * — no User mutation, no storage writes. Provisioning runs from the result
     * via {@link UserProvisioner#provision(IdentityClaims)} at the at-login seam.
     *
     * @param username the rapla username key — case-canonical form the
     *                 provisioner will match against {@code User.getUsername()}.
     * @param password forwarded so impls can re-query the upstream source
     *                 (e.g. LDAP bind for attribute lookup); never persisted.
     */
    IdentityClaims extractClaims(String username, String password) throws RaplaException;
}
