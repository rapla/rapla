package org.rapla.server.spring.graphql;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.rapla.entities.Category;
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
            Map<String, Object> classificationInput, String expectedTypeKey)
    {
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
                Object value = coerceValue(operator, attr, raw);
                if (value != null) c.setValueForAttribute(attr, value);
            }
        }
        return c;
    }

    static Object coerceValue(StorageOperator operator, Attribute attr, Object raw)
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
                Object coerced = coerceSingleValue(operator, attr, t, item);
                if (coerced != null) out.add(coerced);
            }
            return out;
        }
        return coerceSingleValue(operator, attr, t, raw);
    }

    private static Object coerceSingleValue(StorageOperator operator, Attribute attr, AttributeType t, Object raw)
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
                    // Tree-category input carries the category ID
                    Category byId = operator.tryResolve(new ReferenceInfo<>(s, Category.class));
                    if (byId != null) yield byId;
                    // VALUE_LIST enum input — the enum value IS the leaf key,
                    // resolved within the attribute's root-category constraint
                    Object rootConstraint = attr.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
                    yield rootConstraint instanceof Category root ? findCategoryByKey(root, s) : null;
                }
                case ALLOCATABLE -> operator.tryResolve(new ReferenceInfo<>(raw.toString(), Allocatable.class));
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
