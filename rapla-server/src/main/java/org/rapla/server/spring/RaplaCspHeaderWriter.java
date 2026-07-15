package org.rapla.server.spring;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.header.HeaderWriter;

/**
 * PRD 071 Phase 2: path-scoped Content-Security-Policy. A single global policy
 * cannot fit rapla's mixed surfaces, so the header is chosen per request path:
 *
 * <ul>
 *   <li>{@code /api/**} — JSON. Strict {@link CspPolicyBuilder#jsonApiPolicy()},
 *       <b>enforced</b>.</li>
 *   <li>{@code /rapla/**} — public calendar/iCal HTML pages that render
 *       user-controlled data. Strict {@link CspPolicyBuilder#serverPagePolicy()}
 *       ({@code default-src 'none'}), <b>enforced</b> — an injected script cannot
 *       run, killing the A6 reflected-XSS class.</li>
 *   <li>{@code /app/**} — the Angular SPA. The {@link CspPolicyBuilder#build} SPA
 *       policy, <b>enforced</b> (PRD 102 Phase 1). Side-effect-free: that policy
 *       carries NO {@code script-src}/{@code style-src} (the Angular {@code autoCsp}
 *       {@code <meta>} owns {@code script-src}; PRD 102 Phase 6), so Material/CDK
 *       inline styles and any inline bootstrap script are untouched. It enforces the
 *       non-script directives ({@code connect-src 'self'} exfil-confinement,
 *       {@code object-src}/{@code base-uri}/{@code frame-*}/{@code form-action}).</li>
 *   <li>everything else — the {@code /login} page (server-rendered, inline script) and
 *       the explorer tools ({@code /swagger-ui/**}, {@code /graphiql/**}, CDN-loaded) —
 *       the same SPA policy, <b>report-only</b>: they wait for their own enforce pass.</li>
 * </ul>
 */
public class RaplaCspHeaderWriter implements HeaderWriter
{
    private static final String ENFORCE = "Content-Security-Policy";
    private static final String REPORT_ONLY = "Content-Security-Policy-Report-Only";

    private final String spaPolicy;
    private final String apiPolicy = CspPolicyBuilder.jsonApiPolicy();
    private final String serverPagePolicy = CspPolicyBuilder.serverPagePolicy();
    /**
     * PRD 097 D6a — a rendered Mustache document is admin-authored HTML shown to other users.
     * It is served into an <b>opaque origin</b> ({@code sandbox} with no {@code allow-same-origin}),
     * so even if authored markup were hostile it holds no rapla origin and cannot read the session.
     * {@code script-src 'none'} is affordable because the shell is script-free by construction
     * (printing is the browser's own Ctrl+P), and {@code connect-src 'none'} means a document can
     * never call back into the API with the reader's credentials.
     * {@code style-src 'unsafe-inline'} stays: a document IS its inline layout CSS.
     *
     * <p>{@code allow-forms} + {@code form-action 'self'} (2026-07-15, PRD 097 § params): a
     * native GET form submitting to the document's own URL is the script-free way a document
     * carries filter controls (resource picker, date field) — the same gated {@code ?param=}
     * surface as a typed URL, §16-clean (a GET to self reads, never writes). This is far short
     * of Phase 9's write forms (cross-origin POST + capability): {@code 'self'} keeps every
     * submit on rapla's own origin, where the /api/documents/* gate rejects undeclared keys.
     */
    private final String documentPagePolicy = String.join("; ",
            "sandbox allow-forms",
            "default-src 'none'",
            "script-src 'none'",
            "connect-src 'none'",
            "style-src 'unsafe-inline'",
            "img-src 'self' data:",
            "font-src 'self' data:",
            "base-uri 'none'",
            "frame-ancestors 'none'",
            "form-action 'self'");

    public RaplaCspHeaderWriter(String spaPolicy)
    {
        this.spaPolicy = spaPolicy;
    }

    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response)
    {
        String path = pathWithinApplication(request);
        if (path.startsWith("/api/documents/"))
        {
            response.setHeader(ENFORCE, documentPagePolicy);
        }
        else if (path.startsWith("/api/"))
        {
            response.setHeader(ENFORCE, apiPolicy);
        }
        else if (path.startsWith("/rapla/"))
        {
            response.setHeader(ENFORCE, serverPagePolicy);
        }
        else if (path.startsWith("/app"))
        {
            // PRD 102 Phase 1: the SPA's non-script policy is enforced (no script-src/style-src in
            // it, so Material inline styles + inline bootstrap are untouched; this enforces
            // connect-src exfil-confinement + object-src/base-uri/frame-*/form-action).
            response.setHeader(ENFORCE, spaPolicy);
        }
        else
        {
            // /login (inline script) + explorers (CDN) stay report-only until their own enforce pass.
            response.setHeader(REPORT_ONLY, spaPolicy);
        }
    }

    private static String pathWithinApplication(HttpServletRequest request)
    {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx))
        {
            return uri.substring(ctx.length());
        }
        return uri;
    }
}
