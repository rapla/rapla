package org.rapla.server.spring.graphql;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * PRD 064 — Conflicts read API. Single query root
 * {@code conflicts(reservationId)}; per-row {@link Conflict} type
 * derived-field resolution via @SchemaMapping (low cadence — conflicts
 * are typically a handful per reservation, not the 462k-dispatch case
 * that motivated the LightDataFetcher migration on PRD 055 reads).
 *
 * <p>§12 enforcement at the output boundary:
 * <ul>
 *   <li>Anonymous → empty.</li>
 *   <li>Unknown / unreadable {@code reservationId} → empty (existence
 *       not leaked).</li>
 *   <li>For each conflict: if the OTHER reservation isn't caller-readable
 *       OR the allocatable isn't readable → drop the conflict.</li>
 * </ul>
 */
@Controller
public class ConflictGraphQLController
{
    private final StorageOperator operator;

    public ConflictGraphQLController(StorageOperator operator)
    {
        this.operator = operator;
    }

    @QueryMapping
    public List<Conflict> conflicts(@Argument("reservationId") String reservationId,
            graphql.schema.DataFetchingEnvironment env) throws RaplaException
    {
        var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = rc.caller();
        if (caller == null) return List.of();
        if (reservationId == null || reservationId.isBlank()) return List.of();

        PermissionController pc = rc.permissionController() != null
                ? rc.permissionController() : operator.getPermissionController();

        Reservation r;
        try
        {
            r = operator.tryResolve(new ReferenceInfo<>(reservationId, Reservation.class));
        }
        catch (RuntimeException e)
        {
            return List.of();
        }
        if (r == null) return List.of();
        if (!pc.canRead(r, caller)) return List.of();

        // Server-side: the operator is local; use the sync variant rather than
        // wrapping a sync result in a Promise just to await it.
        Collection<Conflict> raw =
                ((org.rapla.storage.SyncStorageOperator) operator).getConflictsSync(r);
        if (raw == null || raw.isEmpty()) return List.of();

        List<Conflict> visible = new ArrayList<>(raw.size());
        for (Conflict c : raw)
        {
            if (c == null) continue;
            // §12 — both sides of the conflict + the allocatable must be readable
            Reservation r1 = operator.tryResolve(c.getReservation1());
            Reservation r2 = operator.tryResolve(c.getReservation2());
            Allocatable alloc = c.getAllocatable();
            if (r1 == null || r2 == null || alloc == null) continue;
            if (!pc.canRead(r1, caller)) continue;
            if (!pc.canRead(r2, caller)) continue;
            if (!pc.canRead(alloc, caller)) continue;
            visible.add(c);
        }
        return visible;
    }

    // ============================================================ Conflict derived fields

    @SchemaMapping(typeName = "Conflict", field = "reservation1")
    public Reservation reservation1(Conflict c) throws RaplaException
    {
        return operator.tryResolve(c.getReservation1());
    }

    @SchemaMapping(typeName = "Conflict", field = "reservation2")
    public Reservation reservation2(Conflict c) throws RaplaException
    {
        return operator.tryResolve(c.getReservation2());
    }

    @SchemaMapping(typeName = "Conflict", field = "appointment1")
    public Appointment appointment1(Conflict c) throws RaplaException
    {
        return operator.tryResolve(c.getAppointment1());
    }

    @SchemaMapping(typeName = "Conflict", field = "appointment2")
    public Appointment appointment2(Conflict c) throws RaplaException
    {
        return operator.tryResolve(c.getAppointment2());
    }

    @SchemaMapping(typeName = "Conflict", field = "startDate")
    public LocalDateTime startDate(Conflict c)
    {
        return c.getStartDate();
    }

    @SchemaMapping(typeName = "Conflict", field = "allocatable")
    public Allocatable allocatable(Conflict c)
    {
        return c.getAllocatable();
    }

}
