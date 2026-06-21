package org.rapla.server.spring.graphql;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.rapla.entities.extensionpoints.FunctionDescriptor;

/**
 * PRD 073 — descriptor-driven generation of GraphQL fields from rapla
 * {@link FunctionDescriptor}s. A function declared by any {@link org.rapla.entities.extensionpoints.FunctionFactory}
 * (core or plugin) becomes a first-class typed field on its source GraphQL type — e.g. the
 * appointmentnote plugin's {@code note} descriptor surfaces as {@code AppointmentBlock.note: String}.
 * The SDL ({@link #generateSdl}) and the wiring ({@link GeneratedClassificationWiring}) both derive
 * the eligible set from the SAME rules here, so schema and resolvers can't drift.
 *
 * <p><b>First cut scope:</b> EVENT-source functions returning {@code String}, emitted onto
 * {@code AppointmentBlock} (the flat table/week-view row), skipping any name already statically
 * defined there. Today that yields exactly {@code note} (core {@code times}/{@code duration} already
 * exist on the block). Widening to more source types / scalar return types is purely a matter of
 * extending {@link #TARGET_TYPES}, {@link #EXISTING_FIELDS}, and {@link #returnTypeToGraphql}.
 */
final class FunctionFieldGenerator
{
    private FunctionFieldGenerator() {}

    /** GraphQL object types we emit generated function-fields onto (all EVENT-source). */
    static final List<String> TARGET_TYPES = List.of("AppointmentBlock", "Appointment");

    /**
     * Field names already statically defined on each target type in {@code schema.graphqls} — a
     * generated field must never collide with them ({@code extend type} would be a duplicate-field
     * schema error). Keep in sync with the static type definitions.
     */
    static final Map<String, Set<String>> EXISTING_FIELDS = Map.of(
            "AppointmentBlock",
            Set.of("start", "end", "isException", "name", "reservation",
                    "allocatables", "duration", "times", "durationMinutes", "compute"),
            "Appointment",
            Set.of("id", "name", "start", "end", "allDay", "repeating", "allocatables", "blocks"));

    /**
     * Functions that are EVENT-level but only meaningful on a materialized {@code AppointmentBlock}
     * (they return blank/garbage on an Appointment/Reservation subject). Kept off non-block targets
     * so we don't generate always-null fields. {@code number} is the block's 1-based sequence index,
     * which is undefined without a concrete occurrence.
     */
    static final Set<String> BLOCK_ONLY = Set.of("number");

    /** rapla return-type → GraphQL scalar. Scalars only (no object/list returns yet). null = not fieldable.
     * rapla wall-time {@code DateTime} maps to the wall-time {@code LocalDateTime} scalar (matching
     * start/end); rapla {@code Date} to the extended {@code Date} scalar. */
    static String returnTypeToGraphql(String raplaReturnType)
    {
        if (raplaReturnType == null) return null;
        return switch (raplaReturnType)
        {
            case "String"   -> "String";
            case "Int"      -> "Int";
            case "Boolean"  -> "Boolean";
            case "DateTime" -> "LocalDateTime";
            case "Date"     -> "Date";
            default          -> null;   // TimeInterval / objects / lists: not yet generated
        };
    }

    /**
     * Coerce a RAW EL eval result to the Java type the GraphQL scalar expects. The EL returns
     * domain objects (LocalDateTime, Boolean, Number) or stringified values; we coerce per scalar so
     * the scalar's {@code serialize} never sees a wrong type. Blank/uncoercible → null.
     */
    static Object coerceScalar(String gqlType, Object o)
    {
        if (o == null) return null;
        switch (gqlType)
        {
            case "Int":
                return coerceInt(o);
            case "Boolean":
                if (o instanceof Boolean b) return b;
                String bs = o.toString().trim();
                return bs.isEmpty() ? null : Boolean.valueOf(bs);
            case "LocalDateTime":
                return (o instanceof java.time.LocalDateTime) ? o : null;
            case "Date":
                if (o instanceof java.time.LocalDate) return o;
                if (o instanceof java.time.LocalDateTime ldt) return ldt.toLocalDate();
                return null;
            default:
                return o;   // String handled via the formatName path, not here
        }
    }

    /** Coerce an EL eval result to an Integer ({@code Int} fields): Number or numeric String → Integer. */
    static Integer coerceInt(Object o)
    {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        String s = o.toString().trim();
        if (s.isEmpty()) return null;
        try { return Integer.valueOf(s); }
        catch (NumberFormatException e) { return null; }
    }

    /** A descriptor is fieldable on {@code type} when it is EVENT-sourced, maps to a GraphQL scalar,
     * and its name isn't already statically defined on that type. */
    static boolean eligible(FunctionDescriptor d, String type)
    {
        if (d == null || d.name() == null || d.name().isBlank()) return false;
        if (!"EVENT".equals(d.sourceLevel())) return false;
        if (returnTypeToGraphql(d.returnType()) == null) return false;
        if (BLOCK_ONLY.contains(d.name()) && !"AppointmentBlock".equals(type)) return false;
        return !EXISTING_FIELDS.getOrDefault(type, Set.of()).contains(d.name());
    }

    /** The eligible descriptors for a target type, deduped by field name (first namespace wins),
     * ordered by name for stable SDL/wiring. */
    static Collection<FunctionDescriptor> eligibleFor(String type, Collection<FunctionDescriptor> all)
    {
        Map<String, FunctionDescriptor> byName = new TreeMap<>();
        for (FunctionDescriptor d : all)
        {
            if (eligible(d, type)) byName.putIfAbsent(d.name(), d);
        }
        return byName.values();
    }

    /**
     * The rapla-EL expression evaluated for a generated field: the (namespace-qualified) function
     * applied to the row subject {@code item}. Core ({@code org.rapla}) functions are written bare;
     * plugin functions carry their {@code namespace:} prefix (as in nameformats).
     */
    static String exprFor(FunctionDescriptor d)
    {
        String prefix = "org.rapla".equals(d.namespace()) ? "" : d.namespace() + ":";
        String arg = d.maxArgs() == 0 ? "()" : "(item)";
        return prefix + d.name() + arg;
    }

    /** Generate the {@code extend type … { … }} SDL for all eligible function-fields. Empty if none. */
    static String generateSdl(Collection<FunctionDescriptor> descriptors)
    {
        StringBuilder sb = new StringBuilder();
        for (String type : TARGET_TYPES)
        {
            Collection<FunctionDescriptor> fields = eligibleFor(type, descriptors);
            if (fields.isEmpty()) continue;
            sb.append("extend type ").append(type).append(" {\n");
            for (FunctionDescriptor d : fields)
            {
                if (d.doc() != null && !d.doc().isBlank())
                {
                    sb.append("  \"").append(d.doc().replace("\"", "'")).append(" (PRD 073, generated)\"\n");
                }
                sb.append("  ").append(d.name()).append(": ")
                        .append(returnTypeToGraphql(d.returnType())).append('\n');
            }
            sb.append("}\n");
        }
        return sb.toString();
    }

    /** Aggregate descriptors from a namespace-keyed FunctionFactory registry, deduped by namespace:name. */
    static List<FunctionDescriptor> aggregate(Map<String, org.rapla.entities.extensionpoints.FunctionFactory> factories)
    {
        Map<String, FunctionDescriptor> byKey = new LinkedHashMap<>();
        if (factories != null)
        {
            for (var factory : factories.values())
            {
                if (factory == null) continue;
                for (FunctionDescriptor d : factory.getDescriptors())
                {
                    if (d == null || d.name() == null) continue;
                    byKey.putIfAbsent(d.namespace() + ":" + d.name(), d);
                }
            }
        }
        return List.copyOf(byKey.values());
    }
}
