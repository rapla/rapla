package org.rapla.server.spring.graphql;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.storage.StorageOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared @oneOf classification-input mapping for the reservation AND
 * allocatable mutation controllers (deduplicated 2026-07-08 — the two
 * private copies had already drifted once: the allocatable side shipped a
 * raw pass-through coercion that silently dropped VALUE_LIST enum values).
 *
 * <p>Semantics (PRD 099 Phase 2): the classification is rebuilt from
 * {@code newClassification()} (type defaults prefilled) on every
 * create/update — replace, not merge. An attribute key PRESENT with
 * {@code null} clears the value; an OMITTED key keeps the type default.
 *
 * <p>CATEGORY coercion resolves by id first (tree-category input carries the
 * category ID), then by leaf key within the attribute's root-category
 * constraint (VALUE_LIST enum input — the enum value IS the PRD 058
 * spec-compliant leaf key).
 */
final class ClassificationInputMapper
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ClassificationInputMapper.class);

    private ClassificationInputMapper()
    {
    }

    @SuppressWarnings("unchecked")
    static Classification buildClassificationFromInput(StorageOperator operator, DynamicType dt,
            Map<String, Object> classificationInput, String expectedTypeKey, User caller, Classification stored)
    {
        // Resource references already stored pass unchanged; only new ids are gated (PRD 096 OQ6 sibling, Swing parity)
        Set<String> storedRefs = new HashSet<>();
        if (stored != null)
        {
            for (Attribute a : stored.getAttributes())
            {
                for (Object v : stored.getValues(a))
                {
                    if (v instanceof Allocatable ref) storedRefs.add(ref.getId());
                }
            }
        }
        Classification c = dt.newClassification();
        if (classificationInput == null) return c;
        // @oneOf variant: classificationInput has exactly one key matching the typeKey
        for (Map.Entry<String, Object> variant : classificationInput.entrySet())
        {
            if (!variant.getKey().equals(expectedTypeKey))
            {
                throw new ReservationMutationController.ReservationMutationException("MISMATCHED_TYPE",
                        "classification." + variant.getKey(),
                        "classification @oneOf variant '" + variant.getKey()
                                + "' does not match typeKey '" + expectedTypeKey + "'");
            }
            Map<String, Object> attrMap = (Map<String, Object>) variant.getValue();
            if (attrMap == null) return c;
            for (Map.Entry<String, Object> e : attrMap.entrySet())
            {
                Attribute attr = dt.getAttribute(e.getKey());
                if (attr == null) continue;     // unknown attribute key — ignore (stale SPA)
                Object raw = e.getValue();
                if (raw == null)
                {
                    // PRD 099 Phase 2 — key present with explicit null = clear.
                    // Only OMITTED keys keep the newClassification() default.
                    c.setValueForAttribute(attr, null);
                    continue;
                }
                Object value = coerceValue(operator, attr, raw, caller, storedRefs);
                if (value != null) c.setValueForAttribute(attr, value);
            }
        }
        return c;
    }

    static Object coerceValue(StorageOperator operator, Attribute attr, Object raw, User caller, Set<String> storedRefs)
    {
        if (raw == null) return null;
        AttributeType t = attr.getType();
        if (t == null) return raw;
        if (raw instanceof List<?> rawList)
        {
            // List value — single-value attr getting a list is invalid; for multi-value attr, take as-is
            List<Object> out = new ArrayList<>();
            for (Object item : rawList)
            {
                Object coerced = coerceSingleValue(operator, attr, t, item, caller, storedRefs);
                if (coerced != null) out.add(coerced);
            }
            return out;
        }
        return coerceSingleValue(operator, attr, t, raw, caller, storedRefs);
    }

    private static Object coerceSingleValue(StorageOperator operator, Attribute attr, AttributeType t, Object raw,
            User caller, Set<String> storedRefs)
    {
        if (raw == null) return null;
        try
        {
            return switch (t)
            {
                case STRING      -> raw.toString();
                case INT         -> raw instanceof Number n ? n.longValue() : Long.parseLong(raw.toString());
                case BOOLEAN     -> raw instanceof Boolean b ? b : Boolean.parseBoolean(raw.toString());
                case DATE        -> raw instanceof LocalDateTime ldt ? ldt : LocalDateTime.parse(raw.toString());
                case CATEGORY    -> {
                    if (raw instanceof Category cat) yield cat;
                    String s = raw.toString();
                    Object rootConstraint = attr.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
                    Category root = rootConstraint instanceof Category r ? r : null;
                    // Tree-category input carries the category ID — only below the root (PRD 096 OQ6)
                    Category byId = operator.tryResolve(new ReferenceInfo<>(s, Category.class));
                    if (byId != null)
                    {
                        if (root == null || root.isAncestorOf(byId)) yield byId;
                        LOGGER.warn("Category {} is not below the root category of attribute '{}' — value dropped", s, attr.getKey());
                        yield null;
                    }
                    // VALUE_LIST enum input — the enum value IS the leaf key,
                    // resolved within the attribute's root-category constraint
                    yield root != null ? findCategoryByKey(root, s) : null;
                }
                case ALLOCATABLE -> {
                    Allocatable ref = operator.tryResolve(new ReferenceInfo<>(raw.toString(), Allocatable.class));
                    if (ref == null) yield null;
                    if (storedRefs.contains(ref.getId())) yield ref;
                    // §12 + @expectedType (PRD 096 OQ6 sibling): a NEW reference must be readable and of the expected type
                    Object expected = attr.getConstraint(ConstraintIds.KEY_DYNAMIC_TYPE);
                    if (operator.getPermissionController().canRead(ref, caller)
                            && (!(expected instanceof DynamicType type) || type.equals(ref.getClassification().getType())))
                    {
                        yield ref;
                    }
                    LOGGER.warn("Resource {} is not a readable {} for attribute '{}' — value dropped", ref.getId(),
                            expected instanceof DynamicType type ? type.getKey() : "(any type)", attr.getKey());
                    yield null;
                }
            };
        }
        catch (Exception e)
        {
            LOGGER.warn("Failed to coerce value for attribute '{}' (type={}): {}", attr.getKey(), t, e.getMessage());
            return null;
        }
    }

    /** Key lookup over the operator's DynamicTypes — null when absent (callers wrap their own error). */
    static DynamicType tryResolveType(StorageOperator operator, String typeKey)
    {
        if (typeKey == null) return null;
        try
        {
            for (DynamicType dt : operator.getDynamicTypes())
            {
                if (typeKey.equals(dt.getKey())) return dt;
            }
        }
        catch (org.rapla.framework.RaplaException e)
        {
            LOGGER.warn("DynamicType lookup failed for '{}': {}", typeKey, e.getMessage());
        }
        return null;
    }

    /** Depth-first key search below a root-category constraint (VALUE_LIST enum → Category). */
    private static Category findCategoryByKey(Category root, String key)
    {
        Category[] children = root.getCategories();
        if (children == null) return null;
        for (Category child : children)
        {
            if (child == null) continue;
            if (key.equals(child.getKey())) return child;
            Category deeper = findCategoryByKey(child, key);
            if (deeper != null) return deeper;
        }
        return null;
    }
}
