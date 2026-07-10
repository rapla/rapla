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

    /** Attributes whose value is a URL and could carry a {@code javascript:} scheme. */
    private static final List<String> URL_ATTRIBUTES = List.of("href", "src", "action", "formaction", "xlink:href");

    private DocumentSanitizer() {}

    /** Sanitize a rendered body fragment; returns the cleaned fragment (no html/head wrapper). */
    static String sanitizeFragment(String renderedHtml)
    {
        Document doc = Jsoup.parseBodyFragment(renderedHtml == null ? "" : renderedHtml);
        doc.outputSettings().prettyPrint(false);
        doc.select(FORBIDDEN_ELEMENTS).remove();

        for (Element element : doc.getAllElements())
        {
            for (Attribute attribute : element.attributes().asList())
            {
                String key = attribute.getKey().toLowerCase(Locale.ROOT);
                if (key.startsWith("on") || key.equals("srcdoc"))
                {
                    element.removeAttr(attribute.getKey());
                }
                else if (URL_ATTRIBUTES.contains(key) && isScriptUrl(attribute.getValue()))
                {
                    element.removeAttr(attribute.getKey());
                }
            }
        }
        return doc.body().html();
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
