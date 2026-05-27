package org.rapla.server.spring.graphql;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PRD 035 Cut C — generates per-DynamicType GraphQL types implementing the
 * static {@code Classification} interface, one per deployment-defined
 * DynamicType. Output is one SDL string that is concatenated with the static
 * {@code schema.graphqls} at {@link HotSwappableGraphQlSource} build time.
 *
 * <p>Per PRD 035 §"Schema design — structural-static + classification-generated"
 * (docs/prd/035-rapla-mcp-server.md line 508-518): structural types are static
 * SDL; classification types regenerate from {@code operator.getDynamicTypes()}
 * at startup and on every DynamicType admin change.
 *
 * <p>Generated shape:
 * <pre>{@code
 * type RoomClassification implements Classification {
 *     # interface fields
 *     typeId:     ID!
 *     type:       DynamicType!
 *     attributes: [AttributeValue!]!
 *     # typed per-attribute fields:
 *     name:       String
 *     seats:      Int
 *     belongsto:  Allocatable
 * }
 * }</pre>
 *
 * <p><b>Codegen consumer audience only.</b> The SPA queries the
 * {@code Classification} interface (descriptor-driven) and never references
 * these generated types — PRD 035 line 540-550 "Approach A" lock-in. Generated
 * types exist for plugin authors, MCP integrators, and similar deployment-coupled
 * consumers who can absorb regen on the rare DynamicType admin change.
 *
 * <p><b>Nullability:</b> all typed fields are emitted as nullable, even when
 * the underlying Attribute is {@code !isOptional()}. Existing records may
 * pre-date a required-flag toggle and carry null; emitting non-null would
 * crash reads. Required semantics are enforced on save, not on read.
 *
 * <p><b>Name sanitization:</b> rapla DynamicType keys like {@code "dynatt:room"}
 * become {@code DynattRoomClassification}. Attribute keys ({@code "seats"},
 * {@code "course_number"}) are preserved as-is when they're valid GraphQL
 * identifiers; non-identifier keys are sanitized.
 *
 * <p>Pure: no Spring deps, no operator deps — takes the DynamicType collection
 * and returns a string. {@link #generate(Collection)} is golden-file testable
 * in isolation.
 */
public final class ClassificationSdlGenerator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ClassificationSdlGenerator.class);

    /** GraphQL reserved keywords — fields named these get a trailing underscore. */
    private static final Set<String> GRAPHQL_RESERVED = Set.of(
            "type", "interface", "fragment", "query", "mutation", "subscription",
            "schema", "scalar", "enum", "union", "input", "extend", "implements",
            "on", "true", "false", "null", "directive", "repeatable");

    /**
     * Hand-written GraphQL type / interface names from {@code schema.graphqls}
     * that a generated {@code <TypeKey>Classification} must never collide with.
     * If a DynamicType has a key that sanitizes to one of these, we skip
     * generating its classification type and log a warning — overwriting a
     * hand-written interface produces a schema parse error.
     *
     * <p>The collision realistic for {@code event} (testdefault.xml has one)
     * is why the reservation interface is named {@code ReservationClassification}
     * not {@code EventClassification} — rapla deployments naturally name
     * reservation DynamicTypes "event", "lecture", "Veranstaltung", etc.
     */
    private static final Set<String> RESERVED_TYPE_NAMES = Set.of(
            "Classification",
            "AllocatableClassification",
            "ReservationClassification");

    private ClassificationSdlGenerator() {}

    /**
     * True if this DynamicType is one of rapla's internal storage-scaffolding
     * types (period, template, defaultUser, anonymousEvent) — its
     * {@code classification-type} annotation is {@code rapla}, not
     * {@code resource}/{@code person}/{@code reservation}. These never
     * surface in the GraphQL API: not in {@code types}, not in
     * {@code type(key:)}, not in {@code allocatables*}, and they don't
     * get a generated {@code <Name>Classification} type either.
     * They remain accessible through the storage operator for the
     * subsystems that need them (period model, template engine, etc.).
     */
    public static boolean isRaplaInternal(DynamicType dt)
    {
        if (dt == null) return false;
        String kind = dt.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        return DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE.equals(kind);
    }

    /**
     * Generate the SDL fragment for all classification types. The result is
     * meant to be concatenated AFTER the static schema (which declares the
     * {@code Classification} interface and the referenced types).
     *
     * @return SDL text, empty string if {@code dynamicTypes} is empty.
     */
    public static String generate(Collection<DynamicType> dynamicTypes)
    {
        if (dynamicTypes == null || dynamicTypes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("# === GENERATED per-DynamicType classification types (PRD 035 Cut C) ===\n");
        sb.append("# Do not hand-edit — regenerated from operator.getDynamicTypes() on startup\n");
        sb.append("# and on every DynamicType UpdateEvent.\n\n");

        Set<String> emittedTypeNames = new HashSet<>();
        for (DynamicType dt : sortedByKey(dynamicTypes))
        {
            if (dt == null) continue;
            if (isRaplaInternal(dt)) continue;     // never surfaced in GraphQL
            String key = dt.getKey();
            if (key == null || key.isBlank()) continue;
            String typeName = sanitizeTypeName(key) + "Classification";
            if (RESERVED_TYPE_NAMES.contains(typeName))
            {
                LOGGER.warn("Skipping DynamicType '{}' — generated GraphQL type name '{}' collides with a hand-written schema type/interface. Rename the DynamicType or the reserved name.",
                        key, typeName);
                continue;
            }
            if (!emittedTypeNames.add(typeName))
            {
                LOGGER.warn("Skipping DynamicType '{}' — sanitized GraphQL type name '{}' collides with prior type",
                        key, typeName);
                continue;
            }
            appendClassificationType(sb, typeName, dt);
        }
        return sb.toString();
    }

    private static List<DynamicType> sortedByKey(Collection<DynamicType> types)
    {
        List<DynamicType> list = new ArrayList<>(types);
        list.sort((a, b) -> {
            String ka = a == null ? "" : a.getKey() == null ? "" : a.getKey();
            String kb = b == null ? "" : b.getKey() == null ? "" : b.getKey();
            return ka.compareTo(kb);
        });
        return list;
    }

    private static void appendClassificationType(StringBuilder sb, String typeName, DynamicType dt)
    {
        String implementsClause = implementsClauseFor(dt);
        sb.append("\"\"\"\n");
        sb.append("Generated classification for DynamicType `").append(dt.getKey()).append("`.\n");
        sb.append("Interface fields are inherited; typed fields below are per-attribute reads.\n");
        sb.append("\"\"\"\n");
        sb.append("type ").append(typeName).append(" implements ").append(implementsClause).append(" {\n");
        sb.append("  typeId:     ID!\n");
        sb.append("  type:       DynamicType!\n");
        sb.append("  attributes: [AttributeValue!]!\n");

        Set<String> emittedFieldNames = new HashSet<>(Set.of("typeId", "type", "attributes"));
        for (Attribute attr : dt.getAttributes())
        {
            if (attr == null) continue;
            String attrKey = attr.getKey();
            if (attrKey == null || attrKey.isBlank()) continue;
            String fieldName = sanitizeFieldName(attrKey);
            if (!emittedFieldNames.add(fieldName))
            {
                LOGGER.warn("DynamicType '{}': skipping attribute '{}' — field name '{}' collides with interface field or earlier attribute",
                        dt.getKey(), attrKey, fieldName);
                continue;
            }
            String fieldType = graphqlTypeFor(attr);
            sb.append("  ").append(fieldName).append(": ").append(fieldType).append("\n");
        }
        sb.append("}\n\n");
    }

    /**
     * Pick the {@code implements} clause for a generated classification type
     * based on rapla's {@code classification-type} annotation on the
     * DynamicType. Three-way split (see schema.graphqls Classification interface):
     * <ul>
     *   <li>resource / person → {@code Classification & AllocatableClassification}</li>
     *   <li>reservation → {@code Classification & EventClassification}</li>
     *   <li>rapla (internal) or null → {@code Classification} only</li>
     * </ul>
     */
    static String implementsClauseFor(DynamicType dt)
    {
        String kind = dt.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        if (DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE.equals(kind)
                || DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON.equals(kind))
        {
            return "Classification & AllocatableClassification";
        }
        if (DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION.equals(kind))
        {
            return "Classification & ReservationClassification";
        }
        // rapla-internal (Period, Template, AnonymousEvent, DefaultUser) or
        // unknown — base interface only, invisible from entity-level narrowing.
        return "Classification";
    }

    /**
     * Map a rapla {@link Attribute} to a GraphQL type expression.
     * All emitted as nullable (see class javadoc on nullability).
     * Multi-select CATEGORY / ALLOCATABLE → nullable list of non-null items.
     */
    private static String graphqlTypeFor(Attribute attr)
    {
        AttributeType t = attr.getType();
        if (t == null) return "String";   // shouldn't happen; defensive
        String base = switch (t)
        {
            case STRING      -> "String";
            case INT         -> "Int";
            case BOOLEAN     -> "Boolean";
            case DATE        -> "LocalDateTime";
            case CATEGORY    -> "Category";
            case ALLOCATABLE -> "Allocatable";
        };
        boolean multiSelect = isMultiSelect(attr);
        return multiSelect ? ("[" + base + "!]") : base;
    }

    private static boolean isMultiSelect(Attribute attr)
    {
        Object c = attr.getConstraint(ConstraintIds.KEY_MULTI_SELECT);
        if (c == null) return false;
        if (c instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(c.toString());
    }

    /**
     * Convert a rapla DynamicType key (e.g. {@code "dynatt:room"}, {@code "lecture"},
     * {@code "exam-prep"}) into a PascalCase GraphQL type name fragment (without
     * the {@code "Classification"} suffix).
     * <ul>
     *   <li>Non-alphanumeric → split delimiter</li>
     *   <li>Each split segment is capitalized</li>
     *   <li>Result: {@code dynatt:room} → {@code DynattRoom}, {@code exam-prep} → {@code ExamPrep}</li>
     * </ul>
     */
    static String sanitizeTypeName(String rawKey)
    {
        StringBuilder out = new StringBuilder();
        boolean capitalize = true;
        for (int i = 0; i < rawKey.length(); i++)
        {
            char c = rawKey.charAt(i);
            boolean alnum = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (!alnum)
            {
                capitalize = true;
                continue;
            }
            if (capitalize)
            {
                out.append(Character.toUpperCase(c));
                capitalize = false;
            }
            else
            {
                out.append(c);
            }
        }
        if (out.isEmpty()) return "Unnamed";
        // GraphQL identifiers can't start with a digit
        if (Character.isDigit(out.charAt(0))) out.insert(0, '_');
        return out.toString();
    }

    /**
     * Sanitize a rapla attribute key into a valid GraphQL field identifier.
     * Keys that are already valid identifiers (e.g. {@code "seats"},
     * {@code "course_number"}, {@code "forename"}) pass through unchanged.
     * Non-identifier keys get the same PascalCase-with-delimiter-strip
     * treatment as type names, then lower-camelCased.
     */
    static String sanitizeFieldName(String rawKey)
    {
        boolean alreadyValid = isValidGraphqlIdentifier(rawKey);
        String candidate;
        if (alreadyValid)
        {
            candidate = rawKey;
        }
        else
        {
            String pascal = sanitizeTypeName(rawKey);
            if (pascal.isEmpty()) return "field";
            candidate = Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
        }
        // Avoid GraphQL reserved keywords as field names (parsers are lax but
        // some tooling chokes); trailing-underscore disambiguation.
        if (GRAPHQL_RESERVED.contains(candidate.toLowerCase(Locale.ROOT)))
        {
            candidate = candidate + "_";
        }
        return candidate;
    }

    private static boolean isValidGraphqlIdentifier(String s)
    {
        if (s == null || s.isEmpty()) return false;
        char first = s.charAt(0);
        if (!(first == '_' || (first >= 'A' && first <= 'Z') || (first >= 'a' && first <= 'z')))
        {
            return false;
        }
        for (int i = 1; i < s.length(); i++)
        {
            char c = s.charAt(i);
            boolean ok = c == '_' || (c >= '0' && c <= '9')
                    || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
            if (!ok) return false;
        }
        return true;
    }
}
