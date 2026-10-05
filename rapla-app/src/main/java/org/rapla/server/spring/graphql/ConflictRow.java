package org.rapla.server.spring.graphql;

import java.time.LocalDateTime;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;

/**
 * Wire row for the GraphQL {@code Conflict} type (PRD 091 D4) — one shape for
 * realized conflicts ({@code conflicts(filter:)}) and potential
 * conflicts ({@code potentialConflicts}). Fully materialized at query time;
 * field names match the schema, so no @SchemaMapping resolvers are needed.
 *
 * <p>Nullability is semantic: {@code reservation1} is null for a brand-new
 * draft (side 1 not persisted); {@code reservation2}/{@code appointment2}
 * are null when §12-masked.
 */
public record ConflictRow(
        String id,
        Allocatable resource,
        String reservation1Id,
        String appointment1Id,
        String reservation2Id,
        String appointment2Id,
        Appointment appointment1,
        Reservation reservation1,
        Reservation reservation2,
        Appointment appointment2,
        String description,
        LocalDateTime startDate,
        boolean disabled)
{
}
