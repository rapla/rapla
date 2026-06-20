package org.rapla.server.spring.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.server.spring.oauth.OidcLoginSuccessHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 072 Phase 1 — the success-handler TAIL. Given a verified
 * {@link OidcUser} (id_token already validated by Spring's oauth2Login head),
 * the handler provisions the rapla User and sets an {@code access_token}
 * cookie (HttpOnly, Secure, SameSite). External IdP tokens are discarded.
 *
 * <p>Tier-3: real Spring context (so the real {@code UserProvisioner},
 * {@code ExternalUserResolver}, {@code RefreshSessionService} wire up over the
 * real file operator). {@code homer} pre-exists in {@code testdefault.xml}, so
 * provisioning resolves him by username — no mocks of rapla internals.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
class OidcLoginSuccessHandlerTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = OidcLoginSuccessHandlerTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @org.springframework.test.context.DynamicPropertySource
    static void registerProps(org.springframework.test.context.DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
        registry.add("rapla.oauth.external.keycloak.enabled", () -> "true");
        registry.add("rapla.oauth.external.keycloak.base-url", () -> "https://kc.example.com");
        registry.add("rapla.oauth.external.keycloak.realm", () -> "rapla-test");
        registry.add("rapla.oauth.external.keycloak.client-id", () -> "rapla-app");
    }

    @Autowired
    OidcLoginSuccessHandler handler;

    @Autowired
    RaplaFacade raplaFacade;

    private OAuth2AuthenticationToken homerOidcAuth()
    {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "header.payload.sig",
                now,
                now.plusSeconds(3600),
                Map.of(
                        "iss", "https://kc.example.com/realms/rapla-test",
                        "sub", "kc-subject-homer",
                        "preferred_username", "homer",
                        "email", "homer@example.com",
                        "name", "Homer Simpson"));
        OidcUser oidcUser = new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("ROLE_USER"), idToken, "preferred_username");
        return new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "keycloak");
    }

    private static Authentication savedContextAuth(MockHttpSession session)
    {
        Object ctx = session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNotNull(ctx, "the handler must save a SecurityContext to the session");
        return ((SecurityContext) ctx).getAuthentication();
    }

    @Test
    void provisionsUserAndSetsAccessTokenCookie() throws Exception
    {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "header.payload.sig",
                now,
                now.plusSeconds(3600),
                Map.of(
                        "iss", "https://kc.example.com/realms/rapla-test",
                        "sub", "kc-subject-homer",
                        "preferred_username", "homer",
                        "email", "homer@example.com",
                        "name", "Homer Simpson"));
        OidcUser oidcUser = new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("ROLE_USER"), idToken, "preferred_username");
        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(
                oidcUser, oidcUser.getAuthorities(), "keycloak");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/keycloak");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, auth);

        Cookie cookie = response.getCookie("access_token");
        assertNotNull(cookie, "access_token cookie must be set by the success handler");
        assertFalse(cookie.getValue() == null || cookie.getValue().isEmpty(), "cookie value must be a minted JWT");
        assertTrue(cookie.isHttpOnly(), "cookie must be HttpOnly");
        assertTrue(cookie.getSecure(), "cookie must be Secure");
        assertEquals("Lax", cookie.getAttribute("SameSite"), "cookie must be SameSite=Lax (CSRF-relevant; review N3)");
        // The minted token is a JWT (three dot-separated segments).
        assertTrue(cookie.getValue().chars().filter(c -> c == '.').count() == 2,
                "access_token must be a signed JWT");

        // PRD 072 Phase 2 — the OIDC login also sets the path-scoped refresh cookie.
        Cookie refreshCookie = response.getCookie("refresh_token");
        assertNotNull(refreshCookie, "OIDC login must set a refresh_token cookie (Phase 2)");
        assertTrue(refreshCookie.isHttpOnly(), "refresh cookie must be HttpOnly");
        assertEquals("Lax", refreshCookie.getAttribute("SameSite"));
        assertEquals("/api/auth/refresh", refreshCookie.getPath(),
                "refresh cookie must be path-scoped so it is not sent on every /api call");
    }

    /**
     * Review N2: rapla is an identity broker that trusts a verified {@code id_token}
     * only. A non-OIDC {@link OAuth2User} (no id_token, just userinfo attributes)
     * must NOT establish a rapla session — provisioning off unverified userinfo is a
     * weaker-trust path. Fail-closed: no claims → no session → no cookie. (The
     * attributes here WOULD provision homer via the dropped fallback, proving the
     * rejection is the id_token gate, not a missing-data accident.)
     */
    @Test
    void nonOidcOauth2UserGetsNoSession() throws Exception
    {
        Map<String, Object> attrs = Map.of(
                "iss", "https://kc.example.com/realms/rapla-test",
                "sub", "kc-subject-homer",
                "preferred_username", "homer",
                "email", "homer@example.com",
                "name", "Homer Simpson");
        OAuth2User oauth2User = new DefaultOAuth2User(
                AuthorityUtils.createAuthorityList("ROLE_USER"), attrs, "preferred_username");
        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(
                oauth2User, oauth2User.getAuthorities(), "keycloak");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/keycloak");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, auth);

        assertNull(response.getCookie("access_token"),
                "a non-OIDC OAuth2User must NOT establish a rapla session (review N2)");
    }

    /**
     * PRD 072 (Swing-SSO broker fix) — after provisioning, the handler replaces the
     * OIDC {@link Authentication} (whose name is the external preferred_username
     * "homer") with one whose principal name is the rapla user's UUID, and persists
     * it to the session. This is what makes the Authorization Server's subsequent
     * loopback authorization_code/token issuance use {@code sub = UUID} (resolvable
     * as a rapla user) instead of the OIDC username (which the rapla token generators
     * + /api cannot resolve → invalid_grant). Before the fix the session context kept
     * the OIDC token, so the saved principal name was "homer".
     */
    @Test
    void reAuthenticatesSessionAsRaplaUuidNotOidcUsername() throws Exception
    {
        OAuth2AuthenticationToken auth = homerOidcAuth();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/keycloak");
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, auth);

        Authentication saved = savedContextAuth(session);
        String expectedId = raplaFacade.getUser("homer").getId();
        assertEquals(expectedId, saved.getName(),
                "the saved SecurityContext principal must be the rapla UUID, not the OIDC username");
        assertNotEquals("homer", saved.getName(),
                "the saved principal must NOT be the external OIDC preferred_username");
    }

    /**
     * PRD 072 — the handler extends {@link org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler}.
     * The Swing-SSO broker flow saves the original {@code /oauth2/authorize} request
     * before bouncing through the IdP; on success the handler must RESUME that saved
     * request (so the loopback authorization_code is issued) rather than always
     * dumping the browser at {@code /app/}. With a saved request present the redirect
     * targets the saved URL; with none it falls back to the default {@code /app/}.
     */
    @Test
    void resumesSavedRequestWhenPresent() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/keycloak");
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Stash a saved request (the Swing /oauth2/authorize the broker preserved).
        MockHttpServletRequest original = new MockHttpServletRequest("GET", "/oauth2/authorize");
        original.setServerName("localhost");
        original.setServerPort(8051);
        original.setScheme("http");
        original.setQueryString("response_type=code&client_id=swing&redirect_uri=http://127.0.0.1:54321/callback");
        original.setSession(session);
        new HttpSessionRequestCache().saveRequest(original, response);

        handler.onAuthenticationSuccess(request, response, homerOidcAuth());

        String redirect = response.getRedirectedUrl();
        assertNotNull(redirect, "a redirect must be issued");
        assertTrue(redirect.contains("/oauth2/authorize"),
                "with a saved request the handler must resume it, not redirect to /app/ — was: " + redirect);
    }

    @Test
    void redirectsToDefaultAppWhenNoSavedRequest() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/keycloak");
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, homerOidcAuth());

        String redirect = response.getRedirectedUrl();
        assertNotNull(redirect, "a redirect must be issued");
        assertTrue(redirect.endsWith("/app/"),
                "with no saved request the handler falls back to the default /app/ — was: " + redirect);
    }
}
