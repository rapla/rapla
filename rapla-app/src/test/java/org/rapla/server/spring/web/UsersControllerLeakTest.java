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
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 051 — tier-3 MockMvc coverage for {@code GET /api/users}.
 *
 * <p>The endpoint is the SPA's "Switch to user" dialog typeahead source.
 * AGENTS.md §12 (data-leak-prevention) mandates two invariants:
 *
 * <ul>
 *   <li><b>Existence-leak guard:</b> users the caller cannot admin
 *       (via {@link org.rapla.storage.PermissionController#canAdminUser})
 *       MUST NOT appear in the response. A non-admin caller gets an
 *       empty list — the existence of admin-able users is itself
 *       information the caller is not entitled to.</li>
 *   <li><b>Per-field shape guard:</b> only {@code username} and
 *       {@code displayName} on the wire. No email, no group list, no
 *       preferences. Per-user data leak surface is the dropdown
 *       label only.</li>
 * </ul>
 *
 * <p>testdefault.xml fixture:
 * <ul>
 *   <li><b>homer</b> — {@code isAdmin=true} (global admin)</li>
 *   <li><b>monty</b> — group-admin via {@code powerplant-admins}
 *       ({@code can_admin_parent=true} on the parent)</li>
 *   <li>plus the bundled bootstrap users from {@code data.xml}.</li>
 * </ul>
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class UsersControllerLeakTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = UsersControllerLeakTest.class.getResourceAsStream("/testdefault.xml"))
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
    void anonymousReturns401() throws Exception
    {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meRequiresAuthentication() throws Exception
    {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsCallerIdentity() throws Exception
    {
        String adminToken = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("homer"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.displayName").exists());
    }

    @Test
    void meReturnsTheCallersOwnRecordEvenForNonAdmin() throws Exception
    {
        // The endpoint isn't gated by canAdminUser — every authenticated
        // caller can ask "who am I". Verifies a non-admin (monty here, group
        // admin but not global) gets HIS own record, not someone else's,
        // and that the id is non-empty.
        String montyToken = OAuthTestSupport.loginAs(mockMvc, "monty", "burns");
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + montyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("monty"))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void globalAdminSeesUserList() throws Exception
    {
        String adminToken = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");

        MvcResult mvc = mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        List<String> usernames = extractUsernames(mvc);
        // monty (group-admin / non-global) must be visible; homer (self)
        // is also included per the design ("self in list").
        assertTrue(usernames.contains("monty"),
                "global admin should see non-admin user monty in the list, got " + usernames);
        assertTrue(usernames.contains("homer"),
                "self (calling user) is included in the list — got " + usernames);
    }

    @Test
    void groupAdminSeesOnlyTheirScope() throws Exception
    {
        String groupAdminToken = OAuthTestSupport.loginAs(mockMvc, "monty", "burns");

        MvcResult mvc = mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + groupAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        List<String> usernames = extractUsernames(mvc);
        // monty is admin of /powerplant via the can_admin_parent
        // annotation on /powerplant/powerplant-admins. Self is in the
        // list. The global admin (homer) MUST NOT appear — canAdminUser
        // returns false for any target whose isAdmin=true.
        assertTrue(usernames.contains("monty"),
                "group admin sees self — got " + usernames);
        assertFalse(usernames.contains("homer"),
                "group admin must NOT see the global admin (existence leak) — got " + usernames);
    }

    @Test
    void responseShapeOnlyExposesUsernameAndDisplayName() throws Exception
    {
        String adminToken = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");

        MvcResult mvc = mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode tree = JsonMapper.builder().build().readTree(mvc.getResponse().getContentAsString());
        assertTrue(tree.isArray());
        assertTrue(tree.size() > 0, "fixture should have at least one user the admin can admin");
        for (JsonNode entry : tree)
        {
            // Whitelist: ONLY username + displayName allowed.
            for (String field : entry.propertyNames())
            {
                assertTrue(field.equals("username") || field.equals("displayName"),
                        "unexpected wire field '" + field + "' on user DTO — must not leak per AGENTS.md §12");
            }
            assertTrue(entry.has("username"), "every entry must carry username");
            // displayName may be empty string (e.g. testdefault.xml users have name=""),
            // but the field itself must be present.
            assertTrue(entry.has("displayName"), "every entry must carry displayName (may be empty)");
        }
    }

    @Test
    void responseDoesNotLeakEmailOrGroups() throws Exception
    {
        // Regression: even if a future code change accidentally returns the
        // full UserImpl, the existing serialiser might expose email / groups
        // / preferences. Explicit assertion that those substrings do NOT
        // appear in the response body.
        String adminToken = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");

        MvcResult mvc = mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = mvc.getResponse().getContentAsString();
        // monty's email in testdefault.xml is monty@rapla.dummy.rapla;
        // if it ever appears in this body, the DTO is leaking.
        assertFalse(body.contains("monty@rapla.dummy.rapla"),
                "response body must not include any user's email — leaked: " + body);
        assertFalse(body.contains("powerplant-admins"),
                "response body must not include group / category keys — leaked: " + body);
        assertFalse(body.contains("\"email\""),
                "response body must not contain the literal 'email' field — leaked: " + body);
        assertFalse(body.contains("\"groups\""),
                "response body must not contain the literal 'groups' field — leaked: " + body);
        assertFalse(body.contains("\"isAdmin\""),
                "response body must not contain the literal 'isAdmin' field — leaked: " + body);
    }

    private List<String> extractUsernames(MvcResult mvc) throws Exception
    {
        JsonNode tree = JsonMapper.builder().build().readTree(mvc.getResponse().getContentAsString());
        assertTrue(tree.isArray(), "expected JSON array, got " + tree.getNodeType());
        List<String> out = new ArrayList<>();
        for (JsonNode entry : tree)
        {
            out.add(entry.get("username").asString());
        }
        return out;
    }
}
