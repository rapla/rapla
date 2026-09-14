package org.rapla.rest.dto;

import java.util.List;

/**
 * PRD 090 — one row of the admin additive-permission migration worklist: an
 * allocatable on which at least one principal gained access at the flip. Names
 * and levels are recomputed from live permissions for display (never persisted —
 * only the allocatable id + ack flag are stored; AGENTS.md §17).
 */
public record PermissionMigrationFinding(
        String allocatableId,
        String allocatableName,
        List<PrincipalEscalation> escalations)
{
    /** One principal that gains access on the allocatable. */
    public record PrincipalEscalation(
            String principalType,   // "USER"
            String principalName,
            String currentLevel,    // precedence-effective level today (e.g. "READ", "DENIED")
            String additiveLevel,   // level gained under additive (e.g. "ALLOCATE")
            String form,            // "DENIED" | "SOFT_DENY"
            String explanation)
    {
    }
}
