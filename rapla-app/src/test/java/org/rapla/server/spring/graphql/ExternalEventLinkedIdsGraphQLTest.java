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
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ExternalSyncEntity;
import org.rapla.entities.storage.ImportExportDirections;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.ExternalSyncEntityImpl;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.externaleventimport.ExternalEventImportMetadata;
import org.rapla.plugin.externaleventimport.ExternalEventImportResult;
import org.rapla.plugin.externaleventimport.ExternalEventImportService;
import org.rapla.plugin.externaleventimport.ImportCriteria;
import org.rapla.plugin.externaleventimport.CreateReservationsRequest;
import org.rapla.plugin.externaleventimport.SyncClassificationRequest;
import org.rapla.plugin.externaleventimport.SyncClassificationResult;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 104 v3 — {@code externalEventLinkedReservationIds}: of the given ids, the subset
 * durably bound to THE deployment's import source. The discriminator is the
 * {@link ExternalSyncEntity} row (system id from
 * {@link ExternalEventImportService#getExternalSystemId()}), NOT the {@code externalid}
 * annotation alone — the iCal import stamps UIDs without sync entities (the
 * holidays-shown-as-Dualis-verknüpft bug, 2026-08-12).
 */
@SpringBootTest(classes = {RaplaSpringBootApplication.class,
        ExternalEventLinkedIdsGraphQLTest.StubImportServiceConfig.class})
@AutoConfigureMockMvc(addFilters = false)
class ExternalEventLinkedIdsGraphQLTest
{
    private static final String SYSTEM_ID = "test-system";

    @TestConfiguration
    static class StubImportServiceConfig
    {
        /** Hand-rolled stub (AGENTS.md §13 allowed list — deployment SPI, no rapla internals). */
        @Bean
        ExternalEventImportService stubImportService()
        {
            return new ExternalEventImportService()
            {
                @Override public ExternalEventImportMetadata getMetadata()
                {
                    return new ExternalEventImportMetadata();
                }

                @Override public ExternalEventImportResult loadEvents(ImportCriteria criteria)
                {
                    throw new UnsupportedOperationException();
                }

                @Override public List<org.rapla.entities.domain.internal.ReservationImpl> createReservations(
                        CreateReservationsRequest request)
                {
                    throw new UnsupportedOperationException();
                }

                @Override public SyncClassificationResult syncClassification(SyncClassificationRequest request)
                {
                    throw new UnsupportedOperationException();
                }

                @Override public String getExternalSystemId()
                {
                    return SYSTEM_ID;
                }
            };
        }
    }

    @TempDir
    static Path tempDir;

    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ExternalEventLinkedIdsGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
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

    @Autowired
    org.rapla.storage.CachableStorageOperator operator;

    @Autowired
    org.springframework.beans.factory.ObjectProvider<ExternalEventImportService> importServices;

    HttpGraphQlTester tester;

    @BeforeEach
    void setUp()
    {
        WebTestClient client = MockMvcWebTestClient.bindTo(mockMvc).build();
        tester = HttpGraphQlTester.builder(client.mutate()).url("/api/graphql").build();
    }

    @Test
    @WithMockUser(username = "homer", roles = "ADMIN")
    void onlySyncEntityBackedIdsCount_annotationAloneDoesNot() throws Exception
    {
        List<Map<String, Object>> rows = tester.document("""
                query {
                  reservations(filter: {
                    from: "2000-01-01T00:00:00",
                    to:   "2040-01-01T00:00:00"
                  }) { id }
                }
                """)
                .execute()
                .path("reservations")
                .entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .get();
        assertTrue(rows.size() >= 2, "fixture needs at least two reservations");
        String boundId = (String) rows.get(0).get("id");
        String annotatedOnlyId = (String) rows.get(1).get("id");

        stamp(boundId, "type:4711", true);
        stamp(annotatedOnlyId, "holiday-uid@example.org", false);

        Map<String, ExternalSyncEntity> stored =
                operator.getImportExportEntities(SYSTEM_ID, ImportExportDirections.IMPORT);
        assertEquals(1, stored.size(), "sync entity must be registered under the system id");
        assertEquals(boundId, stored.values().iterator().next().getRaplaId(), "raplaId must survive the store");
        assertNotNull(importServices.getIfAvailable(), "stub import service must be visible via ObjectProvider");
        assertEquals(SYSTEM_ID, importServices.getIfAvailable().getExternalSystemId());

        List<String> result = tester.document("""
                query($ids: [ID!]!) { externalEventLinkedReservationIds(reservationIds: $ids) }
                """)
                .variable("ids", List.of(boundId, annotatedOnlyId, "does-not-exist"))
                .execute()
                .path("externalEventLinkedReservationIds")
                .entity(new ParameterizedTypeReference<List<String>>() {})
                .get();
        assertEquals(List.of(boundId), result,
                "only the sync-entity-backed binding counts; annotation-only (iCal/holiday) and unknown ids drop silently");
    }

    private void stamp(String reservationId, String externalId, boolean withSyncEntity) throws RaplaException
    {
        Reservation stored = operator.tryResolve(new ReferenceInfo<>(reservationId, Reservation.class));
        assertNotNull(stored);
        Reservation draft = (Reservation) stored.clone();
        try
        {
            draft.setAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID, externalId);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
        if (withSyncEntity)
        {
            ExternalSyncEntityImpl syncEntity = new ExternalSyncEntityImpl();
            syncEntity.setId(externalId);
            syncEntity.setRaplaId(reservationId);
            syncEntity.setExternalSystem(SYSTEM_ID);
            syncEntity.setDirection(ImportExportDirections.IMPORT);
            syncEntity.setData("{}");
            operator.storeAndRemove(List.of(draft, syncEntity),
                    List.<ReferenceInfo<ExternalSyncEntity>> of(), operator.getUser("homer"));
        }
        else
        {
            operator.storeAndRemove(List.of(draft),
                    List.<ReferenceInfo<ExternalSyncEntity>> of(), operator.getUser("homer"));
        }
    }
}
