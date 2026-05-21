package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 050 — verifies the external-auth user lifecycle:
 * <ol>
 *   <li>Once a user is stamped with {@code authenticationSource}, self
 *       changePassword/changeName/changeEmail/confirmEmail all return 401
 *       (RaplaSecurityException → 401 per RaplaExceptionHandler) — naming
 *       the IdP in the message.</li>
 *   <li>The same calls by an admin against the external user also return 401
 *       — no fallback (PRD 050 deliberate choice).</li>
 *   <li>{@code GET /api/storage/profile/capabilities} returns all-false +
 *       the IdP label for external users; all-true + null for local users.</li>
 *   <li>{@code POST /api/storage/user/{id}/disconnect-external-auth} is
 *       admin-only (non-admin → 401), idempotent on already-local users,
 *       and clears the marker.</li>
 * </ol>
 *
 * <p>Fixture: testdefault.xml has only one admin ({@code homer}) — kept local
 * so the disconnect / admin-targets-external tests can use a valid admin
 * actor. {@code monty} (non-admin) is the external-auth target.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class ExternalAuthLifecycleIntegrationTest
{
    @TempDir static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ExternalAuthLifecycleIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
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

    @Autowired MockMvc mockMvc;
    @Autowired RaplaFacade facade;

    @BeforeEach
    void stampMontyAsKeycloak() throws Exception
    {
        User monty = findUser("monty");
        if ("keycloak".equals(monty.getAuthenticationSource())) return;
        User edit = facade.edit(monty);
        edit.setAuthenticationSource("keycloak");
        facade.store(edit);
    }

    private User findUser(String username) throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (username.equals(u.getUsername())) return u;
        }
        throw new IllegalStateException("user not found: " + username);
    }

    private String homerToken() throws Exception
    {
        return OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
    }

    private String montyToken() throws Exception
    {
        return OAuthTestSupport.loginAs(mockMvc, "monty", "burns");
    }

    // ----- capabilities endpoint -----

    @Test
    void capabilities_forExternalUser_returnsAllFalseWithLabel() throws Exception
    {
        mockMvc.perform(get("/api/storage/profile/capabilities")
                        .header("Authorization", "Bearer " + montyToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canChangePassword").value(false))
                .andExpect(jsonPath("$.canChangeName").value(false))
                .andExpect(jsonPath("$.canChangeEmail").value(false))
                .andExpect(jsonPath("$.externalIdpLabel").value("keycloak"));
    }

    @Test
    void capabilities_forLocalUser_returnsAllTrueWithNullLabel() throws Exception
    {
        mockMvc.perform(get("/api/storage/profile/capabilities")
                        .header("Authorization", "Bearer " + homerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canChangePassword").value(true))
                .andExpect(jsonPath("$.canChangeName").value(true))
                .andExpect(jsonPath("$.canChangeEmail").value(true))
                .andExpect(jsonPath("$.externalIdpLabel").doesNotExist());
    }

    @Test
    void legacyCanChangePasswordEndpoint_is404_afterPrd050Removal() throws Exception
    {
        // PRD 050 Phase 6 removed GET /api/storage/change/canchangepassword.
        // The Swing client now routes through getProfileEditCapabilities().
        // Legacy callers get 404 (Spring's no-handler-found path).
        mockMvc.perform(get("/api/storage/change/canchangepassword")
                        .header("Authorization", "Bearer " + montyToken()))
                .andExpect(status().isNotFound());
    }

    // ----- changeName / changeEmail guard -----

    @Test
    void changeName_byExternalSelf_isRejected() throws Exception
    {
        String body = "{\"username\":\"monty\",\"newTitle\":\"\",\"newSurename\":\"M\",\"newLastname\":\"Newman\"}";
        mockMvc.perform(post("/api/storage/change/name")
                        .header("Authorization", "Bearer " + montyToken())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changeName_byAdminAgainstExternalUser_isRejected() throws Exception
    {
        // homer (admin, local) trying to change monty's (external) name → must fail.
        // No fallback (PRD 050).
        String body = "{\"username\":\"monty\",\"newTitle\":\"\",\"newSurename\":\"M\",\"newLastname\":\"Newman\"}";
        mockMvc.perform(post("/api/storage/change/name")
                        .header("Authorization", "Bearer " + homerToken())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changeEmail_byExternalSelf_isRejected() throws Exception
    {
        String body = "{\"username\":\"monty\",\"newEmail\":\"monty@new.example\"}";
        mockMvc.perform(post("/api/storage/change/email")
                        .header("Authorization", "Bearer " + montyToken())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changeName_byLocalSelf_isAllowed() throws Exception
    {
        String body = "{\"username\":\"homer\",\"newTitle\":\"\",\"newSurename\":\"J\",\"newLastname\":\"Simpson\"}";
        mockMvc.perform(post("/api/storage/change/name")
                        .header("Authorization", "Bearer " + homerToken())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());
    }

    // ----- disconnect endpoint -----

    @Test
    void disconnect_byNonAdmin_isRejected() throws Exception
    {
        // monty (non-admin) trying to disconnect his own external auth → 401.
        // Even if the user wants to disconnect themselves, that's an admin action.
        User monty = findUser("monty");
        mockMvc.perform(post("/api/storage/user/" + monty.getId() + "/disconnect-external-auth")
                        .header("Authorization", "Bearer " + montyToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disconnect_byAdmin_clearsMarker() throws Exception
    {
        User monty = findUser("monty");
        assertEquals("keycloak", monty.getAuthenticationSource(),
                "precondition: monty is stamped keycloak");

        mockMvc.perform(post("/api/storage/user/" + monty.getId() + "/disconnect-external-auth")
                        .header("Authorization", "Bearer " + homerToken()))
                .andExpect(status().isOk());

        User montyAfter = findUser("monty");
        assertNull(montyAfter.getAuthenticationSource(),
                "disconnect must clear authentication-source");
    }

    @Test
    void disconnect_onLocalUser_isIdempotent() throws Exception
    {
        User homer = findUser("homer");
        assertNull(homer.getAuthenticationSource(), "precondition: homer is local");

        mockMvc.perform(post("/api/storage/user/" + homer.getId() + "/disconnect-external-auth")
                        .header("Authorization", "Bearer " + homerToken()))
                .andExpect(status().isOk());

        User homerAfter = findUser("homer");
        assertNull(homerAfter.getAuthenticationSource(),
                "no-op disconnect must leave already-local user untouched");
    }
}
