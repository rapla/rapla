package org.rapla.server.spring.graphql;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.rapla.components.util.Tools;
import org.rapla.entities.Category;
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
        // Two conditions, either sufficient — see GraphqlKeyMigration.isRaplaInternal
        // for the rationale (UNRESOLVED_RESOURCE_TYPE / ANONYMOUSEVENT_TYPE are
        // annotated RESOURCE/RESERVATION but still rapla-internal).
        String key = dt.getKey();
        if (key != null && key.startsWith("rapla:")) return true;
        String kind = dt.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        return DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE.equals(kind);
    }

    /**
     * Generate the SDL fragment for all classification types + the per-
     * VALUE_LIST-root enums referenced by CATEGORY-typed attributes. The
     * result is meant to be concatenated AFTER the static schema.
     *
     * @return SDL text, empty string if {@code dynamicTypes} is empty.
     */
    public static String generate(Collection<DynamicType> dynamicTypes)
    {
        if (dynamicTypes == null || dynamicTypes.isEmpty()) return "";

        // Pass 1: discover VALUE_LIST roots referenced by attributes. Used
        // both for enum SDL emission and for graphqlTypeFor() so the
        // classification fields target the enum types rather than `Category`.
        Map<String, Category> valueListEnums = collectValueListEnums(dynamicTypes);

        StringBuilder sb = new StringBuilder();
        sb.append("# === GENERATED per-DynamicType classification types (PRD 035 Cut C) ===\n");
        sb.append("# Do not hand-edit — regenerated from operator.getDynamicTypes() on startup\n");
        sb.append("# and on every DynamicType UpdateEvent.\n\n");

        // Emit enums first so classification types can reference them.
        if (!valueListEnums.isEmpty())
        {
            sb.append("# --- VALUE_LIST root enums (PRD 035 §5b) ---\n");
            for (Map.Entry<String, Category> e : valueListEnums.entrySet())
            {
                appendValueListEnum(sb, e.getKey(), e.getValue());
            }
            sb.append('\n');
        }

        Set<String> emittedTypeNames = new HashSet<>();
        for (DynamicType dt : sortedByKey(dynamicTypes))
        {
            if (dt == null) continue;
            if (isRaplaInternal(dt)) continue;     // never surfaced in GraphQL
            String key = dt.getKey();
            if (key == null || key.isBlank()) continue;
            String typeName = checkGraphQlCompliantName(key) + "Classification";
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
            appendClassificationType(sb, typeName, dt, valueListEnums);
        }
        return sb.toString();
    }

    /**
     * Walk every DynamicType's CATEGORY-typed attributes, identify the
     * rootCategory of each, classify via {@link CategoryKindClassifier}, and
     * collect roots that are VALUE_LIST. Dedup by enum-type-name (so two
     * attributes referencing the same rootCategory share one enum).
     *
     * <p>Returns a LinkedHashMap (enum-type-name → rootCategory) so iteration
     * order in the generated SDL is stable across rebuilds — important for
     * the SHA-256 hash short-circuit in {@link HotSwappableGraphQlSource}.
     */
    private static Map<String, Category> collectValueListEnums(Collection<DynamicType> dynamicTypes)
    {
        Map<String, Category> out = new LinkedHashMap<>();
        Map<String, Category> sorted = new java.util.TreeMap<>();
        for (DynamicType dt : dynamicTypes)
        {
            if (dt == null || isRaplaInternal(dt)) continue;
            for (Attribute attr : dt.getAttributes())
            {
                if (attr == null) continue;
                if (attr.getType() != AttributeType.CATEGORY) continue;
                Object root = attr.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
                if (!(root instanceof Category cat)) continue;
                if (CategoryKindClassifier.kindOf(cat, null) != CategoryKindClassifier.Kind.VALUE_LIST)
                    continue;
                String enumName = enumNameFor(cat);
                if (enumName.isEmpty()) continue;
                sorted.putIfAbsent(enumName, cat);
            }
        }
        out.putAll(sorted);
        return out;
    }

    /**
     * Compute the enum type name for a VALUE_LIST root: sanitized path
     * segments from super-category down to the root, joined by {@code _}.
     * Per the convention locked 2026-05-28:
     * <ul>
     *   <li>{@code Root/raumtyp} → {@code Raumtyp}</li>
     *   <li>{@code Root/Veranstaltungsattribute/Veranstaltungskategorien} → {@code Veranstaltungsattribute_Veranstaltungskategorien}</li>
     * </ul>
     */
    static String enumNameFor(Category root)
    {
        if (root == null || root.getParent() == null) return "";
        java.util.Deque<String> segs = new java.util.ArrayDeque<>();
        Category cur = root;
        while (cur != null && cur.getParent() != null)
        {
            String seg = checkGraphQlCompliantName(cur.getKey());
            if (seg.isEmpty()) return "";
            segs.push(seg);
            cur = cur.getParent();
        }
        return String.join("_", segs);
    }

    /**
     * Enum value name = the category leaf key itself, verbatim.
     *
     * <p>PRD 058 guarantees the key is spec-compliant ASCII
     * ({@code [A-Za-z_][A-Za-z0-9_]*}) which is exactly the GraphQL enum-value
     * shape. Rapla's category uniqueness invariant (per-parent unique keys)
     * guarantees distinct enum values across siblings — no collisions.
     *
     * <p>Earlier this PascalCased the key, which silently dropped leaves like
     * {@code DIN_5_2_3_11} / {@code DIN_5_2_31_1} / {@code DIN_52_3_11} that
     * all collapse to {@code DIN52311}. The verbatim emission preserves the
     * underscores (semantic separators in DIN room references etc.) and
     * matches GraphQL's SCREAMING_SNAKE_CASE convention for enum values.
     */
    static String enumValueFor(Category leaf)
    {
        if (leaf == null) return "";
        String key = leaf.getKey();
        if (key == null || key.isEmpty()) return "";
        if (!Tools.isSpecCompliant(key))
        {
            throw new IllegalStateException(
                    "PRD 058 invariant violated — non-spec category leaf key '" + key
                    + "' reached enum-value emission. Migration should have renamed this; "
                    + "check GraphqlKeyMigration logs at startup.");
        }
        return key;
    }

    private static void appendValueListEnum(StringBuilder sb, String enumName, Category root)
    {
        sb.append("\"\"\"\n");
        sb.append("Generated value-list enum for category root `")
          .append(CategoryKindClassifier.keyPath(root)).append("`.\n");
        sb.append("Values are the sanitized leaf-child keys; descriptions carry the locale-resolved names.\n");
        sb.append("PRD 035 §5b — admin add/remove/rename of children triggers schema rebuild.\n");
        sb.append("\"\"\"\n");
        sb.append("enum ").append(enumName).append(" {\n");
        Category[] children = root.getCategories();
        Set<String> emittedValues = new HashSet<>();
        if (children != null)
        {
            for (Category child : children)
            {
                if (child == null) continue;
                String value = enumValueFor(child);
                if (value.isEmpty())
                {
                    LOGGER.warn("VALUE_LIST root '{}' — skipping leaf with empty/unsanitizable key '{}'",
                            root.getKey(), child.getKey());
                    continue;
                }
                if (!emittedValues.add(value))
                {
                    LOGGER.warn("VALUE_LIST root '{}' — enum value '{}' (from leaf key '{}') collides with prior, skipping",
                            root.getKey(), value, child.getKey());
                    continue;
                }
                String localized = child.getName(Locale.getDefault());
                if (localized != null && !localized.isBlank())
                {
                    sb.append("  \"\"\"").append(escapeDescription(localized)).append("\"\"\"\n");
                }
                sb.append("  ").append(value).append("\n");
            }
        }
        sb.append("}\n\n");
    }

    private static String escapeDescription(String s)
    {
        return s.replace("\"", "\\\"").replace("\n", " ").trim();
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

    private static void appendClassificationType(StringBuilder sb, String typeName, DynamicType dt,
            Map<String, Category> valueListEnums)
    {
        String implementsClause = implementsClauseFor(dt);
        sb.append("\"\"\"\n");
        sb.append("Generated classification for DynamicType `").append(dt.getKey()).append("`.\n");
        sb.append("Interface fields are inherited; typed fields below are per-attribute reads.\n");
        sb.append("\"\"\"\n");
        sb.append("type ").append(typeName).append(" implements ").append(implementsClause).append(" {\n");
        sb.append("  typeId: ID!\n");
        sb.append("  type:   DynamicType!\n");

        Set<String> emittedFieldNames = new HashSet<>(Set.of("typeId", "type"));
        for (Attribute attr : dt.getAttributes())
        {
            if (attr == null) continue;
            String attrKey = attr.getKey();
            if (attrKey == null || attrKey.isBlank()) continue;
            String fieldName = checkGraphQlCompliantName(attrKey);
            if (!emittedFieldNames.add(fieldName))
            {
                LOGGER.warn("DynamicType '{}': skipping attribute '{}' — field name '{}' collides with interface field or earlier attribute",
                        dt.getKey(), attrKey, fieldName);
                continue;
            }
            String fieldType = graphqlTypeFor(attr, valueListEnums);
            sb.append("  ").append(fieldName).append(": ").append(fieldType);
            appendDirectives(sb, attr);
            sb.append('\n');
        }
        sb.append("}\n\n");
    }

    /**
     * Emit custom directives on a generated classification field per the
     * 2026-05-28 β refactor — replaces the dropped {@code AttributeDescriptor}
     * type. Directives carry the metadata that introspection alone doesn't
     * surface (display name, expected allocatable target type, category root,
     * multiplicity flavor for non-default values, required flag).
     */
    private static void appendDirectives(StringBuilder sb, Attribute attr)
    {
        // @displayName — always emitted (default-locale resolved at SDL-gen time)
        String name = attr.getName(java.util.Locale.getDefault());
        if (name != null && !name.isBlank() && !name.equals(attr.getKey()))
        {
            sb.append(" @displayName(value: \"").append(escapeStringLiteral(name)).append("\")");
        }

        // @required — emitted iff the attribute is not optional
        if (!attr.isOptional())
        {
            sb.append(" @required");
        }

        // @expectedType — ALLOCATABLE attrs with a KEY_DYNAMIC_TYPE constraint
        if (attr.getType() == AttributeType.ALLOCATABLE)
        {
            Object dtConstraint = attr.getConstraint(ConstraintIds.KEY_DYNAMIC_TYPE);
            if (dtConstraint instanceof DynamicType expected && expected.getKey() != null)
            {
                sb.append(" @expectedType(key: \"")
                  .append(escapeStringLiteral(expected.getKey()))
                  .append("\")");
            }
        }

        // @rootCategory — CATEGORY attrs with a KEY_ROOT_CATEGORY constraint.
        // Path uses the key form (slash-separated, not locale-resolved) per PRD 035 §5a.
        if (attr.getType() == AttributeType.CATEGORY)
        {
            Object rootConstraint = attr.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
            if (rootConstraint instanceof Category root)
            {
                String path = keyPathOf(root);
                if (!path.isEmpty())
                {
                    sb.append(" @rootCategory(path: \"")
                      .append(escapeStringLiteral(path))
                      .append("\")");
                }
            }
        }

        // @multiplicity — only when non-default (non-SINGLE, non-LIST).
        // LIST is implied by `[X!]` type wrapper; SINGLE is implied by no wrapper.
        // BELONGS_TO and PACKAGE are explicit markers that drive SPA widget choice.
        Multiplicity m = multiplicityOf(attr);
        if (m == Multiplicity.BELONGS_TO || m == Multiplicity.PACKAGE)
        {
            sb.append(" @multiplicity(value: ").append(m.name()).append(")");
        }
    }

    /** Compute the slash-separated KEY path of a category, from super-root down. */
    private static String keyPathOf(Category cat)
    {
        if (cat == null || cat.getParent() == null) return "";
        java.util.Deque<String> segs = new java.util.ArrayDeque<>();
        Category cur = cat;
        while (cur != null && cur.getParent() != null)
        {
            String key = cur.getKey();
            if (key == null || key.isBlank()) return "";
            segs.push(key);
            cur = cur.getParent();
        }
        return String.join("/", segs);
    }

    /**
     * Resolve the rapla multiplicity flavor for an attribute. The four values
     * correspond to mutually-exclusive admin-UI choices (see PRD 057
     * §"Multiplicity expansion"). Server treats first-truthy-constraint-wins;
     * if more than one is set (data error), the earlier one in the
     * BELONGS_TO → PACKAGE → MULTI_SELECT order wins.
     */
    private static Multiplicity multiplicityOf(Attribute attr)
    {
        if (truthy(attr.getConstraint(ConstraintIds.KEY_BELONGS_TO))) return Multiplicity.BELONGS_TO;
        if (truthy(attr.getConstraint(ConstraintIds.KEY_PACKAGE)))    return Multiplicity.PACKAGE;
        if (truthy(attr.getConstraint(ConstraintIds.KEY_MULTI_SELECT))) return Multiplicity.LIST;
        return Multiplicity.SINGLE;
    }

    private static boolean truthy(Object o)
    {
        if (o == null) return false;
        if (o instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(o.toString());
    }

    /** Mirror of the {@code Multiplicity} GraphQL enum. */
    private enum Multiplicity { SINGLE, LIST, BELONGS_TO, PACKAGE }

    /** Escape a Java string for embedding in a GraphQL string literal. */
    private static String escapeStringLiteral(String s)
    {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
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
     *
     * <p>For CATEGORY-typed attributes whose rootCategory is a VALUE_LIST
     * (per §5a kind determination), the field is typed as the generated
     * enum from {@link #enumNameFor} instead of the generic {@code Category}.
     * For ORGANIZATION-kind roots (rare; hierarchical), stays as {@code Category}.
     */
    private static String graphqlTypeFor(Attribute attr, Map<String, Category> valueListEnums)
    {
        AttributeType t = attr.getType();
        if (t == null) return "String";   // shouldn't happen; defensive
        String base;
        if (t == AttributeType.CATEGORY)
        {
            Object root = attr.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
            String enumName = (root instanceof Category cat) ? enumNameFor(cat) : "";
            base = (!enumName.isEmpty() && valueListEnums.containsKey(enumName))
                    ? enumName       // VALUE_LIST → use the generated enum type
                    : "Category";    // ORGANIZATION or no root constraint → generic
        }
        else
        {
            base = switch (t)
            {
                case STRING      -> "String";
                case INT         -> "Int";
                case BOOLEAN     -> "Boolean";
                case DATE        -> "LocalDateTime";
                case ALLOCATABLE -> "Allocatable";
                case CATEGORY    -> "Category";   // unreachable; covered above
            };
        }
        // Per the expanded Multiplicity enum (PRD 057): LIST, BELONGS_TO, and
        // PACKAGE all emit as `[X!]`. SINGLE stays bare.
        boolean multiValued = multiplicityOf(attr) != Multiplicity.SINGLE;
        return multiValued ? ("[" + base + "!]") : base;
    }

    /**
     * Verify a rapla key is GraphQL-spec-compliant and return it verbatim.
     * Used for every identifier the SDL generator emits — type names, enum
     * type names, enum values, field names. PRD 058's migration guarantees
     * that every key reaching this method is already spec-compliant ASCII
     * ({@code [A-Za-z_][A-Za-z0-9_]*}).
     *
     * <p><b>No transformation.</b> The generator respects what the admin
     * chose for the key. If they keyed a DynamicType {@code Room}, the
     * generated GraphQL type is {@code RoomClassification}. If they keyed
     * {@code room}, it's {@code roomClassification}. SCREAMING_SNAKE_CASE
     * enum values come from admins keying their categories that way; if
     * they keyed lowercase, the enum values are lowercase.
     *
     * <p>The Angular SPA may auto-suggest GraphQL conventions
     * (PascalCase types, SCREAMING_SNAKE_CASE enum values) at key-creation
     * time, but the SDL generator never imposes them retroactively.
     *
     * <p>Reserved-keyword trailing-underscore disambiguation is the only
     * transform — some tooling chokes on {@code type} or {@code interface}
     * as a field name even though graphql-java itself is lax.
     *
     * <p>Throws {@link IllegalStateException} on non-spec input. Migration
     * should have caught any non-spec key before it reaches the SDL generator.
     */
    static String checkGraphQlCompliantName(String rawKey)
    {
        if (!Tools.isSpecCompliant(rawKey))
        {
            throw new IllegalStateException(
                    "PRD 058 invariant violated — non-spec attribute key '" + rawKey
                    + "' reached SDL field-name generator. Migration should have renamed this; "
                    + "check GraphqlKeyMigration logs at startup.");
        }
        if (GRAPHQL_RESERVED.contains(rawKey.toLowerCase(Locale.ROOT)))
        {
            return rawKey + "_";
        }
        return rawKey;
    }
}
