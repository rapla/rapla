package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 035 testbed — tier-3 GraphQL resolver tests via MockMvc-bound
 * {@link HttpGraphQlTester}. Goes through the full Spring Security filter
 * chain on each request, so {@link WithMockUser} / {@link WithAnonymousUser}
 * actually reach the resolver (unlike the ExecutionGraphQlServiceTester path
 * earlier, where Reactor scheduling broke ThreadLocal-based propagation).
 *
 * <p>Data fixture: {@code testdefault.xml} copied to a per-class {@link TempDir}
 * via {@link DynamicPropertySource}. The fixture's admin user is {@code homer};
 * {@code monty} is a non-admin. (See {@code rapla-app/src/test/resources/testdefault.xml}.)
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)   // bypass Spring Security filter chain so @WithMockUser
                                            // actually reaches the resolver — see in-class comment
class HelloGraphQLControllerTest
{
    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = HelloGraphQLControllerTest.class.getResourceAsStream("/testdefault.xml"))
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

    HttpGraphQlTester tester;

    @BeforeEach
    void setUp()
    {
        WebTestClient client = MockMvcWebTestClient.bindTo(mockMvc).build();
        tester = HttpGraphQlTester.builder(client.mutate())
                .url("/api/graphql")
                .build();
    }

    // --- trivial probes -------------------------------------------------------

    @Test
    void helloEchoesName()
    {
        tester.document("{ hello(name: \"rapla\") }")
              .execute()
              .path("hello").entity(String.class).isEqualTo("Hello, rapla!");
    }

    @Test
    void helloUsesDefaultWhenNameOmitted()
    {
        tester.document("{ hello }")
              .execute()
              .path("hello").entity(String.class).isEqualTo("Hello, world!");
    }

    @Test
    void versionReturnsCurrentRaplaVersion()
    {
        tester.document("{ version }")
              .execute()
              .path("version").entity(String.class).isEqualTo("2.1-SNAPSHOT");
    }

    @Test
    void serverTimeReturnsAnIsoTimestamp()
    {
        String t = tester.document("{ serverTime }")
                         .execute()
                         .path("serverTime").entity(String.class).get();
        assertFalse(t.isBlank(), "serverTime must not be blank");
        assertFalse(!t.contains("T"), "expected ISO-8601 with 'T' separator, got: " + t);
    }

    // --- me -------------------------------------------------------------------

    @Test
    @WithAnonymousUser
    void anonymousMeReturnsNull()
    {
        tester.document("{ me { username name email isAdmin } }")
              .execute()
              .path("me").valueIsNull();
    }

    @Test
    @WithMockUser(username = "homer")
    void authenticatedMeReturnsHomer()
    {
        tester.document("{ me { username isAdmin } }")
              .execute()
              .path("me.username").entity(String.class).isEqualTo("homer")
              .path("me.isAdmin").entity(Boolean.class).isEqualTo(true);
    }

    // --- users ---------------------------------------------------------------

    @Test
    @WithAnonymousUser
    void anonymousUsersRejected()
    {
        // No caller → UNAUTHENTICATED error, not a silent empty list.
        tester.document("{ users { username } }")
              .execute()
              .errors()
              .satisfy(errs -> {
                  assertFalse(errs.isEmpty(), "anonymous users query must error");
                  assertTrue(errs.toString().contains("UNAUTHENTICATED"),
                          () -> "expected UNAUTHENTICATED; got " + errs);
              });
    }

    @Test
    @WithMockUser(username = "homer")
    void adminSeesUsersInTestFixture()
    {
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Map<String, Object>> users =
                (List<Map<String, Object>>) (List) tester
                        .document("{ users { username isAdmin } }")
                        .execute()
                        .path("users").entityList(Map.class).get();
        assertFalse(users.isEmpty(), "homer (admin) must see at least itself");
        assertFalse(users.stream().noneMatch(u -> "homer".equals(u.get("username"))),
                "homer must see homer in the users list, got: " + users);
    }

    @Test
    @WithAnonymousUser
    void anonymousUserByUsernameRejected()
    {
        tester.document("{ user(username: \"homer\") { username } }")
              .execute()
              .errors()
              .satisfy(errs -> {
                  assertFalse(errs.isEmpty(), "anonymous user query must error");
                  assertTrue(errs.toString().contains("UNAUTHENTICATED"),
                          () -> "expected UNAUTHENTICATED; got " + errs);
              });
    }

    @Test
    @WithMockUser(username = "homer")
    void adminUserByUsernameReturnsHomer()
    {
        tester.document("{ user(username: \"homer\") { username isAdmin } }")
              .execute()
              .path("user.username").entity(String.class).isEqualTo("homer")
              .path("user.isAdmin").entity(Boolean.class).isEqualTo(true);
    }

    @Test
    @WithAnonymousUser
    void anonymousUserByUnknownUsernameRejected()
    {
        // Auth is checked before existence — anonymous always errors, never
        // leaks "no such user" vs "exists but hidden".
        tester.document("{ user(username: \"definitely-not-a-real-user\") { username } }")
              .execute()
              .errors()
              .satisfy(errs -> {
                  assertFalse(errs.isEmpty(), "anonymous user query must error");
                  assertTrue(errs.toString().contains("UNAUTHENTICATED"),
                          () -> "expected UNAUTHENTICATED; got " + errs);
              });
    }

    // --- §12: non-admin caller always sees themselves -----------------------

    @Test
    @WithMockUser(username = "monty")     // testdefault.xml: non-admin (isAdmin="false")
    void nonAdminSeesSelfInUsers()
    {
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Map<String, Object>> users =
                (List<Map<String, Object>>) (List) tester
                        .document("{ users { username isAdmin } }")
                        .execute()
                        .path("users").entityList(Map.class).get();
        // §12: monty (not admin) sees ONLY themselves — not homer or any other user.
        assertFalse(users.isEmpty(), "monty must see themselves in users");
        assertFalse(users.stream().noneMatch(u -> "monty".equals(u.get("username"))),
                "monty must see monty in users, got: " + users);
        assertFalse(users.stream().anyMatch(u -> "homer".equals(u.get("username"))),
                "monty must NOT see homer (admin), got: " + users);
    }

    @Test
    @WithMockUser(username = "monty")
    void nonAdminCanReadOwnUserByUsername()
    {
        tester.document("{ user(username: \"monty\") { username isAdmin } }")
              .execute()
              .path("user.username").entity(String.class).isEqualTo("monty")
              .path("user.isAdmin").entity(Boolean.class).isEqualTo(false);
    }

    @Test
    @WithMockUser(username = "monty")
    void nonAdminCannotReadOtherUserByUsername()
    {
        tester.document("{ user(username: \"homer\") { username } }")
              .execute()
              .path("user").valueIsNull();
    }

    // --- users(filter:) — UserFilter predicates -----------------------------

    @Test
    @WithMockUser(username = "homer")
    void adminUsersFilterByIsAdminTrueReturnsOnlyAdmins()
    {
        // homer is the only admin in testdefault.xml.
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Map<String, Object>> users = (List<Map<String, Object>>) (List) tester
                .document("{ users(filter: { isAdmin: true }) { username isAdmin } }")
                .execute()
                .path("users").entityList(Map.class).get();
        assertFalse(users.isEmpty(), "expected at least homer in admin-only filter");
        assertFalse(users.stream().anyMatch(u -> Boolean.FALSE.equals(u.get("isAdmin"))),
                "isAdmin:true filter must return only admins, got: " + users);
    }

    @Test
    @WithMockUser(username = "homer")
    void adminUsersFilterByIsAdminFalseExcludesAdmins()
    {
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Map<String, Object>> users = (List<Map<String, Object>>) (List) tester
                .document("{ users(filter: { isAdmin: false }) { username isAdmin } }")
                .execute()
                .path("users").entityList(Map.class).get();
        assertFalse(users.stream().anyMatch(u -> Boolean.TRUE.equals(u.get("isAdmin"))),
                "isAdmin:false filter must exclude admins, got: " + users);
    }

    @Test
    @WithMockUser(username = "homer")
    void adminUsersFilterByUsernameContainsCaseInsensitive()
    {
        // testdefault.xml has "monty" — usernameContains: "MO" should match.
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Map<String, Object>> users = (List<Map<String, Object>>) (List) tester
                .document("{ users(filter: { usernameContains: \"MO\" }) { username } }")
                .execute()
                .path("users").entityList(Map.class).get();
        assertFalse(users.stream().noneMatch(u -> "monty".equals(u.get("username"))),
                "usernameContains 'MO' should match 'monty' (case-insensitive), got: " + users);
    }

    @Test
    @WithMockUser(username = "homer")
    void adminUsersFilterByHasAuthSourceFalseReturnsLocalUsers()
    {
        // testdefault.xml users have no authentication-source set — filter
        // hasAuthSource=false should return them; hasAuthSource=true returns [].
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Map<String, Object>> external = (List<Map<String, Object>>) (List) tester
                .document("{ users(filter: { hasAuthSource: true }) { username authSource } }")
                .execute()
                .path("users").entityList(Map.class).get();
        // (Don't assert empty — fixtures could grow; just assert all returned have authSource set.)
        assertFalse(external.stream().anyMatch(u -> u.get("authSource") == null),
                "hasAuthSource:true filter must exclude users without authSource, got: " + external);
    }

    @Test
    @WithMockUser(username = "homer")
    void filterAndedWithOtherPredicates()
    {
        // isAdmin:false AND usernameContains:"mo" should match monty only.
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Map<String, Object>> users = (List<Map<String, Object>>) (List) tester
                .document("{ users(filter: { isAdmin: false, usernameContains: \"mo\" }) { username isAdmin } }")
                .execute()
                .path("users").entityList(Map.class).get();
        assertFalse(users.stream().anyMatch(u -> Boolean.TRUE.equals(u.get("isAdmin"))),
                "AND of predicates must reject admins, got: " + users);
        assertFalse(users.stream().anyMatch(u -> {
            String n = (String) u.get("username");
            return n != null && !n.toLowerCase().contains("mo");
        }), "AND of predicates must reject usernames without 'mo', got: " + users);
    }

    // --- periods -------------------------------------------------------------

    @Test
    void periodsReturnsShapedList()
    {
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Map<String, Object>> periods =
                (List<Map<String, Object>>) (List) tester
                        .document("{ periods { name start end } }")
                        .execute()
                        .path("periods").entityList(Map.class).get();
        periods.forEach(p -> assertFalse(((String) p.get("name")).isBlank(),
                "period name must be non-blank"));
    }

    // --- categories ----------------------------------------------------------

    @Test
    void categoriesReturnsShape()
    {
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Map<String, Object>> cats =
                (List<Map<String, Object>>) (List) tester
                        .document("{ categories { key name path } }")
                        .execute()
                        .path("categories").entityList(Map.class).get();
        cats.forEach(c -> {
            assertFalse(((String) c.get("key")).isBlank(), "category key must be non-blank");
            assertFalse(c.get("name") == null, "category name must not be null");
        });
    }

    @Test
    void categoryByMissingPathReturnsNull()
    {
        tester.document("{ category(path: \"definitely/not/a/real/path\") { key } }")
              .execute()
              .path("category").valueIsNull();
    }

    /**
     * PRD 035 §5a: every returned Category carries a {@code kind} discriminator
     * (VALUE_LIST / ORGANIZATION / SYSTEM). The fixture's user-groups root is
     * filtered out of the Category surface and surfaces only via {@code type
     * Group} — so we never see kind=SYSTEM under categories anyway, but the
     * field must be non-null for every Category we get back.
     */
    @Test
    void categoriesReturnKindDiscriminator()
    {
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Map<String, Object>> cats =
                (List<Map<String, Object>>) (List) tester
                        .document("{ categories { key kind } }")
                        .execute()
                        .path("categories").entityList(Map.class).get();
        cats.forEach(c -> {
            Object kind = c.get("kind");
            assertFalse(kind == null, "category " + c.get("key") + " kind must not be null");
            assertFalse("SYSTEM".equals(kind), "SYSTEM-kind categories should not surface in categories() — got " + c);
            assertTrue("VALUE_LIST".equals(kind) || "ORGANIZATION".equals(kind),
                    "category " + c.get("key") + " kind must be VALUE_LIST or ORGANIZATION, got " + kind);
        });
    }

    /**
     * PRD 035 §5a: user-groups subtree is filtered out of categories() —
     * permission groups surface via the separate Group type. testdefault.xml's
     * user-groups root must not appear in the top-level categories() response.
     */
    @Test
    void userGroupsRootIsHiddenFromCategoriesQuery()
    {
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Map<String, Object>> cats =
                (List<Map<String, Object>>) (List) tester
                        .document("{ categories { key } }")
                        .execute()
                        .path("categories").entityList(Map.class).get();
        cats.forEach(c -> {
            assertFalse("user-groups".equals(c.get("key")),
                    "user-groups subtree must not surface through categories()");
        });
    }

    /**
     * categories(rootKey: "user-groups") must return empty list — the subtree
     * isn't addressable through the Category surface.
     */
    @Test
    void categoriesByUserGroupsRootKeyReturnsEmpty()
    {
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Map<String, Object>> cats =
                (List<Map<String, Object>>) (List) tester
                        .document("{ categories(rootKey: \"user-groups\") { key } }")
                        .execute()
                        .path("categories").entityList(Map.class).get();
        assertTrue(cats.isEmpty(), "user-groups rootKey must return empty list, got " + cats);
    }

    /**
     * Any path under user-groups via category(path:) returns null —
     * the subtree is fully filtered out, paths under it not resolvable.
     */
    @Test
    void categoryByPathUnderUserGroupsReturnsNull()
    {
        tester.document("{ category(path: \"user-groups\") { key } }")
              .execute()
              .path("category").valueIsNull();
        tester.document("{ category(path: \"user-groups/staff\") { key } }")
              .execute()
              .path("category").valueIsNull();
    }

    // --- groups (PRD 035 §5c) ------------------------------------------------

    /**
     * §5c: groups query returns ALL categories under the user-groups subtree
     * (recursive), surfaced as Group instances. testdefault.xml has 8 groups
     * spread across nested levels (my-group, powerplant, powerplant-admins,
     * powerplant-staff, registerer, modify-preferences, read-events-from-others,
     * create-events).
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void groupsReturnsAllPermissionGroups()
    {
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Map<String, Object>> gs = (List<Map<String, Object>>) (List) tester
                .document("{ groups { id key name } }")
                .execute()
                .path("groups").entityList(Map.class).get();
        List<String> keys = gs.stream().map(g -> (String) g.get("key")).toList();
        assertTrue(keys.contains("my-group"),                  () -> "expected my-group in " + keys);
        assertTrue(keys.contains("powerplant"),                () -> "expected powerplant in " + keys);
        assertTrue(keys.contains("powerplant-admins"),         () -> "expected powerplant-admins in " + keys);
        assertTrue(keys.contains("registerer"),                () -> "expected registerer in " + keys);
        assertTrue(keys.contains("create-events"),             () -> "expected create-events in " + keys);
    }

    /** §5c §12: anonymous group queries are rejected, not silently empty. */
    @Test
    @WithAnonymousUser
    void groupsAnonymousRejected()
    {
        tester.document("{ groups { key } }")
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "anonymous groups query must error");
                    assertTrue(errs.toString().contains("UNAUTHENTICATED"),
                            () -> "expected UNAUTHENTICATED; got " + errs);
                });
    }

    /** group(id) lookup with an unknown id returns null. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void groupByUnknownIdReturnsNull()
    {
        tester.document("{ group(id: \"does-not-exist\") { key } }")
              .execute()
              .path("group").valueIsNull();
    }

    /**
     * §5c: User.groups field resolves through the GroupGraphQLController
     * SchemaMapping. monty is in 3 groups in testdefault.xml: my-group,
     * powerplant, powerplant-admins.
     */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void userGroupsExposesMembership()
    {
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Map<String, Object>> gs = (List<Map<String, Object>>) (List) tester
                .document("{ user(username: \"monty\") { username groups { key } } }")
                .execute()
                .path("user.groups").entityList(Map.class).get();
        List<String> keys = gs.stream().map(g -> (String) g.get("key")).toList();
        assertTrue(keys.contains("my-group"),          () -> "monty missing my-group in " + keys);
        assertTrue(keys.contains("powerplant"),        () -> "monty missing powerplant in " + keys);
        assertTrue(keys.contains("powerplant-admins"), () -> "monty missing powerplant-admins in " + keys);
        assertEquals(3, keys.size(), () -> "monty should be in exactly 3 groups, got " + keys);
    }
}
