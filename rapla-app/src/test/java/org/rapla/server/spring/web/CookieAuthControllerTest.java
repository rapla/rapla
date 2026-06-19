package org.rapla.server.spring.web;

import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 072 Phase 2 — tier-3 MockMvc coverage for the cookie-credential
 * (model A) endpoints + the cookie-aware resource-server resolver + CSRF
 * scoping. Real Spring context, no mocks of rapla internals; {@code homer}
 * (global admin) and {@code monty} (group admin) pre-exist in
 * {@code testdefault.xml}.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class CookieAuthControllerTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = CookieAuthControllerTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired
    MockMvc mockMvc;

    /** Logs in via the password grant and returns BOTH access and refresh tokens. */
    private OAuthTestSupport.TokenPair login(String username, String password) throws Exception
    {
        return OAuthTestSupport.loginAsWithRefresh(mockMvc, username, password);
    }

    // ---------------------------------------------------------------------
    // 1. Cookie auth on /api — a valid access_token cookie authorizes /api/**.
    // ---------------------------------------------------------------------

    @Test
    void accessTokenCookieAuthorizesApiGet() throws Exception
    {
        String access = login("homer", "duffs").accessToken();

        // /api/auth/me requires a valid credential; cookie-only (no Bearer).
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", access)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("homer"));
    }

    @Test
    void dualPresentationOneTokenWorksAsCookieAndBearer() throws Exception
    {
        String access = login("homer", "duffs").accessToken();

        // Same minted token, both presentations authorize identically.
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", access)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("homer"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("homer"));
    }

    @Test
    void bearerHeaderWinsOverCookie() throws Exception
    {
        String homer = login("homer", "duffs").accessToken();
        String monty = login("monty", "burns").accessToken();

        // Cookie says monty, Bearer says homer → Bearer must win.
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + homer)
                        .cookie(new Cookie("access_token", monty)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("homer"));
    }

    @Test
    void refreshTypTokenInCookieIsRejectedOnApi() throws Exception
    {
        // The refresh token must NOT authorize /api when presented as the
        // access cookie — same typ-rejection as the Bearer path.
        String refresh = login("homer", "duffs").refreshToken();

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noCredentialReturns401() throws Exception
    {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------
    // 2a. SAS password-grant login ALSO sets the browser credential cookies.
    // ---------------------------------------------------------------------

    @Test
    void passwordGrantLoginSetsAccessAndRefreshCookies() throws Exception
    {
        MvcResult login = mockMvc.perform(post("/oauth2/token")
                        .contentType("application/x-www-form-urlencoded")
                        .content("grant_type=password&username=homer&password="
                                + URLEncoder.encode("duffs", StandardCharsets.UTF_8)
                                + "&client_id=rapla-client"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie access = login.getResponse().getCookie("access_token");
        Cookie refresh = login.getResponse().getCookie("refresh_token");
        assertNotNull(access, "password-grant login must set an access_token cookie");
        assertNotNull(refresh, "password-grant login must set a refresh_token cookie");
        assertTrue(access.isHttpOnly());
        assertTrue(refresh.isHttpOnly());
        assertEquals("Lax", access.getAttribute("SameSite"));
        assertEquals("Lax", refresh.getAttribute("SameSite"));
        // Path-scoped refresh cookie — NOT sent on every /api call.
        assertEquals("/api/auth/refresh", refresh.getPath());
        assertEquals("/", access.getPath());

        // And the access cookie authorizes /api.
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", access.getValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("homer"));
    }

    // ---------------------------------------------------------------------
    // 2. Refresh sets a fresh access cookie.
    // ---------------------------------------------------------------------

    @Test
    void refreshSetsNewAccessCookie() throws Exception
    {
        String refresh = login("homer", "duffs").refreshToken();

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", refresh)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie fresh = result.getResponse().getCookie("access_token");
        assertNotNull(fresh, "refresh must set a fresh access_token cookie");
        assertTrue(fresh.isHttpOnly(), "access cookie must be HttpOnly");
        assertEquals("Lax", fresh.getAttribute("SameSite"));
        assertTrue(fresh.getValue().chars().filter(c -> c == '.').count() == 2,
                "fresh access_token must be a signed JWT");

        // And the fresh access token authorizes /api.
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", fresh.getValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("homer"));
    }

    @Test
    void refreshWithInvalidTokenReturns401() throws Exception
    {
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", "not.a.valid.jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithNoCookieReturns401() throws Exception
    {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------
    // 3. /api/auth/me identity + §12 leak test.
    // ---------------------------------------------------------------------

    @Test
    void meReturnsOnlyOwnIdentity() throws Exception
    {
        String monty = login("monty", "burns").accessToken();

        // monty can ONLY see his own identity — never homer's, regardless of
        // any request shaping. There is no parameter to request another user.
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", monty)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("monty"))
                .andExpect(jsonPath("$.impersonating").value(false))
                .andExpect(jsonPath("$.actor").doesNotExist());
    }

    // ---------------------------------------------------------------------
    // 4. Cookie impersonation switch + end.
    // ---------------------------------------------------------------------

    @Test
    void impersonateSwitchSetsTargetCookieForAdmin() throws Exception
    {
        String adminAccess = login("homer", "duffs").accessToken();

        MvcResult switched = mockMvc.perform(post("/api/auth/impersonate/switch")
                        .with(csrf())
                        .cookie(new Cookie("access_token", adminAccess))
                        .param("target_username", "monty"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie impCookie = switched.getResponse().getCookie("access_token");
        assertNotNull(impCookie, "switch must set an impersonation access_token cookie");

        // The new cookie now identifies monty (effective subject) but /me reports
        // the impersonation state with homer as the actor.
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", impCookie.getValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("monty"))
                .andExpect(jsonPath("$.impersonating").value(true))
                .andExpect(jsonPath("$.actor").value("homer"))
                .andExpect(jsonPath("$.target").value("monty"));
    }

    @Test
    void impersonateSwitchRequiresCanAdminUser() throws Exception
    {
        // monty (group admin) cannot impersonate homer (global admin) → 403.
        String groupAdmin = login("monty", "burns").accessToken();

        mockMvc.perform(post("/api/auth/impersonate/switch")
                        .with(csrf())
                        .cookie(new Cookie("access_token", groupAdmin))
                        .param("target_username", "homer"))
                .andExpect(status().isForbidden());
    }

    @Test
    void impersonateSwitchAnonymousReturns401() throws Exception
    {
        mockMvc.perform(post("/api/auth/impersonate/switch")
                        .param("target_username", "monty"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void impersonateEndRestoresAdminCookie() throws Exception
    {
        String adminAccess = login("homer", "duffs").accessToken();

        // Switch into monty.
        MvcResult switched = mockMvc.perform(post("/api/auth/impersonate/switch")
                        .with(csrf())
                        .cookie(new Cookie("access_token", adminAccess))
                        .param("target_username", "monty"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie impCookie = switched.getResponse().getCookie("access_token");
        assertNotNull(impCookie);

        // End impersonation — must re-establish a NON-impersonating admin cookie.
        // Review B1: ONLY the impersonation access cookie is present (a real browser
        // does NOT send the refresh_token cookie to /api/auth/impersonate/end — it is
        // Path=/api/auth/refresh). The admin is restored from the token's act.sub claim.
        MvcResult ended = mockMvc.perform(post("/api/auth/impersonate/end")
                        .with(csrf())
                        .cookie(new Cookie("access_token", impCookie.getValue())))
                .andExpect(status().isOk())
                .andReturn();

        Cookie restored = ended.getResponse().getCookie("access_token");
        assertNotNull(restored, "end must set a fresh admin access_token cookie");

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", restored.getValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("homer"))
                .andExpect(jsonPath("$.impersonating").value(false));
    }

    @Test
    void switchWhileImpersonatingKeepsRealAdminAsActor() throws Exception
    {
        // Review B2: a chained switch (already impersonating, switch again WITHOUT
        // calling /end) must evaluate canAdminUser + audit + the new act-claim
        // against the REAL admin (homer), not the impersonated effective user —
        // otherwise the admin is laundered out of the chain and the admin check is
        // run against the impersonated user's permissions.
        String adminAccess = login("homer", "duffs").accessToken();

        MvcResult switch1 = mockMvc.perform(post("/api/auth/impersonate/switch")
                        .with(csrf())
                        .cookie(new Cookie("access_token", adminAccess))
                        .param("target_username", "monty"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie imp1 = switch1.getResponse().getCookie("access_token");
        assertNotNull(imp1);

        // Switch AGAIN while the cookie is the monty-impersonation token.
        MvcResult switch2 = mockMvc.perform(post("/api/auth/impersonate/switch")
                        .with(csrf())
                        .cookie(new Cookie("access_token", imp1.getValue()))
                        .param("target_username", "monty"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie imp2 = switch2.getResponse().getCookie("access_token");
        assertNotNull(imp2);

        // The real admin (homer) must remain the actor — NOT the impersonated monty.
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", imp2.getValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actor").value("homer"));
    }

    // ---------------------------------------------------------------------
    // 2b. Browser FORM login (POST /login) sets the credential cookies.
    // ---------------------------------------------------------------------

    @Test
    void formLoginSetsAuthCookiesAndAuthorizesApi() throws Exception
    {
        // The combined /login page's username+password form POSTs /login (Spring
        // formLogin). The success handler must run the rapla TAIL: mint a rapla
        // JWT + set BOTH the access_token and refresh_token cookies — otherwise the
        // browser only gets a JSESSIONID session, which /api (stateless JWT) ignores
        // → /api/auth/me 401 → SPA bounces back to /login (login loop). Caught by the
        // PRD 072 Phase-4 browser e2e gate.
        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "homer")
                        .param("password", "duffs"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        Cookie access = login.getResponse().getCookie("access_token");
        Cookie refresh = login.getResponse().getCookie("refresh_token");
        assertNotNull(access, "form login must set an access_token cookie");
        assertNotNull(refresh, "form login must set a refresh_token cookie");
        assertTrue(access.isHttpOnly());
        assertEquals("/api/auth/refresh", refresh.getPath());

        // And the cookie authorizes /api as the logged-in user.
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", access.getValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("homer"));
    }

    // ---------------------------------------------------------------------
    // 4b. Logout expires both auth cookies (PRD 072 Phase 4 review should-fix).
    // ---------------------------------------------------------------------

    @Test
    void logoutExpiresBothAuthCookies() throws Exception
    {
        // The SPA's sign-out hits POST /api/auth/logout (cookie-auth + XSRF). The
        // server must EXPIRE both the access_token (Path=/) and refresh_token
        // (Path=/api/auth/refresh) cookies so the browser drops them. Spring's
        // default LogoutFilter only matches POST /logout and only clears
        // JSESSIONID — it does NOT know about rapla's stateless auth cookies, so a
        // dedicated endpoint is required.
        String access = login("homer", "duffs").accessToken();

        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .cookie(new Cookie("access_token", access)))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie clearedAccess = result.getResponse().getCookie("access_token");
        Cookie clearedRefresh = result.getResponse().getCookie("refresh_token");
        assertNotNull(clearedAccess, "logout must clear the access_token cookie");
        assertNotNull(clearedRefresh, "logout must clear the refresh_token cookie");
        assertEquals(0, clearedAccess.getMaxAge(), "access_token cookie must be expired (maxAge 0)");
        assertEquals(0, clearedRefresh.getMaxAge(), "refresh_token cookie must be expired (maxAge 0)");
        assertEquals("/", clearedAccess.getPath());
        assertEquals("/api/auth/refresh", clearedRefresh.getPath());
    }

    @Test
    void logoutWithoutAccessCookieStillSucceeds() throws Exception
    {
        // Sign-out must be idempotent / robust: even with no (or an expired)
        // access cookie the endpoint clears whatever is there and returns 204 —
        // /api/auth/** is permitAll, so the bearer-validation gate never blocks it.
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------------
    // 5. CSRF scoped to the cookie path (review B1).
    // ---------------------------------------------------------------------

    @Test
    void xsrfTokenCookieIsMaterializedOnSafeGet() throws Exception
    {
        // Phase 3 blocker fix (CsrfCookieFilter): the XSRF-TOKEN cookie must be
        // WRITTEN on a safe GET so the browser can read it and submit the
        // double-submit X-XSRF-TOKEN header on the next cookie-auth POST. Without
        // this the deferred-CSRF token is never materialized and every cookie-auth
        // POST /api/graphql 403s. This asserts the materialization (the fix);
        // the full positive round-trip (200 with the token) is verified by the
        // Phase-4 browser e2e — a MockMvc-tier positive round-trip is unreliable
        // here because a sibling request can leave the AS chain's HttpSession CSRF
        // token in play, which is a test-harness artifact, not browser behaviour.
        String access = login("homer", "duffs").accessToken();

        MvcResult get = mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", access)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie xsrf = get.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrf, "a GET must materialize the XSRF-TOKEN cookie (deferred-CSRF blocker)");
        assertFalse(xsrf.getValue() == null || xsrf.getValue().isEmpty(), "XSRF-TOKEN must carry a value");
        assertFalse(xsrf.isHttpOnly(), "XSRF-TOKEN must be JS-readable (double-submit header)");
    }

    @Test
    void cookieAuthGraphqlMutationWithoutCsrfTokenIs403() throws Exception
    {
        // Review S1: /api/graphql (POST) is the real cookie-auth write surface, not
        // just /api/auth/*. A cookie-authenticated cross-site POST without the XSRF
        // token must be rejected.
        String access = login("homer", "duffs").accessToken();
        mockMvc.perform(post("/api/graphql")
                        .cookie(new Cookie("access_token", access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"{ __typename }\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void currentShapedPostWithoutAccessCookieStill200s() throws Exception
    {
        // The CURRENT SPA path: Bearer header, no access_token cookie. A mutating
        // POST must NOT require an XSRF token (header-Bearer is CSRF-exempt).
        String access = login("homer", "duffs").accessToken();

        mockMvc.perform(post("/api/auth/api-keys")
                        .header("Authorization", "Bearer " + access)
                        .contentType("application/json")
                        .content("{\"label\":\"csrf-exempt-bearer\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void cookieAuthMutatingPostWithoutCsrfTokenIs403() throws Exception
    {
        // Cookie-authenticated mutating POST WITHOUT the X-XSRF-TOKEN → 403.
        String access = login("homer", "duffs").accessToken();

        mockMvc.perform(post("/api/auth/impersonate/switch")
                        .cookie(new Cookie("access_token", access))
                        .param("target_username", "monty"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // 6. Single-slot re-login overwrites the prior refresh slot (review S6).
    // ---------------------------------------------------------------------

    @Test
    void singleSlotReLoginSharesRefreshToken() throws Exception
    {
        // RefreshSessionService returns the SAME stored refresh token on a
        // second login (single-slot, multi-device share) — both refresh cookies
        // remain valid against the one slot.
        String first = login("homer", "duffs").refreshToken();
        String second = login("homer", "duffs").refreshToken();
        assertEquals(first, second,
                "single-slot model: second login returns the same refresh token");

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", second)))
                .andExpect(status().isOk());
    }
}
