package org.rapla.rest;

import org.rapla.framework.RaplaException;
import org.rapla.rest.dto.PermissionMigrationFinding;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * PRD 090 — admin-only REST contract for draining the additive-permission
 * migration worklist. Admin-scoped operational tooling (like the api-key / auth
 * REST surfaces), <b>not</b> domain data — so it is REST, not GraphQL. Transient:
 * removable in a later release once deployments have migrated.
 *
 * <p>{@code PermissionMigrationController} implements it (PRD 049 single-source
 * routing).
 */
@HttpExchange("/api/admin/permission-migration")
public interface PermissionMigrationService
{
    /** The still-open worklist: frozen allocatables whose effective access rose at
     * the flip, minus acknowledged ones and ones already cleaned by an edit. */
    @GetExchange("/findings")
    List<PermissionMigrationFinding> getFindings() throws RaplaException;

    /** Resolve one allocatable: prune its inert {@code DENIED} rows and mark it
     * acknowledged. Returns the remaining worklist. */
    @PostExchange("/{allocatableId}/resolve")
    List<PermissionMigrationFinding> resolve(@PathVariable("allocatableId") String allocatableId) throws RaplaException;
}
