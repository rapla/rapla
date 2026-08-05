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
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.TypedComponentRole;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 099 D6 slice — {@code newEventOptions}: the caller's "Neu" menu options.
 *
 * <ul>
 *   <li>{@code eventTypes}: RESERVATION types the caller may CREATE — empty when the
 *       defaultwizard plugin is disabled (Swing parity: {@code DefaultWizard.isEnabled()}).</li>
 *   <li>{@code templates}: the event templates the caller may READ (§12 — a template the
 *       caller cannot read is absent, indistinguishable from nonexistent) — empty when the
 *       templatewizard plugin is disabled.</li>
 * </ul>
 *
 * <p>Fixture: homer (admin) owns a private template (all permissions removed — owner/admin
 * only) and a shared one (blanket READ permission). monty is a non-admin who can create
 * the blanket-create "event" type.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class NewEventOptionsGraphQLTest
{
    private static final TypedComponentRole<Boolean> DEFAULTWIZARD_ENABLED =
            new TypedComponentRole<>("org.rapla.plugin.defaultwizard.enabled");
    private static final TypedComponentRole<Boolean> TEMPLATEWIZARD_ENABLED =
            new TypedComponentRole<>("org.rapla.plugin.templatewizard.enabled");

    private static final String QUERY =
            "{ newEventOptions { eventTypes { key name } templates { id name path } } }";

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = NewEventOptionsGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
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
        seedTemplatesOnce();
    }

    /** Idempotent — the Spring context (and store) is shared across the class's tests. */
    private void seedTemplatesOnce() throws Exception
    {
        User homer = operator.getUser("homer");
        User monty = operator.getUser("monty");
        DynamicType templateType = operator.getDynamicType(StorageOperator.RAPLA_TEMPLATE);
        List<String> existing = templateNames();
        if (!existing.contains("SHARED-TEMPLATE"))
        {
            Classification c = templateType.newClassification();
            c.setValue("name", "SHARED-TEMPLATE");
            Allocatable shared = facade.newAllocatable(c, homer);
            for (Permission p : shared.getPermissionList().toArray(new Permission[0]))
            {
                shared.removePermission(p);
            }
            Permission read = shared.newPermission();
            read.setAccessLevel(Permission.AccessLevel.READ);
            shared.addPermission(read);
            facade.storeObjects(new Entity[] { shared });
        }
        if (!existing.contains("PRIVATE-TEMPLATE"))
        {
            Classification c = templateType.newClassification();
            c.setValue("name", "PRIVATE-TEMPLATE");
            Allocatable priv = facade.newAllocatable(c, homer);
            for (Permission p : priv.getPermissionList().toArray(new Permission[0]))
            {
                priv.removePermission(p);
            }
            facade.storeObjects(new Entity[] { priv });

            PermissionController pc = operator.getPermissionController();
            Allocatable stored = operator.tryResolve(priv.getReference());
            assertFalse(pc.canRead(stored, monty),
                    "test precondition: monty must not be able to read the private template");
        }
    }

    private List<String> templateNames() throws Exception
    {
        DynamicType templateType = operator.getDynamicType(StorageOperator.RAPLA_TEMPLATE);
        var filters = templateType.newClassificationFilter().toArray();
        return operator.getAllocatables(filters).stream()
                .map(a -> String.valueOf(a.getClassification().getValue("name")))
                .toList();
    }

    private void setPluginEnabled(TypedComponentRole<Boolean> key, Boolean value) throws Exception
    {
        var prefs = facade.edit(facade.getSystemPreferences());
        if (value == null)
        {
            prefs.removeEntry(key.getId());
        }
        else
        {
            prefs.putEntry(key, value);
        }
        facade.store(prefs);
    }

    private static final ParameterizedTypeReference<Map<String, Object>> OPTIONS =
            new ParameterizedTypeReference<>() {};

    private Map<String, Object> fetchOptions()
    {
        return tester.document(QUERY).execute().path("newEventOptions").entity(OPTIONS).get();
    }

    @SuppressWarnings("unchecked")
    private static List<String> values(Map<String, Object> options, String listField, String field)
    {
        return ((List<Map<String, Object>>) options.get(listField)).stream()
                .map(m -> String.valueOf(m.get(field))).toList();
    }

    // ================================================================ defaults

    @Test
    @WithMockUser(username = "monty")
    void creatableEventTypesAreListed()
    {
        Map<String, Object> options = fetchOptions();
        List<String> typeKeys = values(options, "eventTypes", "key");
        assertTrue(typeKeys.contains("event"), "monty can create the blanket-create 'event' type");
    }

    @Test
    @WithMockUser(username = "monty")
    void templateVisibilityIsFilteredAtTheOutputBoundary()
    {
        Map<String, Object> options = fetchOptions();
        List<String> templateNames = values(options, "templates", "name");
        assertTrue(templateNames.contains("SHARED-TEMPLATE"), "blanket-READ template is visible");
        assertFalse(templateNames.contains("PRIVATE-TEMPLATE"),
                "§12 — a template monty cannot read must be absent");
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void adminSeesPrivateTemplates()
    {
        Map<String, Object> options = fetchOptions();
        List<String> templateNames = values(options, "templates", "name");
        assertTrue(templateNames.contains("SHARED-TEMPLATE"));
        assertTrue(templateNames.contains("PRIVATE-TEMPLATE"), "admin reads everything");
    }

    @Test
    @WithMockUser(username = "monty")
    void templatesCarryAGroupingPath()
    {
        // PRD 104 Phase 1 — path present on the wire; two templates need no groups → empty
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> templates =
                (List<Map<String, Object>>) fetchOptions().get("templates");
        assertFalse(templates.isEmpty());
        for (Map<String, Object> t : templates)
        {
            assertTrue(t.get("path") instanceof List, "path must be a list, got: " + t.get("path"));
        }
    }

    // ================================================================ plugin gates

    @Test
    @WithMockUser(username = "monty")
    void disabledDefaultWizardSuppressesEventTypesButNotTemplates() throws Exception
    {
        setPluginEnabled(DEFAULTWIZARD_ENABLED, false);
        try
        {
            Map<String, Object> options = fetchOptions();
            assertTrue(values(options, "eventTypes", "key").isEmpty(),
                    "defaultwizard disabled → no event types (Swing parity)");
            assertTrue(values(options, "templates", "name").contains("SHARED-TEMPLATE"),
                    "templates unaffected by the defaultwizard toggle");
        }
        finally
        {
            setPluginEnabled(DEFAULTWIZARD_ENABLED, null);
        }
    }

    @Test
    @WithMockUser(username = "monty")
    void disabledTemplateWizardSuppressesTemplatesButNotEventTypes() throws Exception
    {
        setPluginEnabled(TEMPLATEWIZARD_ENABLED, false);
        try
        {
            Map<String, Object> options = fetchOptions();
            assertTrue(values(options, "templates", "name").isEmpty(),
                    "templatewizard disabled → no templates (Swing parity)");
            assertTrue(values(options, "eventTypes", "key").contains("event"),
                    "event types unaffected by the templatewizard toggle");
        }
        finally
        {
            setPluginEnabled(TEMPLATEWIZARD_ENABLED, null);
        }
    }
}
