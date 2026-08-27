package org.rapla.server.spring.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.StorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 2026-08-12 — the SPA's stored-view recovery contract: the FIRST query (before the variable
 * signature is known) sends {@code {}}. Since PRD 097 D8 a view's stored defaultVariables no
 * longer fill missing variables, so a view with a second NonNull variable (the Raumauslastung
 * shape: {@code $allocatableFilter: AllocatableFilter!}) fails that first query with a variable
 * coercion error. The response MUST still carry {@code extensions.view} (incl. the variable
 * signature) — that is what lets the SPA re-query with properly bound variables instead of being
 * stuck on the error forever.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class ViewMetaOnErrorTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ViewMetaOnErrorTest.class.getResourceAsStream("/testdefault.xml"))
        {
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired MockMvc mockMvc;
    @Autowired ViewCatalogService views;
    @Autowired StorageOperator operator;

    @BeforeEach
    void seed() throws Exception
    {
        User admin = operator.getUser("homer");
        String twoFilters = """
                query meta_err($filter: ReservationFilter!, $allocatableFilter: AllocatableFilter!)
                  @view(title: "MetaErr")
                {
                  reservations(filter: $filter) { name }
                  rooms: allocatables(filter: $allocatableFilter) { name }
                }""";
        assertEquals(List.of(), views.saveView("meta_err", twoFilters, true, List.of(), null, admin));
    }

    @Test
    @WithMockUser(username = "homer")
    void aFailingFirstQueryStillCarriesTheVariableSignature() throws Exception
    {
        String body = """
                {"operationName":"meta_err","query":"{__typename}",
                 "extensions":{"storedView":true},"variables":{}}""";
        String response = mockMvc.perform(post("/api/graphql")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = JsonMapper.builder().build().readTree(response);
        assertTrue(json.get("errors") != null && json.get("errors").size() > 0,
                "the {} first query must fail on the NonNull allocatableFilter: " + response);
        JsonNode view = json.path("extensions").path("view");
        assertTrue(!view.isMissingNode() && !view.isNull(),
                "extensions.view must arrive WITH the error so the SPA can recover: " + response);
        JsonNode variables = view.path("variables");
        assertNotNull(variables, response);
        boolean hasAllocatableFilter = false;
        for (JsonNode v : variables)
        {
            if ("allocatableFilter".equals(v.path("name").asString())
                    && "AllocatableFilter!".equals(v.path("type").asString()))
            {
                hasAllocatableFilter = true;
            }
        }
        assertTrue(hasAllocatableFilter,
                "the signature must name the unfilled variable + type: " + response);
    }
}
