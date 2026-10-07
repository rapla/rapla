package org.rapla.server.spring.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.domain.Allocatable;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.externaleventimport.ExternalEventSnapshotProvider;
import org.rapla.plugin.externaleventimport.ImportItem;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

/**
 * A worklist with a real group: {@code ExternalEventGroup.resourceId} must carry the group's id.
 * The sibling {@code ExternalEventWorklistGraphQLTest} only ever sees an empty group list, so a
 * null {@code resourceId} (record component still named {@code allocatableId}) went unnoticed and
 * failed the whole SPA worklist query on the DHBW copy (2026-10-07).
 */
@SpringBootTest(classes = {RaplaSpringBootApplication.class,
        ExternalEventWorklistGroupGraphQLTest.StubProviderConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@Tag("e2e")
class ExternalEventWorklistGroupGraphQLTest
{
    @TestConfiguration
    static class StubProviderConfig
    {
        /** Hand-rolled stub (AGENTS.md §13 allowed list — deployment SPI, no rapla internals). */
        @Bean
        ExternalEventSnapshotProvider stubSnapshotProvider(@Lazy CachableStorageOperator operator)
        {
            return new ExternalEventSnapshotProvider()
            {
                @Override public String systemId()
                {
                    return "TEST";
                }

                @Override public Collection<ImportItem> readAll()
                {
                    final ImportItem item = new ImportItem();
                    item.setSourceItemId("v:1");
                    final Map<String, Object> columns = new LinkedHashMap<>();
                    columns.put("name", "Staged event");
                    item.setColumns(columns);
                    return List.of(item);
                }

                @Override public Map<String, Collection<String>> resolveGroups(Collection<ImportItem> items)
                        throws RaplaException
                {
                    return Map.of("v:1", List.of(groupId(operator)));
                }

                @Override public String externalIdOf(ImportItem item)
                {
                    return "Test:" + item.getSourceItemId();
                }
            };
        }
    }

    static String groupId(CachableStorageOperator operator) throws RaplaException
    {
        final Allocatable first = operator.getAllocatables(null).iterator().next();
        return first.getId();
    }

    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ExternalEventWorklistGroupGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml fixture missing from classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
        registry.add("rapla.externalevents.staging.enabled", () -> "true");
        registry.add("rapla.externalevents.staging.reconcile.enabled", () -> "true");
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

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void groupCarriesItsResourceId() throws Exception
    {
        final String groupId = groupId(operator);
        final List<Map<String, Object>> groups = tester.document("""
                query ($ids: [ID!]!) { externalEventWorklist(resourceIds: $ids) {
                    groups { resourceId name counts { total } } } }
                """)
                .variable("ids", List.of(groupId))
                .execute()
                .path("externalEventWorklist.groups")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
        assertEquals(1, groups.size());
        assertEquals(groupId, groups.get(0).get("resourceId"));
    }
}
