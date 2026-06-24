package org.rapla.server.spring.graphql;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.impl.server.LocalAbstractCachableOperator;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

/**
 * PRD 035 Cut C — GraphQL resolvers for allocatables, dynamic types, and the
 * Classification interface. Goes through {@link StorageOperator} (NOT
 * {@link org.rapla.facade.RaplaFacade}, per the 2026-05-25 decision); §12
 * filtering is inline at the output boundary, matching the pattern in
 * {@link HelloGraphQLController}.
 *
 * <p>The interface fields ({@code Classification.typeKey}, {@code .type})
 * are resolved here via {@link SchemaMapping}; the
 * GENERATED per-DynamicType implementing types (e.g. {@code RoomClassification})
 * are wired programmatically by {@link HotSwappableGraphQlSource} using
 * {@link GeneratedClassificationWiring} — their attribute fields can't carry
 * {@code @SchemaMapping} because there's no Java class to put it on (rapla
 * has no Java class per DynamicType — they're all {@code ClassificationImpl}
 * with a DynamicType pointer; see PRD 035 line 554-560).
 *
 * <p>Reference-valued attributes in classifications (CATEGORY / ALLOCATABLE)
 * carry §12: the resolved value is dropped (singular) or filtered (multi) if
 * the caller can't read the referent. PermissionController.canRead is the
 * gate on every output boundary.
 */
@Controller
public class ClassificationGraphQLController
{
    private final StorageOperator operator;
    private final org.rapla.server.spring.JwtUserResolver jwtUserResolver;

    public ClassificationGraphQLController(StorageOperator operator,
            org.rapla.server.spring.JwtUserResolver jwtUserResolver)
    {
        this.operator = operator;
        this.jwtUserResolver = jwtUserResolver;
    }

    // === Query roots ==========================================================

    @QueryMapping
    public List<Allocatable> allocatables(@Argument("filter") Map<String, Object> filterMap) throws RaplaException
    {
        // PRD 035 §5d Phase 2 — argument is the raw input map (not the
        // AllocatableFilter record). The record can't carry the dynamic
        // `where<TypeKey>` fields that the SDL extension adds per
        // resource/person DynamicType. The map carries everything: legacy
        // scalar predicates (typeKeyEq/typeKeyIn/...) AND the per-type
        // whereXxx blocks consumed by the (Phase 3+) WhereEvaluator.
        AllocatableFilter filter = fromMap(filterMap);

        User caller = UnauthenticatedException.require(resolveCaller());
        PermissionController pc = operator.getPermissionController();
        // PRD 069 — admin-scoped access-by-target predicate (null if no access
        // selector). Resolution enforces the caller's admin scope and throws a
        // uniform FORBIDDEN for unknown/out-of-scope handles (§12).
        AccessTargetFilter accessFilter = AccessTargetFilter.create(
                stringArg(filterMap, "accessibleByUsername"),
                stringArg(filterMap, "accessibleByUserId"),
                stringListArg(filterMap, "accessibleByGroup"),
                accessLevelArg(filterMap), caller, operator, pc);
        // PRD 082 #8 — when the read-model is flipped authoritative, the per-entity canRead scan over
        // (here) ~48k allocatables (~26 ms/query for a non-admin) is replaced by an O(1) membership test
        // against the per-user readable-id cache. The set is canRead-equal by construction (§12), so the
        // filter result is identical; null when off / not the server operator, keeping the canRead path.
        final java.util.Set<String> readableIds =
                (operator instanceof LocalAbstractCachableOperator lo && lo.isReadModelAuthoritative())
                        ? lo.readableAllocatableIds(caller) : null;
        // PRD 066 — dedup by id across the two union arms.
        java.util.LinkedHashMap<String, Allocatable> resultById = new java.util.LinkedHashMap<>();

        // PRD 066 — when idIn is the ONLY selector populated, skip the
        // type-bucket pass entirely (the empty-filter "return everything"
        // semantic doesn't apply when the caller has explicitly named ids).
        boolean hasIdIn = filter != null && filter.idIn() != null && !filter.idIn().isEmpty();
        boolean runTypeBucket = !hasIdIn || hasTypeBucketSelector(filter, filterMap);

        if (runTypeBucket)
        {
            // Operator-level pre-filter when typeKeyEq is set — avoids materializing
            // every allocatable across all types just to narrow to one type.
            ClassificationFilter[] storageFilter = buildStorageFilter(filter);
            Collection<Allocatable> all = operator.getAllocatables(storageFilter);
            if (all == null) all = List.of();
            int cap = (filter != null && filter.limit() != null) ? filter.limit() : Integer.MAX_VALUE;
            for (Allocatable a : all)
            {
                if (a == null) continue;
                if (isInternalAllocatable(a)) continue;   // skip rapla-internal (template/period/etc.)
                // Match-then-canRead — matches() is microseconds on hash-compare;
                // canRead can be a permission-graph walk for non-admins. Filtering
                // first short-circuits the expensive check for non-matching entries.
                if (!matches(a, filter)) continue;
                if (!evaluateWhere(a, filterMap, caller, pc)) continue;
                if (readableIds != null ? !readableIds.contains(a.getId()) : !pc.canRead(a, caller)) continue;
                if (accessFilter != null && !accessFilter.test(a)) continue;   // PRD 069
                resultById.putIfAbsent(a.getId(), a);
                if (resultById.size() >= cap) break;
            }
        }

        // PRD 066 — idIn pass. Each id is resolved + §12-gated; filter rules
        // do NOT apply (the caller picked these explicitly). Cap doesn't
        // apply either — explicit picks always come back.
        if (hasIdIn)
        {
            for (String id : filter.idIn())
            {
                if (id == null || id.isBlank()) continue;
                if (resultById.containsKey(id)) continue;       // already in type-bucket
                Allocatable a;
                try { a = operator.tryResolve(new ReferenceInfo<>(id, Allocatable.class)); }
                catch (RuntimeException e) { continue; }
                if (a == null) continue;
                if (isInternalAllocatable(a)) continue;
                if (readableIds != null ? !readableIds.contains(a.getId()) : !pc.canRead(a, caller)) continue;
                if (accessFilter != null && !accessFilter.test(a)) continue;   // PRD 069
                resultById.put(id, a);
            }
        }

        List<Allocatable> visible = new ArrayList<>(resultById.values());
        // PRD 028 Phase 1 — server-side rank when searchText is set.
        if (filter != null && filter.searchText() != null && !filter.searchText().isBlank())
        {
            SearchMatcher.MatchKind kind = filter.matchKind() != null
                    ? filter.matchKind() : SearchMatcher.MatchKind.SUBSTRING;
            String needle = filter.searchText();
            visible.sort((x, y) -> {
                int rx = SearchMatcher.rank(x.getName(Locale.getDefault()), needle, kind);
                int ry = SearchMatcher.rank(y.getName(Locale.getDefault()), needle, kind);
                if (rx != ry) return Integer.compare(rx, ry);
                String ix = x.getId();
                String iy = y.getId();
                return (ix == null ? "" : ix).compareTo(iy == null ? "" : iy);
            });
        }
        return visible;
    }

    /**
     * PRD 066 — does the filter narrow the type-bucket pass via any selector
     * other than {@code idIn}? Used to decide whether to run the bucket
     * pass when only {@code idIn} is set (otherwise we'd return everything
     * union idIn, which isn't the intended "idIn = exactly these" semantic).
     */
    private static boolean hasTypeBucketSelector(AllocatableFilter f, Map<String, Object> filterMap)
    {
        if (f == null) return false;
        if (f.typeKeyEq() != null && !f.typeKeyEq().isBlank()) return true;
        if (f.typeKeyIn() != null && !f.typeKeyIn().isEmpty()) return true;
        if (f.isPersonEq() != null) return true;
        if (f.nameContains() != null && !f.nameContains().isBlank()) return true;
        if (f.searchText() != null && !f.searchText().isBlank()) return true;
        if (f.ownerEq() != null && !f.ownerEq().isBlank()) return true;
        if (filterMap != null)
        {
            for (Map.Entry<String, Object> e : filterMap.entrySet())
            {
                if (e.getKey() != null && e.getKey().startsWith("where") && e.getValue() != null) return true;
            }
        }
        return false;
    }

    /**
     * Adapt the raw input map (post-§5d Phase 2 resolver shape) to the
     * existing {@link AllocatableFilter} record. The map carries both legacy
     * scalar fields (extracted here) and per-type `where<TypeKey>` blocks
     * (consumed separately by the evaluator).
     */
    @SuppressWarnings("unchecked")
    private static AllocatableFilter fromMap(Map<String, Object> m)
    {
        if (m == null) return null;
        SearchMatcher.MatchKind matchKind = null;
        Object mk = m.get("matchKind");
        if (mk instanceof String s) {
            try { matchKind = SearchMatcher.MatchKind.valueOf(s); }
            catch (IllegalArgumentException ignored) {}
        } else if (mk instanceof SearchMatcher.MatchKind k) {
            matchKind = k;
        }
        return new AllocatableFilter(
                (String) m.get("typeKeyEq"),
                (List<String>) m.get("typeKeyIn"),
                (Boolean) m.get("isPersonEq"),
                (String) m.get("nameContains"),
                (String) m.get("searchText"),
                matchKind,
                (String) m.get("ownerEq"),
                (List<String>) m.get("idIn"),       // PRD 066
                (Integer) m.get("limit"));
    }

    /**
     * §5d Phase 3 — delegates to {@link WhereEvaluator} which dispatches
     * one operator per predicate kind. Combinators + remaining operators
     * land in Phases 4–5.
     */
    private static boolean evaluateWhere(Allocatable a, Map<String, Object> filterMap,
            User caller, PermissionController pc)
    {
        return WhereEvaluator.evaluate(a, filterMap, caller, pc);
    }

    /**
     * Build a {@link ClassificationFilter} array for the storage layer's
     * type-aware accessor when the caller asks for a single type. Returns
     * null (= "all types") otherwise. The storage layer's filter does
     * coarse-grained "DynamicType match"; the in-resolver loop still applies
     * the rest of the predicates (isPerson, nameContains, ownerEq, limit).
     */
    private ClassificationFilter[] buildStorageFilter(AllocatableFilter filter) throws RaplaException
    {
        if (filter == null || filter.typeKeyEq() == null || filter.typeKeyEq().isBlank()) return null;
        DynamicType dt = type(filter.typeKeyEq());
        if (dt == null) return null;   // unknown / rapla-internal — type() returns null
        return new ClassificationFilter[] { dt.newClassificationFilter() };
    }

    /**
     * Allocatables whose DynamicType is rapla-internal (template entries,
     * etc.) never surface through GraphQL — see
     * {@link ClassificationSdlGenerator#isRaplaInternal}.
     */
    private static boolean isInternalAllocatable(Allocatable a)
    {
        if (a.getClassification() == null) return false;
        return ClassificationSdlGenerator.isRaplaInternal(a.getClassification().getType());
    }

    @QueryMapping
    public Allocatable allocatable(@Argument("id") String id) throws RaplaException
    {
        if (id == null || id.isBlank()) return null;
        User caller = UnauthenticatedException.require(resolveCaller());
        Allocatable a;
        try
        {
            a = operator.tryResolve(new ReferenceInfo<>(id, Allocatable.class));
        }
        catch (RuntimeException e)
        {
            return null;
        }
        if (a == null) return null;
        if (isInternalAllocatable(a)) return null;   // rapla-internal — never surface
        PermissionController pc = operator.getPermissionController();
        if (!pc.canRead(a, caller)) return null;
        return a;
    }

    /**
     * User-visible DynamicTypes. Rapla-internal storage-scaffolding types
     * (classification-type=rapla — period / template / defaultUser /
     * anonymousEvent) are filtered out: they're never browseable through
     * GraphQL, matching the SDL generator policy (no
     * {@code Rapla*Classification} types generated). No §12 — DynamicType
     * definitions are deployment-global metadata used by form widgets.
     */
    @QueryMapping
    public List<DynamicType> types() throws RaplaException
    {
        Collection<DynamicType> all = operator.getDynamicTypes();
        if (all == null) return List.of();
        List<DynamicType> out = new ArrayList<>(all.size());
        for (DynamicType dt : all)
        {
            if (dt == null) continue;
            if (ClassificationSdlGenerator.isRaplaInternal(dt)) continue;
            out.add(dt);
        }
        return out;
    }

    @QueryMapping
    public DynamicType type(@Argument("key") String key) throws RaplaException
    {
        if (key == null || key.isBlank()) return null;
        Collection<DynamicType> all = operator.getDynamicTypes();
        if (all == null) return null;
        for (DynamicType dt : all)
        {
            if (dt != null && key.equals(dt.getKey()))
            {
                if (ClassificationSdlGenerator.isRaplaInternal(dt)) return null;
                return dt;
            }
        }
        return null;
    }

    // Per-type derived-field resolvers (Allocatable / DynamicType /
    // Classification interface fields) are wired programmatically as
    // LightDataFetcher singletons by StructuralTypeFetchers — invoked from
    // HotSwappableGraphQlSource at schema build. This skips Spring's
    // per-dispatch DataFetcherHandlerMethod allocation + Method.toGenericString
    // reflection (~34 leaf profile samples, 2026-05-27).

    // === filter predicate =====================================================

    /**
     * PRD 073/074 A — package-visible entry point for the SCALAR predicate, reused by
     * {@code StructuralTypeFetchers.filterAllocatables} on the nested
     * {@code Appointment.allocatables} / {@code AppointmentBlock.allocatables} path. {@code m} is
     * the raw GraphQL input map (null = unconstrained → matches). Evaluates only the scalar fields;
     * the nested path applies {@code where<TypeKey>} via {@link WhereEvaluator}, and {@code idIn} /
     * {@code accessibleBy*} / {@code limit} alongside it — same unified {@code AllocatableFilter} as
     * {@code Query.allocatables}.
     */
    static boolean matchesMap(Allocatable a, Map<String, Object> m)
    {
        return matches(a, fromMap(m));
    }

    private static boolean matches(Allocatable a, AllocatableFilter f)
    {
        if (f == null) return true;
        // typeKeyEq takes precedence over typeKeyIn when both are set.
        if (f.typeKeyEq() != null && !f.typeKeyEq().isBlank())
        {
            DynamicType dt = a.getClassification() == null ? null : a.getClassification().getType();
            if (dt == null || !f.typeKeyEq().equals(dt.getKey())) return false;
        }
        else if (f.typeKeyIn() != null && !f.typeKeyIn().isEmpty())
        {
            DynamicType dt = a.getClassification() == null ? null : a.getClassification().getType();
            if (dt == null || !f.typeKeyIn().contains(dt.getKey())) return false;
        }
        if (f.isPersonEq() != null && a.isPerson() != f.isPersonEq()) return false;
        if (f.nameContains() != null && !f.nameContains().isBlank())
        {
            String hay = a.getName(Locale.getDefault());
            if (hay == null) return false;
            if (!hay.toLowerCase().contains(f.nameContains().toLowerCase())) return false;
        }
        if (f.searchText() != null && !f.searchText().isBlank())
        {
            String hay = a.getName(Locale.getDefault());
            SearchMatcher.MatchKind kind = f.matchKind() != null
                    ? f.matchKind() : SearchMatcher.MatchKind.SUBSTRING;
            if (!SearchMatcher.matches(hay, f.searchText(), kind)) return false;
        }
        if (f.ownerEq() != null && !f.ownerEq().isBlank())
        {
            ReferenceInfo<User> ref = a.getOwnerRef();
            if (ref == null || !f.ownerEq().equals(ref.getId())) return false;
        }
        return true;
    }

    // === auth helpers =========================================================

    private User resolveCaller()
    {
        return jwtUserResolver.resolveCurrentUserOrNull();
    }

    // === PRD 069 access-by-target arg parsing =================================

    static String stringArg(Map<String, Object> m, String key)
    {
        return m == null ? null : (m.get(key) instanceof String s ? s : null);
    }

    @SuppressWarnings("unchecked")
    static List<String> stringListArg(Map<String, Object> m, String key)
    {
        return m == null ? null : (m.get(key) instanceof List<?> l ? (List<String>) l : null);
    }

    static AccessLevel accessLevelArg(Map<String, Object> m)
    {
        if (m == null) return null;
        Object v = m.get("accessLevel");
        if (v instanceof AccessLevel al) return al;
        if (v instanceof String s && !s.isBlank())
        {
            try { return AccessLevel.valueOf(s); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unknown accessLevel: " + s); }
        }
        return null;
    }

    // === DTOs =================================================================

    /** Mirror of the {@code AllocatableFilter} GraphQL input. */
    public record AllocatableFilter(
            String                   typeKeyEq,
            List<String>             typeKeyIn,
            Boolean                  isPersonEq,
            String                   nameContains,
            String                   searchText,
            SearchMatcher.MatchKind  matchKind,
            String                   ownerEq,
            List<String>             idIn,             // PRD 066 — additive id-selection
            Integer                  limit) {}

    // AttributeDescriptorDto and AttributeValueDto records dropped 2026-05-28
    // (PRD 055 β refactor). Descriptor data is now exposed via introspection
    // of generated `<TypeKey>Classification` types + custom directives
    // (@displayName, @expectedType, @rootCategory, @multiplicity, @required)
    // emitted by ClassificationSdlGenerator. AttributeValue output type
    // dropped from the schema — typed-narrow fragments are the read path.
}
