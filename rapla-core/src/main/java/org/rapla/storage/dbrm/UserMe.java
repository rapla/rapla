/*--------------------------------------------------------------------------*
 | Copyright (C) 2026, Christopher Kohlhaas                                 |
 *--------------------------------------------------------------------------*/
package org.rapla.storage.dbrm;

/**
 * Self-information DTO returned by {@code GET /api/users/me}.
 *
 * <p>Identifies the calling user as rapla sees them. The SPA can't derive
 * the rapla User id from JWT claims alone — when an external IdP (Keycloak,
 * etc.) fronts rapla, the JWT {@code sub} is the IdP's id, not the rapla
 * User id. The {@code preferred_username} claim usually matches but isn't
 * guaranteed to. {@code GET /api/users/me} is the authoritative answer:
 * the server already resolved the calling user from the bearer token at
 * the session-check layer, so it just hands back the identifiers.
 *
 * <p>Narrow shape per AGENTS.md §12 — id, username, displayName only.
 * Email / groups / preferences / isAdmin stay out; this DTO is for
 * "who am I" callsites, not for displaying profile data. Add fields
 * deliberately and only when a UI surface needs them.
 */
public record UserMe(String id, String username, String displayName) {}
