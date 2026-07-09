package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * PRD 100 Phase 5 — tier-3 tests for {@code AppointmentBlock.matchedBy} (match provenance, NO arg).
 *
 * <ol>
 *   <li><b>Filter-derived + belongsTo</b> — when the query is scoped to an allocatable id, the block
 *       bound to it reports that id in {@code matchedBy} (the query's own resolved scope is the pool,
 *       so it can't diverge from the filter that selected the block).</li>
 *   <li><b>Unscoped ⇒ empty</b> — with no allocatable scope on the filter, {@code matchedBy} is empty
 *       for every block (compact lanes; §12: the pool is the query's canRead-gated scope, so nothing
 *       unreadable can ever surface).</li>
 * </ol>
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class AppointmentBlockMatchedByGraphQLTest
{
    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = AppointmentBlockMatchedByGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml fixture missing from classpath");
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

    private static final String WINDOW = "from: \\\"2001-10-15T00:00:00\\\", to: \\\"2001-10-17T00:00:00\\\"";

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void scopedQueryReportsTheMatchedAllocatablePerBoundBlock() throws Exception
    {
        // discover an allocatable id a block in the window is bound to
        String discover = "{ appointmentBlocks(filter: { " + WINDOW + " }) { name allocatables { id } } }";
        String json = mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(gqlBody(discover)))
                .andReturn().getResponse().getContentAsString();
        List<String> allocIds = JsonPath.read(json, "$.data.appointmentBlocks[*].allocatables[0].id");
        assertFalse(allocIds.isEmpty(), "fixture window should yield a block with an allocatable");
        String allocId = allocIds.get(0);

        // scope the query to that id → the bound block's matchedBy contains it
        String scoped = "{ appointmentBlocks(filter: { " + WINDOW
                + ", allocatableIdsIn: [\\\"" + allocId + "\\\"] }) { name allocatables { id } matchedBy { id } } }";
        String result = mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(gqlBody(scoped)))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> blocks = JsonPath.read(result, "$.data.appointmentBlocks[*]");
        assertFalse(blocks.isEmpty(), "scoped query should still return the bound block(s): " + result);
        boolean sawMatch = false;
        for (Map<String, Object> b : blocks)
        {
            List<Map<String, Object>> matched = (List<Map<String, Object>>) b.get("matchedBy");
            List<Object> ids = matched.stream().map(m -> m.get("id")).toList();
            // every block returned under this scope is bound to allocId → matchedBy carries it
            assertTrue(ids.contains(allocId),
                    "block '" + b.get("name") + "' under scope " + allocId + " must report it in matchedBy; got " + result);
            sawMatch = true;
        }
        assertTrue(sawMatch);
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void unscopedQueryHasEmptyMatchedByEverywhere() throws Exception
    {
        String unscoped = "{ appointmentBlocks(filter: { " + WINDOW + " }) { name matchedBy { id } } }";
        String result = mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(gqlBody(unscoped)))
                .andReturn().getResponse().getContentAsString();
        List<List<Object>> matchedPerBlock = JsonPath.read(result, "$.data.appointmentBlocks[*].matchedBy");
        assertFalse(matchedPerBlock.isEmpty(), "window should return blocks: " + result);
        for (List<Object> m : matchedPerBlock)
        {
            assertTrue(m.isEmpty(), "unscoped query must yield empty matchedBy (compact); got " + result);
        }
    }

    private static String gqlBody(String query)
    {
        return "{\"query\":\"" + query.replace("\n", " ") + "\"}";
    }
}
