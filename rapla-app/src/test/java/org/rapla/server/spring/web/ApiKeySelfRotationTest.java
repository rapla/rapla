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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 076 Phase 3 — self-rotation (D7 grace, D10 endpoint-binding, D3 no-escalation).
 * A {@code rotate_self} key rotates ITS OWN key into a same-scope successor; the old key keeps
 * working for a short grace window then expires (D9). A key without {@code rotate_self} cannot
 * rotate, cannot rotate another key, and no api-key may reach the generic create endpoint (D10).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class ApiKeySelfRotationTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ApiKeySelfRotationTest.class.getResourceAsStream("/testdefault.xml"))
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

    private JsonNode createKey(String access, String label, String scopesJson) throws Exception
    {
        String body = "{\"label\":\"" + label + "\",\"scopes\":" + scopesJson + "}";
        MvcResult res = mockMvc.perform(post("/api/auth/api-keys")
                        .header("Authorization", "Bearer " + access)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(res.getResponse().getContentAsString());
    }

    private MvcResult rotate(String bearer, String id, Long graceMinutes) throws Exception
    {
        String url = "/api/auth/api-keys/" + id + "/rotate"
                + (graceMinutes == null ? "" : "?graceMinutes=" + graceMinutes);
        return mockMvc.perform(post(url).header("Authorization", "Bearer " + bearer)).andReturn();
    }

    private int getResources(String bearer) throws Exception
    {
        return mockMvc.perform(get("/api/storage/resources").header("Authorization", "Bearer " + bearer))
                .andReturn().getResponse().getStatus();
    }

    private static List<String> scopesOf(JsonNode node)
    {
        List<String> out = new ArrayList<>();
        for (JsonNode s : node.get("scopes")) out.add(s.asText());
        return out;
    }

    @Test
    void rotateSelfIssuesSameScopeSuccessor() throws Exception
    {
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        JsonNode key = createKey(access, "rot", "[\"read\",\"rotate_self\"]");
        String oldId = key.get("id").asText();
        String oldJwt = key.get("key").asText();

        MvcResult res = rotate(oldJwt, oldId, null);
        assertEquals(200, res.getResponse().getStatus(), "rotate_self key must rotate itself");
        JsonNode succ = MAPPER.readTree(res.getResponse().getContentAsString());

        // Successor inherits the scope set EXACTLY — including rotate_self, so it can be rotated again
        // later (D3 no-escalation; D13 strips rotate_self from the OLD key, not the new one).
        assertEquals(java.util.Set.of("read", "rotate_self"), new java.util.HashSet<>(scopesOf(succ)),
                "successor keeps rotate_self so rotation can repeat");
        assertNotEquals(oldId, succ.get("id").asText(), "successor must be a new key");
        // successor authenticates
        assertEquals(200, getResources(succ.get("key").asText()));
    }

    @Test
    void oldKeyValidWithinGraceThenRejected() throws Exception
    {
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        JsonNode key = createKey(access, "grace", "[\"read\",\"rotate_self\"]");
        String oldId = key.get("id").asText();
        String oldJwt = key.get("key").asText();

        rotate(oldJwt, oldId, null); // default grace (180 min) keeps the old key alive
        assertEquals(200, getResources(oldJwt), "old key must still work within the grace window");

        // A fresh key rotated with graceMinutes=0 expires immediately (minute-granularity grace
        // makes a sub-second timing test impractical otherwise).
        JsonNode key2 = createKey(access, "grace0", "[\"read\",\"rotate_self\"]");
        String old2 = key2.get("key").asText();
        rotate(old2, key2.get("id").asText(), 0L);
        Thread.sleep(50);
        assertEquals(401, getResources(old2), "old key must be rejected once the grace window is 0/past");
    }

    @Test
    void rotatedOldKeyLosesRotateSelfButSuccessorKeepsIt() throws Exception
    {
        // D13 — after rotation the OLD key can no longer self-rotate (rotate_self stripped from its
        // stored scopes), but the SUCCESSOR can rotate again. Otherwise you could only ever rotate once.
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        JsonNode key = createKey(access, "chain", "[\"read\",\"rotate_self\"]");
        String oldId = key.get("id").asText();
        String oldJwt = key.get("key").asText();

        JsonNode succ = MAPPER.readTree(rotate(oldJwt, oldId, null).getResponse().getContentAsString());

        // The OLD key still authenticates within grace, but may NOT self-rotate again.
        assertEquals(200, getResources(oldJwt), "old key still valid within grace");
        assertEquals(401, rotate(oldJwt, oldId, null).getResponse().getStatus(),
                "old key lost rotate_self — cannot self-rotate again (D13)");

        // The successor keeps rotate_self, but rotating it is blocked while the old grace token still
        // lives (D14 max-2 cap) — delete the predecessor first, then rotation repeats.
        assertEquals(409, rotate(succ.get("key").asText(), succ.get("id").asText(), null).getResponse().getStatus(),
                "second rotation blocked while predecessor still in grace (D14)");
        mockMvc.perform(delete("/api/auth/api-keys/" + oldId).header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());
        assertEquals(200, rotate(succ.get("key").asText(), succ.get("id").asText(), null).getResponse().getStatus(),
                "successor keeps rotate_self — rotation repeats once the predecessor is gone");
    }

    @Test
    void keyWithoutRotateSelfCannotRotate() throws Exception
    {
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        JsonNode key = createKey(access, "ro", "[\"read\"]");
        MvcResult res = rotate(key.get("key").asText(), key.get("id").asText(), null);
        assertEquals(401, res.getResponse().getStatus(), "a key without rotate_self must not rotate");
    }

    @Test
    void cannotRotateADifferentKey() throws Exception
    {
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        JsonNode k1 = createKey(access, "k1", "[\"read\",\"rotate_self\"]");
        JsonNode k2 = createKey(access, "k2", "[\"read\",\"rotate_self\"]");
        // k1 tries to rotate k2's id → rejected (a key may only rotate ITSELF)
        MvcResult res = rotate(k1.get("key").asText(), k2.get("id").asText(), null);
        assertEquals(401, res.getResponse().getStatus(), "a key may only rotate its own id");
    }

    @Test
    void apiKeyCannotReachGenericCreate() throws Exception
    {
        // D10 — rotate_self is endpoint-bound; it must NOT let an api-key mint a fresh (possibly
        // write_all) key via the generic create endpoint.
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        JsonNode key = createKey(access, "rot", "[\"read\",\"rotate_self\"]");
        MvcResult res = mockMvc.perform(post("/api/auth/api-keys")
                        .header("Authorization", "Bearer " + key.get("key").asText())
                        .contentType("application/json")
                        .content("{\"label\":\"escalate\",\"scopes\":[\"write_all\"]}"))
                .andReturn();
        assertEquals(401, res.getResponse().getStatus(), "an api-key must not mint other keys");
    }
}
