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
    private static final String NAV = "{{#nav}}<style>"
            + ".rapla-nav { display: flex; align-items: center; gap: .4rem; margin: .75rem 0 1rem; }"
            + " .rapla-nav a { text-decoration: none; color: #1a56b0; border: 1px solid #ccd3db;"
            + " border-radius: 6px; padding: .25rem .7rem; background: #f6f8fa; }"
            + " .rapla-nav a:hover { background: #e9eef4; }"
            + " .rapla-nav-range { margin-left: .5rem; color: #333; font-weight: 600; }"
            + " @media print { .rapla-nav { display: none; } }</style>"
            + "<nav class=\"rapla-nav\">"
            + "<a href=\"{{prevUrl}}\" data-nav-from=\"{{prevFrom}}\" data-nav-to=\"{{prevTo}}\" title=\"Zurück\">&#9664;</a>"
            + "<a href=\"{{todayUrl}}\" data-nav-today=\"1\">Heute</a>"
            + "<a href=\"{{nextUrl}}\" data-nav-from=\"{{nextFrom}}\" data-nav-to=\"{{nextTo}}\" title=\"Weiter\">&#9654;</a>"
            + "<span class=\"rapla-nav-range\">{{label}}</span></nav>{{/nav}}\n";

    /**
     * The screen-only print affordance. Self-contained since D7a: it carries its own look and its
     * print-time hide, so a template needs no other partial for it to behave.
     */
    private static final String PRINT_HINT = "<style>.rapla-print-hint { font-size: .875rem; color: #555;"
            + " border: 1px dashed #bbb; padding: .5rem; }"
            + " @media print { .rapla-print-hint { display: none; } }</style>"
            + "<p class=\"rapla-print-hint\">Zum Drucken oder als"
            + " PDF speichern: <kbd>Strg</kbd>+<kbd>P</kbd></p>\n";

    /** D7a — the former shell print rules, offered: 2 cm page margin, no body padding on paper. */
    private static final String PRINT_CSS = "<style>@media print { @page { margin: 2cm; } body { padding: 0; } }</style>\n";

    /** D7a — the former shell default look, offered: light scheme, base font, bordered tables. */
    private static final String BASE_CSS = "<style>:root { color-scheme: light; }"
            + " body { margin: 0; padding: 1.5rem; font-family: system-ui, sans-serif; color: #000; background: #fff; }"
            + " table { border-collapse: collapse; width: 100%; }"
            + " th, td { border: 1px solid #999; padding: .25rem .5rem; text-align: left; vertical-align: top; }</style>\n";

    static final Map<String, String> SOURCES = Map.of(
            "rapla/nav", NAV,
            "rapla/print-hint", PRINT_HINT,
            "rapla/print-css", PRINT_CSS,
            "rapla/base-css", BASE_CSS);

    /** The partial's source, or null — the loader and the validator both resolve through here. */
    static String find(String name)
    {
        return SOURCES.get(name);
    }
}
