package org.rapla.server.spring.document;

import org.junit.jupiter.api.Test;

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
}
