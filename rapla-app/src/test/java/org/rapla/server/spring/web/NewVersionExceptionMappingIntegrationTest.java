package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.RaplaNewVersionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 026 §B2 — tier-3 round-trip verification that a controller throwing
 * {@link RaplaNewVersionException} actually reaches the client as
 * <b>HTTP 409 Conflict</b> through the live {@link RaplaExceptionHandler}.
 * <p>
 * The SPA depends on this status code to drive its "refresh and retry"
 * flow on concurrent-modification — without the dedicated handler, the
 * exception would fall through to the 500 catch-all and become
 * indistinguishable from a real server fault.
 */
@SpringBootTest(classes = {RaplaSpringBootApplication.class,
        NewVersionExceptionMappingIntegrationTest.TestRouteConfig.class})
@AutoConfigureMockMvc
@Tag("e2e")
class NewVersionExceptionMappingIntegrationTest
{
    @TempDir static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = NewVersionExceptionMappingIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in);
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired MockMvc mockMvc;
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void controllerThrowingNewVersionExceptionReaches409() throws Exception
    {
        mockMvc.perform(get("/api/__test/throw-new-version")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Reservation 'My Event' was modified by another user"));
    }

    private String adminToken() throws Exception
    {
        MvcResult mvc = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"homer\",\"password\":\"duffs\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode tree = json.readTree(mvc.getResponse().getContentAsString());
        return tree.get("accessToken").asString();
    }

    /** Test-only route that throws {@link RaplaNewVersionException} on demand.
     *  Registered as a {@code @TestConfiguration} so it doesn't leak into
     *  production. The route path uses a {@code /__test/} prefix to avoid
     *  shadowing any real endpoint. The {@code @RestController} stereotype
     *  on the inner class is enough — Spring discovers it via component
     *  scan when {@link TestRouteConfig} is included in {@code @SpringBootTest.classes}. */
    @TestConfiguration
    static class TestRouteConfig
    {
        @RestController
        @RequestMapping("/__test")
        static class ThrowingController
        {
            @GetMapping("/throw-new-version")
            public String throwNewVersion() throws RaplaNewVersionException
            {
                throw new RaplaNewVersionException(
                        "Reservation 'My Event' was modified by another user");
            }
        }
    }
}
