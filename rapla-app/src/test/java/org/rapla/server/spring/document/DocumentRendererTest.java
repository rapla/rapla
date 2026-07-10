package org.rapla.server.spring.document;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 097 Phase 1 — tier-1 unit tests for the JMustache engine wrapper.
 * The two guarantees the whole PRD rests on: no expression evaluation (SSTI-inert,
 * because logic-less) and HTML auto-escaping of interpolated query data.
 */
class DocumentRendererTest
{
    private final DocumentRenderer renderer = new DocumentRenderer();

    @Test
    void injectionPayloadRendersAsInertEmptyText()
    {
        // The classic SpEL/OGNL template-injection probes. A logic-less engine has no
        // expression language: inside {{ }} these are just missing key lookups and render empty.
        String html = renderer.render("A{{T(java.lang.Runtime).getRuntime().exec('id')}}B C{{7*7}}D", Map.of());

        assertEquals("AB CD", html, "no expression is evaluated; unknown keys render as empty text");
        assertFalse(html.contains("49"), "arithmetic is not an engine capability");
        assertFalse(html.contains("Runtime"), "the payload is never echoed back either");
    }

    @Test
    void foreignExpressionLanguagesArePassedThroughAsLiteralText()
    {
        // Nothing but {{ }} is a delimiter — proof that no second EL (SpEL ${}, JSF #{}) is wired in.
        assertEquals("A${7*7}B#{7*7}C", renderer.render("A${7*7}B#{7*7}C", Map.of()));
    }

    @Test
    void interpolatedValuesAreHtmlEscaped()
    {
        Map<String, Object> data = Map.of("name", "<script>alert('xss')</script>");
        String html = renderer.render("<td>{{name}}</td>", data);

        assertEquals("<td>&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;</td>", html);
        assertFalse(html.contains("<script>"), "query-derived data can never open a tag");
    }

    @Test
    void nullAndMissingValuesRenderEmptyNotAnError()
    {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("present", "x");
        data.put("explicitNull", null);

        assertEquals("[x][][]", renderer.render("[{{present}}][{{explicitNull}}][{{absent}}]", data),
                "Mustache semantics: absent and null both render empty (a missing field may be intentional)");
    }

    @Test
    void listsBecomeSectionsAndBooleansBecomeConditionals()
    {
        Map<String, Object> data = Map.of(
                "rows", List.of(Map.of("n", "a"), Map.of("n", "b")),
                "show", Boolean.TRUE,
                "hide", Boolean.FALSE);

        assertEquals("<i>a</i><i>b</i>", renderer.render("{{#rows}}<i>{{n}}</i>{{/rows}}", data));
        assertEquals("yes", renderer.render("{{#show}}yes{{/show}}{{#hide}}no{{/hide}}", data));
        assertEquals("inverted", renderer.render("{{^hide}}inverted{{/hide}}", data));
    }

    @Test
    void validateReportsParseErrorsWithLineNumber()
    {
        assertTrue(renderer.validate("<p>{{#open}}</p>").isPresent(),
                "an unclosed section is a parse error, not a silent render");

        Optional<DocumentRenderer.TemplateError> error = renderer.validate("line1\nline2 {{#a}}{{/b}}\n");
        assertTrue(error.isPresent());
        assertEquals(2, error.get().line(), "the parse error carries the offending line");

        assertTrue(renderer.validate("<p>{{#rows}}{{x}}{{/rows}}</p>").isEmpty(), "valid template validates clean");
    }

    @Test
    void handlebarsHelperSyntaxIsRejectedByTheRealEngine()
    {
        // "restrict to JMustache" is enforced by the engine, not by the editor's highlighting.
        // {{#if x}} parses as a section named "if x" -> JMustache rejects the space.
        assertTrue(renderer.validate("{{#if x}}y{{/if}}").isPresent(),
                "Handlebars-only syntax must not silently compile");
    }

    @Test
    void compiledTemplatesAreMemoizedByContentHash()
    {
        String template = "{{a}}";
        renderer.render(template, Map.of("a", "1"));
        int afterFirst = renderer.cacheSize();
        renderer.render(template, Map.of("a", "2"));

        assertEquals(afterFirst, renderer.cacheSize(), "same body -> same compiled template");
        renderer.render("{{b}}", Map.of("b", "1"));
        assertEquals(afterFirst + 1, renderer.cacheSize(), "a different body compiles once more");
    }
}
