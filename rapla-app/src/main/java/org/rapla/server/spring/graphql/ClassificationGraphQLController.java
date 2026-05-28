package org.rapla.server.spring.graphql;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
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

    public ClassificationGraphQLController(StorageOperator operator)
    {
        this.operator = operator;
    }

    // === Query roots ==========================================================

    @QueryMapping
    public List<Allocatable> allocatables(@Argument("filter") AllocatableFilter filter) throws RaplaException
    {
        User caller = resolveCaller();
        // Operator-level pre-filter when typeKeyEq is set — avoids materializing
        // every allocatable across all types just to narrow to one type.
        ClassificationFilter[] storageFilter = buildStorageFilter(filter);
        Collection<Allocatable> all = operator.getAllocatables(storageFilter);
        if (all == null) return List.of();
        PermissionController pc = operator.getPermissionController();
        int cap = (filter != null && filter.limit() != null) ? filter.limit() : Integer.MAX_VALUE;
        List<Allocatable> visible = new ArrayList<>(Math.min(cap, 256));
        for (Allocatable a : all)
        {
            if (a == null) continue;
            if (isInternalAllocatable(a)) continue;   // skip rapla-internal (template/period/etc.)
            // Match-then-canRead — matches() is microseconds on hash-compare;
            // canRead can be a permission-graph walk for non-admins. Filtering
            // first short-circuits the expensive check for non-matching entries.
            if (!matches(a, filter)) continue;
            if (caller != null && !pc.canRead(a, caller)) continue;
            if (caller == null && !isWorldReadable(a)) continue;
            visible.add(a);
            if (visible.size() >= cap) break;
        }
        return visible;
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
        User caller = resolveCaller();
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
        if (caller == null && !isWorldReadable(a)) return null;
        if (caller != null && !pc.canRead(a, caller)) return null;
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
        if (f.ownerEq() != null && !f.ownerEq().isBlank())
        {
            ReferenceInfo<User> ref = a.getOwnerRef();
            if (ref == null || !f.ownerEq().equals(ref.getId())) return false;
        }
        return true;
    }

    /**
     * Anonymous-caller read gate. For now nothing is world-readable through
     * GraphQL — anonymous callers see empty results everywhere. SecurityConfig
     * still permits unauthenticated requests to {@code /api/graphql} so the
     * schema and trivial queries (hello/serverTime/version) can be probed
     * without a token. Once we add explicit world-readable allocatables /
     * resources, replace this with a real check.
     */
    private static boolean isWorldReadable(Allocatable a)
    {
        return false;
    }

    // === auth helpers =========================================================

    private User resolveCaller()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        String username = null;
        if (auth.getPrincipal() instanceof Jwt jwt)
        {
            username = jwt.getClaimAsString("preferred_username");
        }
        if (username == null || username.isBlank()) username = auth.getName();
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) return null;
        try { return operator.getUser(username); }
        catch (RaplaException e) { return null; }
    }

    // === DTOs =================================================================

    /** Mirror of the {@code AllocatableFilter} GraphQL input. */
    public record AllocatableFilter(
            String       typeKeyEq,
            List<String> typeKeyIn,
            Boolean      isPersonEq,
            String       nameContains,
            String       ownerEq,
            Integer      limit) {}

    // AttributeDescriptorDto and AttributeValueDto records dropped 2026-05-28
    // (PRD 055 β refactor). Descriptor data is now exposed via introspection
    // of generated `<TypeKey>Classification` types + custom directives
    // (@displayName, @expectedType, @rootCategory, @multiplicity, @required)
    // emitted by ClassificationSdlGenerator. AttributeValue output type
    // dropped from the schema — typed-narrow fragments are the read path.
}
