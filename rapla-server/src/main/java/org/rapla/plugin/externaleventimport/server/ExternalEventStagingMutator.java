package org.rapla.plugin.externaleventimport.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.storage.ExternalSyncEntity;
import org.rapla.entities.storage.ImportExportDirections;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.ExternalSyncEntityImpl;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.externaleventimport.ExternalEventAttribution;
import org.rapla.plugin.externaleventimport.ImportItem;
import org.rapla.rest.JsonParserWrapper;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.PermissionController;
import org.rapla.storage.impl.server.BlockedDispatch;

/**
 * User-driven state changes on staged rows: dismissing a change marker today, ignore/restore
 * next. Needs no source connection, so it wires on the web tier alongside the worklist.
 *
 * <p>Authorization is ours ({@code canAllocate} on the item's group, silently skipping what the
 * caller may not touch); the write itself runs as the configured staging user, because staging
 * rows are system bookkeeping rather than user data.
 */
public class ExternalEventStagingMutator
{
    private final JsonParserWrapper.JsonParser json = JsonParserWrapper.defaultJson().get();
    private final CachableStorageOperator operator;
    private final ExternalEventAttribution attribution;
    private final ExternalEventStagingReader reader;
    private final User stagingUser;

    public ExternalEventStagingMutator(CachableStorageOperator operator, ExternalEventAttribution attribution,
            ExternalEventStagingReader reader, User stagingUser)
    {
        this.operator = operator;
        this.attribution = attribution;
        this.reader = reader;
        this.stagingUser = stagingUser;
    }

    /**
     * Clears the "changed in the source" marker. Not a source change and not a binding change —
     * it only says a human has seen it.
     *
     * @return how many rows were actually cleared; ids the caller may not touch, unknown ids and
     *         rows without a marker are silently absent from that count
     */
    public int dismissChanges(User caller, PermissionController permissionController, Collection<String> sourceItemIds)
            throws RaplaException
    {
        if (caller == null || permissionController == null || sourceItemIds == null || sourceItemIds.isEmpty())
        {
            return 0;
        }
        final Set<String> wanted = new LinkedHashSet<>(sourceItemIds);
        final List<ImportItem> items = new ArrayList<>();
        final List<ExternalSyncEntity> rows = new ArrayList<>();
        for (ExternalSyncEntity row : operator
                .getImportExportEntities(attribution.systemId(), ImportExportDirections.IMPORT).values())
        {
            if (!ExternalEventStagingConstants.CONTEXT_EVENT_STAGING.equals(row.getContext()))
            {
                continue;
            }
            final StagedRowData data = StagedRowData.of(json, row);
            final ImportItem item = data.getSource();
            if (item == null || !wanted.contains(item.getSourceItemId()) || data.getChangedSince() == null)
            {
                continue;
            }
            items.add(item);
            rows.add(row);
        }
        if (items.isEmpty())
        {
            return 0;
        }
        final Collection<String> allowed = reader.bookableGroups(caller, permissionController, items).keySet();
        return clearMarkers(rows.stream()
                .filter(row -> allowed.contains(StagedRowData.of(json, row).getSource().getSourceItemId())).toList());
    }

    /** Clears the "changed" marker of one item after its change was applied ("Übernehmen"), but only
     *  while it is still the marker the applied source was read with — a newer change that arrived
     *  meanwhile stays visible. The caller has already passed the permission gates. */
    boolean clearChanged(String sourceItemId, String expectedChangedSince) throws RaplaException
    {
        final List<ExternalSyncEntity> rows = new ArrayList<>();
        for (ExternalSyncEntity row : operator
                .getImportExportEntities(attribution.systemId(), ImportExportDirections.IMPORT).values())
        {
            final StagedRowData data = StagedRowData.of(json, row);
            if (ExternalEventStagingConstants.CONTEXT_EVENT_STAGING.equals(row.getContext()) && data.getSource() != null
                    && sourceItemId.equals(data.getSource().getSourceItemId())
                    && java.util.Objects.equals(expectedChangedSince, data.getChangedSince()))
            {
                rows.add(row);
            }
        }
        return clearMarkers(rows) > 0;
    }

    private int clearMarkers(List<ExternalSyncEntity> rows) throws RaplaException
    {
        final List<Entity> toStore = new ArrayList<>();
        for (ExternalSyncEntity row : rows)
        {
            final StagedRowData data = StagedRowData.of(json, row);
            data.setChangedSince(null);
            final ExternalSyncEntityImpl clone = (ExternalSyncEntityImpl) row.clone();
            clone.setData(json.toJson(data));
            toStore.add(clone);
        }
        if (toStore.isEmpty())
        {
            return 0;
        }
        new BlockedDispatch(operator, BlockedDispatch.DEFAULT_BLOCK_SIZE, System::currentTimeMillis)
                .storeAndRemove(toStore, List.<ReferenceInfo<ExternalSyncEntity>> of(), stagingUser);
        return toStore.size();
    }
}
