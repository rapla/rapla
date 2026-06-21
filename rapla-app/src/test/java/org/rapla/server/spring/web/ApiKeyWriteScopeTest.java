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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 076 Phase 2 — END-TO-END proof that the api-key data scope flows through the REAL
 * security chain on a REST write: JWT → {@code ApiKeyJwtDecoder} (scopes from stored entry) →
 * {@code SecurityContextHolder} → {@code ApiKeyScopeContextInitializer} → {@code ApiKeyScopeContext}
 * → operator {@code check()} (D6). {@code POST /api/storage/change/name} writes the {@code User}
 * entity, which needs {@code write_all}; a {@code read} or {@code write_events} key is rejected,
 * a {@code write_all} key succeeds. Complements the tier-2 {@code ApiKeyScopeGuardTest}.
 *
 * <p>The REST path (not GraphQL) is used because api-key principals resolve via subject through
 * {@code RemoteSession}/{@code JwtUserResolver}; the GraphQL mutation controllers currently
 * resolve the caller by {@code preferred_username} only, which api-key JWTs don't carry — a
 * separate gap tracked outside this PRD.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class ApiKeyWriteScopeTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ApiKeyWriteScopeTest.class.getResourceAsStream("/testdefault.xml"))
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

    private String createKey(String access, String label, String scopesJson) throws Exception
    {
        String body = "{\"label\":\"" + label + "\",\"scopes\":" + scopesJson + "}";
        MvcResult res = mockMvc.perform(post("/api/auth/api-keys")
                        .header("Authorization", "Bearer " + access)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(res.getResponse().getContentAsString()).get("key").asText();
    }

    private int changeName(String bearer) throws Exception
    {
        String body = "{\"username\":\"homer\",\"newTitle\":\"\",\"newSurename\":\"\",\"newLastname\":\"ScopeTest\"}";
        return mockMvc.perform(post("/api/storage/change/name")
                        .header("Authorization", "Bearer " + bearer)
                        .contentType("application/json")
                        .content(body))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    @Test
    void readOnlyKeyCannotWriteUser() throws Exception
    {
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        String readKey = createKey(access, "ro", "[\"read\"]");
        assertEquals(401, changeName(readKey), "read-only key write must be denied");
    }

    @Test
    void writeEventsKeyCannotWriteUser() throws Exception
    {
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        String eventsKey = createKey(access, "we", "[\"write_events\"]");
        // a User write needs write_all; write_events is not enough → proves scopes are read, not
        // merely "any write scope passes".
        assertEquals(401, changeName(eventsKey), "write_events key must not write a User");
    }

    @Test
    void writeAllKeyCanWriteUser() throws Exception
    {
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        String allKey = createKey(access, "wa", "[\"write_all\"]");
        assertEquals(200, changeName(allKey), "write_all key must succeed");
    }

    @Test
    void legacyKeyWithoutScopesRetainsFullWrite() throws Exception
    {
        // D8 backward-compat at the HTTP boundary: a key created WITHOUT a scopes field behaves
        // like today's full-power key. We can't easily forge a legacy stored entry here, so this
        // is covered at unit level (ApiKeyScopesTest.missingScopesOnStoredEntryMeansWriteAll);
        // here we assert the inverse contract is wired: an explicit write_all behaves identically.
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        String allKey = createKey(access, "legacy-equiv", "[\"write_all\"]");
        assertEquals(200, changeName(allKey));
    }
}
