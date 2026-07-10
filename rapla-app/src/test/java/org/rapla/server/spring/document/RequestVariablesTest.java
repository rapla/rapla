package org.rapla.server.spring.document;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PRD 097 OQ9 — the flat request-parameter contract. A document's URL must be able to express the
 * nested GraphQL inputs its view declares, or a per-resource week plan (
 * {@code ?filter.allocatableIdsIn=<id>}) cannot be linked to at all.
 *
 * <p>Two rules, both chosen because a URL already has them: a dot nests, and a repeated key is a
 * list. No comma-splitting — resource names contain commas, ids may one day too.
 */
class RequestVariablesTest
{
    @Test
    void aPlainParameterIsAScalarVariable()
    {
        assertEquals(Map.of("eventId", "abc"), RequestVariables.expand(Map.of("eventId", List.of("abc"))));
    }

    @Test
    void aDotNestsIntoAnInputObject()
    {
        assertEquals(Map.of("filter", Map.of("from", "2026-06-15T00:00:00")),
                RequestVariables.expand(Map.of("filter.from", List.of("2026-06-15T00:00:00"))));
    }

    @Test
    void siblingsMergeIntoTheSameInputObject()
    {
        Map<String, Object> vars = RequestVariables.expand(new java.util.LinkedHashMap<>(Map.of(
                "filter.from", List.of("A"), "filter.to", List.of("B"))));
        assertEquals(Map.of("from", "A", "to", "B"), vars.get("filter"));
    }

    @Test
    void aRepeatedKeyIsAList()
    {
        assertEquals(Map.of("filter", Map.of("allocatableIdsIn", List.of("r1", "r2"))),
                RequestVariables.expand(Map.of("filter.allocatableIdsIn", List.of("r1", "r2"))));
    }

    @Test
    void aSingleValuedListFieldStaysAScalarAndGraphQlCoercesIt()
    {
        // GraphQL input coercion wraps a single value into a list, so ?filter.allocatableIdsIn=r1
        // binds to [ID!] without the caller needing to repeat the parameter.
        assertEquals(Map.of("filter", Map.of("allocatableIdsIn", "r1")),
                RequestVariables.expand(Map.of("filter.allocatableIdsIn", List.of("r1"))));
    }

    @Test
    void deepNestingWorksForFiltersInsideFilters()
    {
        assertEquals(Map.of("filter", Map.of("allocatableMatching", Map.of("idIn", "r1"))),
                RequestVariables.expand(Map.of("filter.allocatableMatching.idIn", List.of("r1"))));
    }

    @Test
    void aValueThatCollidesWithAnExistingObjectDoesNotClobberIt()
    {
        Map<String, Object> vars = RequestVariables.expand(new java.util.LinkedHashMap<>(Map.of(
                "filter.from", List.of("A"), "filter", List.of("nonsense"))));
        assertEquals(Map.of("from", "A"), vars.get("filter"));
    }

    @Test
    void emptyInputYieldsNoVariables()
    {
        assertEquals(Map.of(), RequestVariables.expand(Map.of()));
        assertEquals(Map.of(), RequestVariables.expand(null));
    }
}
