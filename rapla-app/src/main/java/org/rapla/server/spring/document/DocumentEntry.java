package org.rapla.server.spring.document;

import java.util.List;

/**
 * PRD 097 OQ1-A — a stored document: a named pairing of a data source (a stored GraphQL view,
 * by name) and a presentation (a Mustache template), with its own visibility.
 *
 * <p>{@code valid}/{@code invalidReason} are recomputed at read time (never stored): a document
 * whose template stopped parsing, or whose referenced view was renamed away, is surfaced as
 * invalid — never silently broken, never auto-deleted (the `ViewEntry.invalidReason` pattern).
 */
public record DocumentEntry(
        String name,
        String viewName,
        String template,
        boolean isPublic,
        List<String> groups,
        String defaultVariables,
        boolean valid,
        List<String> invalidReason)
{
}
