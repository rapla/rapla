package org.rapla.server.spring.document;

import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * PRD 097 D6 — strip everything executable from the author's document body, so only the
 * server-authored shell may carry script. Second layer under the response CSP (D6a / PRD 102):
 * even a bypass lands in an opaque origin with {@code connect-src 'none'}.
 *
 * <p><b>Runs on the rendered body, not the raw template.</b> An HTML parser fed a raw template
 * foster-parents stray text out of tables — {@code <table>{{#rows}}<tr>…} would silently move the
 * section tag before the table and break the document. After rendering there are no Mustache tags
 * left, and every interpolated value is already HTML-escaped, so data cannot introduce elements.
 */
final class DocumentSanitizer
{
    /** Elements that execute, navigate, or repoint the document. */
    private static final String FORBIDDEN_ELEMENTS = "script, iframe, object, embed, base, meta[http-equiv], link[rel=import]";

    /** D6c (2) — the same list minus {@code script}: everything else still goes. */
    private static final String FORBIDDEN_ELEMENTS_WITH_SCRIPTS = "iframe, object, embed, base, meta[http-equiv], link[rel=import]";

    /** Attributes whose value is a URL and could carry a {@code javascript:} scheme. */
    private static final List<String> URL_ATTRIBUTES = List.of("href", "src", "action", "formaction", "xlink:href");

    /**
     * PRD 097 D7a — the sanitized page. {@code wholeDocument}: the author wrote a complete HTML
     * file and {@code html} is that page (doctype, head, body) with the executable parts removed;
     * otherwise {@code html} is the body fragment the caller wraps minimally. {@code removed} (D6d)
     * labels every removal — element name, attribute name, or {@code attr=scheme:} for a blocked
     * URL — in document order, so the editor preview can warn the author.
     */
    record Sanitized(String html, boolean wholeDocument, List<String> removed) {}

    private DocumentSanitizer() {}

    /** Body-only sanitization, scripts never allowed. */
    static String sanitizeFragment(String renderedHtml)
    {
        return sanitize(renderedHtml, false).html();
    }

    /**
     * Sanitize a rendered template. A complete HTML document is accepted (D6c/D7a): the author's
     * head passes through and gets the SAME forbidden-element/attribute pass as the body —
     * {@code <base>} repoints every relative URL, {@code meta http-equiv} redirects, scripts
     * execute; everything else ({@code <meta name>}, {@code <link>}, {@code <title>},
     * {@code <style>}) is the author's business.
     *
     * @param allowScripts D6c (2) — keep author {@code <script>} elements. Every other removal
     *                     still runs; the caller decides this from the yml switch AND the
     *                     document's visibility, never from the template.
     */
    static Sanitized sanitize(String renderedHtml, boolean allowScripts)
    {
        String html = renderedHtml == null ? "" : renderedHtml;
        boolean wholeDocument = html.stripLeading().regionMatches(true, 0, "<!doctype", 0, 9)
                || html.stripLeading().regionMatches(true, 0, "<html", 0, 5);
        Document doc = wholeDocument ? Jsoup.parse(html) : Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);

        List<String> removed = new java.util.ArrayList<>();
        for (Element forbidden : doc.select(allowScripts ? FORBIDDEN_ELEMENTS_WITH_SCRIPTS : FORBIDDEN_ELEMENTS))
        {
            removed.add(forbidden.tagName());
            forbidden.remove();
        }

        for (Element element : doc.getAllElements())
        {
            for (Attribute attribute : element.attributes().asList())
            {
                String key = attribute.getKey().toLowerCase(Locale.ROOT);
                if (key.startsWith("on") || key.equals("srcdoc"))
                {
                    element.removeAttr(attribute.getKey());
                    removed.add(key);
                }
                else if (URL_ATTRIBUTES.contains(key) && isScriptUrl(attribute.getValue()))
                {
                    element.removeAttr(attribute.getKey());
                    String scheme = attribute.getValue().strip().toLowerCase(Locale.ROOT).replaceAll("\\s", "");
                    removed.add(key + "=" + scheme.substring(0, scheme.indexOf(':') + 1));
                }
            }
        }
        return new Sanitized(wholeDocument ? doc.outerHtml() : doc.body().html(), wholeDocument, List.copyOf(removed));
    }

    /** {@code javascript:} / {@code data:text/html} / {@code vbscript:}, whitespace- and case-obfuscated. */
    private static boolean isScriptUrl(String value)
    {
        if (value == null) return false;
        String normalized = value.replaceAll("[\\s\\u0000-\\u001f]", "").toLowerCase(Locale.ROOT);
        return normalized.startsWith("javascript:")
                || normalized.startsWith("vbscript:")
                || normalized.startsWith("data:text/html");
    }
}
