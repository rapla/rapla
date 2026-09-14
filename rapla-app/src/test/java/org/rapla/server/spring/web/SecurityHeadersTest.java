package org.rapla.server.spring.web;

import org.junit.jupiter.api.Test;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * PRD 071 Phase 2: the security headers (CSP + companions) are actually emitted
 * on responses from the main filter chain. CSP is path-scoped:
 * <ul>
 *   <li>{@code /rapla/**} (calendar/iCal HTML pages) → strict ENFORCED
 *       {@code default-src 'none'} — kills the A6 reflected-XSS class;</li>
 *   <li>{@code /api/**} (JSON) → strict ENFORCED {@code default-src 'none'};</li>
 *   <li>{@code /app/**} (SPA) → the non-script SPA policy ENFORCED (PRD 102 Phase 1);</li>
 *   <li>{@code /login} + explorers → the same policy report-only (their enforce pass pending).</li>
 * </ul>
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class SecurityHeadersTest extends IsolatedDefaultDatasetTest
{
    @Autowired
    MockMvc mockMvc;

    @Test
    void cspReportOnlyAndCompanionHeadersArePresent() throws Exception
    {
        mockMvc.perform(get("/login"))
                .andExpect(header().exists("Content-Security-Policy-Report-Only"))
                // style-src/img-src/font-src dropped from the SPA/login policy (lowest
                // criticality, no code execution) — only the meaningful directives stay.
                .andExpect(header().string("Content-Security-Policy-Report-Only", not(containsString("style-src"))))
                .andExpect(header().string("Content-Security-Policy-Report-Only", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Content-Security-Policy-Report-Only", containsString("object-src 'none'")))
                .andExpect(header().string("Content-Security-Policy-Report-Only", containsString("connect-src 'self'")))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void calendarPagesGetStrictEnforcedCsp() throws Exception
    {
        // public HTML calendar page renders user-controlled data (resource/event
        // names) — the highest-value enforce target. default-src 'none' means an
        // injected <script> simply cannot execute; style-src allows the external
        // calendar.css + the inline style= attributes; form-action 'self' the GET form.
        mockMvc.perform(get("/rapla/calendar"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString("style-src 'self' 'unsafe-inline'")))
                .andExpect(header().string("Content-Security-Policy", containsString("form-action 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                // strictly enforced — NOT report-only
                .andExpect(header().doesNotExist("Content-Security-Policy-Report-Only"));
    }

    @Test
    void spaAppGetsEnforcedNonScriptCsp() throws Exception
    {
        // PRD 102 Phase 1: /app carries the SPA policy ENFORCED (not report-only). It has NO
        // script-src/style-src (Material + inline bootstrap untouched) but enforces the non-script
        // directives — connect-src exfil-confinement, object-src/base-uri/frame-*/form-action.
        mockMvc.perform(get("/app/"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Content-Security-Policy", containsString("connect-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Content-Security-Policy", not(containsString("script-src"))))
                .andExpect(header().doesNotExist("Content-Security-Policy-Report-Only"));
    }

    @Test
    void apiEndpointsGetStrictEnforcedCsp() throws Exception
    {
        mockMvc.perform(get("/api/graphql/schema"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().doesNotExist("Content-Security-Policy-Report-Only"));
    }
}
