package org.rapla.server.spring.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.externaleventimport.ExternalEventCreateService;
import org.rapla.plugin.externaleventimport.ExternalEventSnapshotProvider;
import org.rapla.plugin.externaleventimport.ImportItem;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.PermissionController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * WP24 (dhbwrapla PRD 004): stamping a reservation through the worklist needs the right to change
 * that reservation, not only to book the group. A reservation the caller may read but not change
 * must answer byte-identically to an id that does not exist, and must never reach the deployment hook.
 */
@SpringBootTest(classes = {RaplaSpringBootApplication.class, ExternalEventBindGuardGraphQLTest.StubConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@Tag("e2e")
class ExternalEventBindGuardGraphQLTest
{
    static final List<String> BOUND = new ArrayList<>();
    static volatile String hiddenId;
    /** A group monty may NOT book (created by the details test); v:3 is staged on it. */
    static volatile String foreignGroupId;
    static volatile String version = "v1";

    @TestConfiguration
    static class StubConfig
    {
        /** Hand-rolled stubs (AGENTS.md §13 allowed list — deployment SPI, no rapla internals). */
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
                    final List<ImportItem> items = new ArrayList<>();
                    for (String id : foreignGroupId == null ? List.of("v:1", "v:2") : List.of("v:1", "v:2", "v:3"))
                    {
                        final ImportItem item = new ImportItem();
                        item.setSourceItemId(id);
                        final Map<String, Object> sourceData = new LinkedHashMap<>();
                        sourceData.put("NAME", "Staged event " + version);
                        item.setSourceData(sourceData);
                        final Map<String, Object> columns = new LinkedHashMap<>();
                        columns.put("name", "Staged event");
                        item.setColumns(columns);
                        items.add(item);
                    }
                    return items;
                }

                @Override public Map<String, Collection<String>> resolveGroups(Collection<ImportItem> items)
                        throws RaplaException
                {
                    final String group = montysGroup(operator).getId();
                    return foreignGroupId == null ? Map.of("v:1", List.of(group), "v:2", List.of(group))
                            : Map.of("v:1", List.of(group), "v:2", List.of(group), "v:3", List.of(foreignGroupId));
                }

                @Override public String externalIdOf(ImportItem item)
                {
                    return "Test:" + item.getSourceItemId();
                }
            };
        }

        /** Returns one template monty may read and one he may not — the controller must drop the second. */
        @Bean
        org.rapla.plugin.externaleventimport.ExternalEventTemplateResolver stubTemplateResolver(
                @Lazy CachableStorageOperator operator)
        {
            return new org.rapla.plugin.externaleventimport.ExternalEventTemplateResolver()
            {
                @Override public DefaultTemplates resolveDefaultTemplates(User caller, List<String> groupIds)
                {
                    return null;
                }

                @Override public List<RankedTemplate> rankTemplates(User caller, List<String> groupIds, String kind)
                        throws RaplaException
                {
                    return List.of(new RankedTemplate(readableFor(operator, true).getId(), "lesbar", "exact"),
                            new RankedTemplate(hiddenId, "versteckt", "parent"));
                }
            };
        }

        @Bean
        ExternalEventCreateService stubCreateService()
        {
            return new ExternalEventCreateService()
            {
                @Override public List<String> createFromStagedItems(User caller, List<String> sourceItemIds,
                        String lectureTemplateId, String examTemplateId, java.time.LocalDateTime defaultStart)
                {
                    return List.of();
                }

                @Override public boolean bindStagedItem(User caller, ImportItem stagedItem, String reservationId)
                {
                    BOUND.add(reservationId);
                    return true;
                }
            };
        }
    }

    static Allocatable readableFor(CachableStorageOperator operator, boolean readable) throws RaplaException
    {
        final User monty = operator.getUser("monty");
        final PermissionController pc = operator.getPermissionController();
        return operator.getAllocatables(null).stream().filter(a -> pc.canRead(a, monty) == readable).findFirst()
                .orElseThrow(() -> new IllegalStateException("fixture: no resource with canRead=" + readable + " for monty"));
    }

    static Allocatable montysGroup(CachableStorageOperator operator) throws RaplaException
    {
        final User monty = operator.getUser("monty");
        final PermissionController pc = operator.getPermissionController();
        return operator.getAllocatables(null).stream()
                .filter(a -> pc.canAllocate(a, monty, LocalDate.now()))
                .findFirst().orElseThrow(() -> new IllegalStateException("fixture: monty can book no resource"));
    }

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        try (InputStream in = ExternalEventBindGuardGraphQLTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml fixture missing from classpath");
            Files.copy(in, tempDir.resolve("rapla-data.xml"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> tempDir.resolve("rapla-data.xml").toAbsolutePath().toString());
        registry.add("rapla.externalevents.staging.enabled", () -> "true");
        registry.add("rapla.externalevents.staging.reconcile.enabled", () -> "true");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CachableStorageOperator operator;

    private String bind(String reservationId) throws Exception
    {
        final String body = "{\"query\":\"mutation($r:ID!){bindStagedEvent(sourceItemId:\\\"v:1\\\",reservationId:$r)}\","
                + "\"variables\":{\"r\":\"" + reservationId + "\"}}";
        final MvcResult result = mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        return answer(result);
    }

    private static String answer(MvcResult result) throws Exception
    {
        return result.getResponse().getStatus() + " " + result.getResponse().getContentType() + " "
                + result.getResponse().getContentAsString();
    }

    private String apply(String sourceItemId) throws Exception
    {
        final String body = "{\"query\":\"mutation($s:ID!){applyStagedChanges(sourceItemId:$s)}\","
                + "\"variables\":{\"s\":\"" + sourceItemId + "\"}}";
        return answer(mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn());
    }

    @Autowired
    org.rapla.plugin.externaleventimport.server.ExternalEventStagingService staging;

    /** dhbwrapla PRD 004 § H3a: "Übernehmen" on a changed item bound to a reservation the caller
     *  may read but not change answers byte-identically to an unknown item; the hook is not reached. */
    @Test
    @WithMockUser(username = "monty")
    void applyOnAReservationTheCallerMayNotChangeAnswersLikeAnUnknownItem() throws Exception
    {
        final User monty = operator.getUser("monty");
        final PermissionController pc = operator.getPermissionController();
        final Reservation readOnly = operator.getReservations().stream()
                .filter(r -> pc.canRead(r, monty) && !pc.canModify(r, monty))
                .findFirst().orElseThrow(() -> new IllegalStateException("fixture: no read-only reservation for monty"));
        final Reservation stamped = (Reservation) operator.editObjects(List.of((org.rapla.entities.Entity) readOnly),
                operator.getUser("homer")).get(readOnly);
        stamped.setAnnotation(org.rapla.entities.domain.RaplaObjectAnnotations.KEY_EXTERNALID, "Test:v:2");
        operator.storeAndRemove(List.of(stamped), List.of(), operator.getUser("homer"));
        version = "v1";
        staging.reconcile(true);
        version = "v2";
        staging.reconcile(true);
        BOUND.clear();

        final String forbidden = apply("v:2");
        final String unknown = apply("v:no-such");

        assertEquals(unknown, forbidden);
        assertTrue(unknown.contains("\"applyStagedChanges\":false"), unknown);
        assertTrue(BOUND.isEmpty(), "the deployment hook must not be reached: " + BOUND);
    }

    @Test
    @WithMockUser(username = "monty")
    void aReservationTheCallerMayNotChangeAnswersLikeAnUnknownOne() throws Exception
    {
        final User monty = operator.getUser("monty");
        final PermissionController pc = operator.getPermissionController();
        final Reservation readOnly = operator.getReservations().stream()
                .filter(r -> pc.canRead(r, monty) && !pc.canModify(r, monty))
                .findFirst().orElseThrow(() -> new IllegalStateException("fixture: no read-only reservation for monty"));
        BOUND.clear();

        final String forbidden = bind(readOnly.getId());
        final String unknown = bind("no-such-reservation");

        assertEquals(unknown, forbidden);
        assertTrue(unknown.contains("\"bindStagedEvent\":false"), unknown);
        assertTrue(BOUND.isEmpty(), "the deployment hook must not be reached: " + BOUND);
    }

    /** dhbwrapla PRD 004 § H5a: template suggestions are cut to what the caller may read (§12). */
    @Test
    @WithMockUser(username = "monty")
    void templateSuggestionsOnlyContainReadableTemplates() throws Exception
    {
        final User homer = operator.getUser("homer");
        final org.rapla.entities.dynamictype.DynamicType type = operator.getDynamicTypes().stream()
                .filter(t -> t.getKey().equals("room")).findFirst().orElseThrow();
        final org.rapla.entities.domain.internal.AllocatableImpl hidden = new org.rapla.entities.domain.internal.AllocatableImpl(
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        hidden.setId(operator.createIdentifier(Allocatable.class, 1).get(0).getId());
        hidden.setClassification(type.newClassification());
        hidden.getClassification().setValue("name", "hidden-template");
        hidden.setOwner(homer);
        final org.rapla.entities.domain.Permission ownerOnly = hidden.newPermission();
        ownerOnly.setUser(homer);
        ownerOnly.setAccessLevel(org.rapla.entities.domain.Permission.ADMIN);
        hidden.addPermission(ownerOnly);
        operator.storeAndRemove(List.of(hidden), List.of(), homer);
        hiddenId = hidden.getId();
        final Allocatable stored = operator.tryResolve(new org.rapla.entities.storage.ReferenceInfo<>(hiddenId, Allocatable.class));
        assertTrue(!operator.getPermissionController().canRead(stored, operator.getUser("monty")),
                "precondition: monty cannot read the hidden template");
        final String body = "{\"query\":\"{externalEventTemplateSuggestions(groupIds:[\\\"x\\\"],kind:\\\"v\\\"){id name reason}}\"}";
        final String answer = mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        assertTrue(answer.contains("\"lesbar\""), answer);
        assertTrue(!answer.contains("versteckt") && !answer.contains(hiddenId), answer);
    }

    private String details(String sourceItemId) throws Exception
    {
        final String body = "{\"query\":\"query($s:ID!){stagedEventDetails(sourceItemId:$s){sourceItemId changedSince fields{key value}}}\","
                + "\"variables\":{\"s\":\"" + sourceItemId + "\"}}";
        return answer(mockMvc.perform(post("/api/graphql").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn());
    }

    /** WP23x C4 ⓘ details: a bookable item shows its source record; an item on a group the caller
     *  may not book answers byte-identically to an unknown id (§12). */
    @Test
    @WithMockUser(username = "monty")
    void detailsShowTheSourceRecordAndHideForeignItemsLikeUnknownOnes() throws Exception
    {
        final User homer = operator.getUser("homer");
        final org.rapla.entities.dynamictype.DynamicType type = operator.getDynamicTypes().stream()
                .filter(t -> t.getKey().equals("room")).findFirst().orElseThrow();
        final org.rapla.entities.domain.internal.AllocatableImpl foreign = new org.rapla.entities.domain.internal.AllocatableImpl(
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        foreign.setId(operator.createIdentifier(Allocatable.class, 1).get(0).getId());
        foreign.setClassification(type.newClassification());
        foreign.getClassification().setValue("name", "foreign-group");
        foreign.setOwner(homer);
        final org.rapla.entities.domain.Permission ownerOnly = foreign.newPermission();
        ownerOnly.setUser(homer);
        ownerOnly.setAccessLevel(org.rapla.entities.domain.Permission.ADMIN);
        foreign.addPermission(ownerOnly);
        operator.storeAndRemove(List.of(foreign), List.of(), homer);
        foreignGroupId = foreign.getId();
        version = "v1";
        staging.reconcile(true);

        final String own = details("v:1");
        assertTrue(own.contains("\"NAME\"") && own.contains("Staged event v1"), own);
        assertEquals(details("v:no-such"), details("v:3"));
        assertTrue(details("v:3").contains("\"stagedEventDetails\":null"), details("v:3"));
    }
}
