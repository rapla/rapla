package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tier-3 MockMvc coverage of {@link org.rapla.server.spring.web.UserListsController}
 * (PRD 089 Phase 1): per-user isolation and the mandatory AGENTS.md §12 leak
 * test. Two accounts on one store get different lists; a list mixing visible,
 * hidden, and non-existent ids reads back byte-identical to the visible-only
 * subset.
 *
 * <p>Fixture: {@code testdefault.xml}. Users: {@code homer/duffs} (admin),
 * {@code monty/burns} (non-admin). Room A66 is publicly readable by all.
 * monty cannot {@code canAdminUser} homer, so homer's id as a {@code kind=user}
 * entry in monty's list is the "exists but you can't see it" case.
 */
@SpringBootTest(classes = {RaplaSpringBootApplication.class})
@AutoConfigureMockMvc
@Tag("e2e")
class UserListsControllerIntegrationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = UserListsControllerIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
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

    private static final String ROOM_A66   = "c24ce517-4697-4e52-9917-ec000c84563c";
    private static final String ROOM_ERWIN = "5521686b-0ab4-4ff4-a56e-0bdf148e8d1d";
    private static final String HOMER_ID    = "3f49044c-1469-4699-8a66-5f46ec6c0a41";
    private static final String UNKNOWN_ID  = "00000000-0000-0000-0000-000000000000";

    private String adminToken() throws Exception { return OAuthTestSupport.loginAs(mockMvc, "homer", "duffs"); }
    private String userToken()  throws Exception { return OAuthTestSupport.loginAs(mockMvc, "monty", "burns"); }

    private MvcResult postRecent(String token, String id, String kind) throws Exception
    {
        return mockMvc.perform(post("/api/recents")
                        .contentType("application/json")
                        .content("{\"id\":\"" + id + "\",\"kind\":\"" + kind + "\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
    }

    private MvcResult postFavorite(String token, String id, String kind) throws Exception
    {
        return mockMvc.perform(post("/api/favorites")
                        .contentType("application/json")
                        .content("{\"id\":\"" + id + "\",\"kind\":\"" + kind + "\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String getBody(String path, String token) throws Exception
    {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // ---------- auth ----------

    @Test
    void recentsRequireAuthentication() throws Exception
    {
        mockMvc.perform(get("/api/recents")).andExpect(status().isUnauthorized());
    }

    @Test
    void favoritesRequireAuthentication() throws Exception
    {
        mockMvc.perform(get("/api/favorites")).andExpect(status().isUnauthorized());
    }

    // ---------- happy path: add / read / clear ----------

    @Test
    void addRecentThenReadReturnsResolvedItem() throws Exception
    {
        String token = adminToken();
        mockMvc.perform(delete("/api/recents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        postRecent(token, ROOM_A66, "resource");

        mockMvc.perform(get("/api/recents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ROOM_A66))
                .andExpect(jsonPath("$[0].kind").value("resource"))
                .andExpect(jsonPath("$[0].label").value("Room A66"));
    }

    @Test
    void recentReTouchPromotesNewestFirst() throws Exception
    {
        String token = adminToken();
        mockMvc.perform(delete("/api/recents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        postRecent(token, ROOM_A66, "resource");
        postRecent(token, ROOM_ERWIN, "resource");
        // re-touch A66 → it must move back to the front
        postRecent(token, ROOM_A66, "resource");

        mockMvc.perform(get("/api/recents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(ROOM_A66))
                .andExpect(jsonPath("$[1].id").value(ROOM_ERWIN));
    }

    @Test
    void favoriteRemoveUnpins() throws Exception
    {
        String token = adminToken();
        postFavorite(token, ROOM_A66, "resource");
        postFavorite(token, ROOM_ERWIN, "resource");
        mockMvc.perform(delete("/api/favorites/" + ROOM_A66).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + ROOM_A66 + "')]").doesNotExist())
                .andExpect(jsonPath("$[?(@.id == '" + ROOM_ERWIN + "')]").exists());
    }

    // ---------- per-user isolation ----------

    @Test
    void twoUsersGetDifferentLists() throws Exception
    {
        String homer = adminToken();
        String monty = userToken();
        mockMvc.perform(delete("/api/recents").header("Authorization", "Bearer " + homer))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/recents").header("Authorization", "Bearer " + monty))
                .andExpect(status().isOk());

        postRecent(homer, ROOM_A66, "resource");
        postRecent(monty, ROOM_ERWIN, "resource");

        mockMvc.perform(get("/api/recents").header("Authorization", "Bearer " + homer))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ROOM_A66));
        mockMvc.perform(get("/api/recents").header("Authorization", "Bearer " + monty))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ROOM_ERWIN));
    }

    // ---------- AGENTS.md §12 leak test ----------

    /**
     * monty's recents list mixes a visible resource (Room A66), a hidden user
     * (homer — monty cannot canAdminUser him), and a non-existent id. The read
     * MUST be byte-identical to the visible-only subset: the hidden and the
     * non-existent entries are indistinguishable (both silently dropped).
     */
    @Test
    void recentsLeakTestMixedIdsReadIdenticalToVisibleSubset() throws Exception
    {
        String monty = userToken();

        // visible-only baseline list for a fresh user
        mockMvc.perform(delete("/api/recents").header("Authorization", "Bearer " + monty))
                .andExpect(status().isOk());
        postRecent(monty, ROOM_A66, "resource");
        String visibleOnly = getBody("/api/recents", monty);

        // now the mixed list: hidden user + non-existent + the same visible resource
        mockMvc.perform(delete("/api/recents").header("Authorization", "Bearer " + monty))
                .andExpect(status().isOk());
        postRecent(monty, ROOM_A66, "resource");
        postRecent(monty, HOMER_ID, "user");        // exists, but monty can't see homer
        postRecent(monty, UNKNOWN_ID, "resource");  // does not exist
        String mixed = getBody("/api/recents", monty);

        assertEquals(visibleOnly, mixed,
                "hidden + non-existent ids must drop out → read identical to visible-only subset");
    }

    @Test
    void hiddenUserAndUnknownIdAreIndistinguishableInRecents() throws Exception
    {
        String monty = userToken();

        mockMvc.perform(delete("/api/recents").header("Authorization", "Bearer " + monty))
                .andExpect(status().isOk());
        postRecent(monty, HOMER_ID, "user");
        String hiddenOnly = getBody("/api/recents", monty);

        mockMvc.perform(delete("/api/recents").header("Authorization", "Bearer " + monty))
                .andExpect(status().isOk());
        postRecent(monty, UNKNOWN_ID, "user");
        String unknownOnly = getBody("/api/recents", monty);

        assertEquals(unknownOnly, hiddenOnly,
                "a hidden-but-existing id and a non-existent id must read identically");
        assertEquals("[]", hiddenOnly);
    }

    @Test
    void favoritesLeakTestMixedIdsReadIdenticalToVisibleSubset() throws Exception
    {
        String monty = userToken();

        mockMvc.perform(delete("/api/favorites/" + ROOM_A66).header("Authorization", "Bearer " + monty));
        mockMvc.perform(delete("/api/favorites/" + HOMER_ID).header("Authorization", "Bearer " + monty));
        mockMvc.perform(delete("/api/favorites/" + UNKNOWN_ID).header("Authorization", "Bearer " + monty));

        postFavorite(monty, ROOM_A66, "resource");
        String visibleOnly = getBody("/api/favorites", monty);

        postFavorite(monty, HOMER_ID, "user");
        postFavorite(monty, UNKNOWN_ID, "resource");
        String mixed = getBody("/api/favorites", monty);

        assertEquals(visibleOnly, mixed,
                "hidden + non-existent favorites must drop out → read identical to visible-only subset");
    }
}
