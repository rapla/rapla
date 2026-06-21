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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 076 Phase 1 — a created key carries a scope set, defaulting to least-privilege
 * {@code {read}} (D5), echoed on create + list, and validated against the fixed vocabulary.
 * Enforcement of those scopes on writes is Phase 2 ({@code ApiKeyWriteScopeTest}).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class ApiKeyScopeTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ApiKeyScopeTest.class.getResourceAsStream("/testdefault.xml"))
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

    private MvcResult create(String access, String bodyJson) throws Exception
    {
        return mockMvc.perform(post("/api/auth/api-keys")
                        .header("Authorization", "Bearer " + access)
                        .contentType("application/json")
                        .content(bodyJson))
                .andReturn();
    }

    @Test
    void createWithoutScopesDefaultsToReadOnly() throws Exception
    {
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        MvcResult res = create(access, "{\"label\":\"default-scope\"}");
        assertEquals(200, res.getResponse().getStatus());
        JsonNode created = MAPPER.readTree(res.getResponse().getContentAsString());
        assertEquals(Set.of("read"), scopeSet(created), "new key with no scopes ⇒ {read}");

        // and the listing echoes the same scope set
        String thumb = created.get("thumbprint").asText();
        JsonNode listed = listKeys(access);
        JsonNode mine = findById(listed, thumb);
        assertNotNull(mine);
        assertEquals(Set.of("read"), scopeSet(mine));
    }

    @Test
    void explicitScopesAreStoredAndEchoed() throws Exception
    {
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        MvcResult res = create(access, "{\"label\":\"events-rot\",\"scopes\":[\"write_events\",\"rotate_self\"]}");
        assertEquals(200, res.getResponse().getStatus());
        JsonNode created = MAPPER.readTree(res.getResponse().getContentAsString());
        assertEquals(Set.of("write_events", "rotate_self"), scopeSet(created));

        // a scoped key still authenticates on a read endpoint (decoder resolves the entry)
        mockMvc.perform(get("/api/storage/resources")
                        .header("Authorization", "Bearer " + created.get("key").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void unknownScopeIsRejectedWith400() throws Exception
    {
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        MvcResult res = create(access, "{\"label\":\"bad\",\"scopes\":[\"read\",\"superuser\"]}");
        assertEquals(400, res.getResponse().getStatus(), "unknown scope ⇒ 400 Bad Request");
    }

    private JsonNode listKeys(String access) throws Exception
    {
        MvcResult listed = mockMvc.perform(get("/api/auth/api-keys")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(listed.getResponse().getContentAsString());
    }

    private static Set<String> scopeSet(JsonNode node)
    {
        JsonNode arr = node.get("scopes");
        assertNotNull(arr, "response must carry a scopes array");
        Set<String> out = new java.util.HashSet<>();
        for (JsonNode s : arr) out.add(s.asText());
        return out;
    }

    private static JsonNode findById(JsonNode list, String id)
    {
        for (JsonNode node : list)
        {
            if (id.equals(node.get("id").asText())) return node;
        }
        return null;
    }
}
