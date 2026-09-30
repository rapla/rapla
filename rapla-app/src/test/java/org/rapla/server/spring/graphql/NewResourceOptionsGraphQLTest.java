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
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 122 — {@code newResourceOptions} (D4), {@code resourcePrototype} (D5) and the create gate.
 *
 * <p>Fixture: homer is admin; monty is a non-admin outside {@code registerer} and may create no
 * resource/person type; {@code registrar} (seeded here) is in {@code registerer}, which holds the
 * only CREATE permission on a resource type ({@code resource3}).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class NewResourceOptionsGraphQLTest
{
    private static final String OPTIONS = "{ newResourceOptions { resourceTypes { key classificationType } } }";
    private static final String DENIED_ID = "a0000000-0000-4000-8000-000000000122";

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = NewResourceOptionsGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
        {
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
    @Autowired
    CachableStorageOperator operator;
    @Autowired
    RaplaFacade facade;

    HttpGraphQlTester tester;

    @BeforeEach
    void setUp() throws Exception
    {
        tester = HttpGraphQlTester.builder(MockMvcWebTestClient.bindTo(mockMvc).build().mutate())
                .url("/api/graphql").build();
        if (operator.getUser("registrar") == null)
        {
            User u = facade.newUser();
            u.setUsername("registrar");
            u.addGroup(operator.getSuperCategory().getCategory("user-groups").getCategory("registerer"));
            facade.store(u);
        }
    }

    private List<String> creatable(String field)
    {
        return tester.document(OPTIONS).execute()
                .path("newResourceOptions.resourceTypes[*]." + field).entityList(String.class).get();
    }

    // ================================================================ newResourceOptions

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void adminGetsResourceAndPersonTypesButNoInternalOrReservationType()
    {
        List<String> keys = creatable("key");
        assertTrue(keys.containsAll(List.of("room", "lecturer", "resource1", "resource2", "resource3")),
                () -> "admin may create every resource/person type: " + keys);
        assertFalse(keys.contains("event"), "reservation types belong to newEventOptions");
        assertFalse(keys.stream().anyMatch(k -> k.startsWith("rapla")), () -> "internal type offered: " + keys);
        List<String> kinds = creatable("classificationType");
        assertTrue(kinds.contains("RESOURCE") && kinds.contains("PERSON"), () -> "both kinds expected: " + kinds);
    }

    @Test
    @WithMockUser(username = "monty")
    void nonAdminWithoutCreatePermissionGetsNoTypes()
    {
        assertEquals(List.of(), creatable("key"));
    }

    @Test
    @WithMockUser(username = "registrar")
    void nonAdminGetsExactlyTheTypesItMayCreate()
    {
        assertEquals(List.of("resource3"), creatable("key"));
    }

    // ================================================================ resourcePrototype

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void prototypeReturnsTheTypeDefaults() throws Exception
    {
        DynamicType room = facade.getDynamicType("room");
        if (!"auto-room".equals(room.getAttribute("name").defaultValue()))
        {
            DynamicType edit = facade.edit(room);
            edit.getAttribute("name").setDefaultValue("auto-room");
            facade.store(edit);
        }
        Map<String, Object> cls = tester.document("""
                { resourcePrototype(typeKey: "room") {
                    typeKey
                    classification { typeKey ... on roomClassification { name seats } }
                } }
                """)
                .execute()
                .path("resourcePrototype.classification")
                .entity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .get();
        assertEquals("room", cls.get("typeKey"));
        assertEquals("auto-room", cls.get("name"), "type default must be prefilled");
        assertNull(cls.get("seats"), "attributes without default stay null");
    }

    @Test
    @WithMockUser(username = "registrar")
    void prototypeOfACreatableTypeAnswersForNonAdmin()
    {
        tester.document("{ resourcePrototype(typeKey: \"resource3\") { typeKey } }").execute()
                .path("resourcePrototype.typeKey").entity(String.class).isEqualTo("resource3");
    }

    /** §12 — non-creatable, wrong kind (monty CAN create "event") and unknown answer identically. */
    @Test
    @WithMockUser(username = "monty")
    void nonCreatableAndWrongKindAnswerLikeUnknownForNonAdmin()
    {
        String unknown = prototypeErrors("doesnotexist1234");
        assertEquals(unknown, prototypeErrors("resource3"), "non-creatable must look unknown");
        assertEquals(unknown, prototypeErrors("event"), "a creatable RESERVATION type must look unknown");
    }

    /** §12 — admin passes canCreate everywhere, so the kind and internal gates alone must hold. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void internalAndWrongKindAnswerLikeUnknownForAdmin()
    {
        String unknown = prototypeErrors("doesnotexist1234");
        assertEquals(unknown, prototypeErrors("rapla:template"), "internal type must look unknown");
        assertEquals(unknown, prototypeErrors("event"), "RESERVATION type must look unknown");
    }

    /** Errors of one prototype call — compared unmasked, so the requested key must not be echoed. */
    private String prototypeErrors(String typeKey)
    {
        StringBuilder out = new StringBuilder();
        tester.document("query ($k: String!) { resourcePrototype(typeKey: $k) { typeKey } }")
                .variable("k", typeKey)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "expected an error for typeKey " + typeKey);
                    errs.forEach(e -> out.append(e.getErrorType()).append('|').append(e.getMessage())
                            .append('|').append(e.getPath()).append('|').append(e.getExtensions()).append('\n'));
                });
        return out.toString();
    }

    // ================================================================ create gate

    /** H1 — admin passes canCreate everywhere: internal and RESERVATION types must still look unknown. */
    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void createOfInternalOrWrongKindTypeAnswersLikeUnknownForAdmin()
    {
        String unknown = createErrors("doesnotexist1234", "a0000000-0000-4000-8000-000000000201");
        assertEquals(unknown, createErrors("rapla:template", "a0000000-0000-4000-8000-000000000202"));
        assertEquals(unknown, createErrors("rapla:period", "a0000000-0000-4000-8000-000000000203"));
        assertEquals(unknown, createErrors("event", "a0000000-0000-4000-8000-000000000204"));
    }

    /** H1 — monty CAN create "event", yet createResource must treat it like an unknown type. */
    @Test
    @WithMockUser(username = "monty")
    void createOfCreatableReservationTypeAnswersLikeUnknownForNonAdmin()
    {
        String unknown = createErrors("doesnotexist1234", "a0000000-0000-4000-8000-000000000211");
        assertEquals(unknown, createErrors("event", "a0000000-0000-4000-8000-000000000212"));
    }

    /** Errors of one createResource call (unmasked); asserts that nothing was stored under the id. */
    private String createErrors(String typeKey, String id)
    {
        StringBuilder out = new StringBuilder();
        tester.document("mutation ($id: ID!, $k: String!) { createResource(input: "
                        + "{ id: $id, typeKey: $k, classification: { room: {} } }) { id } }")
                .variable("id", id)
                .variable("k", typeKey)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "expected an error for typeKey " + typeKey);
                    errs.forEach(e -> out.append(e.getErrorType()).append('|').append(e.getMessage())
                            .append('|').append(e.getPath()).append('|').append(e.getExtensions()).append('\n'));
                });
        assertNull(operator.tryResolve(new ReferenceInfo<>(id, Allocatable.class)),
                () -> "nothing may be stored for typeKey " + typeKey);
        return out.toString();
    }

    @Test
    @WithMockUser(username = "monty")
    void createWithoutCreatePermissionIsDenied()
    {
        tester.document("""
                mutation { createResource(input: {
                  id: "%s",
                  typeKey: "resource3",
                  classification: { resource3: {} }
                }) { id } }
                """.formatted(DENIED_ID))
                .execute()
                .errors()
                .satisfy(errs -> assertTrue(errs.toString().contains("PERMISSION_DENIED"),
                        () -> "expected PERMISSION_DENIED, got " + errs));
        assertNull(operator.tryResolve(new ReferenceInfo<>(DENIED_ID, Allocatable.class)), "nothing may be stored");
    }
}
