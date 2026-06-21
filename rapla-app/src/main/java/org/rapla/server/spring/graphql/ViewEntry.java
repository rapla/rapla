package org.rapla.server.spring.graphql;

import java.util.List;

/** PRD 074 — catalog entry for a view (BUILTIN or CUSTOM). */
public record ViewEntry(
        String name,
        String title,
        String queryText,
        boolean builtin,
        boolean isPublic,
        List<String> groups,
        boolean valid,
        List<String> invalidReason,
        String defaultVariables)
{
    static ViewEntry builtin(String name, String title, String queryText)
    {
        return new ViewEntry(name, title, queryText, true, true, List.of(), true, List.of(), null);
    }
}
