package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 096 OQ6 sibling — resource-reference attributes ({@code resource1.a1} → room, {@code resource2.a1} →
 * lecturer list). A NEWLY referenced resource must be readable by the caller (canRead, AGENTS.md §12) and of
 * the {@code @expectedType}; anything else is masked like an unknown id. A reference that is already stored
 * goes back unchanged (Swing parity: the client echoes it, only new picks are limited to readable ones).
 * The fixture copy gives monty ({@code my-group}) create on resource1, info-only (READ_NO_ALLOCATION) on one
 * room and one lecturer, edit on one existing resource1/resource2, and adds a room and a lecturer without any
 * permission. Stored values are read through the operator, so a read-side filter cannot hide a stored reference.
 * Security filters are off: this pins the mapper, not the auth chain; {@code @WithMockUser} carries the gates.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class ResourceRefInputGraphQLTest
{
    static final String READABLE_ROOM = "c24ce517-4697-4e52-9917-ec000c84563c";
    static final String LECTURER = "f92e9a11-c342-4413-a924-81eee17ccf92";
    static final String HIDDEN_ROOM = "a0a1b2c3-0000-4000-8000-0000000000aa";
    static final String INFO_ROOM = "a0a1b2c3-0000-4000-8000-0000000000ab";
    static final String INFO_LECTURER = "a0a1b2c3-0000-4000-8000-0000000000ac";
    static final String HIDDEN_LECTURER = "a0a1b2c3-0000-4000-8000-0000000000ad";
    static final String PART = "a0a1b2c3-0000-4000-8000-0000000000b1";
    static final String GROUP = "a0a1b2c3-0000-4000-8000-0000000000b2";
    static final String UNKNOWN = "a0a1b2c3-0000-4000-8000-0000000000ff";

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        String xml;
        try (InputStream in = ResourceRefInputGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
        {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String roomRef = "<rapla:constraint name=\"dynamic-type\">room</rapla:constraint>";
        String readType = "<rapla:permission access=\"read_type\"/>";
        int at = xml.indexOf(readType, uniqueIndex(xml, roomRef)) + readType.length();
        xml = xml.substring(0, at) + "\n" + perm("create") + xml.substring(at);
        String stamp = " created-at=\"2026-10-01T00:00:00.000Z\" last-changed=\"2026-10-01T00:00:00.000Z\">";
        String added = resource(HIDDEN_ROOM, "<dynatt:room><dynatt:name>Hidden</dynatt:name></dynatt:room>", "", stamp)
                + resource(INFO_ROOM, "<dynatt:room><dynatt:name>Info</dynatt:name></dynatt:room>", perm("read_no_allocation"), stamp)
                + resource(PART, "<dynatt:resource1><dynatt:name>Part</dynatt:name><dynatt:a1>" + INFO_ROOM
                        + "</dynatt:a1></dynatt:resource1>", perm("edit"), stamp)
                + resource(GROUP, "<dynatt:resource2><dynatt:name>Group</dynatt:name><dynatt:a1>" + LECTURER
                        + "</dynatt:a1><dynatt:a1>" + INFO_LECTURER + "</dynatt:a1></dynatt:resource2>", perm("edit"), stamp)
                + "<rapla:person id=\"" + INFO_LECTURER + "\"" + stamp
                + "<dynatt:lecturer><dynatt:surname>Info</dynatt:surname></dynatt:lecturer>" + perm("read_no_allocation")
                + "</rapla:person>\n"
                + "<rapla:person id=\"" + HIDDEN_LECTURER + "\"" + stamp
                + "<dynatt:lecturer><dynatt:surname>Hidden</dynatt:surname></dynatt:lecturer></rapla:person>\n";
        at = uniqueIndex(xml, "<rapla:resource id=\"" + READABLE_ROOM + "\"");
        xml = xml.substring(0, at) + added + xml.substring(at);
        dataFile = tempDir.resolve("rapla-data.xml");
        Files.writeString(dataFile, xml);
    }

    private static String perm(String access)
    {
        return "<rapla:permission group=\"category[key='my-group']\" access=\"" + access + "\"/>";
    }

    private static String resource(String id, String classification, String permission, String stamp)
    {
        return "<rapla:resource id=\"" + id + "\"" + stamp + classification + permission + "</rapla:resource>\n";
    }

    private static int uniqueIndex(String s, String anchor)
    {
        int at = s.indexOf(anchor);
        assertTrue(at >= 0 && s.indexOf(anchor, at + 1) < 0, "fixture anchor must occur exactly once: " + anchor);
        return at;
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    CachableStorageOperator operator;

    HttpGraphQlTester tester;

    @BeforeEach
    void setUp()
    {
        WebTestClient client = MockMvcWebTestClient.bindTo(mockMvc).build();
        tester = HttpGraphQlTester.builder(client.mutate()).url("/api/graphql").build();
    }

    /** The ids the store holds in a1 of {@code id}. */
    private List<String> storedRefs(String id)
    {
        Allocatable a = operator.tryResolve(id, Allocatable.class);
        assertNotNull(a, "precondition: resource " + id + " exists");
        Classification c = a.getClassification();
        Collection<Object> values = c.getValues(c.getAttribute("a1"));
        return values.stream().map(v -> ((Allocatable) v).getId()).toList();
    }

    /** Creates a resource1 referencing {@code refId}; returns the createResource payload. */
    private Map<String, Object> createPart(String id, String refId)
    {
        return tester.document("""
                mutation ($id: ID!, $r: ID!) {
                  createResource(input: { id: $id, typeKey: "resource1",
                    classification: { resource1: { name: "Teil", a1: $r } } }) {
                    classification { ... on resource1Classification { a1 { id } } }
                  }
                }
                """)
                .variable("id", id)
                .variable("r", refId)
                .execute()
                .path("createResource")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
    }

    private List<String> createdRef(String refId)
    {
        String id = UUID.randomUUID().toString();
        createPart(id, refId);
        return storedRefs(id);
    }

    @Test
    @WithMockUser(username = "monty")
    void readableResourceOfTheExpectedTypeIsStored()
    {
        assertEquals(List.of(READABLE_ROOM), createdRef(READABLE_ROOM));
    }

    @Test
    @WithMockUser(username = "monty")
    void newUnreadableResourceIsNeverStored()
    {
        assertEquals(List.of(), createdRef(HIDDEN_ROOM), "a room monty cannot read must not be stored (§12)");
        assertEquals(List.of(), createdRef(INFO_ROOM), "info-only is not enough for a NEW reference (Swing picker = canRead)");
    }

    @Test
    @WithMockUser(username = "monty")
    void hiddenAndUnknownIdsAnswerIdentically()
    {
        assertEquals(createPart(UUID.randomUUID().toString(), UNKNOWN), createPart(UUID.randomUUID().toString(), HIDDEN_ROOM));
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void resourceOfAnotherTypeIsNeverStored()
    {
        assertEquals(List.of(), createdRef(LECTURER), "a lecturer must not be stored in a room reference");
    }

    @Test
    @WithMockUser(username = "monty")
    void storedInfoOnlyReferenceSurvivesAnUpdate()
    {
        tester.document("""
                mutation ($id: ID!, $r: ID!) {
                  updateResource(id: $id, input: { typeKey: "resource1",
                    classification: { resource1: { name: "Part renamed", a1: $r } } }) { id }
                }
                """)
                .variable("id", PART)
                .variable("r", INFO_ROOM)
                .execute()
                .errors().verify();
        assertEquals(List.of(INFO_ROOM), storedRefs(PART), "an unchanged stored reference must not be lost on save");
    }

    @Test
    @WithMockUser(username = "monty")
    void mixedListKeepsStoredEntriesAndDropsNewHiddenOnes()
    {
        tester.document("""
                mutation ($id: ID!, $r: [ID!]!) {
                  updateResource(id: $id, input: { typeKey: "resource2",
                    classification: { resource2: { name: "Group renamed", a1: $r } } }) { id }
                }
                """)
                .variable("id", GROUP)
                .variable("r", List.of(LECTURER, INFO_LECTURER, HIDDEN_LECTURER))
                .execute()
                .errors().verify();
        assertEquals(List.of(LECTURER, INFO_LECTURER), storedRefs(GROUP));
    }
}
