/*--------------------------------------------------------------------------*
 | Copyright (C) 2026, Christopher Kohlhaas                                 |
 *--------------------------------------------------------------------------*/
package org.rapla.storage.dbrm;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Narrow user DTO used by {@link UsersService#list()} — the SPA's
 * "Switch to user" dialog typeahead source. PRD 051.
 *
 * <p>AGENTS.md §12 (data-leak-prevention) mandates the per-field
 * shape: only what the dropdown needs ({@code username} for the
 * action, {@code displayName} for the label). NO email, NO group
 * list, NO preferences, NO isAdmin flag. The full {@code User}
 * entity is not safe on the wire even for callers who can admin
 * the target — it carries permissions structure that may leak the
 * deployment's category tree.
 *
 * <p>Existence-leak is enforced upstream: the endpoint only emits
 * entries the caller passes {@code PermissionController.canAdminUser}
 * against.
 */
public final class UserSummary
{
    @JsonProperty("username")
    private String username;

    @JsonProperty("displayName")
    private String displayName;

    public UserSummary() {}

    public UserSummary(String username, String displayName)
    {
        this.username = username;
        this.displayName = displayName == null ? "" : displayName;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName == null ? "" : displayName; }
}
