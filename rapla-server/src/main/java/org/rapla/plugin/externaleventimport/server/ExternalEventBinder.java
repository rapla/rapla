package org.rapla.plugin.externaleventimport.server;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.externaleventimport.ExternalEventCreateService;
import org.rapla.plugin.externaleventimport.ImportItem;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.PermissionController;
import org.rapla.storage.SyncStorageOperator;

/**
 * Binds an existing reservation to an OPEN staged item — the case where somebody entered the
 * event by hand and only afterwards wants the source to know about it.
 *
 * <p>Everything deployment-specific about the stamp itself sits behind
 * {@link ExternalEventCreateService#bindStagedItem}; this class owns only the gate.
 *
 * <p>§12: every rejection returns {@code false}. Unknown item, item not OPEN, no bookable group,
 * unreadable reservation and a deployment that refuses are indistinguishable from outside — a
 * caller must not be able to probe which of them applied.
 */
public class ExternalEventBinder
{
    private static final int DEFAULT_LIMIT = 3;

    /** Below this, a candidate is noise. Name overlap alone only clears it when the names are
     *  nearly identical; a shared everyday word like "Software" must never suggest anything.
     *  An empty list is the honest answer — the UI says "no suggestions", which is true. */
    private static final double MIN_SCORE = 0.55;

    /** Structured identifiers beat prose: they are the source's own keys. Still only signals —
     *  two source records can share an event number (verified 2026-08-11, T3INF3001.2). */
    private static final double EVENT_NR_WEIGHT = 1.0;
    private static final double UNIT_WEIGHT = 0.8;

    private final ExternalEventStagingReader reader;
    private final ExternalEventCreateService createService;
    private final CachableStorageOperator operator;

    public ExternalEventBinder(ExternalEventStagingReader reader, ExternalEventCreateService createService,
            CachableStorageOperator operator)
    {
        this.reader = reader;
        this.createService = createService;
        this.operator = operator;
    }

    /**
     * Reservations in the given window that could be the staged item — ranked, never chosen.
     *
     * <p>Only reservations allocated to a group the caller may book, only ones the caller may
     * read (the query is user-scoped), and never one that already carries an external id: those
     * belong to another staged item and binding would steal them.
     *
     * <p>The window comes from the caller (the calendar range on screen) rather than from a
     * semester lookup — the deployment-specific scope resolution does not exist yet, and this
     * needs no server-side notion of "semester" to be useful.
     */
    public List<BindCandidate> candidates(User caller, PermissionController permissionController, String sourceItemId,
            LocalDateTime from, LocalDateTime to, Integer limit) throws RaplaException
    {
        if (caller == null || permissionController == null || sourceItemId == null || from == null || to == null)
        {
            return List.of();
        }
        final List<ImportItem> open = reader.loadOpenItems(List.of(sourceItemId));
        if (open.isEmpty())
        {
            return List.of();
        }
        final ImportItem item = open.get(0);
        final Collection<String> groupIds = reader.bookableGroups(caller, permissionController, open)
                .get(sourceItemId);
        if (groupIds == null || groupIds.isEmpty())
        {
            return List.of();
        }
        final List<Allocatable> groups = new ArrayList<>();
        for (String id : groupIds)
        {
            final Allocatable allocatable = operator.tryResolve(new ReferenceInfo<>(id, Allocatable.class));
            if (allocatable != null)
            {
                groups.add(allocatable);
            }
        }
        if (groups.isEmpty())
        {
            return List.of();
        }
        final Collection<Reservation> reservations = ((SyncStorageOperator) operator).getReservationsSync(caller,
                groups.toArray(Allocatable[]::new), null, from, to, null);

        final Set<String> wanted = tokens(text(item));
        final List<BindCandidate> candidates = new ArrayList<>();
        for (Reservation reservation : reservations)
        {
            if (reservation.getAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID) != null)
            {
                continue;
            }
            final String name = reservation.getName(null);
            candidates.add(new BindCandidate(reservation.getId(), name, firstDate(reservation),
                    score(wanted, item, name)));
        }
        candidates.removeIf(candidate -> candidate.score() < MIN_SCORE);
        candidates.sort(Comparator.comparingDouble(BindCandidate::score).reversed());
        final int max = limit != null && limit > 0 ? limit : DEFAULT_LIMIT;
        return candidates.size() > max ? new ArrayList<>(candidates.subList(0, max)) : candidates;
    }

    private static String firstDate(Reservation reservation)
    {
        LocalDateTime earliest = null;
        for (Appointment appointment : reservation.getAppointments())
        {
            final LocalDateTime start = appointment.getStart();
            if (start != null && (earliest == null || start.isBefore(earliest)))
            {
                earliest = start;
            }
        }
        return earliest != null ? earliest.toString() : null;
    }

    private static String text(ImportItem item)
    {
        final Object full = item.getColumns().get("fullName");
        final Object name = item.getColumns().get("name");
        return String.valueOf(full != null ? full : name);
    }

    /** Structured identifiers first, prose second. Deliberately dumb and explainable: the score
     *  orders a list a human reads, it never decides. */
    private static double score(Set<String> wanted, ImportItem item, String reservationName)
    {
        double score = contains(reservationName, item.getColumns().get("eventNr")) ? EVENT_NR_WEIGHT : 0;
        if (contains(reservationName, item.getColumns().get("unitId")))
        {
            score += UNIT_WEIGHT;
        }
        final Set<String> found = tokens(reservationName);
        if (wanted.isEmpty() || found.isEmpty())
        {
            return score;
        }
        final Set<String> union = new LinkedHashSet<>(wanted);
        union.addAll(found);
        int shared = 0;
        for (String token : wanted)
        {
            if (found.contains(token))
            {
                shared++;
            }
        }
        // Jaccard: one shared everyday word among many stays far below the threshold, while
        // near-identical names clear it on their own.
        return score + (double) shared / union.size();
    }

    private static boolean contains(String haystack, Object needle)
    {
        final String value = needle != null ? needle.toString().trim() : "";
        return !value.isEmpty() && haystack != null && haystack.contains(value);
    }

    private static Set<String> tokens(String value)
    {
        if (value == null)
        {
            return Set.of();
        }
        final Set<String> result = new LinkedHashSet<>();
        for (String token : value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
        {
            if (token.length() > 2)
            {
                result.add(token);
            }
        }
        return result;
    }

    public boolean bind(User caller, PermissionController permissionController, String sourceItemId,
            String reservationId) throws RaplaException
    {
        // No deployment create service = nothing can stamp; same silent false as any rejection.
        if (createService == null || caller == null || permissionController == null || sourceItemId == null
                || reservationId == null)
        {
            return false;
        }
        final List<ImportItem> open = reader.loadOpenItems(List.of(sourceItemId));
        if (open.isEmpty())
        {
            return false;
        }
        final ImportItem item = open.get(0);
        if (!reader.bookableGroups(caller, permissionController, open).containsKey(sourceItemId))
        {
            return false;
        }
        return createService.bindStagedItem(caller, item, reservationId);
    }
}
