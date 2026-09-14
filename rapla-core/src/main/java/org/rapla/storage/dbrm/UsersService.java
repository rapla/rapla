/*--------------------------------------------------------------------------*
 | Copyright (C) 2026, Christopher Kohlhaas                                 |
 *--------------------------------------------------------------------------*/
package org.rapla.storage.dbrm;

import org.rapla.framework.RaplaException;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

/**
 * PRD 051 — list users the caller can admin. The SPA's "Switch to
 * user" dialog uses this as the typeahead source. Server-side filter
 * runs {@link org.rapla.storage.PermissionController#canAdminUser}
 * against every user; only those the caller can admin are included
 * (the calling user is also in the list, per design).
 *
 * <p>Empty array = caller has no admin authority — the SPA reads
 * this signal to decide whether to make the username chip
 * clickable.
 *
 * <p>Translates directly to a GraphQL {@code query { users { … } }}
 * field name when the schema migration lands. The "viewer-scoped"
 * filter (return only what the caller can see) is the standard
 * GraphQL resolver pattern and matches AGENTS.md §12.
 */
@HttpExchange("/api/users")
public interface UsersService
{
    @GetExchange
    List<UserSummary> list() throws RaplaException;

    /**
     * Returns the calling user's own identifiers. Useful for callers
     * (SPA, third-party integrators) that need the rapla User id to
     * scope subsequent queries — the JWT alone isn't enough when an
     * external IdP is fronting rapla, since the JWT {@code sub} is
     * the IdP's id, not rapla's. Always available, no permission
     * predicate beyond the standard auth gate.
     */
    @GetExchange("/me")
    UserMe me() throws RaplaException;
}
