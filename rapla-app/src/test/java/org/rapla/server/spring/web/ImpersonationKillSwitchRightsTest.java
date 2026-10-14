package org.rapla.server.spring.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 131 D11 — the kill switch {@code rapla.auth.impersonation.enabled=false} covers the annotation right too:
 * alice (right via {@code impersonated_by}) and the global admin are refused on both endpoints.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class, properties = "rapla.auth.impersonation.enabled=false")
@AutoConfigureMockMvc
class ImpersonationKillSwitchRightsTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ImpersonationKillSwitchRightsTest.class.getResourceAsStream("/testdefault.xml"))
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
    RaplaFacade facade;

    @Autowired
    CachableStorageOperator operator;

    @BeforeEach
    void seed() throws Exception
    {
        ImpersonationRightsFixture.seed(facade, operator);
    }

    @Test
    void bearerEndpointRefusesTheAnnotationRightAndTheAdmin() throws Exception
    {
        for (String[] login : new String[][] { { "alice", ImpersonationRightsFixture.PASSWORD }, { "homer", "duffs" } })
        {
            String token = OAuthTestSupport.loginAs(mockMvc, login[0], login[1]);
            MvcResult result = mockMvc.perform(post("/api/auth/impersonate")
                            .header("Authorization", "Bearer " + token)
                            .contentType("application/x-www-form-urlencoded")
                            .content("target_username=export"))
                    .andReturn();
            assertTrue(result.getResponse().getStatus() >= 400, login[0]);
            assertFalse(result.getResponse().getContentAsString().contains("access_token"), login[0]);
        }
    }

    @Test
    void cookieEndpointRefusesTheAnnotationRightAndTheAdmin() throws Exception
    {
        for (String[] login : new String[][] { { "alice", ImpersonationRightsFixture.PASSWORD }, { "homer", "duffs" } })
        {
            String token = OAuthTestSupport.loginAs(mockMvc, login[0], login[1]);
            MvcResult result = mockMvc.perform(post("/api/auth/impersonate/switch")
                            .with(csrf())
                            .cookie(new Cookie("access_token", token))
                            .header("X-Rapla-Act-As", "self")
                            .param("target_username", "export"))
                    .andReturn();
            assertTrue(result.getResponse().getStatus() >= 400, login[0]);
            assertNull(result.getResponse().getCookie("access_token"), login[0]);
        }
    }

    @Test
    void actAsRefusesEveryNonSelfValueButSelfStillWorks() throws Exception
    {
        for (String[] login : new String[][] { { "alice", ImpersonationRightsFixture.PASSWORD }, { "homer", "duffs" } })
        {
            String token = OAuthTestSupport.loginAs(mockMvc, login[0], login[1]);
            String exportId = operator.getUser("export").getId();
            assertTrue(mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", token))
                    .header("X-Rapla-Act-As", exportId)).andReturn().getResponse().getStatus() >= 400, login[0]);
            mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", token)).header("X-Rapla-Act-As", "self"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.impersonating").value(false));
        }
    }
}
