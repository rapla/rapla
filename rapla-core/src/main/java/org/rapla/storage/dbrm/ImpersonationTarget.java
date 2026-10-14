/*--------------------------------------------------------------------------*
 | Copyright (C) 2026, Christopher Kohlhaas                                 |
 *--------------------------------------------------------------------------*/
package org.rapla.storage.dbrm;

/**
 * Response of {@code POST /api/auth/impersonate/switch} (PRD 131 D7): the authorized target. The SPA keeps
 * {@code id} in its tab and sends it as {@code X-Rapla-Act-As}; no credential changes hands.
 */
public record ImpersonationTarget(String id, String username) {}
