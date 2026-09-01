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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 109 Phase 1 — tier-3 proof that {@link LegacyPathFilter} is wired ahead of the
 * Spring Security chain: the legacy prefix must not become an authentication bypass.
 * The tier-1 {@code LegacyPathFilterTest} covers the mapping table itself.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class,
        properties = "rapla.legacy-context-path=/wochenplan")
@AutoConfigureMockMvc
class LegacyPathIntegrationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = LegacyPathIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
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

    @Test
    void indexPageAnswersUnderTheLegacyPrefixWithoutRedirect() throws Exception
    {
        mockMvc.perform(get("/wochenplan/")).andExpect(status().isOk());
        mockMvc.perform(get("/index")).andExpect(status().isOk());
    }

    @Test
    void rewrittenPathsBehaveIdenticallyToTheCanonicalOnes() throws Exception
    {
        // internal_calendar is permitAll in SecurityConfig — its access control lives in
        // CalendarPageController (internal_request flag + user parameter), not in the
        // security chain. The invariant is therefore equivalence, not a fixed status.
        for (String path : new String[] { "/rapla/calendar", "/rapla/internal_calendar", "/index" })
        {
            int canonical = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
            mockMvc.perform(get("/wochenplan" + path)).andExpect(status().is(canonical));
        }
    }

    @Test
    void theLegacyPrefixIsNotAnAuthenticationBypass() throws Exception
    {
        // /graphiql is .authenticated() on the canonical path…
        int canonical = mockMvc.perform(get("/graphiql")).andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertNotEquals(200, canonical,
                "/graphiql must be gated — test premise broken");
        // …and it is NOT in the verbatim map, so the prefix cannot reach it unrewritten:
        // it falls through to the 301 onto the canonical (still gated) path.
        mockMvc.perform(get("/wochenplan/graphiql"))
                .andExpect(status().isMovedPermanently())
                .andExpect(redirectedUrl("/graphiql"));
    }

    @Test
    void unmappedLegacyPathsRedirectToTheCanonicalPath() throws Exception
    {
        mockMvc.perform(get("/wochenplan/rapla/storage/refresh"))
                .andExpect(status().isMovedPermanently())
                .andExpect(redirectedUrl("/rapla/storage/refresh"));
        mockMvc.perform(get("/wochenplan/rapla/raplaclient.jnlp"))
                .andExpect(status().isMovedPermanently())
                .andExpect(redirectedUrl("/raplaclient.jnlp"));
    }
}
