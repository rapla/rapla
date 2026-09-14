package org.rapla.server.spring.document;

import org.jsoup.nodes.Entities;

/**
 * PRD 097 D7a — what is left of the page shell: the minimal wrapper for BODY-ONLY templates
 * (doctype, charset, title — no CSS, no look; print CSS and default look are the
 * {@code rapla/print-css} / {@code rapla/base-css} partials the template opts into), and the
 * server-authored PREVIEW script the editor path injects. A full-HTML template never comes here.
 */
final class DocumentShell
{
    private DocumentShell() {}

    static String wrapFragment(String title, String sanitizedBody, String lang)
    {
        return "<!doctype html>\n"
                + "<html lang=\"" + escape(lang == null || lang.isBlank() ? "de" : lang) + "\">\n"
                + "<head>\n"
                + "<meta charset=\"utf-8\">\n"
                + "<title>" + escape(title) + "</title>\n"
                + "</head>\n"
                + "<body>\n"
                + sanitizedBody
                + "\n</body>\n</html>\n";
    }

    /**
     * Editor preview only: the click/submit handler that reports nav targets and form params to
     * the editor (the sandboxed srcdoc iframe cannot navigate). Inserted before the last
     * {@code </body>}; real pages stay script-free by construction (D6a).
     */
    static String injectPreviewScript(String page)
    {
        int at = page.lastIndexOf("</body>");
        return at < 0 ? page + PREVIEW_NAV_SCRIPT : page.substring(0, at) + PREVIEW_NAV_SCRIPT + page.substring(at);
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
}
