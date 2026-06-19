/*--------------------------------------------------------------------------*
 | Copyright (C) 2026, Christopher Kohlhaas                                 |
 *--------------------------------------------------------------------------*/
package org.rapla.storage.dbrm;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * PRD 072 Phase 2 — response body for {@code GET /api/auth/me}. Carries ONLY
 * the caller's own identity (AGENTS.md §12 — never another user's data):
 * username, display name, the caller's roles, and the impersonation state.
 *
 * <p>When the caller is impersonating, {@code impersonating} is {@code true},
 * {@code username}/{@code name} reflect the impersonated TARGET (the effective
 * subject of the token), and {@code actor}/{@code target} name the admin actor
 * and the target respectively. When not impersonating, {@code impersonating} is
 * {@code false} and {@code actor}/{@code target} are {@code null}.
 */
public final class IdentityResponse
{
    @JsonProperty("username")
    private String username;

    @JsonProperty("name")
    private String name;

    @JsonProperty("admin")
    private boolean admin;

    @JsonProperty("roles")
    private List<String> roles;

    @JsonProperty("impersonating")
    private boolean impersonating;

    @JsonProperty("actor")
    private String actor;

    @JsonProperty("target")
    private String target;

    public IdentityResponse() {}

    public IdentityResponse(String username, String name, boolean admin, List<String> roles,
                            boolean impersonating, String actor, String target)
    {
        this.username = username;
        this.name = name;
        this.admin = admin;
        this.roles = roles;
        this.impersonating = impersonating;
        this.actor = actor;
        this.target = target;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public boolean isImpersonating() { return impersonating; }
    public void setImpersonating(boolean impersonating) { this.impersonating = impersonating; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
}
