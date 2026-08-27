package org.rapla.plugin.externaleventimport.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.storage.ExternalSyncEntity;
import org.rapla.entities.storage.ImportExportDirections;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.externaleventimport.ExternalEventSnapshotProvider;
import org.rapla.plugin.externaleventimport.ImportItem;
import org.rapla.test.util.FacadeTestSupport;

/** Tier-2 coverage for the external-event staging reconciliation. Real {@code FileOperator},
 *  hand-rolled provider — no mocks of rapla types (AGENTS.md §13). */
class ExternalEventStagingServiceTest extends FacadeTestSupport
{
    private StubProvider provider;
    private Set<String> bound;
    private AtomicLong now;
    private ExternalEventStagingService service;

    @BeforeEach
    void setUpService() throws Exception
    {
        provider = new StubProvider();
        bound = new LinkedHashSet<>();
        now = new AtomicLong(1_000_000L);
        service = newService(0L);
    }

    private ExternalEventStagingService newService(long freshnessMillis) throws Exception
    {
        return new ExternalEventStagingService(provider, operator, operator.getUser("admin"), freshnessMillis,
                now::get);
    }

    private static ImportItem item(String id, String name, String semester)
    {
        final ImportItem item = new ImportItem();
        item.setSourceItemId("v:" + id);
        final Map<String, String> hierarchy = new LinkedHashMap<>();
        hierarchy.put("semester", semester);
        item.setHierarchy(hierarchy);
        final Map<String, Object> columns = new LinkedHashMap<>();
        columns.put("name", name);
        item.setColumns(columns);
        final Map<String, Object> sourceData = new LinkedHashMap<>();
        sourceData.put("dualisId", id);
        // The real provider carries the whole source row here, name included — a rename IS a
        // source change, while a display column is not.
        sourceData.put("NAME", name);
        item.setSourceData(sourceData);
        return item;
    }

    /** The binding is DERIVED — so a "bound" item needs a real entity carrying the external id,
     *  not a stubbed flag. Storing it registers the id in the operator's externalIds index. */
    private void bind(String sourceItemId) throws Exception
    {
        bound.add(sourceItemId);
        final org.rapla.entities.dynamictype.DynamicType type = operator.getDynamicTypes()
                .stream().filter(t -> t.getKey().equals("room")).findFirst().orElseThrow();
        final org.rapla.entities.domain.Allocatable allocatable = facade
                .newAllocatable(type.newClassification(), operator.getUser("admin"));
        allocatable.getClassification().setValue("name", "bound-" + sourceItemId);
        allocatable.setAnnotation(org.rapla.entities.domain.RaplaObjectAnnotations.KEY_EXTERNALID,
                "bound:" + sourceItemId);
        facade.store(allocatable);
    }

    private Collection<ExternalSyncEntity> stagingRows() throws RaplaException
    {
        final List<ExternalSyncEntity> result = new ArrayList<>();
        for (ExternalSyncEntity entity : operator.getImportExportEntities("TEST", ImportExportDirections.IMPORT)
                .values())
        {
            if (ExternalEventStagingConstants.CONTEXT_EVENT_STAGING.equals(entity.getContext()))
            {
                result.add(entity);
            }
        }
        return result;
    }

    private ExternalSyncEntity row(String sourceItemId) throws RaplaException
    {
        return stagingRows().stream()
                .filter(entity -> entity.getId().equals(ExternalEventStagingConstants.idOf(sourceItemId))).findFirst()
                .orElse(null);
    }

    @Test
    void firstRunInsertsEveryItem() throws Exception
    {
        provider.items.add(item("1", "Programmieren I", "SoSe 2026"));
        provider.items.add(item("2", "Datenbanken", "SoSe 2026"));

        final StagingReconcileResult result = service.reconcile(true);

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.stored()).isEqualTo(2);
        assertThat(stagingRows()).hasSize(2);
        assertThat(row("v:1")).isNotNull();
    }

    /** The no-op-write requirement: at a 20-minute cadence a blind upsert would push the whole
     *  store into the update history 72x a day and drown every pod's polling. */
    @Test
    void secondRunOnUnchangedSnapshotStoresNothing() throws Exception
    {
        provider.items.add(item("1", "Programmieren I", "SoSe 2026"));
        service.reconcile(true);
        final String dataAfterFirstRun = row("v:1").getData();

        final StagingReconcileResult result = service.reconcile(true);

        assertThat(result.stored()).isZero();
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(row("v:1").getData()).isEqualTo(dataAfterFirstRun);
    }

    @Test
    void changedSourceUpdatesTheRowInPlace() throws Exception
    {
        provider.items.add(item("1", "Programmieren I", "SoSe 2026"));
        service.reconcile(true);

        provider.items.clear();
        provider.items.add(item("1", "Programmieren I (neu)", "SoSe 2026"));
        final StagingReconcileResult result = service.reconcile(true);

        assertThat(result.changed()).isEqualTo(1);
        assertThat(row("v:1").getData()).contains("Programmieren I (neu)");
    }

    /** An unbound staged item the source withdrew leaves no trace — nothing to clean up. */
    @Test
    void withdrawnUnboundItemIsDeleted() throws Exception
    {
        provider.items.add(item("1", "Programmieren I", "SoSe 2026"));
        provider.items.add(item("2", "Datenbanken", "SoSe 2026"));
        service.reconcile(true);

        provider.items.removeIf(item -> item.getSourceItemId().equals("v:2"));
        final StagingReconcileResult result = service.reconcile(true);

        assertThat(result.deleted()).isEqualTo(1);
        assertThat(row("v:2")).isNull();
        assertThat(row("v:1")).isNotNull();
    }

    /** Gone is gone, bound or not (user decision 2026-08-11): rows drop out of the source export
     *  when they age out of its window, so keeping them would collect noise rather than signal. */
    @Test
    void aWithdrawnItemIsDeletedEvenWhenBound() throws Exception
    {
        provider.items.add(item("1", "Programmieren I", "SoSe 2026"));
        provider.items.add(item("2", "Datenbanken", "SoSe 2026"));
        bind("v:2");
        service.reconcile(true);

        provider.items.removeIf(item -> item.getSourceItemId().equals("v:2"));
        final StagingReconcileResult result = service.reconcile(true);

        assertThat(result.deleted()).isEqualTo(1);
        assertThat(row("v:2")).isNull();
    }

    /** Display columns are OUR shaping, not the source's: changing them must not rewrite the
     *  store nor flag rows as changed in the source (scar 2026-08-11). */
    @Test
    void aChangedDisplayColumnAloneIsNotASourceChange() throws Exception
    {
        provider.items.add(item("1", "Programmieren I", "SoSe 2026"));
        service.reconcile(true);

        final ImportItem sameSourceNewColumns = item("1", "Programmieren I", "SoSe 2026");
        sameSourceNewColumns.getColumns().put("fullName", "Programmieren I (MOS-TINF23A)");
        provider.items.clear();
        provider.items.add(sameSourceNewColumns);
        final StagingReconcileResult result = service.reconcile(true);

        assertThat(result.stored()).isZero();
        assertThat(result.unchanged()).isEqualTo(1);
    }

    /** A row that comes back is simply inserted again — and reads as LINKED right away if its
     *  reservation still carries the stamp. */
    @Test
    void aReturningItemIsInsertedAgain() throws Exception
    {
        provider.items.add(item("1", "Programmieren I", "SoSe 2026"));
        service.reconcile(true);
        provider.items.clear();
        service.reconcile(true);
        assertThat(row("v:1")).isNull();

        provider.items.add(item("1", "Programmieren I", "SoSe 2026"));
        final StagingReconcileResult result = service.reconcile(true);

        assertThat(result.created()).isEqualTo(1);
        assertThat(row("v:1")).isNotNull();
    }

    /** A run that fails mid-read must never diff — an incomplete snapshot would mark thousands
     *  of rows gone. */
    @Test
    void partialReadLeavesTheStoreUntouched() throws Exception
    {
        provider.items.add(item("1", "Programmieren I", "SoSe 2026"));
        service.reconcile(true);
        final String dataAfterFirstRun = row("v:1").getData();

        provider.failure = new RaplaException("tunnel down");
        final StagingReconcileResult result = service.reconcile(true);

        assertThat(result.error()).contains("tunnel down");
        assertThat(result.stored()).isZero();
        assertThat(result.deleted()).isZero();
        assertThat(stagingRows()).hasSize(1);
        assertThat(row("v:1").getData()).isEqualTo(dataAfterFirstRun);
    }

    @Test
    void freshnessWindowSkipsATickThatFollowsTooSoon() throws Exception
    {
        service = newService(20 * 60 * 1000L);
        provider.items.add(item("1", "Programmieren I", "SoSe 2026"));
        service.reconcile(false);

        provider.items.add(item("2", "Datenbanken", "SoSe 2026"));
        now.addAndGet(60 * 1000L);
        final StagingReconcileResult skipped = service.reconcile(false);

        assertThat(skipped.skipped()).isTrue();
        assertThat(stagingRows()).hasSize(1);

        now.addAndGet(20 * 60 * 1000L);
        final StagingReconcileResult ran = service.reconcile(false);

        assertThat(ran.skipped()).isFalse();
        assertThat(stagingRows()).hasSize(2);
    }

    /** Large runs are dispatched in blocks so they cannot hold the store locks for their whole
     *  duration (the block concept the dhbw sync already applies). Every row must still land. */
    @Test
    void aRunLargerThanOneBlockStoresEveryRow() throws Exception
    {
        service = new ExternalEventStagingService(provider, operator, operator.getUser("admin"), 0L, now::get, 10);
        for (int i = 0; i < 25; i++)
        {
            provider.items.add(item("block-" + i, "Event " + i, "SoSe 2026"));
        }

        final StagingReconcileResult result = service.reconcile(true);

        assertThat(result.created()).isEqualTo(25);
        assertThat(stagingRows()).hasSize(25);
        assertThat(row("v:block-24")).isNotNull();
    }

    /** Deletions are blocked too — a rollover can remove thousands of rows at once. */
    @Test
    void aDeletionLargerThanOneBlockRemovesEveryRow() throws Exception
    {
        service = new ExternalEventStagingService(provider, operator, operator.getUser("admin"), 0L, now::get, 10);
        for (int i = 0; i < 25; i++)
        {
            provider.items.add(item("block-" + i, "Event " + i, "SoSe 2026"));
        }
        service.reconcile(true);

        provider.items.clear();
        provider.items.add(item("keeper", "Bleibt", "SoSe 2026"));
        final StagingReconcileResult result = service.reconcile(true);

        assertThat(result.deleted()).isEqualTo(25);
        assertThat(stagingRows()).hasSize(1);
    }

    /** The startup run wants current data regardless of when the last tick fired. */
    @Test
    void bypassIgnoresTheFreshnessWindow() throws Exception
    {
        service = newService(20 * 60 * 1000L);
        provider.items.add(item("1", "Programmieren I", "SoSe 2026"));
        service.reconcile(false);

        provider.items.add(item("2", "Datenbanken", "SoSe 2026"));
        final StagingReconcileResult result = service.reconcile(true);

        assertThat(result.skipped()).isFalse();
        assertThat(stagingRows()).hasSize(2);
    }

    private class StubProvider implements ExternalEventSnapshotProvider
    {
        private final List<ImportItem> items = new ArrayList<>();
        private RaplaException failure;

        @Override
        public String systemId()
        {
            return "TEST";
        }

        @Override
        public Collection<ImportItem> readAll() throws RaplaException
        {
            if (failure != null)
            {
                throw failure;
            }
            return new ArrayList<>(items);
        }

        @Override
        public Map<String, Collection<String>> resolveGroups(Collection<ImportItem> items)
        {
            return Map.of();
        }

        @Override
        public String externalIdOf(ImportItem item)
        {
            return bound.contains(item.getSourceItemId()) ? "bound:" + item.getSourceItemId() : null;
        }

        @Override
        public String scopeKeyOf(ImportItem item)
        {
            return item.getHierarchy().get("semester");
        }
    }
}
