package org.rapla.plugin.externaleventimport.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
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
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ExternalSyncEntity;
import org.rapla.entities.storage.ImportExportDirections;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.externaleventimport.ExternalEventCreateService;
import org.rapla.plugin.externaleventimport.ExternalEventSnapshotProvider;
import org.rapla.plugin.externaleventimport.ImportItem;
import org.rapla.rest.JsonParserWrapper;
import org.rapla.test.util.FacadeTestSupport;

/**
 * dhbwrapla PRD 004 § H3a "Übernehmen": a bound item whose source changed is re-applied to its
 * reservation through the deployment hook, then its "changed" marker is cleared. Every rejection is
 * the same silent false and leaves the marker in place.
 */
class ExternalEventApplyChangesTest extends FacadeTestSupport
{
    private final JsonParserWrapper.JsonParser json = JsonParserWrapper.defaultJson().get();
    private StubProvider provider;
    private StubCreateService createService;
    private ExternalEventStagingService staging;
    private ExternalEventBinder binder;
    private Allocatable openGroup;
    private Allocatable restrictedGroup;
    private User admin;
    private User nonAdmin;
    private long clock = 1_000L;

    @BeforeEach
    void setUp() throws Exception
    {
        admin = operator.getUser("homer");
        nonAdmin = operator.getUser("monty");
        provider = new StubProvider();
        createService = new StubCreateService();
        final ExternalEventStagingReader reader = new ExternalEventStagingReader(operator, provider);
        final ExternalEventStagingMutator mutator = new ExternalEventStagingMutator(operator, provider, reader, admin);
        binder = new ExternalEventBinder(reader, createService, operator, mutator);
        staging = new ExternalEventStagingService(provider, operator, admin, 0L, () -> clock);

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
    }

    private Allocatable group(String name) throws Exception
    {
        final DynamicType type = operator.getDynamicTypes().stream().filter(t -> t.getKey().equals("room"))
                .findFirst().orElseThrow();
        final Allocatable allocatable = facade.newAllocatable(type.newClassification(), admin);
        allocatable.getClassification().setValue("name", name);
        return allocatable;
    }

    /** Stages the item, binds it to a homer-owned reservation via the external id, then changes
     *  the source so the next reconcile sets the "changed" marker. */
    private Reservation changedBoundItem(String id, Allocatable group) throws Exception
    {
        provider.put(id, "v1", group);
        reconcile();
        final DynamicType type = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        final Reservation reservation = facade.newReservation(type.newClassification(), admin);
        reservation.getClassification().setValue("name", "event " + id);
        final LocalDateTime start = LocalDateTime.of(2026, 10, 12, 10, 0);
        reservation.addAppointment(facade.newAppointmentWithUser(start, start.plusHours(1), admin));
        reservation.addAllocatable(group);
        reservation.setAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID, provider.externalIdOf(provider.items.get(id)));
        facade.store(reservation);
        provider.put(id, "v2", group);
        reconcile();
        assertThat(changedSince(id)).as("precondition: changed marker set").isNotNull();
        return facade.getPersistent(reservation);
    }

    private void reconcile() throws RaplaException
    {
        clock += 1_000L;
        staging.reconcile(true);
    }

    private Object changedSince(String sourceItemId) throws RaplaException
    {
        for (ExternalSyncEntity row : operator.getImportExportEntities("TEST", ImportExportDirections.IMPORT).values())
        {
            final StagedRowData data = StagedRowData.of(json, row);
            if (data.getSource() != null && sourceItemId.equals(data.getSource().getSourceItemId()))
            {
                return data.getChangedSince();
            }
        }
        throw new IllegalStateException("no row " + sourceItemId);
    }

    @Test
    void applyReappliesTheSourceAndClearsTheMarker() throws Exception
    {
        final Reservation reservation = changedBoundItem("chg", openGroup);

        assertThat(binder.apply(admin, operator.getPermissionController(), "chg")).isTrue();

        assertThat(createService.bound).containsExactly("chg->" + reservation.getId());
        assertThat(changedSince("chg")).isNull();
        reconcile();
        assertThat(changedSince("chg")).as("an unchanged snapshot must not set the marker again").isNull();
    }

    @Test
    void everyRejectionIsTheSameFalseAndKeepsTheMarker() throws Exception
    {
        changedBoundItem("chg", openGroup);
        changedBoundItem("hidden", restrictedGroup);
        provider.put("open", "v1", openGroup);
        provider.put("quiet", "v1", openGroup);
        reconcile();

        assertThat(binder.apply(admin, operator.getPermissionController(), "open")).as("OPEN item").isFalse();
        assertThat(binder.apply(admin, operator.getPermissionController(), "no-such")).as("unknown").isFalse();
        assertThat(binder.apply(nonAdmin, operator.getPermissionController(), "hidden")).as("forbidden group").isFalse();
        assertThat(binder.apply(nonAdmin, operator.getPermissionController(), "chg")).as("forbidden reservation").isFalse();
        assertThat(binder.apply(admin, operator.getPermissionController(), null)).isFalse();

        assertThat(createService.bound).isEmpty();
        assertThat(changedSince("chg")).isNotNull();
        assertThat(changedSince("hidden")).isNotNull();
    }

    @Test
    void aBoundItemWithoutChangesIsNotApplied() throws Exception
    {
        changedBoundItem("chg", openGroup);
        assertThat(binder.apply(admin, operator.getPermissionController(), "chg")).isTrue();
        createService.bound.clear();

        assertThat(binder.apply(admin, operator.getPermissionController(), "chg")).isFalse();
        assertThat(createService.bound).isEmpty();
    }

    @Test
    void aFailingHookKeepsTheMarker() throws Exception
    {
        changedBoundItem("chg", openGroup);
        createService.answer = false;

        assertThat(binder.apply(admin, operator.getPermissionController(), "chg")).isFalse();
        assertThat(changedSince("chg")).isNotNull();
    }

    /** Review round 16 M1: a reconcile that sets a NEWER marker while the hook writes the older
     *  source must not be swallowed — the newer change stays visible. */
    @Test
    void aChangeArrivingDuringApplyKeepsItsMarker() throws Exception
    {
        changedBoundItem("chg", openGroup);
        final Object before = changedSince("chg");
        createService.duringBind = () -> {
            provider.put("chg", "v3", openGroup);
            try
            {
                reconcile();
            }
            catch (RaplaException e)
            {
                throw new IllegalStateException(e);
            }
        };

        assertThat(binder.apply(admin, operator.getPermissionController(), "chg")).as("the reservation was written").isTrue();

        assertThat(changedSince("chg")).isNotNull().isNotEqualTo(before);
    }

    /** dhbwrapla PRD 004 § H5a ⓘ details (WP23x C4): the full source record of a staged item,
     *  gated like the worklist — unknown and not bookable are the same null. */
    @Test
    void detailsCarryTheSourceRecordOnlyForBookableItems() throws Exception
    {
        changedBoundItem("chg", openGroup);
        provider.put("hidden", "v1", restrictedGroup);
        reconcile();
        final ExternalEventStagingReader reader = new ExternalEventStagingReader(operator, provider);

        final ExternalEventStagingReader.StagedDetails row = reader.loadDetails("chg");
        assertThat(row).isNotNull();
        assertThat(row.item().getSourceData()).containsEntry("NAME", "Verteilte Systeme v2");
        assertThat(row.changedSince()).isNotNull();
        assertThat(reader.loadDetails("no-such")).isNull();

        assertThat(binder.details(admin, operator.getPermissionController(), "chg")).isNotNull();
        assertThat(binder.details(nonAdmin, operator.getPermissionController(), "hidden")).isNull();
        assertThat(binder.details(nonAdmin, operator.getPermissionController(), "no-such")).isNull();
    }

    /** ⓘ labels: without a deployment mapping the details list every raw source field. */
    @Test
    void detailsDefaultToTheRawSourceFields() throws Exception
    {
        changedBoundItem("chg", openGroup);
        final ExternalEventStagingReader.StagedDetails row = new ExternalEventStagingReader(operator, provider)
                .loadDetails("chg");
        assertThat(row.fields()).extracting(org.rapla.plugin.externaleventimport.ExternalEventAttribution.DetailField::label)
                .containsExactly("dualisId", "NAME");
        assertThat(row.fields().get(1).value()).isEqualTo("Verteilte Systeme v2");
    }

    /** Review round 19 M1: details are looked up by the row id (idOf(sourceItemId)), not by
     *  scanning and parsing every Halde row — a row that only CARRIES the id in its data under a
     *  foreign row id is not found. Unknown and forbidden thereby cost the same one lookup. */
    @Test
    void detailsAreLookedUpByTheRowIdNotByScanning() throws Exception
    {
        final ImportItem decoy = new ImportItem();
        decoy.setSourceItemId("decoy");
        decoy.setSourceData(new LinkedHashMap<>(Map.of("NAME", "Decoy")));
        final StagedRowData data = new StagedRowData();
        data.setSource(decoy);
        final org.rapla.entities.storage.internal.ExternalSyncEntityImpl row = new org.rapla.entities.storage.internal.ExternalSyncEntityImpl();
        row.setId(ExternalEventStagingConstants.idOf("somebody-else"));
        row.setExternalSystem("TEST");
        row.setDirection(ImportExportDirections.IMPORT);
        row.setContext(ExternalEventStagingConstants.CONTEXT_EVENT_STAGING);
        row.setData(json.toJson(data));
        operator.storeAndRemove(List.of(row), List.of(), admin);

        assertThat(new ExternalEventStagingReader(operator, provider).loadDetails("decoy")).isNull();
    }

    private static class StubProvider implements ExternalEventSnapshotProvider
    {
        private final Map<String, ImportItem> items = new LinkedHashMap<>();
        private final Map<String, Collection<String>> groups = new LinkedHashMap<>();

        void put(String id, String version, Allocatable group)
        {
            final ImportItem item = new ImportItem();
            item.setSourceItemId(id);
            final Map<String, Object> sourceData = new LinkedHashMap<>();
            sourceData.put("dualisId", id);
            sourceData.put("NAME", "Verteilte Systeme " + version);
            item.setSourceData(sourceData);
            final Map<String, Object> columns = new LinkedHashMap<>();
            columns.put("fullName", "Verteilte Systeme");
            item.setColumns(columns);
            items.put(id, item);
            groups.put(id, List.of(group.getId()));
        }

        @Override public String systemId()
        {
            return "TEST";
        }

        @Override public Collection<ImportItem> readAll()
        {
            return new ArrayList<>(items.values());
        }

        @Override public Map<String, Collection<String>> resolveGroups(Collection<ImportItem> items)
        {
            return groups;
        }

        @Override public String externalIdOf(ImportItem item)
        {
            return "Test:" + item.getSourceItemId();
        }
    }

    private static class StubCreateService implements ExternalEventCreateService
    {
        private final List<String> bound = new ArrayList<>();
        private boolean answer = true;
        private Runnable duringBind;

        @Override
        public List<String> createFromStagedItems(User caller, List<String> sourceItemIds, String lectureTemplateId,
                String examTemplateId, LocalDateTime defaultStart)
        {
            return List.of();
        }

        @Override
        public boolean bindStagedItem(User caller, ImportItem stagedItem, String reservationId)
        {
            if (answer)
            {
                bound.add(stagedItem.getSourceItemId() + "->" + reservationId);
            }
            if (duringBind != null)
            {
                final Runnable once = duringBind;
                duringBind = null;
                once.run();
            }
            return answer;
        }
    }
}
