package org.rapla.server.spring.graphql;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaException;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * PRD 081 — omnibox multisearch. One §12-scoped, ranked, kind-bucketed
 * surface that turns a free-text term into typed result rows. The server
 * returns kind + data; ACTIONS are a CLIENT concern (the SPA derives the
 * action buttons from {@code kind}).
 *
 * <p><b>Phase 1: RESOURCE + EVENT.</b> OCCURRENCE / GROUP are reserved in
 * {@link SearchKind} for later phases and currently yield no bucket.
 *
 * <ul>
 *   <li><b>RESOURCE</b> reuses the PRD 028 {@code allocatables} evaluator
 *       ({@link ClassificationGraphQLController#allocatables}) — already
 *       §12-filtered. Matching is FUZZY by default (OQ3) for typo recall.</li>
 *   <li><b>EVENT</b> is a WINDOWLESS name scan (OQ1) over every reservation in
 *       the operator cache ({@link CachableStorageOperator#getReservations()}),
 *       gated per hit by {@code canModify} — the omnibox surfaces only events the
 *       caller can EDIT (event rows carry edit/navigate actions), which is
 *       STRICTER than §12 read. Matching is SUBSTRING only — NEVER fuzzy: the
 *       candidate set is large and per-candidate Levenshtein would be O(n·m).
 *       Phase 1 is the naive full scan; a name index is a measured follow-up.</li>
 * </ul>
 *
 * <p><b>§12 — existence never leaks.</b> A term matching only hidden entities
 * returns the SAME empty {@code groups} list as a no-match term: empty buckets
 * are omitted, and the per-kind permission gate (RESOURCE: read; EVENT: edit)
 * runs at the output boundary.
 * Ranking (OQ2): kind buckets, each internally sorted by match strength →
 * position → id; {@code score} is the inverted {@link SearchMatcher} rank so
 * higher = better on the wire.
 */
@Controller
public class SearchGraphQLController
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchGraphQLController.class);

    /** Default per-kind cap (OQ: {@code limit} caps PER KIND). */
    private static final int DEFAULT_LIMIT = 20;

    private final StorageOperator operator;
    private final ClassificationGraphQLController classificationController;

    public SearchGraphQLController(StorageOperator operator,
            ClassificationGraphQLController classificationController)
    {
        this.operator = operator;
        this.classificationController = classificationController;
    }

    @QueryMapping
    public SearchResults search(@Argument("query") String query,
            @Argument("kinds") List<SearchKind> kinds,
            @Argument("limit") Integer limit,
            graphql.schema.DataFetchingEnvironment env) throws RaplaException
    {
        var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        User caller = UnauthenticatedException.require(rc.caller());
        PermissionController pc = rc.permissionController() != null
                ? rc.permissionController() : operator.getPermissionController();
        Locale locale = rc.locale() != null ? rc.locale() : Locale.getDefault();

        String needle = query == null ? "" : query.trim();
        int cap = (limit != null && limit > 0) ? limit : DEFAULT_LIMIT;
        Set<SearchKind> wanted = (kinds == null || kinds.isEmpty())
                ? EnumSet.of(SearchKind.RESOURCE, SearchKind.EVENT)
                : EnumSet.copyOf(kinds);

        List<SearchGroup> groups = new ArrayList<>();
        if (needle.isEmpty()) return new SearchResults(groups);   // blank term → no results

        if (wanted.contains(SearchKind.RESOURCE))
        {
            List<SearchHit> hits = searchResources(needle, cap, locale);
            if (!hits.isEmpty()) groups.add(new SearchGroup(SearchKind.RESOURCE, "Ressourcen", hits));
        }
        if (wanted.contains(SearchKind.EVENT))
        {
            List<SearchHit> hits = searchEvents(needle, cap, caller, pc, locale);
            if (!hits.isEmpty()) groups.add(new SearchGroup(SearchKind.EVENT, "Veranstaltungen", hits));
        }
        return new SearchResults(groups);
    }

    // === RESOURCE ============================================================

    /**
     * RESOURCE bucket — delegates §12 + matching to the PRD 028
     * {@code allocatables} evaluator (no limit so ranking is global), then
     * re-ranks/caps locally so {@code score} and order are locale-consistent.
     * FUZZY by default (OQ3).
     */
    private List<SearchHit> searchResources(String needle, int cap, Locale locale) throws RaplaException
    {
        Map<String, Object> filter = Map.of(
                "searchText", needle,
                "matchKind", SearchMatcher.MatchKind.FUZZY.name());
        List<Allocatable> matched = classificationController.allocatables(filter);
        if (matched == null || matched.isEmpty()) return List.of();

        record Scored(Allocatable a, int rank) {}
        List<Scored> scored = new ArrayList<>(matched.size());
        for (Allocatable a : matched)
        {
            if (a == null) continue;
            int rank = SearchMatcher.rank(a.getName(locale), needle, SearchMatcher.MatchKind.FUZZY);
            if (rank == Integer.MAX_VALUE) continue;            // defensive — allocatables already filtered
            scored.add(new Scored(a, rank));
        }
        scored.sort(Comparator.comparingInt((Scored s) -> s.rank())
                .thenComparing(s -> idOf(s.a())));
        if (scored.size() > cap)
            LOGGER.debug("search RESOURCE truncated {} → {} for term '{}'", scored.size(), cap, needle);

        List<SearchHit> hits = new ArrayList<>(Math.min(cap, scored.size()));
        for (Scored s : scored)
        {
            if (hits.size() >= cap) break;
            Allocatable a = s.a();
            hits.add(new ResourceHit(a.getId(), a.getName(locale), typeKeyOf(a),
                    score(s.rank()), a));
        }
        return hits;
    }

    // === EVENT ===============================================================

    /**
     * EVENT bucket — windowless name scan over every cached reservation, gated
     * per hit by {@code canModify}: the omnibox only surfaces events the caller
     * can EDIT (event rows carry edit/navigate actions, so a non-editable event
     * is noise and a wider leak surface). This is STRICTER than §12 read — it
     * never exposes an event the caller couldn't already open for editing in the
     * Swing client. SUBSTRING only (never FUZZY — large candidate set). Match
     * BEFORE the permission walk: the name compare is microseconds; the walk is not.
     */
    private List<SearchHit> searchEvents(String needle, int cap, User caller,
            PermissionController pc, Locale locale) throws RaplaException
    {
        Collection<Reservation> all = ((CachableStorageOperator) operator).getReservations();
        if (all == null || all.isEmpty()) return List.of();

        record Scored(Reservation r, int rank) {}
        List<Scored> scored = new ArrayList<>();
        for (Reservation r : all)
        {
            if (r == null) continue;
            if (isInternalOrTemplate(r)) continue;
            int rank = SearchMatcher.rank(r.getName(locale), needle, SearchMatcher.MatchKind.SUBSTRING);
            if (rank == Integer.MAX_VALUE) continue;            // no name match — cheap, before the perm walk
            if (!pc.canModify(r, caller)) continue;             // editable-only — never trust the scan
            scored.add(new Scored(r, rank));
        }
        if (scored.isEmpty()) return List.of();
        scored.sort(Comparator.comparingInt((Scored s) -> s.rank())
                .thenComparing(s -> idOf(s.r())));
        if (scored.size() > cap)
            LOGGER.debug("search EVENT truncated {} → {} for term '{}'", scored.size(), cap, needle);

        List<SearchHit> hits = new ArrayList<>(Math.min(cap, scored.size()));
        for (Scored s : scored)
        {
            if (hits.size() >= cap) break;
            Reservation r = s.r();
            hits.add(new EventHit(r.getId(), r.getName(locale), eventSublabel(r, locale),
                    score(s.rank()), r, r.getFirstDate()));
        }
        return hits;
    }

    // === helpers =============================================================

    /** Inverted rank → score (HIGHER is better on the wire). Bounded in (0, 1]. */
    static double score(int rank)
    {
        if (rank == Integer.MAX_VALUE) return 0.0;
        return 1.0 / (1.0 + rank);
    }

    private static String idOf(Allocatable a) { String id = a.getId(); return id == null ? "" : id; }
    private static String idOf(Reservation r) { String id = r.getId(); return id == null ? "" : id; }

    private static String typeKeyOf(Allocatable a)
    {
        Classification c = a.getClassification();
        if (c == null) return null;
        DynamicType dt = c.getType();
        return dt == null ? null : dt.getKey();
    }

    /** Sublabel for an event row — its DynamicType key (the "context line"). */
    private static String eventSublabel(Reservation r, Locale locale)
    {
        Classification c = r.getClassification();
        if (c == null) return null;
        DynamicType dt = c.getType();
        return dt == null ? null : dt.getKey();
    }

    /** Template reservations + rapla-internal types never surface as events. */
    private static boolean isInternalOrTemplate(Reservation r)
    {
        if (r.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE) != null) return true;
        Classification c = r.getClassification();
        return c != null && ClassificationSdlGenerator.isRaplaInternal(c.getType());
    }

    // === GraphQL output types ================================================

    /** Mirrors the GraphQL {@code SearchKind} enum (names must match the SDL). */
    public enum SearchKind { RESOURCE, EVENT, OCCURRENCE, GROUP }

    /** {@code type SearchResults}. */
    public record SearchResults(List<SearchGroup> groups) {}

    /** {@code type SearchGroup}. */
    public record SearchGroup(SearchKind kind, String heading, List<SearchHit> hits) {}

    /** {@code interface SearchHit} — common shape; the concrete type is chosen
     *  by the TypeResolver wired in {@link GeneratedClassificationWiring}. */
    public sealed interface SearchHit permits ResourceHit, EventHit
    {
        String id();
        String label();
        String sublabel();
        double score();
    }

    /** {@code type ResourceHit implements SearchHit}. */
    public record ResourceHit(String id, String label, String sublabel, double score,
            Allocatable allocatable) implements SearchHit {}

    /** {@code type EventHit implements SearchHit}. */
    public record EventHit(String id, String label, String sublabel, double score,
            Reservation reservation, LocalDateTime firstOccurrenceStart) implements SearchHit {}
}
