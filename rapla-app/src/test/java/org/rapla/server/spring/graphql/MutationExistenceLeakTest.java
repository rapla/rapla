package org.rapla.server.spring.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.PermissionController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * security-audit A0d — §12 existence non-leakage on the GraphQL mutation surface.
 *
 * <p>A mutation targeting an id the caller cannot READ must respond identically to a
 * mutation targeting a nonexistent id ({@code REFERENCE_NOT_FOUND}). Before the fix,
 * an existing-but-unreadable id returned {@code PERMISSION_DENIED} while a nonexistent
 * id returned {@code REFERENCE_NOT_FOUND} — an existence oracle: a non-admin could probe
 * guessed ids and learn which ones exist behind their read scope.
 *
 * <p>Fixture: homer (admin) owns a freshly-created room that monty (non-admin) cannot read.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class MutationExistenceLeakTest
{
    private static final String FAKE_ID = "00000000-0000-0000-0000-deadbeef0000";

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = MutationExistenceLeakTest.class.getResourceAsStream("/testdefault.xml"))
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
    String hiddenAllocatableId;

    @BeforeEach
    void setUp() throws Exception
    {
        WebTestClient client = MockMvcWebTestClient.bindTo(mockMvc).build();
        tester = HttpGraphQlTester.builder(client.mutate())
                .url("/api/graphql").build();

        User homer = operator.getUser("homer");
        User monty = operator.getUser("monty");
        DynamicType roomType = facade.getDynamicType("room");
        Classification c = roomType.newClassification();
        c.setValue("name", "HIDDEN-FROM-MONTY");
        Allocatable hidden = facade.newAllocatable(c, homer);   // owned by homer
        // Drop the default everyone-read permission so no permission row matches monty. Under the
        // additive model (PRD 090) an unmatched user resolves to DENIED, so the non-owner non-admin
        // monty can neither read nor modify — while homer still reads/edits it as the owner.
        for (org.rapla.entities.domain.Permission existing : hidden.getPermissionList().toArray(new org.rapla.entities.domain.Permission[0]))
        {
            hidden.removePermission(existing);
        }
        facade.storeObjects(new Entity[] { hidden });
        hiddenAllocatableId = hidden.getId();

        // Guard: the whole test is only meaningful if monty genuinely cannot read it.
        PermissionController pc = operator.getPermissionController();
        Allocatable stored = operator.tryResolve(hidden.getReference());
        assertFalse(pc.canRead(stored, monty),
                "test precondition: monty must not be able to read the seeded resource");
    }

    @Test
    @WithMockUser(username = "monty")
    void updateAllocatable_hiddenId_indistinguishableFromNonexistent()
    {
        String hidden = errorCodeForUpdateAllocatable(hiddenAllocatableId);
        String fake = errorCodeForUpdateAllocatable(FAKE_ID);
        assertEquals("REFERENCE_NOT_FOUND", fake, "nonexistent id must be REFERENCE_NOT_FOUND");
        assertEquals(fake, hidden,
                "existing-but-unreadable id must be indistinguishable from a nonexistent id (§12)");
    }

    private String errorCodeForUpdateAllocatable(String id)
    {
        String[] code = { null };
        tester.document("""
                mutation ($id: ID!) {
                  updateAllocatable(id: $id, input: {
                    typeKey: "room", classification: { room: { name: "x" } }
                  }) { id }
                }
                """)
                .variable("id", id)
                .execute()
                .errors()
                .satisfy(errs -> {
                    assertFalse(errs.isEmpty(), "expected an error for id " + id);
                    String joined = errs.toString();
                    code[0] = joined.contains("PERMISSION_DENIED") ? "PERMISSION_DENIED"
                            : joined.contains("REFERENCE_NOT_FOUND") ? "REFERENCE_NOT_FOUND"
                            : joined;
                });
        return code[0];
    }
}
