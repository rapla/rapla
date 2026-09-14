package org.rapla.plugin.externaleventimport.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.externaleventimport.ExternalEventCreateService;
import org.rapla.plugin.externaleventimport.ExternalEventSnapshotProvider;
import org.rapla.plugin.externaleventimport.ImportItem;
import org.rapla.test.util.FacadeTestSupport;

/**
 * §12 for the manual bind: every rejection looks the same from outside, so a caller cannot use
 * the mutation to probe which staged items exist or which groups they may not book.
 */
class ExternalEventBinderTest extends FacadeTestSupport
{
    private StubProvider provider;
    private StubCreateService createService;
    private ExternalEventBinder binder;
    private Allocatable openGroup;
    private Allocatable restrictedGroup;
    private User admin;
    private User nonAdmin;

    @BeforeEach
    void setUp() throws Exception
    {
        admin = operator.getUser("homer");
        nonAdmin = operator.getUser("monty");
        provider = new StubProvider();
        createService = new StubCreateService();
        final ExternalEventStagingReader reader = new ExternalEventStagingReader(operator, provider);
        binder = new ExternalEventBinder(reader, createService, operator);

        openGroup = group("open-group");
        restrictedGroup = group("restricted-group");
        for (Permission permission : new ArrayList<>(restrictedGroup.getPermissionList()))
        {
            restrictedGroup.removePermission(permission);
        }
        final Permission ownerOnly = restrictedGroup.newPermission();
        ownerOnly.setUser(admin);
        ownerOnly.setAccessLevel(Permission.ALLOCATE);
        restrictedGroup.addPermission(ownerOnly);
        facade.storeObjects(new org.rapla.entities.Entity[] { openGroup, restrictedGroup });

        provider.items.add(item("open-item"));
        provider.items.add(item("hidden-item"));
        provider.groups.put("open-item", List.of(openGroup.getId()));
        provider.groups.put("hidden-item", List.of(restrictedGroup.getId()));
        new ExternalEventStagingService(provider, operator, admin, 0L, () -> 1_000L).reconcile(true);
    }

    private Allocatable group(String name) throws Exception
    {
        final DynamicType type = operator.getDynamicTypes().stream().filter(t -> t.getKey().equals("room"))
                .findFirst().orElseThrow();
        final Allocatable allocatable = facade.newAllocatable(type.newClassification(), admin);
        allocatable.getClassification().setValue("name", name);
        return allocatable;
    }

    private static ImportItem item(String id)
    {
        final ImportItem item = new ImportItem();
        item.setSourceItemId(id);
        final Map<String, Object> sourceData = new LinkedHashMap<>();
        sourceData.put("dualisId", id);
        item.setSourceData(sourceData);
        final Map<String, Object> columns = new LinkedHashMap<>();
        columns.put("fullName", "Verteilte Systeme");
        item.setColumns(columns);
        return item;
    }

    @Test
    void bindsAnOpenItemTheCallerMayBook() throws Exception
    {
        assertThat(binder.bind(admin, operator.getPermissionController(), "open-item", "r-1")).isTrue();
        assertThat(createService.bound).containsExactly("open-item->r-1");
    }

    /** Unknown item, forbidden group and already-bound must be indistinguishable — and none of
     *  them may reach the deployment hook. */
    @Test
    void everyRejectionLooksTheSameAndNeverStamps() throws Exception
    {
        assertThat(binder.bind(admin, operator.getPermissionController(), "does-not-exist", "r-1")).isFalse();
        assertThat(binder.bind(nonAdmin, operator.getPermissionController(), "hidden-item", "r-1")).isFalse();
        assertThat(binder.bind(admin, operator.getPermissionController(), null, "r-1")).isFalse();
        assertThat(binder.bind(admin, operator.getPermissionController(), "open-item", null)).isFalse();

        assertThat(createService.bound).isEmpty();
    }

    @Test
    void withoutADeploymentServiceNothingBinds() throws Exception
    {
        final ExternalEventBinder none = new ExternalEventBinder(
                new ExternalEventStagingReader(operator, provider), null, operator);

        assertThat(none.bind(admin, operator.getPermissionController(), "open-item", "r-1")).isFalse();
    }

    /** Suggestions rank, they never decide — and they never offer an already-stamped event. */
    @Test
    void candidatesRankBySimilarityAndSkipAlreadyStampedReservations() throws Exception
    {
        final org.rapla.entities.dynamictype.DynamicType eventType = facade.getDynamicTypes(
                org.rapla.entities.dynamictype.DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        final java.time.LocalDateTime start = java.time.LocalDateTime.of(2026, 6, 10, 9, 0);

        final org.rapla.entities.domain.Reservation match = event(eventType, start, "Verteilte Systeme Vorlesung");
        final org.rapla.entities.domain.Reservation other = event(eventType, start, "Kolloquium");
        final org.rapla.entities.domain.Reservation stamped = event(eventType, start, "Verteilte Systeme Praktikum");
        stamped.setAnnotation(org.rapla.entities.domain.RaplaObjectAnnotations.KEY_EXTERNALID, "Test:elsewhere");
        for (org.rapla.entities.domain.Reservation r : List.of(match, other, stamped))
        {
            r.addAllocatable(openGroup);
            facade.store(r);
        }

        final List<BindCandidate> candidates = binder.candidates(admin, operator.getPermissionController(),
                "open-item", start.minusDays(1), start.plusDays(1), 5);

        // "Kolloquium" teilt kein Wort — unter der Schwelle, also gar nicht erst vorgeschlagen.
        assertThat(candidates).extracting(BindCandidate::name).containsExactly("Verteilte Systeme Vorlesung");
        assertThat(candidates.get(0).score()).isGreaterThanOrEqualTo(0.55);
    }

    /** Reported 2026-08-11: two different source items both got "Softwarequalität und Verteilte
     *  Systeme" suggested because a single everyday word overlapped. Noise must stay out. */
    @Test
    void anEverydayWordInCommonSuggestsNothing() throws Exception
    {
        final org.rapla.entities.dynamictype.DynamicType eventType = facade.getDynamicTypes(
                org.rapla.entities.dynamictype.DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        final java.time.LocalDateTime start = java.time.LocalDateTime.of(2026, 6, 10, 9, 0);
        final org.rapla.entities.domain.Reservation weak = event(eventType, start,
                "Advanced Software Engineering Praktikum");
        weak.addAllocatable(openGroup);
        facade.store(weak);

        final String id = stageWith("weak-item", "fullName", "Software Testing und Qualitaet");

        assertThat(binder.candidates(admin, operator.getPermissionController(), id,
                start.minusDays(1), start.plusDays(1), 5)).isEmpty();
    }

    /** The source's own identifier carries the day even when the names differ. */
    @Test
    void aMatchingEventNumberIsEnoughOnItsOwn() throws Exception
    {
        final org.rapla.entities.dynamictype.DynamicType eventType = facade.getDynamicTypes(
                org.rapla.entities.dynamictype.DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        final java.time.LocalDateTime start = java.time.LocalDateTime.of(2026, 6, 10, 9, 0);
        final org.rapla.entities.domain.Reservation numbered = event(eventType, start, "Vorlesung T3INF3001.2");
        numbered.addAllocatable(openGroup);
        facade.store(numbered);

        final String id = stageWith("numbered-item", "eventNr", "T3INF3001.2");

        assertThat(binder.candidates(admin, operator.getPermissionController(), id,
                start.minusDays(1), start.plusDays(1), 5)).extracting(BindCandidate::name)
                .containsExactly("Vorlesung T3INF3001.2");
    }

    /** Stages a NEW item with the given display column. Changing an existing row's columns would
     *  not reach the store — display columns deliberately do not count as a source change. */
    private String stageWith(String id, String key, String value) throws Exception
    {
        final ImportItem staged = item(id);
        staged.getColumns().put(key, value);
        provider.items.add(staged);
        provider.groups.put(id, List.of(openGroup.getId()));
        new ExternalEventStagingService(provider, operator, admin, 0L, () -> 2_000L).reconcile(true);
        return id;
    }

    private org.rapla.entities.domain.Reservation event(org.rapla.entities.dynamictype.DynamicType type,
            java.time.LocalDateTime start, String name) throws Exception
    {
        final org.rapla.entities.domain.Reservation reservation = facade.newReservation(type.newClassification(),
                admin);
        reservation.getClassification().setValue("name", name);
        reservation.addAppointment(facade.newAppointmentWithUser(start, start.plusHours(1), admin));
        return reservation;
    }

    private class StubProvider implements ExternalEventSnapshotProvider
    {
        private final List<ImportItem> items = new ArrayList<>();
        private final Map<String, Collection<String>> groups = new LinkedHashMap<>();

        @Override
        public String systemId()
        {
            return "TEST";
        }

        @Override
        public Collection<ImportItem> readAll() throws RaplaException
        {
            return new ArrayList<>(items);
        }

        @Override
        public Map<String, Collection<String>> resolveGroups(Collection<ImportItem> items)
        {
            return groups;
        }

        @Override
        public String externalIdOf(ImportItem item)
        {
            return "Test:" + item.getSourceItemId();
        }
    }

    private static class StubCreateService implements ExternalEventCreateService
    {
        private final List<String> bound = new ArrayList<>();

        @Override
        public List<String> createFromStagedItems(User caller, List<String> sourceItemIds, String lectureTemplateId,
                String examTemplateId, java.time.LocalDateTime defaultStart)
        {
            return List.of();
        }

        @Override
        public boolean bindStagedItem(User caller, ImportItem stagedItem, String reservationId)
        {
            bound.add(stagedItem.getSourceItemId() + "->" + reservationId);
            return true;
        }
    }
}
