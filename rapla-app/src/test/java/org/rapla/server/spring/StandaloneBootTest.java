package org.rapla.server.spring;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
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
 * PRD 054 acceptance test — the standalone Spring profile boots cleanly with
 * {@code passwordCheckDisabled=true} + {@code FileOperator} storage, and the
 * OAuth discovery endpoint reports {@code enabled=true} per the "keep the OAuth
 * stuff" decision (the Angular SPA's login flow still runs in standalone; any
 * password is accepted because of the password-check skip).
 *
 * <p>Runs as part of the regular reactor test phase (not gated behind
 * {@code -Pstandalone}) — that way a future PR adding security wiring that
 * accidentally breaks the standalone profile gets caught here, not at MSI
 * build time on the maintainer's Windows machine.
 *
 * <p>Tagged {@code @Tag("e2e")} only because of the full-context boot cost;
 * does NOT need a network port (uses MockMvc).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("standalone")
@Tag("e2e")
class StandaloneBootTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void setup() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = StandaloneBootTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        // Override the standalone profile's default %APPDATA%/rapla/rapla-data.xml
        // with the test temp file. Same property the standalone profile would set
        // at runtime (just with a tempdir target for test isolation).
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void standaloneContextBootsWithPasswordCheckDisabledAndOAuthEnabled() throws Exception
    {
        // Discovery still reports oauth enabled per the standalone profile
        // ("keep the oauth stuff" — the SPA's login screen + Spring AS form
        // login still run, just with the server-side password check no-oped).
        mockMvc.perform(get("/api/auth/oauth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }
}
