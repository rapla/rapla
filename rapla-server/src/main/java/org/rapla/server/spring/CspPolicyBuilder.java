package org.rapla.server.spring;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Builds the rapla Content-Security-Policy (PRD 071 Phase 2). Base policy is
 * deployment-independent ({@code 'self'} everywhere, no inline/eval, no framing);
 * {@code connect-src} is extended with the origins the SPA actually talks to —
 * derived from the configured external OAuth IdP(s) so a deployment needs ZERO
 * CSP-specific config (the IdP base-url is already set for OAuth to work at all).
 *
 * <p>Shipped first as {@code Content-Security-Policy-Report-Only} (non-breaking);
 * {@code style-src 'self'} will report Material/CDK inline styles until
 * {@code autoCsp} is enabled on the Angular build.
 */
public final class CspPolicyBuilder
{
    private CspPolicyBuilder()
    {
    }

    /**
     * @param extraConnectSrc URLs (IdP issuer/token/jwks endpoints) whose ORIGIN is
     *                        added to {@code connect-src}; null/blank/malformed ignored
     */
    public static String build(Collection<String> extraConnectSrc)
    {
        Set<String> connect = new LinkedHashSet<>();
        connect.add("'self'");
        if (extraConnectSrc != null)
        {
            for (String url : extraConnectSrc)
            {
                String origin = originOf(url);
                if (origin != null)
                {
                    connect.add(origin);
                }
            }
        }
        // NOTE: no script-src / default-src here — the Angular build (autoCsp) owns
        // script-src via a <meta> CSP (strict-dynamic + per-build hashes), shipped in
        // the bundle so dev == prod. This header carries the directives autoCsp does
        // NOT manage; a default-src here would re-impose a script policy and conflict
        // with the autoCsp loader.
        return String.join("; ",
                "style-src 'self'",
                "img-src 'self' data:",
                "font-src 'self'",
                "connect-src " + String.join(" ", connect),
                "object-src 'none'",
                // base-uri 'self' (NOT 'none'): the Angular SPA ships <base href="/app/">,
                // which 'none' blocks. Confirmed in the 2026-06-19 report-only walk.
                "base-uri 'self'",
                "frame-ancestors 'none'",
                "frame-src 'none'",
                "form-action 'self'");
    }

    /**
     * Strict ENFORCED policy for the server-rendered HTML pages under {@code /rapla/**}
     * (calendar + iCal landing pages). They render user-controlled data (resource /
     * event names) but run NO scripts of their own, so {@code default-src 'none'} makes
     * an injected {@code <script>} structurally unable to execute — the A6 reflected-XSS
     * class dies here. {@code style-src 'self' 'unsafe-inline'} covers the external
     * {@code calendar.css}/{@code default.css} plus the inline {@code style=} attributes
     * ({@code AbstractHTMLCalendarPage}); {@code form-action 'self'} the GET filter form;
     * {@code img-src} the favicon and per-cell colours.
     */
    public static String serverPagePolicy()
    {
        return String.join("; ",
                "default-src 'none'",
                "style-src 'self' 'unsafe-inline'",
                "img-src 'self' data:",
                "font-src 'self'",
                "form-action 'self'",
                "base-uri 'none'",
                "frame-ancestors 'none'");
    }

    /**
     * Strict ENFORCED policy for the JSON API under {@code /api/**}. A JSON response
     * loads no sub-resources; {@code default-src 'none'} + {@code frame-ancestors 'none'}
     * make it inert and unframeable if ever rendered/embedded as a document.
     */
    public static String jsonApiPolicy()
    {
        return String.join("; ",
                "default-src 'none'",
                "base-uri 'none'",
                "frame-ancestors 'none'",
                "form-action 'none'");
    }

    static String originOf(String url)
    {
        if (url == null || url.isBlank())
        {
            return null;
        }
        try
        {
            URI u = URI.create(url.trim());
            if (u.getScheme() == null || u.getHost() == null)
            {
                return null;
            }
            String origin = u.getScheme() + "://" + u.getHost();
            if (u.getPort() != -1)
            {
                origin += ":" + u.getPort();
            }
            return origin;
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
