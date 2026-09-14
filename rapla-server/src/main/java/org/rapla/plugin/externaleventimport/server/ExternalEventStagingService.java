package org.rapla.plugin.externaleventimport.server;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.storage.ExternalSyncEntity;
import org.rapla.entities.storage.ImportExportDirections;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.ExternalSyncEntityImpl;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.externaleventimport.ExternalEventSnapshotProvider;
import org.rapla.plugin.externaleventimport.ImportItem;
import org.rapla.rest.JsonParserWrapper;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.impl.server.BlockedDispatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodic full reconciliation of the external-event staging store — the "Halde".
 *
 * <p>Source-agnostic: everything deployment-specific comes through
 * {@link ExternalEventSnapshotProvider}. This is the ONLY path allowed to infer "gone" from
 * absence, because only a full snapshot sees the complete id set. Three invariants it must
 * never break:
 * <ul>
 *   <li>an item whose serialized source data is unchanged produces ZERO writes. Staging rows
 *       do NOT ride the update history (neither {@code updateIndizes} nor
 *       {@code UpdateDataManagerImpl} pass {@code ExternalSyncEntity} on), so the cost of a
 *       blind upsert is not client polling but the database: at a 20-minute cadence it would
 *       be tens of thousands of row rewrites 72x a day, each inside a dispatch holding store
 *       locks,</li>
 *   <li>a snapshot that failed mid-read is not diffed at all,</li>
 *   <li>anything missing from the snapshot is deleted, bound or not.</li>
 * </ul>
 *
 * <p>Multi-pod: the meta row doubles as the lock. A pod skips its tick when another run is in
 * flight or the last one is younger than the freshness window; the store's optimistic version
 * check on that row settles the race.
 */
public class ExternalEventStagingService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalEventStagingService.class);

    private static final long STALE_RUN_MILLIS = 10 * 60 * 1000L;

    private static final int DEFAULT_BLOCK_SIZE = BlockedDispatch.DEFAULT_BLOCK_SIZE;

    private final JsonParserWrapper.JsonParser json = JsonParserWrapper.defaultJson().get();
    private final ExternalEventSnapshotProvider provider;
    private final CachableStorageOperator operator;
    private final User importUser;
    private final long freshnessMillis;
    private final LongSupplier clock;
    private final int blockSize;

    public ExternalEventStagingService(ExternalEventSnapshotProvider provider, CachableStorageOperator operator,
            User importUser, long freshnessMillis, LongSupplier clock)
    {
        this(provider, operator, importUser, freshnessMillis, clock, DEFAULT_BLOCK_SIZE);
    }

    public ExternalEventStagingService(ExternalEventSnapshotProvider provider, CachableStorageOperator operator,
            User importUser, long freshnessMillis, LongSupplier clock, int blockSize)
    {
        this.provider = provider;
        this.operator = operator;
        this.importUser = importUser;
        this.freshnessMillis = freshnessMillis;
        this.clock = clock;
        this.blockSize = blockSize;
    }

    public StagingReconcileResult reconcile(boolean bypassFreshness)
    {
        final long now = clock.getAsLong();
        final StagingMeta meta;
        try
        {
            meta = readMeta();
        }
        catch (RaplaException ex)
        {
            LOGGER.error("Could not read the staging meta row: " + ex.getMessage(), ex);
            return StagingReconcileResult.failed(ex.getMessage());
        }
        if (meta.isRunning() && now - meta.getLastStarted() < STALE_RUN_MILLIS)
        {
            return StagingReconcileResult.skipped("another reconciliation is in flight");
        }
        if (!bypassFreshness && meta.getLastFinished() > 0 && now - meta.getLastFinished() < freshnessMillis)
        {
            return StagingReconcileResult.skipped("last reconciliation is younger than the freshness window");
        }
        meta.setRunning(true);
        meta.setLastStarted(now);
        try
        {
            storeMeta(meta);
        }
        catch (RaplaException ex)
        {
            return StagingReconcileResult.skipped("lost the race for the reconciliation lock: " + ex.getMessage());
        }

        StagingReconcileResult result;
        try
        {
            result = applySnapshot(provider.readAll(), now);
        }
        catch (Throwable ex)
        {
            // One line per failed tick, not a stack trace: an unreachable source (tunnel/VPN
            // down) is an operational state, not a bug, and it repeats every cadence.
            LOGGER.error("Reconciliation failed, leaving the staging store untouched: " + ex.getMessage());
            LOGGER.debug("Reconciliation failure detail", ex);
            result = StagingReconcileResult.failed(ex.getMessage());
        }

        meta.setRunning(false);
        meta.setLastError(result.error());
        if (result.error() == null)
        {
            meta.setLastFinished(clock.getAsLong());
            meta.setLastCreated(result.created());
            meta.setLastChanged(result.changed());
            meta.setLastDeleted(result.deleted());
            meta.setLastUnchanged(result.unchanged());
        }
        try
        {
            storeMeta(meta);
        }
        catch (RaplaException ex)
        {
            LOGGER.error("Could not write the staging meta row: " + ex.getMessage(), ex);
        }
        return result;
    }

    private StagingReconcileResult applySnapshot(Collection<ImportItem> snapshot, long now) throws RaplaException
    {
        final String timestamp = Instant.ofEpochMilli(now).toString();
        final Map<String, ExternalSyncEntity> existing = readStagingRows();
        final List<Entity> toStore = new ArrayList<>();
        final List<ReferenceInfo<ExternalSyncEntity>> toRemove = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        int created = 0;
        int changed = 0;
        int deleted = 0;
        int unchanged = 0;

        for (ImportItem item : snapshot)
        {
            final String id = ExternalEventStagingConstants.idOf(item.getSourceItemId());
            seen.add(id);
            final ExternalSyncEntity row = existing.get(id);
            if (row == null)
            {
                final StagedRowData data = new StagedRowData();
                data.setSource(item);
                data.setScopeKey(provider.scopeKeyOf(item));
                toStore.add(newRow(id, data));
                created++;
                continue;
            }
            final StagedRowData data = parseRow(row);
            if (!sourceFingerprint(item).equals(sourceFingerprint(data.getSource())))
            {
                data.setSource(item);
                data.setScopeKey(provider.scopeKeyOf(item));
            }
            else
            {
                unchanged++;
                continue;
            }
            final boolean sourceChanged = true;
            if (sourceChanged && isBound(item))
            {
                data.setChangedSince(timestamp);
            }
            toStore.add(edit(row, data));
            changed++;
        }

        for (Map.Entry<String, ExternalSyncEntity> entry : existing.entrySet())
        {
            if (seen.contains(entry.getKey()))
            {
                continue;
            }
            // Gone from the snapshot means gone — bound or not (user decision 2026-08-11).
            // Rows leave the source export because they aged out of its window, not because
            // anything was cancelled, so keeping them would collect noise. A row that returns
            // is inserted again and, if its reservation still carries the stamp, reads as
            // LINKED right away.
            toRemove.add(entry.getValue().getReference());
            deleted++;
        }

        new BlockedDispatch(operator, blockSize, clock).storeAndRemove(toStore, toRemove, importUser);
        LOGGER.info("External event reconciliation [" + provider.systemId() + "]: " + created + " new, " + changed
                + " changed, " + deleted + " deleted, " + unchanged + " unchanged ("
                + snapshot.size() + " source items)");
        return new StagingReconcileResult(false, null, created, changed, deleted, unchanged, null);
    }

    /** What the SOURCE said, without the display columns.
     *
     *  <p>{@code columns} is presentation shaped by our own mapper — adding one must not make
     *  every row "changed", which would rewrite the whole store and, worse, flag every bound row
     *  as "changed in the source" when nothing changed there. Scar 2026-08-11: widening
     *  sourceData did exactly that, 45 095 rows and ~4 250 false badges. */
    private String sourceFingerprint(ImportItem item)
    {
        if (item == null)
        {
            return "";
        }
        return json.toJson(item.getSourceData()) + '|' + json.toJson(item.getHierarchy());
    }

    private boolean isBound(ImportItem item) throws RaplaException
    {
        final String externalId = provider.externalIdOf(item);
        return externalId != null && operator.tryResolveExternalId(externalId) != null;
    }

    private Map<String, ExternalSyncEntity> readStagingRows() throws RaplaException
    {
        final Map<String, ExternalSyncEntity> result = new LinkedHashMap<>();
        for (ExternalSyncEntity entity : operator
                .getImportExportEntities(provider.systemId(), ImportExportDirections.IMPORT).values())
        {
            if (ExternalEventStagingConstants.CONTEXT_EVENT_STAGING.equals(entity.getContext()))
            {
                result.put(entity.getId(), entity);
            }
        }
        return result;
    }

    private StagedRowData parseRow(ExternalSyncEntity row)
    {
        return StagedRowData.of(json, row);
    }

    private ExternalSyncEntityImpl newRow(String id, StagedRowData data)
    {
        final ExternalSyncEntityImpl entity = new ExternalSyncEntityImpl();
        entity.setId(id);
        entity.setExternalSystem(provider.systemId());
        entity.setDirection(ImportExportDirections.IMPORT);
        entity.setContext(ExternalEventStagingConstants.CONTEXT_EVENT_STAGING);
        entity.setData(json.toJson(data));
        return entity;
    }

    private ExternalSyncEntityImpl edit(ExternalSyncEntity row, StagedRowData data)
    {
        final ExternalSyncEntityImpl clone = (ExternalSyncEntityImpl) row.clone();
        clone.setData(json.toJson(data));
        return clone;
    }

    private StagingMeta readMeta() throws RaplaException
    {
        final ExternalSyncEntity row = metaRow();
        if (row == null || row.getData() == null || row.getData().isEmpty())
        {
            return new StagingMeta();
        }
        return json.fromJson(row.getData(), StagingMeta.class);
    }

    private ExternalSyncEntity metaRow() throws RaplaException
    {
        return operator.getImportExportEntities(provider.systemId(), ImportExportDirections.IMPORT)
                .get(ExternalEventStagingConstants.META_ID);
    }

    private void storeMeta(StagingMeta meta) throws RaplaException
    {
        final ExternalSyncEntity row = metaRow();
        final ExternalSyncEntityImpl entity;
        if (row != null)
        {
            entity = (ExternalSyncEntityImpl) row.clone();
        }
        else
        {
            entity = new ExternalSyncEntityImpl();
            entity.setId(ExternalEventStagingConstants.META_ID);
            entity.setExternalSystem(provider.systemId());
            entity.setDirection(ImportExportDirections.IMPORT);
            entity.setContext(ExternalEventStagingConstants.CONTEXT_EVENT_STAGING_META);
        }
        entity.setData(json.toJson(meta));
        operator.storeAndRemove(List.of(entity), List.of(), importUser);
    }
}
