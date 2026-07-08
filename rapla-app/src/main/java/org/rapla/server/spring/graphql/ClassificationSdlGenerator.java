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
import org.rapla.entities.dynamictype.AttributeAnnotations;
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
 *     typeKey:    String!
 *     type:       DynamicType!
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
        // The id keeps the immutable 'rapla:' marker even when the key was
        // sanitized to 'rapla_…' by an old migration — see DynamicTypeImpl.isInternal.
        String id = dt.getId();
        if (id != null && id.startsWith("rapla:")) return true;
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
        return generate(dynamicTypes, Locale.getDefault());
    }

    /**
     * @param locale the server-configured display language (admin "Server
     *   Sprache" — see {@code ServerLocaleResolver}) used to resolve every
     *   {@code @displayName} and VALUE_LIST enum description. NOT the JVM
     *   default, which is deployment-environment noise.
     */
    public static String generate(Collection<DynamicType> dynamicTypes, Locale locale)
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
                appendValueListEnum(sb, e.getKey(), e.getValue(), locale);
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
            appendClassificationType(sb, typeName, dt, valueListEnums, locale);
        }

        // === PRD 056 — write-side typed inputs (symmetric β² mirror of reads) ===
        appendWriteSideInputs(sb, dynamicTypes, valueListEnums);

        // === PRD 035 §5d — typed <TypeKey>Where + per-enum *Where inputs ===
        appendWhereInputs(sb, dynamicTypes, valueListEnums);

        // === PRD 059 Phase 7 — per-kind type enums + typeIn on both filters ===
        appendTypeInEnum(sb, dynamicTypes);

        return sb.toString();
    }

    /**
     * PRD 059 Phase 7 — the single, schema-validated type selector. Emits
     * one enum PER KIND ({@code AllocatableTypeKey} = resource+person DTs,
     * {@code ReservationTypeKey} = reservation DTs) and extends the matching
     * static filter input with {@code typeIn}. Replaces the removed
     * String-typed {@code typeKeyEq}/{@code typeKeyIn} fields — unknown keys
     * AND wrong-kind keys (an allocatable key on {@code ReservationFilter})
     * are rejected at validation time instead of silently yielding an empty
     * result. Enum value = {@link #checkGraphQlCompliantName} of the DT key
     * (identical to the key except for GraphQL-reserved words, which get a
     * trailing underscore).
     */
    private static void appendTypeInEnum(StringBuilder sb, Collection<DynamicType> dynamicTypes)
    {
        List<String> allocatableKeys = new ArrayList<>();
        List<String> reservationKeys = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (DynamicType dt : sortedByKey(dynamicTypes))
        {
            if (dt == null || isRaplaInternal(dt)) continue;
            String key = dt.getKey();
            if (key == null || key.isBlank()) continue;
            String kind = dt.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
            boolean isAllocatableKind = DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE.equals(kind)
                    || DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON.equals(kind);
            boolean isReservationKind = DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION.equals(kind);
            if (!isAllocatableKind && !isReservationKind) continue;
            String name = checkGraphQlCompliantName(key);
            if (!seen.add(name)) continue;
            (isAllocatableKind ? allocatableKeys : reservationKeys).add(name);
        }
        if (allocatableKeys.isEmpty() && reservationKeys.isEmpty()) return;
        sb.append("\n# === PRD 059 Phase 7 GENERATED per-kind type selectors ===\n");
        if (!allocatableKeys.isEmpty())
        {
            sb.append("\"One value per resource/person DynamicType key — schema-validated type selection.\"\n");
            sb.append("enum AllocatableTypeKey {\n");
            for (String v : allocatableKeys) sb.append("  ").append(v).append('\n');
            sb.append("}\n\n");
            sb.append("extend input AllocatableFilter {\n");
            sb.append("  \"PRD 059 Phase 7 — match any resource/person DynamicType key in the list (cross-type union). THE type selector; pre-filters at the storage layer.\"\n");
            sb.append("  typeIn: [AllocatableTypeKey!]\n");
            sb.append("}\n\n");
        }
        if (!reservationKeys.isEmpty())
        {
            sb.append("\"One value per reservation DynamicType key — schema-validated type selection.\"\n");
            sb.append("enum ReservationTypeKey {\n");
            for (String v : reservationKeys) sb.append("  ").append(v).append('\n');
            sb.append("}\n\n");
            sb.append("extend input ReservationFilter {\n");
            sb.append("  \"PRD 059 Phase 7 — match any reservation DynamicType key in the list (union).\"\n");
            sb.append("  typeIn: [ReservationTypeKey!]\n");
            sb.append("}\n\n");
        }
    }

    /**
     * Generate per-DynamicType ClassificationInput types + the @oneOf
     * polymorphic dispatch wrappers (AllocatableClassificationInput,
     * ReservationClassificationInput). Mirrors read-side classification
     * type generation but with input-specific encoding:
     *
     * <ul>
     *   <li>ALLOCATABLE attrs → ID (write uses references, read returns
     *       full entities)</li>
     *   <li>CATEGORY ORGANIZATION attrs → ID</li>
     *   <li>CATEGORY VALUE_LIST attrs → same generated enum (shared)</li>
     *   <li>Multi-valued → [X!] (no outer !)</li>
     *   <li>All fields nullable — required semantics enforced server-side
     *       per Attribute.isOptional() on save, not via input typing</li>
     *   <li>typeId / type interface fields omitted — they belong on output</li>
     * </ul>
     *
     * <p>Per-DynamicType inputs are named `<typeKey>ClassificationInput`
     * (verbatim key + suffix). Dispatch wrappers carry one variant per
     * contributing DynamicType, variant name = verbatim type key, so
     * callers can do `classification: { [typeId]: payload }`.
     */
    private static void appendWriteSideInputs(StringBuilder sb, Collection<DynamicType> dynamicTypes,
            Map<String, Category> valueListEnums)
    {
        sb.append("\n# === PRD 056 GENERATED per-DynamicType classification INPUTS ===\n");
        sb.append("# Symmetric β² — typed write inputs mirror typed read outputs.\n\n");

        Set<String> emittedInputNames = new HashSet<>();
        List<String> allocatableVariants = new ArrayList<>();
        List<String> reservationVariants = new ArrayList<>();

        for (DynamicType dt : sortedByKey(dynamicTypes))
        {
            if (dt == null) continue;
            if (isRaplaInternal(dt)) continue;
            String key = dt.getKey();
            if (key == null || key.isBlank()) continue;
            String inputName = checkGraphQlCompliantName(key) + "ClassificationInput";
            if (!emittedInputNames.add(inputName))
            {
                LOGGER.warn("DynamicType '{}': sanitized input name '{}' already emitted", key, inputName);
                continue;
            }
            appendClassificationInputType(sb, inputName, dt, valueListEnums);

            // Collect variant entry for the @oneOf wrapper based on classification-kind.
            String kind = dt.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
            String variantField = key;          // verbatim — matches typeId discriminator
            String variantLine = "  " + variantField + ": " + inputName;
            if (DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE.equals(kind)
                    || DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON.equals(kind))
            {
                allocatableVariants.add(variantLine);
            }
            else if (DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION.equals(kind))
            {
                reservationVariants.add(variantLine);
            }
        }

        // Emit the @oneOf dispatch wrappers. Empty deployments still get a
        // valid wrapper — empty @oneOf inputs are spec-valid (just unsatisfiable
        // for any caller, which is fine when the deployment has no such types yet).
        appendOneOfWrapper(sb, "AllocatableClassificationInput", allocatableVariants);
        appendOneOfWrapper(sb, "ReservationClassificationInput", reservationVariants);
    }

    /** Emit `input <Name>ClassificationInput { ... per-attribute typed fields ... }`. */
    private static void appendClassificationInputType(StringBuilder sb, String inputName,
            DynamicType dt, Map<String, Category> valueListEnums)
    {
        sb.append("\"\"\"\nWrite-side typed input for DynamicType `")
          .append(dt.getKey()).append("` classifications.\n")
          .append("All fields nullable — required semantics enforced server-side per Attribute.isOptional().\n")
          .append("\"\"\"\n");
        sb.append("input ").append(inputName).append(" {\n");
        Set<String> emittedFieldNames = new HashSet<>();
        for (Attribute attr : dt.getAttributes())
        {
            if (attr == null) continue;
            String attrKey = attr.getKey();
            if (attrKey == null || attrKey.isBlank()) continue;
            String fieldName = checkGraphQlCompliantFieldName(attrKey);
            if (!emittedFieldNames.add(fieldName))
            {
                LOGGER.warn("DynamicType '{}': input field name '{}' collides", dt.getKey(), fieldName);
                continue;
            }
            String fieldType = inputGraphqlTypeFor(attr, valueListEnums);
            sb.append("  ").append(fieldName).append(": ").append(fieldType).append("\n");
        }
        sb.append("}\n\n");
    }

    /**
     * Input-side type encoding — differs from the read-side
     * {@link #graphqlTypeFor} in two ways:
     * <ul>
     *   <li>ALLOCATABLE → ID (caller supplies references)</li>
     *   <li>CATEGORY ORGANIZATION → ID (same reason)</li>
     *   <li>CATEGORY VALUE_LIST → same enum (shared between read/write)</li>
     *   <li>Always nullable (no outer !) — required handled server-side</li>
     * </ul>
     */
    private static String inputGraphqlTypeFor(Attribute attr, Map<String, Category> valueListEnums)
    {
        AttributeType t = attr.getType();
        if (t == null) return "String";
        String base;
        if (t == AttributeType.CATEGORY)
        {
            Object root = attr.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
            String enumName = (root instanceof Category cat) ? enumNameFor(cat) : "";
            base = (!enumName.isEmpty() && valueListEnums.containsKey(enumName))
                    ? enumName       // VALUE_LIST shares the enum with reads
                    : "ID";          // ORGANIZATION → reference by id
        }
        else
        {
            base = switch (t)
            {
                case STRING      -> "String";
                case INT         -> "Int";
                case BOOLEAN     -> "Boolean";
                case DATE        -> "LocalDateTime";
                case ALLOCATABLE -> "ID";     // write uses id reference
                case CATEGORY    -> "ID";     // unreachable; covered above
            };
        }
        return isList(attr) ? ("[" + base + "!]") : base;
    }

    /** Emit `input <Name> @oneOf { variant: <TypeInput> ... }`. */
    private static void appendOneOfWrapper(StringBuilder sb, String wrapperName, List<String> variants)
    {
        sb.append("\"\"\"\n@oneOf polymorphic dispatch — exactly one variant matching the entity's typeId.\n\"\"\"\n");
        sb.append("input ").append(wrapperName).append(" @oneOf {\n");
        if (variants.isEmpty())
        {
            // GraphQL spec requires non-empty input types. Stub field for the
            // deployment-has-no-such-types degenerate case. Server validates
            // it's never actually set.
            sb.append("  _empty: String\n");
        }
        else
        {
            for (String v : variants) sb.append(v).append("\n");
        }
        sb.append("}\n\n");
    }

    /**
     * PRD 035 §5d Phase 1 — emit typed where-predicate inputs.
     *
     * For each VALUE_LIST enum, emit `<enum>Where` (eq/ne/in/isNull) and
     * `<enum>ListWhere` (contains/containsAny/containsAll/isEmpty/isNull).
     *
     * For each RESOURCE, PERSON or RESERVATION DynamicType, emit
     * `<typeKey>Where` with one field per attribute (predicate type matched
     * by attribute kind + multi-select cardinality) plus AND / OR / NOT
     * combinators. Resource/person where-fields extend `AllocatableFilter`;
     * reservation where-fields extend `ReservationFilter` (PRD 059 Phase 6)
     * — the SAME input shape and the SAME WhereEvaluator on both paths.
     */
    private static void appendWhereInputs(StringBuilder sb, Collection<DynamicType> dynamicTypes,
            Map<String, Category> valueListEnums)
    {
        sb.append("\n# === PRD 035 §5d GENERATED <TypeKey>Where + per-enum *Where inputs ===\n");
        sb.append("# Per VALUE_LIST root: <enum>Where + <enum>ListWhere.\n");
        sb.append("# Per RESOURCE/PERSON/RESERVATION DT: <typeKey>Where with AND/OR/NOT combinators.\n\n");

        for (Map.Entry<String, Category> e : valueListEnums.entrySet())
        {
            appendEnumWhereInputs(sb, e.getKey());
        }

        // PRD 074 b — pre-pass: which DT keys get a <T>Where, so a reference field can target the
        // referenced type's <refKey>RefWhere (typed recursive where). Computed before emission
        // because reference fields in a <T>Where need to know the target set.
        Set<String> whereTypeNames = new java.util.LinkedHashSet<>();
        for (DynamicType dt : sortedByKey(dynamicTypes))
        {
            if (dt == null || isRaplaInternal(dt)) continue;
            String kind = dt.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
            if (!DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE.equals(kind)
                    && !DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON.equals(kind)) continue;
            String key = dt.getKey();
            if (key != null && !key.isBlank()) whereTypeNames.add(checkGraphQlCompliantName(key));
        }

        Set<String> emittedWhereNames = new HashSet<>();
        List<String> allocatableFilterLines = new ArrayList<>();
        List<String> reservationFilterLines = new ArrayList<>();
        for (DynamicType dt : sortedByKey(dynamicTypes))
        {
            if (dt == null) continue;
            if (isRaplaInternal(dt)) continue;
            String kind = dt.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
            boolean isAllocatableKind = DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE.equals(kind)
                    || DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON.equals(kind);
            boolean isReservationKind = DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION.equals(kind);
            if (!isAllocatableKind && !isReservationKind) continue;
            String key = dt.getKey();
            if (key == null || key.isBlank()) continue;
            String whereName = checkGraphQlCompliantName(key) + "Where";
            if (!emittedWhereNames.add(whereName))
            {
                LOGGER.warn("DynamicType '{}': where input '{}' name collision; skipping", key, whereName);
                continue;
            }
            appendTypeWhereInput(sb, whereName, dt, valueListEnums, whereTypeNames);
            if (isAllocatableKind)
            {
                appendRefWhereInput(sb, checkGraphQlCompliantName(key));   // PRD 074 b — <type>RefWhere (allocatables can be referenced)
            }
            String fieldName = "where" + capitalizeFirst(checkGraphQlCompliantName(key));
            (isAllocatableKind ? allocatableFilterLines : reservationFilterLines)
                    .add("  " + fieldName + ": " + whereName);
        }

        // === Phase 2 / Phase 6 — extend the static filter inputs with one
        // `where<TypeKey>` field per DT. Without this extension the Spring
        // binder rejects `whereRoom:` / `whereEvent:` as "field not in
        // <filter>". GraphQL `extend input` is the spec-correct hook for
        // runtime-generated additions to a statically-declared input.
        if (!allocatableFilterLines.isEmpty())
        {
            sb.append("extend input AllocatableFilter {\n");
            for (String line : allocatableFilterLines) sb.append(line).append("\n");
            sb.append("}\n\n");
        }
        if (!reservationFilterLines.isEmpty())
        {
            sb.append("extend input ReservationFilter {\n");
            for (String line : reservationFilterLines) sb.append(line).append("\n");
            sb.append("}\n\n");
        }
    }

    private static String capitalizeFirst(String s)
    {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Emit per-enum `<enum>Where` (single) and `<enum>ListWhere` (multi) inputs. */
    private static void appendEnumWhereInputs(StringBuilder sb, String enumName)
    {
        sb.append("input ").append(enumName).append("Where {\n");
        sb.append("  eq:     ").append(enumName).append("\n");
        sb.append("  ne:     ").append(enumName).append("\n");
        sb.append("  in:     [").append(enumName).append("!]\n");
        sb.append("  isNull: Boolean\n");
        sb.append("}\n\n");

        sb.append("input ").append(enumName).append("ListWhere {\n");
        sb.append("  contains:    ").append(enumName).append("\n");
        sb.append("  containsAny: [").append(enumName).append("!]\n");
        sb.append("  containsAll: [").append(enumName).append("!]\n");
        sb.append("  isEmpty:     Boolean\n");
        sb.append("  isNull:      Boolean\n");
        sb.append("}\n\n");
    }

    /**
     * PRD 074 b — emit `<typeName>RefWhere`: the filter for an allocatable-REFERENCE attribute that
     * points to this type. Carries id/name predicates (`eq`/`ne`/`in`/`isNull`/`nameContains`) plus
     * a typed nested `where: <typeName>Where` to filter the referenced entity by its OWN attributes
     * (e.g. rooms by their building's Standort). Resolved §12-gated in WhereEvaluator.
     */
    private static void appendRefWhereInput(StringBuilder sb, String typeName)
    {
        sb.append("\"PRD 074 b — filter an allocatable reference to `").append(typeName)
          .append("` by id/name, or recurse into its attributes via `where`.\"\n");
        sb.append("input ").append(typeName).append("RefWhere {\n");
        sb.append("  eq:           ID\n");
        sb.append("  ne:           ID\n");
        sb.append("  in:           [ID!]\n");
        sb.append("  isNull:       Boolean\n");
        sb.append("  nameContains: String\n");
        sb.append("  where:        ").append(typeName).append("Where\n");
        sb.append("}\n\n");
    }

    /** Emit a `<typeKey>Where` input for one resource/person DynamicType. */
    private static void appendTypeWhereInput(StringBuilder sb, String whereName,
            DynamicType dt, Map<String, Category> valueListEnums, Set<String> whereTypeNames)
    {
        sb.append("\"\"\"\n");
        sb.append("Generated typed-where predicate for DynamicType `").append(dt.getKey()).append("`.\n");
        sb.append("Multiple operators inside one field AND together; AND/OR/NOT combinators\n");
        sb.append("compose multiple where shapes recursively.\n");
        sb.append("\"\"\"\n");
        sb.append("input ").append(whereName).append(" {\n");

        Set<String> emittedFieldNames = new HashSet<>();
        for (Attribute attr : dt.getAttributes())
        {
            if (attr == null) continue;
            String attrKey = attr.getKey();
            if (attrKey == null || attrKey.isBlank()) continue;
            String fieldName = checkGraphQlCompliantFieldName(attrKey);
            if (!emittedFieldNames.add(fieldName))
            {
                LOGGER.warn("DynamicType '{}': where field name '{}' collides; skipping", dt.getKey(), fieldName);
                continue;
            }
            String predicateType = wherePredicateTypeFor(attr, valueListEnums, whereTypeNames);
            if (predicateType == null)
            {
                LOGGER.warn("DynamicType '{}': attribute '{}' has no supported where-predicate type "
                        + "(multi-select STRING/INT/BOOLEAN/DATE not yet supported); skipping field",
                        dt.getKey(), attrKey);
                continue;
            }
            sb.append("  ").append(fieldName).append(": ").append(predicateType).append("\n");
        }

        // Combinators — recursive references to the same where input.
        sb.append("  AND: [").append(whereName).append("!]\n");
        sb.append("  OR:  [").append(whereName).append("!]\n");
        sb.append("  NOT: ").append(whereName).append("\n");
        sb.append("}\n\n");
    }

    /**
     * Resolve the where-predicate input type name for one attribute.
     * Returns null when no predicate type covers this attribute (e.g.
     * multi-select STRING — deferred to Phase 5).
     */
    private static String wherePredicateTypeFor(Attribute attr, Map<String, Category> valueListEnums,
            Set<String> whereTypeNames)
    {
        AttributeType t = attr.getType();
        if (t == null) return null;
        boolean multi = isList(attr);

        if (t == AttributeType.CATEGORY)
        {
            Object root = attr.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
            String enumName = (root instanceof Category cat) ? enumNameFor(cat) : "";
            boolean valueList = !enumName.isEmpty() && valueListEnums.containsKey(enumName);
            if (valueList)
            {
                return multi ? (enumName + "ListWhere") : (enumName + "Where");
            }
            return multi ? "CategoryListWhere" : "CategoryWhere";
        }
        if (t == AttributeType.ALLOCATABLE)
        {
            if (multi) return "AllocatableListWhere";
            // PRD 074 b — typed reference: if the attribute constrains to a known DynamicType that
            // has a generated <T>Where, target <T>RefWhere (id/name + nested typed where). Otherwise
            // the generic id/name AllocatableWhere.
            Object dtConstraint = attr.getConstraint(ConstraintIds.KEY_DYNAMIC_TYPE);
            if (dtConstraint instanceof DynamicType ref && ref.getKey() != null)
            {
                String refName = checkGraphQlCompliantName(ref.getKey());
                if (whereTypeNames.contains(refName)) return refName + "RefWhere";
            }
            return "AllocatableWhere";
        }
        if (multi)
        {
            // STRING/INT/BOOLEAN/DATE list — no static *ListWhere yet. Phase 5.
            return null;
        }
        return switch (t)
        {
            case STRING   -> "StringWhere";
            case INT      -> "IntWhere";
            case BOOLEAN  -> "BooleanWhere";
            case DATE     -> "LocalDateTimeWhere";
            case CATEGORY -> null;     // unreachable; covered above
            case ALLOCATABLE -> null;  // unreachable; covered above
        };
    }

    /** Check + return a key as a valid GraphQL field name (per spec). */
    private static String checkGraphQlCompliantFieldName(String key)
    {
        // checkGraphQlCompliantName handles spec validation; field names share
        // the spec but reserved keywords get a trailing underscore.
        String name = checkGraphQlCompliantName(key);
        if (GRAPHQL_RESERVED.contains(name.toLowerCase(Locale.ROOT)))
        {
            return name + "_";
        }
        return name;
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

    private static void appendValueListEnum(StringBuilder sb, String enumName, Category root, Locale locale)
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
                String localized = child.getName(locale);
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
            Map<String, Category> valueListEnums, Locale locale)
    {
        String implementsClause = implementsClauseFor(dt);
        sb.append("\"\"\"\n");
        sb.append("Generated classification for DynamicType `").append(dt.getKey()).append("`.\n");
        sb.append("Interface fields are inherited; typed fields below are per-attribute reads.\n");
        sb.append("\"\"\"\n");
        sb.append("type ").append(typeName).append(" implements ").append(implementsClause).append(" {\n");
        sb.append("  typeKey: String!\n");
        sb.append("  type:    DynamicType!\n");

        Set<String> emittedFieldNames = new HashSet<>(Set.of("typeKey", "type"));
        Set<String> titleKeys = titleAttributeKeys(dt);
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
            appendDirectives(sb, attr, locale);
            String editView = titleKeys.contains(attrKey) ? "title" : editViewOf(attr);
            if (editView != null)
            {
                sb.append(" @editView(value: \"").append(editView).append("\")");
            }
            sb.append('\n');
        }
        sb.append("}\n\n");
    }

    /**
     * PRD 096 D5 revision — the title attributes of a DynamicType: every
     * direct attribute reference ({@code {key}}) in the DISPLAY nameformat
     * whose attribute exists, is not list-valued, and is not explicitly
     * annotated edit-view=no-view (the explicit annotation wins). Function
     * expressions and literals in the format are ignored — {@code {surname}
     * {forename}} yields both, {@code {name} concat(...)} yields name.
     */
    private static Set<String> titleAttributeKeys(DynamicType dt)
    {
        String format = dt.getAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT);
        if (format == null) return Set.of();
        Set<String> keys = new HashSet<>();
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\\{(\\w+)\\}").matcher(format);
        while (m.find())
        {
            String key = m.group(1);
            Attribute attr = dt.getAttribute(key);
            if (attr == null || isList(attr)) continue;
            if (AttributeAnnotations.VALUE_EDIT_VIEW_NO_VIEW.equals(
                    attr.getAnnotation(AttributeAnnotations.KEY_EDIT_VIEW))) continue;
            keys.add(key);
        }
        return keys;
    }

    /**
     * Non-default placement from the {@code edit-view} attribute annotation:
     * "additional" / "no-view", or null for the main default (omitted on the
     * wire — absence of {@code @editView} means main).
     */
    private static String editViewOf(Attribute attr)
    {
        String view = attr.getAnnotation(AttributeAnnotations.KEY_EDIT_VIEW);
        if (AttributeAnnotations.VALUE_EDIT_VIEW_ADDITIONAL.equals(view)) return "additional";
        if (AttributeAnnotations.VALUE_EDIT_VIEW_NO_VIEW.equals(view)) return "no-view";
        return null;
    }

    /**
     * Emit custom directives on a generated classification field per the
     * 2026-05-28 β refactor — replaces the dropped {@code AttributeDescriptor}
     * type. Directives carry the metadata that introspection alone doesn't
     * surface (display name, expected allocatable target type, category root,
     * multiplicity flavor for non-default values, required flag).
     */
    private static void appendDirectives(StringBuilder sb, Attribute attr, Locale locale)
    {
        // @displayName — always emitted (server-configured "Server Sprache"
        // resolved at SDL-gen time; NOT the JVM default — see ServerLocaleResolver)
        String name = attr.getName(locale);
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
     * Schema cardinality — true when the attribute holds a list of values
     * rather than a single one. Driven exclusively by the {@code multi-select}
     * constraint. The {@code belongsTo} / {@code package} markers are
     * <b>semantic flags</b> (drive the SPA widget choice via the
     * {@code @multiplicity} directive); they do NOT imply list cardinality.
     *
     * <p>Pre-fix bug: {@link #multiplicityOf} returned {@code BELONGS_TO}
     * for any belongsTo-flagged attribute regardless of its multi-select
     * setting. The schema then emitted {@code [Allocatable!]} for a
     * single-valued {@code Gebaeude} field — runtime returns a single
     * Allocatable → "expected type LIST" mismatch at every read. Same
     * shape for {@code package} attributes that happen to be single-select.
     * Splitting cardinality from marker semantics fixes both.
     */
    private static boolean isList(Attribute attr)
    {
        return truthy(attr.getConstraint(ConstraintIds.KEY_MULTI_SELECT));
    }

    /**
     * Resolve the rapla multiplicity FLAVOR — the marker that drives the
     * SPA widget choice via the {@code @multiplicity} directive. This is a
     * semantic categorization, not a cardinality assertion: a BELONGS_TO
     * attribute may be single-valued (multi-select=false) or list-valued
     * (multi-select=true). Use {@link #isList} for the cardinality
     * question.
     *
     * <p>First-truthy-constraint-wins; if more than one is set (data error),
     * the earlier one in the BELONGS_TO → PACKAGE → MULTI_SELECT order wins.
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
        // Cardinality is `multi-select`-only — see isList() rationale.
        // BELONGS_TO/PACKAGE markers are emitted as @multiplicity directives
        // on the field (separate concern from cardinality).
        return isList(attr) ? ("[" + base + "!]") : base;
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
