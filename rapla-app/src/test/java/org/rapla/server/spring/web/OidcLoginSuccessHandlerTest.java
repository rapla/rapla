package org.rapla.server.spring.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.server.spring.oauth.OidcLoginSuccessHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.authority.AuthorityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
