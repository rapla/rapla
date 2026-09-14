package org.rapla.server.spring.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.server.spring.RefreshSessionService;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Security audit PH2 / F5-2 / WP S6 — ending a refresh session must hold on the DB-backed store (HSQLDB), not only on the
 * file store: clearSession and a password change both reject the previously issued refresh token.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
@Tag("e2e")
@Tag("db")
class PasswordChangeRevokesRefreshSessionDbTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = PasswordChangeRevokesRefreshSessionDbTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in);
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
        // Unique in-memory schema name per test class so parallel runs don't collide.
        registry.add("rapla.db-datasources.rapladb.url", () -> "jdbc:hsqldb:mem:rapla-password-revoke-db-test");
        registry.add("rapla.db-datasources.rapladb.username", () -> "SA");
        registry.add("rapla.db-datasources.rapladb.password", () -> "");
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    CachableStorageOperator operator;
    @Autowired
    RefreshSessionService refreshSessionService;

    /** (c) clearSession on the DB store ends the session. */
    @Test
    void clearSessionRevokesRefreshTokenOnDbStore() throws Exception
    {
        User homer = operator.getUser("homer");
        String token = refreshSessionService.issueAndPersistRefreshToken(homer);
        assertDoesNotThrow(() -> refreshSessionService.validate(token), "precondition: token valid");

        refreshSessionService.clearSession(homer);

        assertThrows(RaplaSecurityException.class, () -> refreshSessionService.validate(token),
                "clearSession must end the session on the DB-backed store");
    }

    /** (d) F5-2 — a password change on the DB store rejects the old refresh token. */
    @Test
    void passwordChangeRevokesRefreshTokenOnDbStore() throws Exception
    {
        OAuthTestSupport.TokenPair monty = OAuthTestSupport.loginAsWithRefresh(mockMvc, "monty", "burns");
        assertDoesNotThrow(() -> refreshSessionService.validate(monty.refreshToken()), "precondition: token valid");

        int status = mockMvc.perform(post("/api/storage/change/password")
                        .header("Authorization", "Bearer " + monty.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"monty\",\"oldPassword\":\"burns\",\"newPassword\":\"burns-db\"}"))
                .andReturn().getResponse().getStatus();
        assertEquals(200, status, "the password change itself must succeed");

        assertThrows(RaplaSecurityException.class, () -> refreshSessionService.validate(monty.refreshToken()),
                "a refresh token issued before the password change must not survive it on the DB store");
    }
}
