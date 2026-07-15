package org.rapla.server.spring.document;

import org.jsoup.nodes.Entities;

/**
 * PRD 097 D7 — the server-authored page shell that wraps the sanitized author body: doctype,
 * charset, title, and the {@code @media print} / {@code @page} rules that turn the page into a
 * vector PDF through the browser's own print dialog (D4 — no server PDF, no JS PDF library).
 *
 * <p><b>Script-free by construction.</b> D6a offered two ways to trigger printing under
 * {@code CSP: sandbox}: allow scripts back in behind a nonce, or use a script-free print
 * affordance. We take the second: on real document pages the shell carries no script, so the
 * response keeps {@code script-src 'none'} (the ONE exception is the server-authored PREVIEW
 * script — editor iframe only, never real pages). Printing is the browser's own action
 * (Ctrl+P / menu), announced by the {@code rapla/print-hint} builtin PARTIAL — template content
 * since 2026-07-15 (affordances are the author's; the shell keeps invariants + fallback CSS).
 */
final class DocumentShell
{
    private DocumentShell() {}

    static String wrap(String title, String sanitizedBody)
    {
        return wrap(title, sanitizedBody, false);
    }

    /**
     * Preview variant (2026-07-15): in the editor's sandboxed {@code srcdoc} iframe ANY href —
     * even {@code #} — resolves against the PARENT page's URL and navigating there dead-ends in
     * a browser error page ("localhost refused to connect"; verified in Chrome). So the preview
     * shell carries the ONE script of the document system: it {@code preventDefault()}s every
     * link click, and for links carrying the {@code data-nav-*} contract it postMessages the
     * target window to the editor, which writes it into the vars box and re-previews ("full
     * transparency"). Requires {@code sandbox="allow-scripts"} on the editor's iframe — still an
     * opaque origin with no cookies, and author scripts are still stripped by the sanitizer;
     * this script is server-authored, never author content.
     */
    static String wrap(String title, String sanitizedBody, boolean previewMode)
    {
        return "<!doctype html>\n"
                + "<html lang=\"de\">\n"
                + "<head>\n"
                + "<meta charset=\"utf-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + "<title>" + escape(title) + "</title>\n"
                + "<style>\n" + SHELL_CSS + "</style>\n"
                + (previewMode ? PREVIEW_NAV_SCRIPT : "")
                + "</head>\n"
                + "<body>\n"
                + "<main class=\"rapla-document\">\n"
                + sanitizedBody
                + "\n</main>\n"
                + "</body>\n</html>\n";
    }

    private static String escape(String value)
    {
        return Entities.escape(value == null ? "" : value);
    }

    /**
     * The preview-only click handler. Every link click is prevented (no navigation can succeed
     * from the srcdoc iframe); nav links carrying the {@code data-nav-*} contract report their
     * target window to the editor. The message shape is validated editor-side.
     */
    private static final String PREVIEW_NAV_SCRIPT = """
            <script>
            document.addEventListener('click', function (e) {
              var a = e.target && e.target.closest ? e.target.closest('a') : null;
              if (!a) return;
              e.preventDefault();
              var msg = null;
              if (a.hasAttribute('data-nav-today')) msg = { type: 'rapla-preview-nav', today: true };
              else if (a.getAttribute('data-nav-from')) msg = { type: 'rapla-preview-nav',
                  from: a.getAttribute('data-nav-from'), to: a.getAttribute('data-nav-to') };
              else if (/^https?:\\/\\//i.test(a.getAttribute('href') || ''))
                  msg = { type: 'rapla-preview-open', href: a.getAttribute('href') };
              if (msg && window.parent !== window) window.parent.postMessage(msg, '*');
            });
            document.addEventListener('submit', function (e) {
              e.preventDefault();
              var params = {};
              new FormData(e.target).forEach(function (v, k) { params[k] = String(v); });
              if (window.parent !== window)
                  window.parent.postMessage({ type: 'rapla-preview-params', params: params }, '*');
            });
            </script>
            """;

    private static final String SHELL_CSS = """
            :root { color-scheme: light; }
            body { margin: 0; padding: 1.5rem; font-family: system-ui, sans-serif; color: #000; background: #fff; }
            .rapla-document table { border-collapse: collapse; width: 100%; }
            .rapla-document th, .rapla-document td { border: 1px solid #999; padding: .25rem .5rem; text-align: left; }
            /* Offered default look for the {{> rapla/print-hint}} partial — template content
               since 2026-07-15, no longer shell chrome. */
            .rapla-print-hint { font-size: .875rem; color: #555; border: 1px dashed #bbb; padding: .5rem; }
            /* Offered default look for the template-placed {{#nav}} block — override freely. */
            .rapla-nav { display: flex; align-items: center; gap: .4rem; margin: .75rem 0 1rem; }
            .rapla-nav a { text-decoration: none; color: #1a56b0; border: 1px solid #ccd3db;
                           border-radius: 6px; padding: .25rem .7rem; background: #f6f8fa; }
            .rapla-nav a:hover { background: #e9eef4; }
            .rapla-nav-range { margin-left: .5rem; color: #333; font-weight: 600; }
            @media print {
              @page { margin: 2cm; }
              body { padding: 0; }
              .rapla-print-hint { display: none; }
              .rapla-nav { display: none; }
            }
            """;
}
