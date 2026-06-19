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
 *   <li>everything else (the Angular SPA at {@code /app}, the {@code /login}
 *       page) — the {@link CspPolicyBuilder#build} SPA policy, <b>report-only</b>:
 *       Material/CDK inline styles and the login page's inline script would break
 *       under enforce, so these wait for the report-only walk.</li>
 * </ul>
 *
 * <p>The explorer tools ({@code /swagger-ui/**}, {@code /graphiql/**}) load from a
 * CDN and are handled separately; they get the report-only fall-through here, which
 * never blocks.
 */
public class RaplaCspHeaderWriter implements HeaderWriter
{
    private static final String ENFORCE = "Content-Security-Policy";
    private static final String REPORT_ONLY = "Content-Security-Policy-Report-Only";

    private final String spaReportOnlyPolicy;
    private final String apiPolicy = CspPolicyBuilder.jsonApiPolicy();
    private final String serverPagePolicy = CspPolicyBuilder.serverPagePolicy();

    public RaplaCspHeaderWriter(String spaReportOnlyPolicy)
    {
        this.spaReportOnlyPolicy = spaReportOnlyPolicy;
    }

    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response)
    {
        String path = pathWithinApplication(request);
        if (path.startsWith("/api/"))
        {
            response.setHeader(ENFORCE, apiPolicy);
        }
        else if (path.startsWith("/rapla/"))
        {
            response.setHeader(ENFORCE, serverPagePolicy);
        }
        else
        {
            response.setHeader(REPORT_ONLY, spaReportOnlyPolicy);
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
