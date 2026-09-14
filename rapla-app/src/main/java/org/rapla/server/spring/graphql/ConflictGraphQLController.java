package org.rapla.server.spring.graphql;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
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
import org.springframework.stereotype.Controller;

/**
 * PRD 064 — Conflicts read API; reshaped to the PRD 091 D4 id-based row
 * ({@link ConflictRow}, one wire shape for realized and potential
 * conflicts). Rows are fully materialized here — the §12 gate already
 * resolves both reservations per row, so there is no extra cost and no
 * @SchemaMapping indirection.
 *
 * <p>§12 enforcement at the output boundary:
 * <ul>
 *   <li>Anonymous → error (UNAUTHENTICATED).</li>
 *   <li>Unknown / unreadable {@code reservationId} → empty (existence
 *       not leaked).</li>
 *   <li>For each conflict: if the OTHER reservation isn't caller-readable
 *       OR the allocatable isn't readable → drop the conflict.</li>
 * </ul>
 *
 * <p>Perspective convention (PRD 091 D4): side 1 of every returned row is
 * the queried reservation — conflicts whose stored side 2 is the queried
 * reservation are flipped.
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
    public List<ConflictRow> conflicts(@Argument("reservationId") String reservationId,
            graphql.schema.DataFetchingEnvironment env) throws RaplaException
    {
        var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = UnauthenticatedException.require(rc.caller());
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

        Locale locale = rc.locale();
        List<ConflictRow> visible = new ArrayList<>(raw.size());
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
            if (!rc.canReadAllocatable(alloc)) continue;   // PRD 082 #8 — index membership when flipped, else canRead (§12)

            Appointment a1 = operator.tryResolve(c.getAppointment1());
            Appointment a2 = operator.tryResolve(c.getAppointment2());
            if (a1 == null || a2 == null) continue;   // dangling appointment ref — row unusable

            // Perspective convention: side 1 = the queried reservation.
            boolean flip = !r1.getId().equals(r.getId());
            Reservation own = flip ? r2 : r1;
            Reservation other = flip ? r1 : r2;
            Appointment ownApp = flip ? a2 : a1;
            Appointment otherApp = flip ? a1 : a2;

            visible.add(new ConflictRow(
                    c.getId(),
                    alloc,
                    own.getId(), ownApp.getId(),
                    other.getId(), otherApp.getId(),
                    ownApp, own, other, otherApp,
                    other.getName(locale),
                    c.getStartDate()));
        }
        return visible;
    }
}
