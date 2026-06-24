package org.rapla.server.spring.graphql;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.storage.PermissionController;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;

/**
 * PRD 059 Phase 3 — typed-where predicate evaluator. Walks the
 * {@code where<TypeKey>} block in the filter map matching the allocatable's
 * DynamicType, dispatching predicate operators against the classification's
 * per-attribute values.
 *
 * <p>A {@code where<OtherType>} block against an allocatable whose DT key
 * doesn't match contributes no constraint (returns true) — combined with
 * {@code typeKeyIn:} this lets cross-type queries narrow each type with its
 * own predicate set (see PRD 059 §"Predicate semantics").
 *
 * <p>Phase 3 operator coverage:
 * <ul>
 *   <li>{@code StringWhere}  → eq, contains (case-insensitive), startsWith</li>
 *   <li>{@code IntWhere}     → gte, lte</li>
 *   <li>{@code BooleanWhere} → eq</li>
 *   <li>{@code <Enum>Where}  → eq</li>
 *   <li>{@code CategoryWhere}→ eq</li>
 * </ul>
 *
 * <p>Combinators (AND/OR/NOT) are Phase 4; remaining operators (ne, in,
 * gt/lt, between, endsWith, isNull) are Phase 5. Multi-select attributes
 * ({@code *ListWhere}) are also Phase 4+.
 */
final class WhereEvaluator
{
    /** Hard cap on AND/OR/NOT recursion — guards against unbounded nesting. */
    static final int DEPTH_CAP = 10;

    private WhereEvaluator() {}

    /** PRD 074 b — caller/pc enable §12-gated recursion into referenced allocatables' typed where. */
    static boolean evaluate(Allocatable a, Map<String, Object> filterMap, User caller, PermissionController pc)
    {
        if (filterMap == null || filterMap.isEmpty()) return true;
        Classification c = a.getClassification();
        if (c == null) return true;
        DynamicType dt = c.getType();
        if (dt == null) return true;
        String typeKey = dt.getKey();
        if (typeKey == null || typeKey.isEmpty()) return true;
        String whereField = "where" + Character.toUpperCase(typeKey.charAt(0)) + typeKey.substring(1);
        Object block = filterMap.get(whereField);
        if (block instanceof Map<?, ?> wb)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) wb;
            return evaluateWhereBlock(c, dt, typed, 0, caller, pc);
        }
        // A where<Type> block acts as an IMPLICIT TYPE GATE (option B′): a where constrains exactly one
        // type and would otherwise leave every other type unfiltered (the over-include footgun). So if
        // the filter carries any where<Type> block but NONE for this allocatable's own type, exclude
        // it — UNLESS an explicit typeKeyIn/typeKeyEq is present, which is then authoritative (it
        // already gated upstream; here where only refines, types without a block pass unrefined).
        // Multiple where<…> blocks ⇒ the union of their types passes (each refined by its own block).
        if (!hasExplicitTypeGate(filterMap) && hasAnyWhereBlock(filterMap)) return false;
        return true;
    }

    @SuppressWarnings("unchecked")
    private static boolean evaluateWhereBlock(Classification c, DynamicType dt, Map<String, Object> where, int depth,
            User caller, PermissionController pc)
    {
        if (depth > DEPTH_CAP)
        {
            throw new IllegalArgumentException(
                    "where predicate depth exceeds cap of " + DEPTH_CAP
                            + " — AND/OR/NOT nesting too deep");
        }
        for (Map.Entry<String, Object> e : where.entrySet())
        {
            String fieldName = e.getKey();
            Object predValue = e.getValue();
            if (predValue == null) continue;
            // Combinators — recurse on sub-where shapes.
            if ("AND".equals(fieldName))
            {
                if (!(predValue instanceof List<?> list)) continue;
                for (Object sub : list)
                {
                    if (!(sub instanceof Map)) continue;
                    if (!evaluateWhereBlock(c, dt, (Map<String, Object>) sub, depth + 1, caller, pc)) return false;
                }
                continue;
            }
            if ("OR".equals(fieldName))
            {
                if (!(predValue instanceof List<?> list)) continue;
                // OR: [] is vacuously false — no clause can match.
                boolean anyMatch = false;
                for (Object sub : list)
                {
                    if (!(sub instanceof Map)) continue;
                    if (evaluateWhereBlock(c, dt, (Map<String, Object>) sub, depth + 1, caller, pc))
                    {
                        anyMatch = true;
                        break;
                    }
                }
                if (!anyMatch) return false;
                continue;
            }
            if ("NOT".equals(fieldName))
            {
                if (!(predValue instanceof Map)) continue;
                if (evaluateWhereBlock(c, dt, (Map<String, Object>) predValue, depth + 1, caller, pc)) return false;
                continue;
            }
            // Attribute predicate.
            Attribute attr = findAttribute(dt, fieldName);
            if (attr == null) continue;
            if (!(predValue instanceof Map)) continue;
            Map<String, Object> pred = (Map<String, Object>) predValue;
            if (!evaluatePredicate(attr, c, pred, depth, caller, pc)) return false;
        }
        return true;
    }

    private static boolean isMultiSelect(Attribute attr)
    {
        Object c = attr.getConstraint(org.rapla.entities.dynamictype.ConstraintIds.KEY_MULTI_SELECT);
        if (c == null) return false;
        if (c instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(c.toString());
    }

    private static Attribute findAttribute(DynamicType dt, String fieldName)
    {
        for (Attribute attr : dt.getAttributes())
        {
            if (attr == null) continue;
            String key = attr.getKey();
            if (key == null) continue;
            if (fieldName.equals(key)) return attr;
            if (fieldName.length() > 1 && fieldName.endsWith("_")
                    && fieldName.substring(0, fieldName.length() - 1).equals(key)) return attr;
        }
        return null;
    }

    private static boolean evaluatePredicate(Attribute attr, Classification c, Map<String, Object> pred,
            int depth, User caller, PermissionController pc)
    {
        AttributeType t = attr.getType();
        if (t == null) return true;
        boolean multi = isMultiSelect(attr);

        // isNull semantic applies uniformly. For multi-select, "null" means
        // "no values stored". For singular, "null" means the attribute was
        // never set on the classification.
        Object isNull = pred.get("isNull");
        if (isNull instanceof Boolean b)
        {
            boolean isNullValue;
            if (multi)
            {
                java.util.Collection<Object> values = c.getValues(attr);
                isNullValue = values == null || values.isEmpty();
            }
            else
            {
                isNullValue = c.getValue(attr.getKey()) == null;
            }
            if (b) return isNullValue;
            if (isNullValue) return false;
        }

        if (multi)
        {
            java.util.Collection<Object> values = c.getValues(attr);
            if (values == null) values = List.of();
            return switch (t)
            {
                case CATEGORY    -> matchCategoryList(values, pred);
                case ALLOCATABLE -> matchAllocatableList(values, pred);
                // Multi-select STRING/INT/BOOLEAN/DATE aren't emitted in
                // the SDL yet (Phase 1 skip-with-warn) — no predicate to
                // evaluate.
                default          -> true;
            };
        }

        Object value = c.getValue(attr.getKey());
        return switch (t)
        {
            case STRING      -> matchString(value, pred);
            case INT         -> matchInt(value, pred);
            case BOOLEAN     -> matchBoolean(value, pred);
            case CATEGORY    -> matchCategory(value, pred);
            case ALLOCATABLE -> matchAllocatable(value, pred, depth, caller, pc);
            case DATE        -> matchDate(value, pred);
        };
    }

    // === StringWhere — eq, ne, in, contains, startsWith, endsWith, isNull ====

    private static boolean matchString(Object value, Map<String, Object> pred)
    {
        // value==null with no isNull predicate implies "and not null" fails
        // any other operator. isNull is handled upstream.
        if (value == null) return !hasAnyOperator(pred);
        String s = value.toString();
        Object eq = pred.get("eq");
        Object ne = pred.get("ne");
        Object in = pred.get("in");
        Object contains = pred.get("contains");
        Object startsWith = pred.get("startsWith");
        Object endsWith = pred.get("endsWith");
        if (eq != null && !eq.toString().equals(s)) return false;
        if (ne != null && ne.toString().equals(s)) return false;
        if (in instanceof List<?> list && !containsString(list, s)) return false;
        if (contains != null && !s.toLowerCase(Locale.ROOT).contains(contains.toString().toLowerCase(Locale.ROOT))) return false;
        if (startsWith != null && !s.startsWith(startsWith.toString())) return false;
        if (endsWith != null && !s.endsWith(endsWith.toString())) return false;
        return true;
    }

    private static boolean containsString(List<?> list, String s)
    {
        for (Object o : list)
        {
            if (o != null && s.equals(o.toString())) return true;
        }
        return false;
    }

    // === IntWhere — eq, ne, in, gt, gte, lt, lte, isNull =====================

    private static boolean matchInt(Object value, Map<String, Object> pred)
    {
        if (value == null) return !hasAnyOperator(pred);
        Long v = coerceLong(value);
        if (v == null) return false;
        Object eq = pred.get("eq");
        Object ne = pred.get("ne");
        Object in = pred.get("in");
        Object gt = pred.get("gt");
        Object gte = pred.get("gte");
        Object lt = pred.get("lt");
        Object lte = pred.get("lte");
        if (eq != null && ((Number) eq).longValue() != v.longValue()) return false;
        if (ne != null && ((Number) ne).longValue() == v.longValue()) return false;
        if (in instanceof List<?> list && !containsLong(list, v)) return false;
        if (gt != null && v <= ((Number) gt).longValue()) return false;
        if (gte != null && v < ((Number) gte).longValue()) return false;
        if (lt != null && v >= ((Number) lt).longValue()) return false;
        if (lte != null && v > ((Number) lte).longValue()) return false;
        return true;
    }

    private static Long coerceLong(Object o)
    {
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static boolean containsLong(List<?> list, long v)
    {
        for (Object o : list)
        {
            if (o instanceof Number n && n.longValue() == v) return true;
        }
        return false;
    }

    // === BooleanWhere — eq, isNull ===========================================

    private static boolean matchBoolean(Object value, Map<String, Object> pred)
    {
        if (value == null) return !hasAnyOperator(pred);
        Object eq = pred.get("eq");
        if (eq == null) return true;
        boolean v = (value instanceof Boolean b) ? b : Boolean.parseBoolean(value.toString());
        boolean target = (eq instanceof Boolean b) ? b : Boolean.parseBoolean(eq.toString());
        return v == target;
    }

    // === CATEGORY — enum (<Enum>Where) AND org (CategoryWhere) ==============
    // Single-select branch (value is Category or List of one Category).
    // Multi-select branch routes to matchCategoryList.

    private static boolean matchCategory(Object value, Map<String, Object> pred)
    {
        if (value == null) return !hasAnyOperator(pred);
        if (!(value instanceof Category cat)) return false;
        Object eq = pred.get("eq");
        Object ne = pred.get("ne");
        Object in = pred.get("in");
        Object path = pred.get("path");
        Object descendantOf = pred.get("descendantOf");
        if (eq != null && !categoryMatches(cat, eq.toString())) return false;
        if (ne != null && categoryMatches(cat, ne.toString())) return false;
        if (in instanceof List<?> list && !categoryMatchesAny(cat, list)) return false;
        if (path != null && !path.toString().equals(keyPathOf(cat))) return false;
        if (descendantOf != null && !isDescendantOf(cat, descendantOf.toString())) return false;
        return true;
    }

    private static boolean categoryMatches(Category cat, String target)
    {
        return target.equals(cat.getId()) || target.equals(cat.getKey());
    }

    private static boolean categoryMatchesAny(Category cat, List<?> list)
    {
        for (Object o : list)
        {
            if (o != null && categoryMatches(cat, o.toString())) return true;
        }
        return false;
    }

    private static String keyPathOf(Category cat)
    {
        java.util.Deque<String> segs = new java.util.ArrayDeque<>();
        Category cur = cat;
        while (cur != null && cur.getParent() != null)
        {
            String k = cur.getKey();
            if (k == null || k.isEmpty()) return "";
            segs.push(k);
            cur = cur.getParent();
        }
        return String.join("/", segs);
    }

    private static boolean isDescendantOf(Category cat, String ancestorIdOrKey)
    {
        Category cur = cat.getParent();
        while (cur != null && cur.getParent() != null)
        {
            if (ancestorIdOrKey.equals(cur.getId()) || ancestorIdOrKey.equals(cur.getKey())) return true;
            cur = cur.getParent();
        }
        return false;
    }

    // === <Enum>ListWhere / CategoryListWhere ================================

    private static boolean matchCategoryList(java.util.Collection<Object> values, Map<String, Object> pred)
    {
        Object contains = pred.get("contains");
        Object containsAny = pred.get("containsAny");
        Object containsAll = pred.get("containsAll");
        Object isEmpty = pred.get("isEmpty");
        java.util.List<Category> cats = new java.util.ArrayList<>(values.size());
        for (Object v : values) if (v instanceof Category cat) cats.add(cat);
        if (isEmpty instanceof Boolean b)
        {
            if (b != cats.isEmpty()) return false;
        }
        if (contains != null && !hasAnyMatchingCategory(cats, List.of(contains))) return false;
        if (containsAny instanceof List<?> list && !hasAnyMatchingCategory(cats, list)) return false;
        if (containsAll instanceof List<?> list && !hasAllMatchingCategories(cats, list)) return false;
        return true;
    }

    private static boolean hasAnyMatchingCategory(java.util.Collection<Category> haystack, List<?> needles)
    {
        for (Object n : needles)
        {
            if (n == null) continue;
            String target = n.toString();
            for (Category cat : haystack)
            {
                if (cat != null && categoryMatches(cat, target)) return true;
            }
        }
        return false;
    }

    private static boolean hasAllMatchingCategories(java.util.Collection<Category> haystack, List<?> needles)
    {
        for (Object n : needles)
        {
            if (n == null) continue;
            String target = n.toString();
            boolean found = false;
            for (Category cat : haystack)
            {
                if (cat != null && categoryMatches(cat, target)) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    // === ALLOCATABLE — single / list ========================================

    private static boolean matchAllocatable(Object value, Map<String, Object> pred,
            int depth, User caller, PermissionController pc)
    {
        if (value == null) return !hasAnyOperator(pred);
        if (!(value instanceof Allocatable a)) return false;
        // A dangling/deleted reference target resolves to an unresolved-resource placeholder
        // (rapla-internal type, no real attributes). It can't satisfy id/name/attribute predicates —
        // treat it like a null reference: excluded whenever the predicate carries any operator, so a
        // building-name/where filter never false-matches a room whose Gebaeude was deleted.
        Classification refCls = a.getClassification();
        if (refCls == null || refCls.getType() == null
                || ClassificationSdlGenerator.isRaplaInternal(refCls.getType()))
        {
            return !hasAnyOperator(pred);
        }
        Object eq = pred.get("eq");
        Object ne = pred.get("ne");
        Object in = pred.get("in");
        if (eq != null && !eq.toString().equals(a.getId())) return false;
        if (ne != null && ne.toString().equals(a.getId())) return false;
        if (in instanceof List<?> list && !containsAllocatableId(list, a)) return false;
        // PRD 074 a — match the referenced allocatable by its display name (one-query reference join,
        // e.g. rooms whose Gebaeude.nameContains "MOS").
        Object nameContains = pred.get("nameContains");
        if (nameContains != null)
        {
            String hay = a.getName(Locale.getDefault());
            if (hay == null || !hay.toLowerCase(Locale.ROOT)
                    .contains(nameContains.toString().toLowerCase(Locale.ROOT))) return false;
        }
        // PRD 074 b — typed recursive where on the referenced entity (e.g. filter rooms by the
        // building's OWN attributes: Gebaeude.where { Standort: { eq: … } }). §12: the referenced
        // allocatable must be canRead by the caller, else the room is dropped (no attribute leak).
        Object nested = pred.get("where");
        if (nested instanceof Map<?, ?> nm)
        {
            if (caller != null && pc != null && !pc.canRead(a, caller)) return false;
            Classification rc = a.getClassification();
            if (rc == null || rc.getType() == null) return false;
            @SuppressWarnings("unchecked")
            Map<String, Object> nestedWhere = (Map<String, Object>) nm;
            if (!evaluateWhereBlock(rc, rc.getType(), nestedWhere, depth + 1, caller, pc)) return false;
        }
        return true;
    }

    private static boolean containsAllocatableId(List<?> list, Allocatable a)
    {
        for (Object o : list)
        {
            if (o != null && o.toString().equals(a.getId())) return true;
        }
        return false;
    }

    private static boolean matchAllocatableList(java.util.Collection<Object> values, Map<String, Object> pred)
    {
        Object contains = pred.get("contains");
        Object containsAny = pred.get("containsAny");
        Object containsAll = pred.get("containsAll");
        Object isEmpty = pred.get("isEmpty");
        java.util.List<Allocatable> as = new java.util.ArrayList<>(values.size());
        for (Object v : values) if (v instanceof Allocatable a) as.add(a);
        if (isEmpty instanceof Boolean b)
        {
            if (b != as.isEmpty()) return false;
        }
        if (contains != null && !hasAnyMatchingAllocatable(as, List.of(contains))) return false;
        if (containsAny instanceof List<?> list && !hasAnyMatchingAllocatable(as, list)) return false;
        if (containsAll instanceof List<?> list && !hasAllMatchingAllocatables(as, list)) return false;
        return true;
    }

    private static boolean hasAnyMatchingAllocatable(java.util.Collection<Allocatable> haystack, List<?> needles)
    {
        for (Object n : needles)
        {
            if (n == null) continue;
            String target = n.toString();
            for (Allocatable a : haystack)
            {
                if (a != null && target.equals(a.getId())) return true;
            }
        }
        return false;
    }

    private static boolean hasAllMatchingAllocatables(java.util.Collection<Allocatable> haystack, List<?> needles)
    {
        for (Object n : needles)
        {
            if (n == null) continue;
            String target = n.toString();
            boolean found = false;
            for (Allocatable a : haystack)
            {
                if (a != null && target.equals(a.getId())) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    // === LocalDateTimeWhere — eq, ne, gt, gte, lt, lte, between, isNull ====

    private static boolean matchDate(Object value, Map<String, Object> pred)
    {
        if (value == null) return !hasAnyOperator(pred);
        java.time.LocalDateTime v = coerceDateTime(value);
        if (v == null) return false;
        Object eq = pred.get("eq");
        Object ne = pred.get("ne");
        Object gt = pred.get("gt");
        Object gte = pred.get("gte");
        Object lt = pred.get("lt");
        Object lte = pred.get("lte");
        Object between = pred.get("between");
        if (eq != null && !v.equals(coerceDateTime(eq))) return false;
        if (ne != null && v.equals(coerceDateTime(ne))) return false;
        if (gt != null && !v.isAfter(coerceDateTime(gt))) return false;
        if (gte != null && v.isBefore(coerceDateTime(gte))) return false;
        if (lt != null && !v.isBefore(coerceDateTime(lt))) return false;
        if (lte != null && v.isAfter(coerceDateTime(lte))) return false;
        if (between instanceof List<?> list && list.size() == 2)
        {
            java.time.LocalDateTime from = coerceDateTime(list.get(0));
            java.time.LocalDateTime to = coerceDateTime(list.get(1));
            if (from == null || to == null) return false;
            if (v.isBefore(from) || v.isAfter(to)) return false;
        }
        return true;
    }

    private static java.time.LocalDateTime coerceDateTime(Object o)
    {
        if (o == null) return null;
        if (o instanceof java.time.LocalDateTime ldt) return ldt;
        if (o instanceof java.util.Date d) return d.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDateTime();
        try { return java.time.LocalDateTime.parse(o.toString()); } catch (Exception e) { return null; }
    }

    /**
     * True iff at least one operator key (other than isNull) is present in
     * the predicate map. Drives the "value is null → any operator fails"
     * semantic when isNull wasn't explicitly handled.
     */
    private static boolean hasAnyOperator(Map<String, Object> pred)
    {
        for (Map.Entry<String, Object> e : pred.entrySet())
        {
            if ("isNull".equals(e.getKey())) continue;
            if (e.getValue() != null) return true;
        }
        return false;
    }

    /** True if the filter carries any generated {@code where<TypeKey>} block (a typed predicate map). */
    private static boolean hasAnyWhereBlock(Map<String, Object> filterMap)
    {
        for (Map.Entry<String, Object> e : filterMap.entrySet())
        {
            if (e.getKey() != null && e.getKey().startsWith("where") && e.getValue() instanceof Map)
            {
                return true;
            }
        }
        return false;
    }

    /** True if the filter sets an explicit type gate ({@code typeKeyIn} non-empty / {@code typeKeyEq}
     * non-blank). When present it is authoritative and the implicit where-type gate is NOT applied. */
    private static boolean hasExplicitTypeGate(Map<String, Object> filterMap)
    {
        Object in = filterMap.get("typeKeyIn");
        if (in instanceof java.util.List<?> list && !list.isEmpty()) return true;
        Object eq = filterMap.get("typeKeyEq");
        return eq != null && !eq.toString().isBlank();
    }
}
