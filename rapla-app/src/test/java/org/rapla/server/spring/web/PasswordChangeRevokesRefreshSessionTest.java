package org.rapla.server.spring.web;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
 * Security audit F5-2 — a successful password change ends the user's refresh session: the refresh token issued
 * before the change must no longer validate. Covers both change paths (remote storage API and the change-password page).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class PasswordChangeRevokesRefreshSessionTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = PasswordChangeRevokesRefreshSessionTest.class.getResourceAsStream("/testdefault.xml"))
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
    @Autowired
    CachableStorageOperator operator;
    @Autowired
    RefreshSessionService refreshSessionService;

    @Test
    void remotePasswordChangeRevokesRefreshToken() throws Exception
    {
        OAuthTestSupport.TokenPair monty = OAuthTestSupport.loginAsWithRefresh(mockMvc, "monty", "burns");
        assertDoesNotThrow(() -> refreshSessionService.validate(monty.refreshToken()), "precondition: token valid before the change");

        int status = mockMvc.perform(post("/api/storage/change/password")
                        .header("Authorization", "Bearer " + monty.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"monty\",\"oldPassword\":\"burns\",\"newPassword\":\"burns-2\"}"))
                .andReturn().getResponse().getStatus();
        assertEquals(200, status, "the password change itself must succeed");

        assertThrows(RaplaSecurityException.class, () -> refreshSessionService.validate(monty.refreshToken()),
                "a refresh token issued before the password change must not survive it");
    }

    @Test
    void pagePasswordChangeRevokesRefreshToken() throws Exception
    {
        // the page only lets a user without a password set one (see ChangePasswordGateTest)
        operator.changePassword(operator.getUser("homer"), "duffs".toCharArray(), new char[0]);
        OAuthTestSupport.TokenPair homer = OAuthTestSupport.loginAsWithRefresh(mockMvc, "homer", "");
        assertDoesNotThrow(() -> refreshSessionService.validate(homer.refreshToken()), "precondition: token valid before the change");

        int status = mockMvc.perform(post("/change-password")
                        .header("Authorization", "Bearer " + homer.accessToken())
                        .param("newPassword", "duffs")
                        .param("confirmPassword", "duffs"))
                .andReturn().getResponse().getStatus();
        assertEquals(3, status / 100, "the password change itself must succeed (redirect)");

        assertThrows(RaplaSecurityException.class, () -> refreshSessionService.validate(homer.refreshToken()),
                "a refresh token issued before the password change must not survive it");
    }
}
