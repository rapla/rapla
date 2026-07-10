package org.rapla.server.spring.document;

import org.jsoup.nodes.Entities;

/**
 * PRD 097 D7 — the server-authored page shell that wraps the sanitized author body: doctype,
 * charset, title, and the {@code @media print} / {@code @page} rules that turn the page into a
 * vector PDF through the browser's own print dialog (D4 — no server PDF, no JS PDF library).
 *
 * <p><b>Script-free by construction.</b> D6a offered two ways to trigger printing under
 * {@code CSP: sandbox}: allow scripts back in behind a nonce, or use a script-free print
 * affordance. We take the second: the shell contains no script at all, so the response can carry
 * {@code script-src 'none'} and the sandbox needs no {@code allow-scripts}. Printing is the
 * browser's own action (Ctrl+P / menu), which is why the shell renders a print hint that is
 * itself hidden when printing.
 */
final class DocumentShell
{
    private DocumentShell() {}

    static String wrap(String title, String sanitizedBody)
    {
        return "<!doctype html>\n"
                + "<html lang=\"de\">\n"
                + "<head>\n"
                + "<meta charset=\"utf-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + "<title>" + escape(title) + "</title>\n"
                + "<style>\n" + SHELL_CSS + "</style>\n"
                + "</head>\n"
                + "<body>\n"
                + "<p class=\"rapla-print-hint\">Zum Drucken oder als PDF speichern: <kbd>Strg</kbd>+<kbd>P</kbd></p>\n"
                + "<main class=\"rapla-document\">\n"
                + sanitizedBody
                + "\n</main>\n"
                + "</body>\n</html>\n";
    }

    private static String escape(String value)
    {
        return Entities.escape(value == null ? "" : value);
    }

    private static final String SHELL_CSS = """
            :root { color-scheme: light; }
            body { margin: 0; padding: 1.5rem; font-family: system-ui, sans-serif; color: #000; background: #fff; }
            .rapla-document table { border-collapse: collapse; width: 100%; }
            .rapla-document th, .rapla-document td { border: 1px solid #999; padding: .25rem .5rem; text-align: left; }
            .rapla-print-hint { font-size: .875rem; color: #555; border: 1px dashed #bbb; padding: .5rem; }
            @media print {
              @page { margin: 2cm; }
              body { padding: 0; }
              .rapla-print-hint { display: none; }
            }
            """;
}
