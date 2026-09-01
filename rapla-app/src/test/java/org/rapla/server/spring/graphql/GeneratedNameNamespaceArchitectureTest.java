package org.rapla.server.spring.graphql;

import org.junit.jupiter.api.Test;
import org.rapla.components.util.Tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hand-written {@code schema.graphqls} and the names the generator derives from
 * admin keys share one GraphQL type namespace. Generated names are {@code <key> +
 * reserved suffix}; keys never end with a reserved suffix ({@link Tools#isSpecCompliant}).
 * This test keeps the hand-written side of that contract: no core type may end with a
 * reserved suffix word (except the fixed predicate types whose stems are reserved keys),
 * and no core filter field may take the {@code where<Key>} shape.
 */
class GeneratedNameNamespaceArchitectureTest
{
    /** Hand-written predicate types; their stems are reserved as DynamicType keys ({@code Tools.isReservedTypeKey}). */
    private static final Set<String> FIXED_WHERE_TYPES = Set.of(
            "StringWhere", "IntWhere", "BooleanWhere", "LocalDateTimeWhere",
            "CategoryWhere", "CategoryListWhere", "AllocatableWhere", "AllocatableListWhere");
    private static final Set<String> FIXED_CLASSIFICATION_TYPES = Set.of(
            "Classification", "AllocatableClassification", "ReservationClassification",
            "AllocatableClassificationInput", "ReservationClassificationInput");

    @Test
    void noHandWrittenTypeEndsWithAReservedSuffix() throws IOException
    {
        String sdl = schema();
        Matcher m = Pattern.compile("^(?:type|input|enum|interface|union|scalar)\\s+([A-Za-z_][A-Za-z0-9_]*)", Pattern.MULTILINE).matcher(sdl);
        List<String> offenders = new ArrayList<>();
        while (m.find())
        {
            String name = m.group(1);
            if (FIXED_WHERE_TYPES.contains(name) || FIXED_CLASSIFICATION_TYPES.contains(name)) continue;
            if (Tools.endsWithReservedGraphqlSuffix(name)) offenders.add(name);
        }
        assertTrue(offenders.isEmpty(), () -> "core types ending with a reserved generated-name suffix " + Tools.RESERVED_GRAPHQL_SUFFIXES + ": " + offenders);
    }

    @Test
    void coreFilterInputsHaveNoWhereKeyShapedFields() throws IOException
    {
        String sdl = schema();
        List<String> offenders = new ArrayList<>();
        for (String input : List.of("AllocatableFilter", "ReservationFilter"))
        {
            Matcher block = Pattern.compile("^input " + input + "\\s*\\{([\\s\\S]*?)^\\}", Pattern.MULTILINE).matcher(sdl);
            assertTrue(block.find(), input + " missing in schema.graphqls");
            Matcher f = Pattern.compile("^\\s+(where[A-Z][A-Za-z0-9_]*)\\s*:", Pattern.MULTILINE).matcher(block.group(1));
            while (f.find()) offenders.add(input + "." + f.group(1));
        }
        assertTrue(offenders.isEmpty(), () -> "core filter fields shaped like generated where<Key>: " + offenders);
    }

    private static String schema() throws IOException
    {
        try (var in = GeneratedNameNamespaceArchitectureTest.class.getResourceAsStream("/graphql/schema.graphqls"))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
