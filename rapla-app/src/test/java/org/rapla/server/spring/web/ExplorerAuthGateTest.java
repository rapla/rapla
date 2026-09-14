package org.rapla.server.spring.web;

import org.junit.jupiter.api.Test;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 071 Phase 3: the API-explorer pages ({@code /swagger-ui/**} and
 * {@code /graphiql/**}) are no longer {@code permitAll} — they require an
 * authenticated session.
 *
 * <p>These pages are STATIC HTML resources. A browser navigating to them sends
 * only cookies (not the SPA's localStorage Bearer), so server-side they can be
 * gated only by the form-login session / remember-me cookie. The OAuth2 SPA
 * login also establishes a JSESSIONID (the {@code /oauth2/authorize} flow signs
 * in via form-login), so a dev who has signed into the SPA carries a session
 * that satisfies this gate.
 *
 * <p>Unauthenticated access is rejected by the main chain's authentication
 * entry point. Because the chain configures {@code oauth2ResourceServer().jwt()}
 * (a {@code JwtDecoder} bean is present), the active entry point is the Bearer
 * one, so an anonymous GET gets a <b>401</b> (not a 302 to {@code /login}) — the
 * page is gated either way; this test asserts the real status.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class ExplorerAuthGateTest extends IsolatedDefaultDatasetTest
{
    @Autowired
    MockMvc mockMvc;

    @Test
    void unauthenticatedGraphiqlIsRejected() throws Exception
    {
        mockMvc.perform(get("/graphiql/index.html"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedSwaggerUiIsRejected() throws Exception
    {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedBareGraphiqlIsRejected() throws Exception
    {
        mockMvc.perform(get("/graphiql"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedBareSwaggerUiIsRejected() throws Exception
    {
        mockMvc.perform(get("/swagger-ui"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedGraphiqlLoadsPage() throws Exception
    {
        mockMvc.perform(get("/graphiql/index.html").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("GraphiQL")));
    }

    @Test
    void authenticatedSwaggerUiLoadsPage() throws Exception
    {
        mockMvc.perform(get("/swagger-ui/index.html").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedBareGraphiqlRedirectsToIndex() throws Exception
    {
        mockMvc.perform(get("/graphiql").with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection());
    }

    /**
     * Gating the explorer UIs must NOT gate the public SDL endpoint. The GraphQL
     * schema printer ({@code /api/graphql/schema}, the GraphQL pendant to the
     * public OpenAPI {@code /v3/api-docs}) is API-shape metadata only — it stays
     * reachable WITHOUT authentication, exactly like Swagger's spec. The path does
     * not match the {@code /graphiql/**} gate (it lives under {@code /api/}).
     */
    @Test
    void graphqlSchemaSdlStaysPublicWithoutAuth() throws Exception
    {
        mockMvc.perform(get("/api/graphql/schema"))
                .andExpect(status().isOk());
    }

    /**
     * PRD 072 Phase 3: the explorers no longer piggyback on the SPA's
     * {@code localStorage.access_token} Bearer. They rely on the HttpOnly
     * {@code access_token} cookie (Phase 2) the browser sends automatically.
     * The bespoke {@code localStorage}/{@code Drop token} plumbing must be gone.
     */
    @Test
    void graphiqlHtmlDropsLocalStorageBearerPiggyback() throws Exception
    {
        mockMvc.perform(get("/graphiql/index.html").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                // No bespoke Bearer-from-localStorage AUTH plumbing. Assert on CODE
                // patterns only — broad substrings (`localStorage.getItem`, `Drop token`)
                // would false-positive on GraphiQL's own query persistence
                // (`localStorage.getItem('graphiql:query')`) and on explanatory comments
                // mentioning the removed "Drop token" button. The signals below pin the
                // actual invariant: no access-token read, no Bearer header, no token wipe.
                .andExpect(content().string(not(containsString("localStorage.removeItem"))))
                .andExpect(content().string(not(containsString("getItem('access_token')"))))
                .andExpect(content().string(not(containsString("'Bearer '"))));
    }

    // NOTE: there is intentionally no tier-3 content assertion for the
    // Swagger UI page. Under the TEST classpath the springdoc-openapi
    // starter (test-scope, pom line ~553) auto-configures a /swagger-ui/**
    // resource handler that serves the swagger-ui WEBJAR's own index.html,
    // shadowing our static/swagger-ui/index.html. So MockMvc here returns the
    // webjar dist page, not ours — any content assertion would test the wrong
    // artifact. In production springdoc is ABSENT, so the static page is what
    // ships; that file's Phase-3 rewrite is verified by grep (see PRD 072 §3).

    /**
     * PRD 072 Phase 3: the GraphiQL {@code POST /api/graphql} is a mutating
     * cookie-auth request, so it must carry the {@code X-XSRF-TOKEN} header read
     * from the non-HttpOnly {@code XSRF-TOKEN} cookie that Spring's
     * {@code CookieCsrfTokenRepository.withHttpOnlyFalse()} sets.
     */
    @Test
    void graphiqlHtmlSendsXsrfTokenHeader() throws Exception
    {
        mockMvc.perform(get("/graphiql/index.html").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("X-XSRF-TOKEN")))
                .andExpect(content().string(containsString("XSRF-TOKEN")));
    }

    /**
     * PRD 072 Phase 3: on a 401 the explorer fetcher refreshes via
     * {@code POST /api/auth/refresh} (cookie credential) then replays; on a
     * refresh-401 it bounces to {@code /login}.
     */
    @Test
    void graphiqlHtmlRefreshesViaCookieEndpoint() throws Exception
    {
        mockMvc.perform(get("/graphiql/index.html").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/auth/session/refresh")));
    }
}
