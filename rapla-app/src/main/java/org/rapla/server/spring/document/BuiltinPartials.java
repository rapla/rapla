package org.rapla.server.spring.document;

import java.util.Map;

/**
 * PRD 097 partials, step 1 (2026-07-15) — server-shipped Mustache partials, referenced from
 * templates as {@code {{> rapla/name}}} and resolved by {@link DocumentRenderer}'s template
 * loader. BUILTIN ONLY for now: the sources are static and read-only, contain no partial
 * references themselves (so no include cycles are possible), and the {@code rapla/} prefix is
 * reserved. Custom (admin-saved) partials — with their editor mode, error attribution and
 * dependency tracking — are a deliberate later step.
 */
final class BuiltinPartials
{
    private BuiltinPartials() {}

    /**
     * The shared window-navigation block (template-placed nav, 2026-07-15): renders the
     * {@code nav} model entry — script-free relative {@code ?date=} links; {@code .rapla-nav}
     * in the shell CSS is the offered default look. Templates that want a different nav simply
     * do not reference the partial.
     */
    /*
     * The data-nav-* attributes are the PREVIEW contract: on real documents they render empty
     * (the href navigates); in the editor preview the shell's script intercepts the click and
     * postMessages the target window to the editor. Custom nav markup that sets the same
     * attributes gets working preview navigation too.
     */
    private static final String NAV = "{{#nav}}<nav class=\"rapla-nav\">"
            + "<a href=\"{{prevUrl}}\" data-nav-from=\"{{prevFrom}}\" data-nav-to=\"{{prevTo}}\" title=\"Zurück\">&#9664;</a>"
            + "<a href=\"{{todayUrl}}\" data-nav-today=\"1\">Heute</a>"
            + "<a href=\"{{nextUrl}}\" data-nav-from=\"{{nextFrom}}\" data-nav-to=\"{{nextTo}}\" title=\"Weiter\">&#9654;</a>"
            + "<span class=\"rapla-nav-range\">{{label}}</span></nav>{{/nav}}\n";

    /**
     * The screen-only print affordance (moved OUT of the shell 2026-07-15 — an affordance is
     * template content, not chrome: signage documents don't want it, and it was the last
     * shell-owned markup). {@code .rapla-print-hint} styling incl. the print-time hide stays in
     * the shell CSS as the offered default.
     */
    private static final String PRINT_HINT = "<p class=\"rapla-print-hint\">Zum Drucken oder als"
            + " PDF speichern: <kbd>Strg</kbd>+<kbd>P</kbd></p>\n";

    static final Map<String, String> SOURCES = Map.of(
            "rapla/nav", NAV,
            "rapla/print-hint", PRINT_HINT);

    /** The partial's source, or null — the loader and the validator both resolve through here. */
    static String find(String name)
    {
        return SOURCES.get(name);
    }
}
