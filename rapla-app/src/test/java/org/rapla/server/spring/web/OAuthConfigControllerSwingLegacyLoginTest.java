package org.rapla.server.spring.web;

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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 029 Phase 3: when an admin sets {@code rapla.oauth.swing-legacy-login=true}
 * (and opts the SSO button in), {@code /api/auth/oauth/config} echoes both flags
 * so the Swing client renders the legacy username/password dialog — with the
 * "Sign in with browser…" button still present for users who want to try SSO.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class OAuthConfigControllerSwingLegacyLoginTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = OAuthConfigControllerSwingLegacyLoginTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
        registry.add("rapla.oauth.swing-legacy-login", () -> "true");
        registry.add("rapla.oauth.swing-legacy-show-sso-button", () -> "true");
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void discoveryEchoesBothSwingLegacyFlags() throws Exception
    {
        mockMvc.perform(get("/api/auth/oauth/config"))
                .andExpect(status().isOk())
                // OAuth itself stays enabled — the legacy dialog still needs a
                // working /api/auth/oauth/config probe + the SSO button must
                // have an auth server to talk to.
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.swingLegacyLogin").value(true))
                .andExpect(jsonPath("$.swingLegacyShowSsoButton").value(true));
    }
}
