package org.rapla.server.spring.document;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 097 D6 — the author's template body is static HTML: {@code <script>}, event-handler
 * attributes and {@code javascript:} URLs are stripped. CSS survives (it cannot execute).
 *
 * <p>Sanitization runs on the <em>rendered</em> body, not on the raw template: an HTML parser
 * fed a raw template would foster-parent stray Mustache tags out of tables
 * ({@code <table>{{#rows}}<tr>} → the section tag is relocated before the table, silently
 * breaking the document). After rendering there are no Mustache tags left, and query data is
 * already HTML-escaped, so it cannot introduce elements.
 */
class DocumentSanitizerTest
{
    @Test
    void scriptElementsAreRemoved()
    {
        String out = DocumentSanitizer.sanitizeFragment("<p>ok</p><script>alert(1)</script><p>x</p>");
        assertFalse(out.contains("script"), "no <script> survives");
        assertTrue(out.contains("<p>ok</p>"));
        assertTrue(out.contains("<p>x</p>"));
    }

    @Test
    void eventHandlerAttributesAreRemoved()
    {
        String out = DocumentSanitizer.sanitizeFragment("<div onclick=\"steal()\" ONMOUSEOVER='x' id=\"keep\">t</div>");
        assertFalse(out.toLowerCase().contains("onclick"));
        assertFalse(out.toLowerCase().contains("onmouseover"), "handler detection is case-insensitive");
        assertTrue(out.contains("id=\"keep\""), "harmless attributes stay");
    }

    @Test
    void javascriptUrlsAreRemoved()
    {
        String out = DocumentSanitizer.sanitizeFragment(
                "<a href=\"javascript:alert(1)\">a</a><a href=\"  JaVaScRiPt: x\">b</a><a href=\"/ok\">c</a>");
        assertFalse(out.toLowerCase().contains("javascript"), "obfuscated scheme is caught too");
        assertTrue(out.contains("href=\"/ok\""), "ordinary links survive — the link navigation between documents");
    }

    @Test
    void embeddingElementsAreRemoved()
    {
        String out = DocumentSanitizer.sanitizeFragment(
                "<iframe src=\"//evil\"></iframe><object></object><embed><base href=\"//evil\">ok");
        assertFalse(out.contains("iframe"));
        assertFalse(out.contains("object"));
        assertFalse(out.contains("embed"));
        assertFalse(out.contains("base"), "<base> would repoint every relative URL in the document");
        assertTrue(out.contains("ok"));
    }

    @Test
    void cssSurvivesBecauseItCannotExecute()
    {
        String out = DocumentSanitizer.sanitizeFragment("<style>@page { margin: 2cm }</style><p style=\"color:red\">x</p>");
        assertTrue(out.contains("@page"), "print CSS is the point of the feature");
        assertTrue(out.contains("style=\"color:red\""));
    }

    @Test
    void tableStructureIsPreserved()
    {
        // The rendered body of a grouped document. A parser that foster-parents would move
        // content out of the table; this asserts the row survives inside it.
        String out = DocumentSanitizer.sanitizeFragment("<table><tr><td>9:00</td><td>Mathe</td></tr></table>");
        assertTrue(out.replaceAll("\\s+", "").contains("<tr><td>9:00</td><td>Mathe</td></tr>"),
                "rows stay inside the table");
    }

    @Test
    void escapedDataCannotIntroduceElements()
    {
        // What Mustache produces from a hostile field value: already escaped, so the sanitizer
        // sees text, not a tag — and must not "helpfully" unescape it.
        String out = DocumentSanitizer.sanitizeFragment("<td>&lt;script&gt;alert(1)&lt;/script&gt;</td>");
        assertTrue(out.contains("&lt;script&gt;"), "escaped text stays escaped text");
        assertFalse(out.contains("<script>"));
    }

    // PRD 097 D7a — the author owns the whole page: the head passes through, filtered with the
    // same rules as the body (executable / repointing parts go, everything else stays put).
    @Test
    void fullDocumentKeepsTheAuthorsHeadMinusExecutableParts()
    {
        DocumentSanitizer.Sanitized out = DocumentSanitizer.sanitize("""
                <!doctype html><html lang="fr"><head>
                <meta charset="utf-8"><meta name="description" content="d">
                <meta http-equiv="refresh" content="0;url=//evil">
                <base href="//evil/"><link rel="icon" href="/fav.ico"><link rel="import" href="//evil/x">
                <script>alert(1)</script>
                <title>Leihschein</title><style>@page { size: A4 }</style>
                </head><body><p id="k">ok</p></body></html>""", false);
        assertTrue(out.wholeDocument());
        String html = out.html();
        assertTrue(html.stripLeading().toLowerCase().startsWith("<!doctype html>"), html);
        assertTrue(html.contains("<html lang=\"fr\">"), html);
        assertTrue(html.contains("<meta charset=\"utf-8\">"), html);
        assertTrue(html.contains("<meta name=\"description\""), html);
        assertTrue(html.contains("<link rel=\"icon\""), html);
        assertTrue(html.contains("<title>Leihschein</title>"), html);
        assertTrue(html.indexOf("<style>@page { size: A4 }</style>") < html.indexOf("<body>"),
                "author CSS stays in the head, not moved: " + html);
        assertTrue(html.contains("<p id=\"k\">ok</p>"), html);
        assertFalse(html.contains("http-equiv"), html);
        assertFalse(html.contains("<base"), html);
        assertFalse(html.contains("rel=\"import\""), html);
        assertFalse(html.contains("<script"), html);
    }

    @Test
    void aBodyOnlyTemplateIsAFragment()
    {
        DocumentSanitizer.Sanitized out = DocumentSanitizer.sanitize("<p>ok</p>", false);
        assertFalse(out.wholeDocument());
        assertTrue(out.html().contains("<p>ok</p>"));
        assertFalse(out.html().contains("<html"), out.html());
    }

    // PRD 097 D6d — the sanitizer reports what it removed, so the editor preview can warn.
    @Test
    void removalsAreReported()
    {
        DocumentSanitizer.Sanitized out = DocumentSanitizer.sanitize(
                "<script>a()</script><p onclick=\"x()\" id=\"k\">ok</p><a href=\"javascript:y()\">l</a>"
                + "<base href=\"//evil/\"><iframe></iframe>", false);
        assertEquals(List.of("script", "onclick", "href=javascript:", "base", "iframe"),
                out.removed().stream().sorted(java.util.Comparator.comparingInt(
                        List.of("script", "onclick", "href=javascript:", "base", "iframe")::indexOf)).toList(),
                out.removed().toString());
    }

    @Test
    void cleanHtmlReportsNoRemovals()
    {
        assertTrue(DocumentSanitizer.sanitize("<p style=\"color:red\" id=\"k\">ok</p>", false).removed().isEmpty());
        assertTrue(DocumentSanitizer.sanitize("<script>a()</script>", true).removed().isEmpty(),
                "a kept script is not a removal");
    }

    // PRD 097 D6c (2) — with author scripts allowed, <script> stays; every other removal still runs.
    @Test
    void scriptsAreKeptOnlyWhenAllowed()
    {
        String html = "<script>window.print()</script><base href=\"//evil/\"><p onclick=\"x()\">ok</p>";
        DocumentSanitizer.Sanitized allowed = DocumentSanitizer.sanitize(html, true);
        assertTrue(allowed.html().contains("<script>window.print()</script>"), allowed.html());
        assertFalse(allowed.html().contains("base"), "the non-script removals still run");
        assertFalse(allowed.html().contains("onclick"), "attribute stripping still runs");

        DocumentSanitizer.Sanitized denied = DocumentSanitizer.sanitize(html, false);
        assertFalse(denied.html().contains("script"), denied.html());
    }
}
