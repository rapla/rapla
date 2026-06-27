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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 076 Phase 6 (D11/D12) — interactive rotation from a human cookie/user session.
 *
 * <p>The shipped self-rotation (Phase 3, {@link ApiKeySelfRotationTest}) is machine-only — the
 * api-key rotates ITSELF via its own credential. D12 lets the USER rotate any key they own from
 * their interactive session (the SPA "Manage API keys" UI). This covers: a user rotating their own
 * key, the {@code graceMinutes} 2-day cap (400), no cross-user / existence leak (§12), and D11
 * expired-entry pruning from {@code list()}.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class ApiKeyInteractiveRotationTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ApiKeyInteractiveRotationTest.class.getResourceAsStream("/testdefault.xml"))
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

    /** Create a ROTATABLE key (read + rotate_self) as the given user (interactive Bearer access token). */
    private JsonNode createKey(String access, String label) throws Exception
    {
        return createKey(access, label, "[\"read\",\"rotate_self\"]");
    }

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

    /** Rotate with the caller's INTERACTIVE access token (not an api-key JWT). */
    private MvcResult rotate(String access, String id, Long graceMinutes) throws Exception
    {
        String url = "/api/auth/api-keys/" + id + "/rotate"
                + (graceMinutes == null ? "" : "?graceMinutes=" + graceMinutes);
        return mockMvc.perform(post(url).header("Authorization", "Bearer " + access)).andReturn();
    }

    private int getResources(String bearer) throws Exception
    {
        return mockMvc.perform(get("/api/storage/resources").header("Authorization", "Bearer " + bearer))
                .andReturn().getResponse().getStatus();
    }

    private JsonNode list(String access) throws Exception
    {
        MvcResult res = mockMvc.perform(get("/api/auth/api-keys").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andReturn();
        return MAPPER.readTree(res.getResponse().getContentAsString());
    }

    @Test
    void humanRotatesOwnKey() throws Exception
    {
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        JsonNode key = createKey(access, "ui");
        String oldId = key.get("id").asText();
        String oldJwt = key.get("key").asText();

        // Rotate with the USER's access token (no rotate_self scope, no kid match) — D12 allows it.
        MvcResult res = rotate(access, oldId, null);
        assertEquals(200, res.getResponse().getStatus(), "a user may rotate a key they own");
        JsonNode succ = MAPPER.readTree(res.getResponse().getContentAsString());

        assertEquals(200, getResources(succ.get("key").asText()), "successor must authenticate");
        assertEquals(200, getResources(oldJwt), "old key valid within the default grace window");
    }

    @Test
    void readOnlyKeyIsNotRotatable() throws Exception
    {
        // D15 (Model B) — a key without rotate_self is NOT rotatable, even by its owner via the UI.
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        JsonNode readOnly = createKey(access, "ro", "[\"read\"]");
        assertEquals(401, rotate(access, readOnly.get("id").asText(), null).getResponse().getStatus(),
                "a read-only key (no rotate_self) cannot be rotated");
    }

    @Test
    void rotatedOldKeyIsNoLongerRotatable() throws Exception
    {
        // D13 + D15 — after rotation the OLD key loses rotate_self in its STORED entry, so even
        // holding its (scope-less, D8) JWT you cannot rotate it again: only the latest token rotates.
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        JsonNode a = createKey(access, "chain");
        String aId = a.get("id").asText();
        rotate(access, aId, 180L); // A -> B; A grace-degraded, rotate_self stripped from A
        assertEquals(401, rotate(access, aId, 180L).getResponse().getStatus(),
                "the rotated-away old key is no longer rotatable (rotate_self stripped, D13/D15)");
    }

    @Test
    void graceMinutesAboveTwoDaysRejected() throws Exception
    {
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        JsonNode key = createKey(access, "cap");
        MvcResult res = rotate(access, key.get("id").asText(), 2881L); // 2 days + 1 min
        assertEquals(400, res.getResponse().getStatus(), "graceMinutes > 2 days must be a 400");
    }

    @Test
    void cannotRotateAnotherUsersKeyAndItLooksLikeNotFound() throws Exception
    {
        // §12 — rotating a key the caller does not own must be indistinguishable from a nonexistent id.
        String homer = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        String monty = OAuthTestSupport.loginAs(mockMvc, "monty", "burns");
        JsonNode montysKey = createKey(monty, "montys");

        int crossUser = rotate(homer, montysKey.get("id").asText(), null).getResponse().getStatus();
        int nonexistent = rotate(homer, "does-not-exist-kid", null).getResponse().getStatus();

        assertEquals(nonexistent, crossUser, "another user's key id must look exactly like a nonexistent id");
        assertEquals(401, crossUser, "neither may rotate");
        // And monty's key is untouched — still authenticates.
        assertEquals(200, getResources(montysKey.get("key").asText()));
    }

    @Test
    void chainIsCappedAtTwoLiveTokens() throws Exception
    {
        // D14 sprawl guardrail — at most 2 live tokens per rotation chain (active + one grace
        // predecessor). A second rotation is refused (409) until the prior grace token is deleted.
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        JsonNode a = createKey(access, "chain");
        String aId = a.get("id").asText();

        JsonNode b = MAPPER.readTree(rotate(access, aId, 180L).getResponse().getContentAsString());
        String bId = b.get("id").asText();

        // A is still in its grace window → rotating B again would make 3 live tokens → 409.
        assertEquals(409, rotate(access, bId, 180L).getResponse().getStatus(),
                "second rotation blocked while the prior grace token is still alive");

        // Delete the old grace token, and rotation is allowed again (back to ≤2).
        mockMvc.perform(delete("/api/auth/api-keys/" + aId).header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());
        assertEquals(200, rotate(access, bId, 180L).getResponse().getStatus(),
                "after deleting the predecessor, rotation is allowed again");
    }

    @Test
    void expiredKeyIsPrunedFromList() throws Exception
    {
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        JsonNode key = createKey(access, "prune");
        String oldId = key.get("id").asText();

        // grace=0 → the old key's exp is set to now; it drops out of list() once now passes it (D11).
        JsonNode succ = MAPPER.readTree(rotate(access, oldId, 0L).getResponse().getContentAsString());
        String succId = succ.get("id").asText();
        Thread.sleep(50);

        JsonNode listed = list(access);
        boolean oldPresent = false;
        boolean succPresent = false;
        for (JsonNode k : listed)
        {
            if (oldId.equals(k.get("id").asText())) oldPresent = true;
            if (succId.equals(k.get("id").asText())) succPresent = true;
        }
        assertFalse(oldPresent, "the expired old key must not appear in list()");
        assertTrue(succPresent, "the live successor must appear in list()");
    }
}
